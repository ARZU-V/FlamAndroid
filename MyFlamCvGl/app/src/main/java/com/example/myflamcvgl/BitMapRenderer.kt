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

/**
 * This class does all the hard work of drawing the picture on the screen using OpenGL.
 * It's a special renderer that knows how to take a Bitmap and turn it into a texture.
 */
class BitmapRenderer : GLSurfaceView.Renderer {

    // These are just easy names for our effects so we don't have to remember numbers.
    companion object {
        const val EFFECT_NONE = 0
        const val EFFECT_GRAYSCALE = 1
        const val EFFECT_INVERT = 2
    }
    var currentEffect: Int = EFFECT_NONE

    // This short program tells the GPU where to draw the rectangle for our video.
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 vTexCoord;
        varying vec2 TexCoord;
        void main() {
          gl_Position = vPosition;
          TexCoord = vTexCoord;
        }
    """.trimIndent()


    // This tells the GPU how to color each pixel. It's where our cool effects live.
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

    // Just a simple rectangle that fills the whole screen.
    private val quadVertices = floatArrayOf(
        // X, Y, Z
        -1.0f, -1.0f, 0.0f, // Bottom Left
        1.0f, -1.0f, 0.0f, // Bottom Right
        -1.0f,  1.0f, 0.0f, // Top Left
        1.0f,  1.0f, 0.0f, // Top Right
    )

    // Tells OpenGL how to map the texture (our image) onto the rectangle.
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

    /**
     * MainActivity calls this to give us the newest video frame.
     * 'synchronized' is important 'cause this is called from a different thread.
     */
    fun updateBitmap(bitmap: Bitmap) {
        synchronized(this) {
            bitmapToRender = bitmap
            bitmapNeedsUpdate = true
        }
    }

    /**
     * This runs once when the view is first created. Good for all the setup stuff.
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // Black background

        // Put our rectangle coordinates into special buffers that OpenGL can read fast.
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

        // Compile our shader programs so the GPU can run them.
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        shaderProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        // Create a single texture handle to hold our video frame.
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
    }

    /**
     * This is the main drawing loop! It runs for every single frame we want to show.
     */
    override fun onDrawFrame(gl: GL10?) {
        // First, check if MainActivity gave us a new bitmap to draw.
        synchronized(this) {
            if (bitmapNeedsUpdate) {
                val bitmap = bitmapToRender
                if (bitmap != null && !bitmap.isRecycled) {
                    loadBitmapToTexture(bitmap)
                }
                bitmapNeedsUpdate = false
            }
        }

        // Clear the screen, then tell OpenGL to use our shader program.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(shaderProgram)

        // Find the variables inside our shader program.
        val positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        val effectTypeHandle = GLES20.glGetUniformLocation(shaderProgram, "uEffectType")

        // This is the magic line that tells the shader which effect to use (None, Grayscale, etc.).
        GLES20.glUniform1i(effectTypeHandle, currentEffect)

        // Give the shader our rectangle's coordinates.
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // Give the shader our texture's coordinates.
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        // Tell the shader which texture to use for drawing.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        // Finally, draw the rectangle!
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Clean up.
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    // A helper function to compile a shader.
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    // A helper function to upload our Android Bitmap to the GPU so it can be drawn.
    private fun loadBitmapToTexture(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set some parameters for how the texture should be drawn.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // This is the Android utility that does the actual upload.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }
}