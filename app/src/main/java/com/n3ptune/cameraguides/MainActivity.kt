package com.n3ptune.cameraguides

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MpOption(val mp: Int, val size: Size, val isMaxSensor: Boolean)

class MainActivity : ComponentActivity() {

    private lateinit var preview: PreviewView
        private lateinit var overlay: GuideOverlayView
            private lateinit var techName: TextView
                private lateinit var techDesc: TextView
                    private lateinit var btnMp: TextView
                        private lateinit var btnFlash: TextView
                            private lateinit var btnGuides: TextView
                                private val chipViews = mutableListOf<TextView>()

                                private var provider: ProcessCameraProvider? = null
                                    private var camera: Camera? = null
                                        private var imageCapture: ImageCapture? = null
                                            private var lens = CameraSelector.LENS_FACING_BACK
                                            private var flashMode = ImageCapture.FLASH_MODE_OFF

                                            private var mpOptions = listOf<MpOption>()
                                            private var selectedMpIndex = 0

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
                                                btnMp = findViewById(R.id.btnMp)
                                                btnFlash = findViewById(R.id.btnFlash)
                                                btnGuides = findViewById(R.id.btnGuides)

                                                buildChips()
                                                select(savedInstanceState?.getInt(KEY_TECHNIQUE) ?: 0)
                                                selectedMpIndex = savedInstanceState?.getInt(KEY_MP_INDEX) ?: 0

                                                btnMp.setOnClickListener {
                                                    if (mpOptions.size <= 1) {
                                                        val only = mpOptions.firstOrNull()
                                                        if (only != null) {
                                                            toast("Only ${only.mp}MP (${only.size.width}x${only.size.height}) exposed by hardware")
                                                        }
                                                        return@setOnClickListener
                                                    }
                                                    selectedMpIndex = (selectedMpIndex + 1) % mpOptions.size
                                                    val opt = mpOptions[selectedMpIndex]
                                                    toast("${opt.mp}MP (${opt.size.width}x${opt.size.height})")
                                                    bindCamera(resetMpList = false)
                                                }

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
                                                    selectedMpIndex = 0
                                                    bindCamera(resetMpList = true)
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
                                                outState.putInt(KEY_MP_INDEX, selectedMpIndex)
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
                                                    bindCamera(resetMpList = true)
                                                }, ContextCompat.getMainExecutor(this))
                                            }

                                            /**
                                             * Queries Camera2 StreamConfigurationMap (both default pixel-binned and API 31+
                                             * SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION for unbinned Quad-Bayer 48/50/64/108/200MP)
                                             * and groups distinct 4:3 megapixel tiers (e.g. 12MP, 24MP, 50MP).
                                             */
                                            @OptIn(ExperimentalCamera2Interop::class)
                                            private fun queryMpOptions(cameraInfo: CameraInfo): List<MpOption> {
                                                val chars = Camera2CameraInfo.extractCameraCharacteristics(cameraInfo)
                                                val defaultMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                                                val defaultSizes = mutableListOf<Size>()
                                                if (defaultMap != null) {
                                                    defaultMap.getOutputSizes(ImageFormat.JPEG)?.let { defaultSizes.addAll(it) }
                                                    defaultMap.getHighResolutionOutputSizes(ImageFormat.JPEG)?.let { defaultSizes.addAll(it) }
                                                }

                                                val maxResSizes = mutableListOf<Size>()
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    val maxMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                                                    if (maxMap != null) {
                                                        maxMap.getOutputSizes(ImageFormat.JPEG)?.let { maxResSizes.addAll(it) }
                                                        maxMap.getHighResolutionOutputSizes(ImageFormat.JPEG)?.let { maxResSizes.addAll(it) }
                                                    }
                                                }

                                                fun is4by3(s: Size): Boolean {
                                                    val w = max(s.width, s.height).toFloat()
                                                    val h = min(s.width, s.height).toFloat()
                                                    return h > 0f && abs((w / h) - (4f / 3f)) < 0.035f
                                                }

                                                val candidates = mutableListOf<MpOption>()
                                                for (s in defaultSizes) {
                                                    if (is4by3(s)) {
                                                        val mp = ((s.width.toLong() * s.height.toLong()) / 1_000_000.0).roundToInt()
                                                        if (mp >= 2) candidates.add(MpOption(mp, s, isMaxSensor = false))
                                                    }
                                                }
                                                for (s in maxResSizes) {
                                                    if (is4by3(s)) {
                                                        val mp = ((s.width.toLong() * s.height.toLong()) / 1_000_000.0).roundToInt()
                                                        if (mp >= 2) candidates.add(MpOption(mp, s, isMaxSensor = true))
                                                    }
                                                }

                                                // Deduplicate by rounded MP (keep highest pixel count per MP label)
                                                val byMp = candidates
                                                .groupBy { it.mp }
                                                .mapValues { (_, list) ->
                                                    list.maxBy { it.size.width.toLong() * it.size.height.toLong() }
                                                }
                                                .values
                                                .sortedBy { it.mp }

                                                if (byMp.isEmpty()) return emptyList()

                                                    // Find the standard default (highest 4:3 resolution from default pixel-binned map, usually 12MP or 16MP)
                                                    val defaultMax = candidates.filter { !it.isMaxSensor }
                                                    .maxByOrNull { it.size.width.toLong() * it.size.height.toLong() }
                                                    ?: byMp.first()

                                                    // Include practical shooting tiers:
                                                    // 1. Standard default binned MP (e.g., 12MP)
                                                    // 2. Any higher MP modes exposed (e.g., 24MP, 48MP, 50MP, 64MP, 108MP, 200MP)
                                                    // 3. One compact lower-MP tier if available (e.g., 5MP or 8MP for lightweight sharing)
                                                    val result = mutableListOf<MpOption>()
                                                    val compactTier = byMp.filter { it.mp in 3 until defaultMax.mp - 2 }
                                                    .maxByOrNull { it.mp }
                                                    if (compactTier != null) result.add(compactTier)

                                                        result.add(defaultMax)
                                                        for (opt in byMp) {
                                                            if (opt.mp > defaultMax.mp + 1) {
                                                                result.add(opt)
                                                            }
                                                        }

                                                        // Default selection to the standard pixel-binned mode (like the stock camera app)
                                                        return result.distinctBy { it.mp }
                                            }

                                            private fun bindCamera(resetMpList: Boolean = false) {
                                                val p = provider ?: return
                                                var selector = CameraSelector.Builder().requireLensFacing(lens).build()
                                                if (!p.hasCamera(selector)) {
                                                    selector = CameraSelector.DEFAULT_BACK_CAMERA.takeIf { p.hasCamera(it) }
                                                    ?: CameraSelector.DEFAULT_FRONT_CAMERA.takeIf { p.hasCamera(it) }
                                                    ?: run { toast("No camera found"); return }
                                                }

                                                val camInfo = selector.filter(p.availableCameraInfos).firstOrNull()
                                                if (camInfo != null && (resetMpList || mpOptions.isEmpty())) {
                                                    mpOptions = queryMpOptions(camInfo)
                                                    // Start on the standard default binned mode (e.g., 12MP)
                                                    val stdIdx = mpOptions.indexOfFirst { !it.isMaxSensor && it.mp >= 8 }
                                                    selectedMpIndex = if (stdIdx >= 0) stdIdx else mpOptions.lastIndex.coerceAtLeast(0)
                                                }

                                                val currentMp = mpOptions.getOrNull(selectedMpIndex)
                                                btnMp.text = if (currentMp != null) "${currentMp.mp}MP" else "4:3"

                                                val aspectStrategy = AspectRatioStrategy(
                                                    AspectRatio.RATIO_4_3,
                                                    AspectRatioStrategy.FALLBACK_RULE_AUTO,
                                                )

                                                val previewResolution = ResolutionSelector.Builder()
                                                .setAspectRatioStrategy(aspectStrategy)
                                                .build()

                                                val captureResolutionBuilder = ResolutionSelector.Builder()
                                                .setAspectRatioStrategy(aspectStrategy)

                                                if (currentMp != null) {
                                                    captureResolutionBuilder.setResolutionStrategy(
                                                        ResolutionStrategy(
                                                            currentMp.size,
                                                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                                        )
                                                    )
                                                    if (currentMp.isMaxSensor) {
                                                        captureResolutionBuilder.setAllowedResolutionMode(
                                                            ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                                                        )
                                                    }
                                                }

                                                val previewUse = Preview.Builder()
                                                .setResolutionSelector(previewResolution)
                                                .build()
                                                .also { it.surfaceProvider = preview.surfaceProvider }

                                                val capture = ImageCapture.Builder()
                                                .setResolutionSelector(captureResolutionBuilder.build())
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
                                                val mpTag = mpOptions.getOrNull(selectedMpIndex)?.let { "_${it.mp}MP" } ?: ""
                                                val name = "CAM_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + mpTag
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
                                                                            val resInfo = capture.resolutionInfo?.resolution
                                                                            val msg = if (resInfo != null) {
                                                                                val actualMp = ((resInfo.width.toLong() * resInfo.height.toLong()) / 1_000_000.0).roundToInt()
                                                                                "Saved ${actualMp}MP (${resInfo.width}x${resInfo.height}) to Pictures/CameraGuides"
                                                                            } else {
                                                                                "Saved to Pictures/CameraGuides"
                                                                            }
                                                                            toast(msg)
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
                                                    private const val KEY_MP_INDEX = "mp_index"
                                                }
}
