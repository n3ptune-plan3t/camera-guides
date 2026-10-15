package com.n3ptune.cameraguides

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GuidePaints(val main: Paint, val thin: Paint)

class Technique(
    val name: String,
    val description: String,
    val draw: (Canvas, Float, Float, GuidePaints) -> Unit,
)

private fun ruleOfThirds(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    for (i in 1..2) {
        c.drawLine(w * i / 3, 0f, w * i / 3, h, p.main)
        c.drawLine(0f, h * i / 3, w, h * i / 3, p.main)
    }
}

// Receding golden-ratio squares plus a quarter-arc spiral through them.
// Computed in landscape space and transposed for portrait frames.
private fun goldenSpiral(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val portrait = h > w
    val bigW = if (portrait) h else w
    val bigH = if (portrait) w else h
    c.save()
    if (portrait) {
        c.concat(Matrix().apply { setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)) })
    }
    var x = 0f
    var y = 0f
    var rw = bigW
    var rh = bigH
    val spiral = Path()
    for (i in 0 until 8) {
        val s = min(rw, rh)
        if (s < 8f) break
        when (i % 4) {
            0 -> { // square on the left
                if (i == 0) spiral.moveTo(x, y + s)
                spiral.arcTo(RectF(x, y, x + 2 * s, y + 2 * s), 180f, 90f)
                c.drawLine(x + s, y, x + s, y + rh, p.thin)
                x += s; rw -= s
            }
            1 -> { // square on top
                spiral.arcTo(RectF(x - s, y, x + s, y + 2 * s), 270f, 90f)
                c.drawLine(x, y + s, x + rw, y + s, p.thin)
                y += s; rh -= s
            }
            2 -> { // square on the right
                val cx = x + rw - s
                spiral.arcTo(RectF(cx - s, y - s, cx + s, y + s), 0f, 90f)
                c.drawLine(cx, y, cx, y + rh, p.thin)
                rw -= s
            }
            else -> { // square on the bottom
                val cy = y + rh - s
                spiral.arcTo(RectF(x, cy - s, x + 2 * s, cy + s), 90f, 90f)
                c.drawLine(x, cy, x + rw, cy, p.thin)
                rh -= s
            }
        }
    }
    c.drawPath(spiral, p.main)
    c.restore()
}

private fun symmetry(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(w / 2, 0f, w / 2, h, p.main)
    c.drawLine(0f, h / 2, w, h / 2, p.thin)
}

private fun diagonals(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(0f, 0f, w, h, p.main)
    c.drawLine(0f, h, w, 0f, p.main)
}

private fun leadingLines(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val cx = w / 2
    val cy = h * 0.58f
    val starts = listOf(0f to 0f, w * 0.3f to 0f, w * 0.7f to 0f, w to 0f, 0f to h, w to h)
    for ((sx, sy) in starts) c.drawLine(sx, sy, cx, cy, p.main)
}

private fun sCurve(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val path = Path()
    for (i in 0..60) {
        val t = i / 60f
        val px = t * w
        val py = h / 2 + (h / 2.6f) * sin(t * 2 * PI - PI / 2).toFloat()
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    c.drawPath(path, p.main)
}

private fun frameWithinFrame(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val m = min(w, h) * 0.18f
    val fw = w - 2 * m
    val fh = h - 2 * m
    val cx = m + fw / 2
    val cy = m + fh * 0.3f
    val r = fw / 2
    val path = Path()
    path.moveTo(m, m + fh)
    path.lineTo(m, cy)
    path.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f)
    path.lineTo(m + fw, m + fh)
    path.close()
    c.drawPath(path, p.main)
}

private fun repetition(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val cols = 3
    val rows = 2
    val pad = min(w, h) * 0.06f
    val cw = (w - pad * (cols + 1)) / cols
    val ch = (h - pad * (rows + 1)) / rows
    for (r in 0 until rows) for (col in 0 until cols) {
        val x = pad + col * (cw + pad)
        val y = pad + r * (ch + pad)
        c.drawRect(x, y, x + cw, y + ch, p.main)
    }
}

private fun juxtaposition(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val o = w * 0.28f
    c.drawLine(o, 0f, o - w * 0.35f, h, p.main)
    c.drawLine(w - o, 0f, w - o - w * 0.35f, h, p.main)
}

val techniques = listOf(
    Technique("Rule of Thirds", "Splits the frame into a 3x3 grid. Place subjects and horizons on the lines or intersections.", ::ruleOfThirds),
    Technique("Golden Ratio", "Receding golden-ratio squares with a Fibonacci spiral curling toward the focal point.", ::goldenSpiral),
    Technique("Symmetry", "A centered guide for symmetric subjects and reflections.", ::symmetry),
    Technique("Diagonals", "Corner-to-corner diagonals for dynamic, angled compositions.", ::diagonals),
    Technique("Leading Lines", "Converging lines that pull the viewer's eye toward a focal point.", ::leadingLines),
    Technique("S-Curve", "A flowing S-shaped path: roads, rivers and coastlines that wind through the frame.", ::sCurve),
    Technique("Frame in Frame", "An inset arch for shooting through doorways, windows or foliage.", ::frameWithinFrame),
    Technique("Repetition", "A repeating grid for scenes with patterns: windows, arches, columns.", ::repetition),
    Technique("Juxtaposition", "Two parallel diagonals marking a zone to contrast two subjects.", ::juxtaposition),
)
