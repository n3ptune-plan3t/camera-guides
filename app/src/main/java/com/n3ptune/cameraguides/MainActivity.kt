package com.n3ptune.cameraguides

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Rational
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
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

/** Where a sensor mode comes from. Only STANDARD is the "default" (pixel-binned) camera-app mode. */
enum class Tier { STANDARD, HIGH_RES, MAX_RES }

data class MpOption(val size: Size, val tier: Tier) {
    val mp: Int get() = megapixels(size)
}

private class MpQuery(val options: List<MpOption>, val defaultIndex: Int)

/** Portrait-shaped ratio (w < h). w == 0 means "Full" (whole screen). */
data class RatioOption(val label: String, val w: Int, val h: Int) {
    val isFull: Boolean get() = w == 0
}

val ratios = listOf(
    RatioOption("4:3", 3, 4),
    RatioOption("3:2", 2, 3),
    RatioOption("16:9", 9, 16),
    RatioOption("1:1", 1, 1),
    RatioOption("5:4", 4, 5),
    RatioOption("Full", 0, 0),
)

fun megapixels(s: Size): Int = ((s.width.toLong() * s.height.toLong()) / 1_000_000.0).roundToInt()

private fun pixels(s: Size): Long = s.width.toLong() * s.height.toLong()

/** Size of a sensor frame after cropping to [target] (= long side / short side, >= 1). */
private fun croppedSize(sensor: Size, target: Double): Size {
    val l = max(sensor.width, sensor.height)
    val s = min(sensor.width, sensor.height)
    return if (l.toDouble() / s > target) Size((s * target).roundToInt(), s)
    else Size(l, (l / target).roundToInt())
}

class MainActivity : ComponentActivity() {

    private lateinit var root: ConstraintLayout
    private lateinit var viewfinder: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var controls: FrameLayout
    private lateinit var preview: PreviewView
    private lateinit var overlay: GuideOverlayView
    private lateinit var techName: TextView
    private lateinit var techDesc: TextView
    private lateinit var btnMp: TextView
    private lateinit var btnRatio: TextView
    private lateinit var btnFlash: TextView
    private lateinit var btnGuides: TextView
    private val chipViews = mutableListOf<TextView>()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    private var mpOptions = listOf<MpOption>()
    private var defaultMpIndex = 0
    private var selectedMpIndex = 0

    private var ratioIndex = 0
    private var viewportAspect = Rational(3, 4)
    private var targetAspect = 4.0 / 3.0 // long / short of the current output ratio

    private var selected = 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else toast("Camera permission is required")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Android 15 enforces edge-to-edge for targetSdk 35
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        viewfinder = findViewById(R.id.viewfinder)
        panel = findViewById(R.id.panel)
        controls = findViewById(R.id.controls)
        preview = findViewById(R.id.preview)
        overlay = findViewById(R.id.overlay)
        techName = findViewById(R.id.techName)
        techDesc = findViewById(R.id.techDesc)
        btnMp = findViewById(R.id.btnMp)
        btnRatio = findViewById(R.id.btnRatio)
        btnFlash = findViewById(R.id.btnFlash)
        btnGuides = findViewById(R.id.btnGuides)

        ratioIndex = savedInstanceState?.getInt(KEY_RATIO) ?: 0
        btnRatio.text = ratios[ratioIndex].label

        buildChips()
        select(savedInstanceState?.getInt(KEY_TECHNIQUE) ?: 0)

        btnMp.setOnClickListener {
            if (mpOptions.size <= 1) {
                mpOptions.firstOrNull()?.let {
                    toast("Only ${it.mp}MP (${it.size.width}x${it.size.height}) exposed by hardware")
                }
                return@setOnClickListener
            }
            selectedMpIndex = (selectedMpIndex + 1) % mpOptions.size
            val eff = croppedSize(mpOptions[selectedMpIndex].size, targetAspect)
            toast("${megapixels(eff)}MP (${eff.width}x${eff.height}) · ${ratios[ratioIndex].label}")
            bindCamera(resetMpList = false)
        }

        btnRatio.setOnClickListener {
            ratioIndex = (ratioIndex + 1) % ratios.size
            btnRatio.text = ratios[ratioIndex].label
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TECHNIQUE, selected)
        outState.putInt(KEY_RATIO, ratioIndex)
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
     * Lists the 4:3 sensor modes: STANDARD (default pixel-binned map, what the stock camera app
     * starts on, e.g. 12MP), HIGH_RES (default map's high-resolution sizes) and MAX_RES
     * (API 31+ maximum-resolution map, unbinned 48/50/108/200MP).
     * The default is always the largest STANDARD size, never a HIGH_RES/MAX_RES one.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun queryMpOptions(cameraInfo: CameraInfo): MpQuery {
        val chars = Camera2CameraInfo.extractCameraCharacteristics(cameraInfo)

        fun is4by3(s: Size): Boolean {
            val l = max(s.width, s.height).toFloat()
            val sh = min(s.width, s.height).toFloat()
            return sh > 0f && abs((l / sh) - (4f / 3f)) < 0.035f
        }

        val found = LinkedHashMap<Size, Tier>()
        fun add(sizes: Array<Size>?, tier: Tier) {
            sizes?.forEach { s ->
                if (is4by3(s) && megapixels(s) >= 2) {
                    val old = found[s]
                    if (old == null || tier.ordinal < old.ordinal) found[s] = tier
                }
            }
        }

        chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let {
            add(it.getOutputSizes(ImageFormat.JPEG), Tier.STANDARD)
            add(it.getHighResolutionOutputSizes(ImageFormat.JPEG), Tier.HIGH_RES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)?.let {
                add(it.getOutputSizes(ImageFormat.JPEG), Tier.MAX_RES)
                add(it.getHighResolutionOutputSizes(ImageFormat.JPEG), Tier.MAX_RES)
            }
        }

        val all = found.map { MpOption(it.key, it.value) }
        if (all.isEmpty()) return MpQuery(emptyList(), 0)

        val standardDefault = all.filter { it.tier == Tier.STANDARD }.maxByOrNull { pixels(it.size) }
            ?: all.filter { it.tier != Tier.MAX_RES }.maxByOrNull { pixels(it.size) }
            ?: all.minByOrNull { pixels(it.size) }!!

        // One entry per rounded MP value (largest size wins), but never replace the default itself.
        val byMp = all.groupBy { it.mp }.values
            .map { g -> if (g.any { it.size == standardDefault.size }) standardDefault else g.maxBy { pixels(it.size) } }
            .sortedBy { it.mp }

        val result = mutableListOf<MpOption>()
        // One compact tier below the default (e.g. 8MP) for light sharing.
        byMp.filter { it.mp in 3 until standardDefault.mp - 2 }.maxByOrNull { it.mp }?.let { result.add(it) }
        result.add(standardDefault)
        byMp.filter { it.mp > standardDefault.mp + 1 }.forEach { result.add(it) }

        val distinct = result.distinctBy { it.mp }
        return MpQuery(distinct, distinct.indexOfFirst { it.size == standardDefault.size }.coerceAtLeast(0))
    }

    /** Sizes the viewfinder / controls panel for the chosen ratio and records the crop viewport. */
    private fun applyRatioLayout() {
        val r = ratios[ratioIndex]
        val dm = resources.displayMetrics
        val dp = dm.density
        val availW = (if (root.width > 0) root.width else dm.widthPixels).toFloat()
        val availH = (if (root.height > 0) root.height - root.paddingTop - root.paddingBottom
        else dm.heightPixels).toFloat()

        val vf = viewfinder.layoutParams as ConstraintLayout.LayoutParams
        val pl = panel.layoutParams as ConstraintLayout.LayoutParams

        val wantedH = if (r.isFull) availH else availW * r.h / r.w
        val fill = r.isFull || wantedH >= availH - 1f

        if (fill) {
            vf.dimensionRatio = null
            vf.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            viewportAspect = Rational(availW.roundToInt(), availH.roundToInt())
            targetAspect = (availH / availW).toDouble()
        } else {
            vf.dimensionRatio = "H,${r.w}:${r.h}"
            vf.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
            viewportAspect = Rational(r.w, r.h)
            targetAspect = r.h.toDouble() / r.w
        }

        // Tall ratios leave no room under the viewfinder, so the controls float over its bottom.
        val floating = fill || (availH - wantedH) < PANEL_MIN_DP * dp
        if (floating) {
            pl.topToBottom = ConstraintLayout.LayoutParams.UNSET
            pl.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            pl.height = ViewGroup.LayoutParams.WRAP_CONTENT
            panel.setBackgroundColor(Color.argb(150, 0, 0, 0))
            controls.minimumHeight = (112 * dp).toInt()
        } else {
            pl.topToBottom = R.id.viewfinder
            pl.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            pl.height = 0
            panel.setBackgroundColor(Color.TRANSPARENT)
            controls.minimumHeight = 0
        }
        viewfinder.layoutParams = vf
        panel.layoutParams = pl
    }

    private fun updateMpLabel() {
        val cur = mpOptions.getOrNull(selectedMpIndex)
        btnMp.text = if (cur != null) "${megapixels(croppedSize(cur.size, targetAspect))}MP"
        else ratios[ratioIndex].label
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
            val q = queryMpOptions(camInfo)
            mpOptions = q.options
            defaultMpIndex = q.defaultIndex
            selectedMpIndex = defaultMpIndex // start on the stock default (binned) mode
        }
        selectedMpIndex = selectedMpIndex.coerceIn(0, max(mpOptions.lastIndex, 0))

        applyRatioLayout()
        updateMpLabel()

        // Sensor is always read as 4:3; the ViewPort crops preview AND photo to the chosen ratio.
        val aspectStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_4_3,
            AspectRatioStrategy.FALLBACK_RULE_AUTO,
        )
        val previewResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectStrategy)
            .build()

        val captureResolution = ResolutionSelector.Builder().setAspectRatioStrategy(aspectStrategy)
        mpOptions.getOrNull(selectedMpIndex)?.let { cur ->
            captureResolution.setResolutionStrategy(
                ResolutionStrategy(cur.size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            if (cur.tier != Tier.STANDARD) {
                captureResolution.setAllowedResolutionMode(
                    ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE
                )
            }
        }

        val previewUse = Preview.Builder()
            .setResolutionSelector(previewResolution)
            .build()
            .also { it.surfaceProvider = preview.surfaceProvider }

        val capture = ImageCapture.Builder()
            .setResolutionSelector(captureResolution.build())
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()

        val viewPort = ViewPort.Builder(viewportAspect, preview.display?.rotation ?: Surface.ROTATION_0)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(previewUse)
            .addUseCase(capture)
            .build()

        try {
            p.unbindAll()
            camera = p.bindToLifecycle(this, selector, group)
            imageCapture = capture
        } catch (e: Exception) {
            toast("Could not start camera: ${e.message}")
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val ratio = ratios[ratioIndex]
        val cur = mpOptions.getOrNull(selectedMpIndex)
        val mpTag = cur?.let { "_${megapixels(croppedSize(it.size, targetAspect))}MP" } ?: ""
        val name = "CAM_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
            mpTag + "_" + ratio.label.replace(':', 'x')
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
                    val info = capture.resolutionInfo
                    val msg = if (info != null) {
                        val w = info.cropRect.width()
                        val h = info.cropRect.height()
                        val actualMp = ((w.toLong() * h.toLong()) / 1_000_000.0).roundToInt()
                        "Saved ${actualMp}MP (${max(w, h)}x${min(w, h)}) to Pictures/CameraGuides"
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
        private const val KEY_RATIO = "ratio"
        private const val PANEL_MIN_DP = 200f
    }
}
