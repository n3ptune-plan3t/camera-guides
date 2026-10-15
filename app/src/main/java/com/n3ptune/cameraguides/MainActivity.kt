package com.n3ptune.cameraguides

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var preview: PreviewView
    private lateinit var overlay: GuideOverlayView
    private lateinit var techName: TextView
    private lateinit var techDesc: TextView
    private lateinit var btnFlash: TextView
    private lateinit var btnGuides: TextView
    private val chipViews = mutableListOf<TextView>()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else toast("Camera permission is required")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Android 15 enforces edge-to-edge for targetSdk 35
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        techName = findViewById(R.id.techName)
        techDesc = findViewById(R.id.techDesc)
        btnFlash = findViewById(R.id.btnFlash)
        btnGuides = findViewById(R.id.btnGuides)

        buildChips()
        select(savedInstanceState?.getInt(KEY_TECHNIQUE) ?: 0)

        btnGuides.setOnClickListener {
            overlay.guidesVisible = !overlay.guidesVisible
            btnGuides.text = if (overlay.guidesVisible) "Guides: On" else "Guides: Off"
        }
        btnFlash.setOnClickListener {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = flashMode
            btnFlash.text = when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> "Flash: Auto"
                ImageCapture.FLASH_MODE_ON -> "Flash: On"
                else -> "Flash: Off"
            }
        }
        findViewById<View>(R.id.btnFlip).setOnClickListener {
            lens = if (lens == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            bindCamera()
        }
        findViewById<View>(R.id.shutter).setOnClickListener { takePhoto() }

        preview.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val point = preview.meteringPointFactory.createPoint(e.x, e.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                overlay.showFocus(e.x, e.y)
                v.performClick()
            }
            true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private var selected = 0

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TECHNIQUE, selected)
    }

    private fun buildChips() {
        val container = findViewById<LinearLayout>(R.id.chips)
        val dp = resources.displayMetrics.density
        techniques.forEachIndexed { i, t ->
            val chip = TextView(this).apply {
                text = t.name
                textSize = 14f
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                setOnClickListener { select(i) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (8 * dp).toInt() }
            container.addView(chip, lp)
            chipViews.add(chip)
        }
    }

    private fun select(index: Int) {
        selected = index
        val t = techniques[index]
        overlay.technique = t
        techName.text = t.name
        techDesc.text = t.description
        val dp = resources.displayMetrics.density
        chipViews.forEachIndexed { i, chip ->
            val on = i == index
            chip.background = GradientDrawable().apply {
                cornerRadius = 20 * dp
                setColor(if (on) 0xFFFFFFFF.toInt() else 0x33FFFFFF)
            }
            chip.setTextColor(if (on) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            provider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val p = provider ?: return
        var selector = CameraSelector.Builder().requireLensFacing(lens).build()
        if (!p.hasCamera(selector)) {
            // Device has only one camera (or an emulator): fall back to the default.
            selector = CameraSelector.DEFAULT_BACK_CAMERA.takeIf { p.hasCamera(it) }
                ?: CameraSelector.DEFAULT_FRONT_CAMERA.takeIf { p.hasCamera(it) }
                ?: run { toast("No camera found"); return }
        }
        // 4:3 stream in a 3:4 viewfinder, so the overlay lines up with the picture.
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .build()
        val previewUse = Preview.Builder().setResolutionSelector(resolution).build()
            .also { it.surfaceProvider = preview.surfaceProvider }
        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolution)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()
        try {
            p.unbindAll()
            camera = p.bindToLifecycle(this, selector, previewUse, capture)
            imageCapture = capture
        } catch (e: Exception) {
            toast("Could not start camera: ${e.message}")
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val name = "CAM_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraGuides")
        }
        val options = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .build()
        overlay.flash()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    toast("Saved to Pictures/CameraGuides")
                }

                override fun onError(e: ImageCaptureException) {
                    toast("Capture failed: ${e.message}")
                }
            },
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val KEY_TECHNIQUE = "technique"
    }
}
