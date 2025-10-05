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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity() {

    // UI and Rendering
    private lateinit var textureView: TextureView // textureView variable
    private lateinit var glSurfaceView: GLSurfaceView // glSurfaceView variable
    private lateinit var bitmapRenderer: BitmapRenderer // bitmapRender
    private lateinit var toggleButton: Button // Button variable

    // State Management
    private var isProcessingEnabled = true // State to control processing

    // A reusable bitmap for the processed output
    private var processedBitmap: Bitmap? = null

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

        // Initialize all our views
        textureView = findViewById(R.id.textureView)
        glSurfaceView = findViewById(R.id.glSurfaceView)
        toggleButton = findViewById(R.id.toggleButton) // ADDED: Find the button

        setupOpenGL()
        setupClickListener() // ADDED: Set up the button's click listener

        textureView.surfaceTextureListener = surfaceTextureListener
        checkCameraPermission()
    }

    // ADDED: A new function to handle the button click
    private fun setupClickListener() {
        toggleButton.setOnClickListener {
            // Flip the processing state
            isProcessingEnabled = !isProcessingEnabled
            // Update the button text to reflect the current state
            toggleButton.text = if (isProcessingEnabled) "Show Original" else "Show Processed"
        }
    }

    // This is called for every new frame from the camera
    private fun processCurrentFrame() {
        val bitmap = textureView.bitmap ?: return

        // MODIFIED: Decide which bitmap to show based on our state variable
        val finalBitmapToShow: Bitmap
        if (isProcessingEnabled) {
            // If processing is on, call the C++ function
            if (processedBitmap == null || processedBitmap!!.width != bitmap.width || processedBitmap!!.height != bitmap.height) {
                processedBitmap = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            }
            processFrameToBitmap(bitmap, processedBitmap!!)
            finalBitmapToShow = processedBitmap!!
        } else {
            // If processing is off, just show the original camera frame
            finalBitmapToShow = bitmap
        }

        // Give the final bitmap (either processed or original) to our OpenGL renderer
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
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            processCurrentFrame()
        }
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
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
        }
        override fun onDisconnected(camera: CameraDevice) {
            camera.close(); cameraDevice = null
        }
        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
        }
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
        startBackgroundThread()
        glSurfaceView.onResume()
        if (textureView.isAvailable) {
            openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        cameraCaptureSession?.close()
        cameraDevice?.close()
        stopBackgroundThread()
    }
}