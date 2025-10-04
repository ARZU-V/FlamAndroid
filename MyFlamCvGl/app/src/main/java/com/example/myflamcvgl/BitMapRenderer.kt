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

    private val vertexShaderCode =
        "attribute vec4 vPosition;" +
                "attribute vec2 vTexCoord;" +
                "varying vec2 TexCoord;" +
                "void main() {" +
                "  gl_Position = vPosition;" +
                "  TexCoord = vTexCoord;" +
                "}"

    private val fragmentShaderCode =
        "precision mediump float;" +
                "uniform sampler2D sTexture;" +
                "varying vec2 TexCoord;" +
                "void main() {" +
                "  gl_FragColor = texture2D(sTexture, TexCoord);" +
                "}"

    private val squareCoords = floatArrayOf(
        -1.0f,  1.0f, // top left
        -1.0f, -1.0f, // bottom left
        1.0f, -1.0f, // bottom right
        1.0f,  1.0f  // top right
    )

    private val textureCoords = floatArrayOf(
        0.0f, 0.0f, // top left
        0.0f, 1.0f, // bottom left
        1.0f, 1.0f, // bottom right
        1.0f, 0.0f  // top right
    )

    private var vertexBuffer: FloatBuffer
    private var textureBuffer: FloatBuffer
    private var program: Int = 0
    private var textureId: Int = 0
    private var bitmapToRender: Bitmap? = null
    private val bitmapLock = Any()

    init {
        vertexBuffer = ByteBuffer.allocateDirect(squareCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(squareCoords)
                position(0)
            }
        }
        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(textureCoords)
                position(0)
            }
        }
    }

    fun updateBitmap(bitmap: Bitmap) {
        synchronized(bitmapLock) {
            bitmapToRender = bitmap
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { shader ->
            GLES20.glShaderSource(shader, vertexShaderCode)
            GLES20.glCompileShader(shader)
        }

        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { shader ->
            GLES20.glShaderSource(shader, fragmentShaderCode)
            GLES20.glCompileShader(shader)
        }

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        synchronized(bitmapLock) {
            bitmapToRender?.let {
                if (!it.isRecycled) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0)
                }
            }
        }

        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }
}