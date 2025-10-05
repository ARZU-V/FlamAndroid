package com.example.myflamcvgl


import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BitmapRenderer : GLSurfaceView.Renderer {
    companion object {
        const val EFFECT_NONE = 0
        const val EFFECT_GRAYSCALE = 1
        const val EFFECT_INVERT = 2
    }
    var currentEffect: Int = EFFECT_NONE
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 vTexCoord;
        varying vec2 TexCoord;
        void main() {
          gl_Position = vPosition;
          TexCoord = vTexCoord;
        }
    """.trimIndent()


    private val fragmentShaderCode = """
    precision mediump float;
    uniform sampler2D uTexture;
    varying vec2 TexCoord;

    uniform int uEffectType; 

    void main() {
      // Get the original color from the texture
      vec4 originalColor = texture2D(uTexture, TexCoord);

      if (uEffectType == 1) {
        // --- GRAYSCALE EFFECT ---
        float gray = dot(originalColor.rgb, vec3(0.299, 0.587, 0.114));
        gl_FragColor = vec4(gray, gray, gray, 1.0);

      } else if (uEffectType == 2) {
        // --- INVERT EFFECT ---
        gl_FragColor = vec4(1.0 - originalColor.r, 1.0 - originalColor.g, 1.0 - originalColor.b, 1.0);

      } else {
        // --- NO EFFECT (uEffectType == 0) ---
        gl_FragColor = originalColor;
      }
    }
""".trimIndent()

    private val quadVertices = floatArrayOf(
        // X, Y, Z
        -1.0f, -1.0f, 0.0f, // Bottom Left
        1.0f, -1.0f, 0.0f, // Bottom Right
        -1.0f,  1.0f, 0.0f, // Top Left
        1.0f,  1.0f, 0.0f, // Top Right
    )

    private val textureCoords = floatArrayOf(
        // U, V
        0.0f, 1.0f, // Bottom Left
        1.0f, 1.0f, // Bottom Right
        0.0f, 0.0f, // Top Left
        1.0f, 0.0f  // Top Right
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private var shaderProgram: Int = 0
    private var textureId: Int = 0

    @Volatile
    private var bitmapToRender: Bitmap? = null
    private var bitmapNeedsUpdate = false

    fun updateBitmap(bitmap: Bitmap) {
        synchronized(this) {
            bitmapToRender = bitmap
            bitmapNeedsUpdate = true
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // Black background

        // Initialize buffers
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(quadVertices)
                position(0)
            }
        }
        texCoordBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(textureCoords)
                position(0)
            }
        }

        // Compile shaders and link program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Create texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
    }

    override fun onDrawFrame(gl: GL10?) {
        // Handle bitmap update inside the GL thread
        synchronized(this) {
            if (bitmapNeedsUpdate) {
                val bitmap = bitmapToRender
                if (bitmap != null && !bitmap.isRecycled) {
                    loadBitmapToTexture(bitmap)
                }
                bitmapNeedsUpdate = false
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(shaderProgram)

        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        val effectTypeHandle = GLES20.glGetUniformLocation(shaderProgram, "uEffectType")
        GLES20.glUniform1i(effectTypeHandle, currentEffect)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun loadBitmapToTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }
}