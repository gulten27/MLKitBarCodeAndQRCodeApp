package com.gultendogan.barcodeandqrcodeapp.barcode

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.gultendogan.barcodeandqrcodeapp.R
import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.gultendogan.barcodeandqrcodeapp.databinding.ActivityMainBinding
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.Barcode.FORMAT_QR_CODE
import com.gultendogan.barcodeandqrcodeapp.databinding.ActivityBarcodeBinding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


// This is an array of all the permission specified in the manifest.
val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
const val RATIO_4_3_VALUE = 4.0 / 3.0
const val RATIO_16_9_VALUE = 16.0 / 9.0
typealias BarcodeAnalyzerListener = (barcode: MutableList<Barcode>) -> Unit

class BarcodeActivity : AppCompatActivity() {
    private val executor by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val multiPermissionCallback =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.entries.size < 1) {
                Toast.makeText(this, "Please Accept all the permissions", Toast.LENGTH_SHORT).show()
            } else {
                binding.viewFinder.post {
                    startCamera()
                }
            }

        }

    private var processingBarcode = AtomicBoolean(false)
    private lateinit var binding: ActivityBarcodeBinding
    private lateinit var cameraInfo: CameraInfo
    private lateinit var cameraControl: CameraControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding =DataBindingUtil.setContentView(this, R.layout.activity_barcode)
        binding.clearText.setOnClickListener {
            val myClipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val myClip = ClipData.newPlainText("barcode data", binding.BarcodeValue.text.toString())
            myClipboard.setPrimaryClip(myClip)
            Toast.makeText(this, "Text copied to cliboard", Toast.LENGTH_SHORT).show()
        }
        // Request camera permissions
        multiPermissionCallback.launch(
            REQUIRED_PERMISSIONS
        )
        if (allPermissionsGranted()) {
            binding.viewFinder.post {
                //Initialize graphics overlay
                startCamera()
            }
        } else {
            requestAllPermissions()
        }


    }
    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCamera() {

        val metrics = DisplayMetrics().also { binding.viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        val rotation = binding.viewFinder.display.rotation

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

            val textBarcodeAnalyzer = initializeAnalyzer(screenAspectRatio, rotation)
            cameraProvider.unbindAll()

            try {
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, textBarcodeAnalyzer
                )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                cameraControl.setLinearZoom(0.5f)


            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun requestAllPermissions() {
        multiPermissionCallback.launch(
            REQUIRED_PERMISSIONS
        )
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun initializeAnalyzer(screenAspectRatio: Int, rotation: Int): UseCase {
        return ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .build()
            .also {

                it.setAnalyzer(executor, BarCodeAnalyser { barcode ->
                    if (processingBarcode.compareAndSet(false, false)) {
                        onBarcodeDetected(barcode)
                    }
                })
            }
    }


    private fun onBarcodeDetected(barcodes: List<Barcode>) {
        if (barcodes.isNotEmpty()) {

            binding.BarcodeValue.text = barcodes[0].rawValue
            if (barcodes[0].format == FORMAT_QR_CODE) {
                Toast.makeText(this, "QR code Detected", Toast.LENGTH_SHORT).show()
                finish()
            } else {

                Toast.makeText(this, "Bar code Detected", Toast.LENGTH_SHORT).show()
            }


        }
    }

    override fun onDestroy() {
        super.onDestroy()
        finish()
    }
}