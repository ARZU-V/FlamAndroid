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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity() {

    // UI and Rendering
    private lateinit var textureView: TextureView
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var bitmapRenderer: BitmapRenderer

    // A reusable bitmap for processing
    private var processedBitmap: Bitmap? = null

    // Camera and Threading
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread

    // Native JNI function
    private external fun CannyEdgeDetection(bitmapIn: Bitmap, bitmapOut: Bitmap)

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val TAG = "CameraApp"
        init {
            System.loadLibrary("myflamcvgl") // Make sure your library name is correct
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        glSurfaceView = findViewById(R.id.glSurfaceView)

        // Setup OpenGL
        glSurfaceView.setEGLContextClientVersion(2)
        bitmapRenderer = BitmapRenderer()
        glSurfaceView.setRenderer(bitmapRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        textureView.surfaceTextureListener = surfaceTextureListener
        checkCameraPermission()
    }

    // This is called for every new frame from the camera.
    private fun processFrameAndRender() {
        // Get the current frame from the invisible TextureView
        val bitmap = textureView.bitmap ?: return

        // Initialize our output bitmap once
        if (processedBitmap == null) {
            processedBitmap = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        }

        // Call our C++ function to apply Canny edge detection
        applyCannyEdgeDetection(bitmap, processedBitmap!!)

        // Update the renderer with the new processed bitmap
        bitmapRenderer.updateBitmap(processedBitmap!!)

        // Tell the GLSurfaceView to redraw itself
        glSurfaceView.requestRender()
    }

    // --- The rest of the camera setup code remains largely the same ---

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // This is the trigger for our processing pipeline
            processFrameAndRender()
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
            camera.close()
            cameraDevice = null
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