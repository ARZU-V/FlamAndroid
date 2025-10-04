package com.example.myflamcvgl // Make sure this is your correct package name

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.myflamcvgl.databinding.ActivityMainBinding // Import View Binding class
import java.lang.Float.max

class MainActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {

    // 1. Declare the binding variable
    private lateinit var binding: ActivityMainBinding

    private var srcBitmap: Bitmap? = null
    private var dstBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 2. Initialize the binding object
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        srcBitmap = BitmapFactory.decodeResource(this.resources, R.drawable.mountain)
        dstBitmap = srcBitmap!!.copy(srcBitmap!!.config ?: Bitmap.Config.ARGB_8888, true)

        // 3. Access views through the binding object
        binding.imageView.setImageBitmap(dstBitmap)
        binding.sldSigma.setOnSeekBarChangeListener(this)
    }

    private fun doBlur() {
        // Access progress through the binding object
        val sigma = max(0.1F, binding.sldSigma.progress / 10F)

        // The native call remains the same
        myBlur(srcBitmap!!, dstBitmap!!, sigma)

        // We need to invalidate the imageView to force it to redraw the updated bitmap
        binding.imageView.invalidate()
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        doBlur()
    }

    fun btnFlip_click(view: View) {
        this.myFlip(srcBitmap!!, srcBitmap!!)
        this.doBlur()
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

    /**
     * Native methods implemented by the 'myflamcvgl' library.
     */
    private external fun myBlur(bitmapIn: Bitmap, bitmapOut: Bitmap, sigma: Float)
    private external fun myFlip(bitmapIn: Bitmap, bitmapOut: Bitmap)

    companion object {
        init {
            // 4. Load the correct library name
            System.loadLibrary("myflamcvgl")
        }
    }
}