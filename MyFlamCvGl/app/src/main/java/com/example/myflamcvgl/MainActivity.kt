package com.example.myflamcvgl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
class MainActivity : AppCompatActivity() {

    // UI and Rendering
    private lateinit var textureView: TextureView
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var bitmapRenderer: BitmapRenderer
    private lateinit var toggleButton: Button

    private lateinit var fpsTextView: TextView


    // State Management
    private var isProcessingEnabled = true

    private var frameCount = 0
    private var lastFpsTime = 0L

    // A reusable bitmap for the processed output
    private var processedBitmap: Bitmap? = null

    // Networking
    private lateinit var webServer: WebServer // ADDED: WebServer variable

    // Camera and Threading
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread

    // Native C++ function
    external fun processFrameToBitmap(bitmapIn: Bitmap, bitmapOut: Bitmap)

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val TAG = "CameraApp"

        init {
            System.loadLibrary("native-lib")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webServer = WebServer() // ADDED: Initialize the WebServer

        // Initialize all our views
        textureView = findViewById(R.id.textureView)
        glSurfaceView = findViewById(R.id.glSurfaceView)
        toggleButton = findViewById(R.id.toggleButton)
        // For Fps
        fpsTextView = findViewById(R.id.fpsTextView)
        setupOpenGL()
        setupClickListener()

        textureView.surfaceTextureListener = surfaceTextureListener
        checkCameraPermission()
    }

    private fun setupClickListener() {
        toggleButton.setOnClickListener {
            isProcessingEnabled = !isProcessingEnabled
            toggleButton.text = if (isProcessingEnabled) "Show Original" else "Show Processed"
        }
    }

    private fun processCurrentFrame() {
        val bitmap = textureView.bitmap ?: return

        val finalBitmapToShow: Bitmap
        if (isProcessingEnabled) {
            if (processedBitmap == null || processedBitmap!!.width != bitmap.width || processedBitmap!!.height != bitmap.height) {
                processedBitmap = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            }
            processFrameToBitmap(bitmap, processedBitmap!!)
            finalBitmapToShow = processedBitmap!!
            //Used to update the FPS counter
            updateFpsCounter()
        } else {
            finalBitmapToShow = bitmap
        }

        // ADDED: Stream the final frame to the web server
        val stream = ByteArrayOutputStream()
        finalBitmapToShow.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        webServer.sendFrame(stream.toByteArray())

        // Give the final bitmap to our OpenGL renderer for local display
        bitmapRenderer.updateBitmap(finalBitmapToShow)
        glSurfaceView.requestRender()
    }

    private fun setupOpenGL() {
        glSurfaceView.setEGLContextClientVersion(2)
        bitmapRenderer = BitmapRenderer()
        glSurfaceView.setRenderer(bitmapRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    // --- The rest of the camera setup code remains the same ---

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { openCamera() }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { processCurrentFrame() }
    }

    private fun openCamera() {
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList[0]
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
            manager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera.", e)
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) { cameraDevice = camera; createCameraPreviewSession() }
        override fun onDisconnected(camera: CameraDevice) { camera.close(); cameraDevice = null }
        override fun onError(camera: CameraDevice, error: Int) { onDisconnected(camera) }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture!!
            texture.setDefaultBufferSize(textureView.width, textureView.height)
            val surface = Surface(texture)
            val previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)
            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create camera session.", e)
        }
    }

    private fun checkCameraPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            startBackgroundThread()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackgroundThread()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    /**
     * Calculates and displays the Frames Per Second (FPS). This function works by
     * incrementing a frame counter on every call. Approximately once per second,
     * it takes the accumulated count as the current FPS value, updates the UI,
     * and then resets the counter and timer for the next measurement interval.
     */

    private fun updateFpsCounter() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFpsTime >= 1000) {
            val fps = frameCount
            runOnUiThread {
                fpsTextView.text = "FPS: $fps"
            }
            frameCount = 0
            lastFpsTime = currentTime
        }
    }


    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, e.message, e)
        }
    }

    override fun onResume() {
        super.onResume()
        webServer.start() // Start the server when the app resumes
        startBackgroundThread()
        glSurfaceView.onResume()
        if (textureView.isAvailable) {
            openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        webServer.stop() //Stop the server when the app is paused
        glSurfaceView.onPause()
        cameraCaptureSession?.close()
        cameraDevice?.close()
        stopBackgroundThread()
    }
}