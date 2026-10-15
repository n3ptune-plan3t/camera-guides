package com.n3ptune.cameraguides

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var technique: Technique? = null
        set(value) { field = value; invalidate() }

    var guidesVisible: Boolean = true
        set(value) { field = value; invalidate() }

    private val dp = resources.displayMetrics.density

    private val paints = GuidePaints(
        main = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(210, 255, 255, 255)
            strokeWidth = 1.5f * dp
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(2f * dp, 0f, 0f, Color.argb(150, 0, 0, 0))
        },
        thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(90, 255, 255, 255)
            strokeWidth = 1f * dp
        },
    )

    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 2f * dp
    }
    private var focusX = 0f
    private var focusY = 0f
    private var focusAlpha = 0f
    private var flashAlpha = 0f

    /** White flash, shown when the shutter fires. */
    fun flash() {
        ValueAnimator.ofFloat(0.85f, 0f).apply {
            duration = 180
            addUpdateListener { flashAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Focus ring at the tapped point. */
    fun showFocus(x: Float, y: Float) {
        focusX = x; focusY = y
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 900
            addUpdateListener { focusAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (guidesVisible) technique?.draw?.invoke(canvas, width.toFloat(), height.toFloat(), paints)
        if (focusAlpha > 0f) {
            focusPaint.alpha = (focusAlpha * 255).toInt()
            canvas.drawCircle(focusX, focusY, 36 * dp * (0.8f + 0.2f * focusAlpha), focusPaint)
        }
        if (flashAlpha > 0f) canvas.drawColor(Color.argb((flashAlpha * 255).toInt(), 255, 255, 255))
    }
}
