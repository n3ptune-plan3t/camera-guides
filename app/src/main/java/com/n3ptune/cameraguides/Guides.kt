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

// 1 : 0.618 : 1 grid (Phi / Golden Grid). Tighter central box than Rule of Thirds.
private fun phiGrid(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val a = 1f / 2.618034f // ~0.382
    val b = 1f - a         // ~0.618
    c.drawLine(w * a, 0f, w * a, h, p.main)
    c.drawLine(w * b, 0f, w * b, h, p.main)
    c.drawLine(0f, h * a, w, h * a, p.main)
    c.drawLine(0f, h * b, w, h * b, p.main)
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

// Main diagonal + perpendiculars from the opposite corners.
private fun goldenTriangles(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(0f, 0f, w, h, p.main)
    val d2 = w * w + h * h
    val t1 = (w * w) / d2
    c.drawLine(w, 0f, t1 * w, t1 * h, p.main)
    val t2 = (h * h) / d2
    c.drawLine(0f, h, t2 * w, t2 * h, p.main)
}

// Edwin Westhoff's Diagonal Method: 45-degree bisectors from each corner.
private fun diagonalMethod(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val s = min(w, h)
    c.drawLine(0f, 0f, s, s, p.main)
    c.drawLine(w, 0f, w - s, s, p.main)
    c.drawLine(0f, h, s, h - s, p.main)
    c.drawLine(w, h, w - s, h - s, p.main)
}

// Jay Hambidge's Root-2 Dynamic Symmetry Armature:
// Main diagonals + reciprocals (corner to opposite-side midpoint intersections).
private fun dynamicSymmetry(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(0f, 0f, w, h, p.main)
    c.drawLine(0f, h, w, 0f, p.main)
    c.drawLine(0f, 0f, w, h / 2, p.thin)
    c.drawLine(w, 0f, 0f, h / 2, p.thin)
    c.drawLine(0f, h, w, h / 2, p.thin)
    c.drawLine(w, h, 0f, h / 2, p.thin)
    c.drawLine(0f, h / 2, w, h / 2, p.thin)
    c.drawLine(w / 2, 0f, w / 2, h, p.thin)
}

// Classical diamond + cross-armature for balance and centered focal tension.
private fun harmonicArmature(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(0f, 0f, w, h, p.thin)
    c.drawLine(0f, h, w, 0f, p.thin)
    c.drawLine(w / 2, 0f, w / 2, h, p.thin)
    c.drawLine(0f, h / 2, w, h / 2, p.thin)
    val path = Path().apply {
        moveTo(w / 2, 0f)
        lineTo(w, h / 2)
        lineTo(w / 2, h)
        lineTo(0f, h / 2)
        close()
    }
    c.drawPath(path, p.main)
}

private fun symmetry(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(w / 2, 0f, w / 2, h, p.main)
    c.drawLine(0f, h / 2, w, h / 2, p.thin)
}

// Bullseye / Center Composition: concentric rings and crosshair.
private fun centerTarget(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val cx = w / 2
    val cy = h / 2
    val r = min(w, h) * 0.28f
    c.drawLine(cx, 0f, cx, h, p.thin)
    c.drawLine(0f, cy, w, cy, p.thin)
    c.drawCircle(cx, cy, r, p.main)
    c.drawCircle(cx, cy, r * 0.35f, p.main)
}

private fun diagonals(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    c.drawLine(0f, 0f, w, h, p.main)
    c.drawLine(0f, h, w, 0f, p.main)
}

// Pyramid / Triangular Composition: stable base with apex near top center.
private fun trianglePyramid(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val apexX = w / 2
    val apexY = h * 0.14f
    val baseY = h * 0.88f
    val marginX = w * 0.10f
    val path = Path().apply {
        moveTo(apexX, apexY)
        lineTo(w - marginX, baseY)
        lineTo(marginX, baseY)
        close()
    }
    c.drawPath(path, p.main)
    c.drawLine(apexX, apexY, apexX, baseY, p.thin)
}

// Rule of Odds: 3 staggered focal circles forming a visual triangle.
private fun ruleOfOdds(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val r = min(w, h) * 0.11f
    val pts = listOf(
        w * 0.50f to h * 0.32f,
        w * 0.27f to h * 0.64f,
        w * 0.73f to h * 0.64f,
    )
    val tri = Path().apply {
        moveTo(pts[0].first, pts[0].second)
        lineTo(pts[1].first, pts[1].second)
        lineTo(pts[2].first, pts[2].second)
        close()
    }
    c.drawPath(tri, p.thin)
    for ((px, py) in pts) {
        c.drawCircle(px, py, r, p.main)
    }
}

// Negative Space: small isolated subject zone at a lower-left power point, open surroundings.
private fun negativeSpace(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val sx = w * 0.14f
    val sy = h * 0.56f
    val sw = w * 0.28f
    val sh = h * 0.26f
    c.drawRoundRect(RectF(sx, sy, sx + sw, sy + sh), 12f, 12f, p.main)
    c.drawLine(sx + sw, sy + sh / 2, w * 0.88f, sy + sh / 2, p.thin)
    c.drawLine(sx + sw / 2, sy, sx + sw / 2, h * 0.12f, p.thin)
}

// Rule of Space / Lead Room: subject column on the left with directional gaze/motion space ahead.
private fun ruleOfSpace(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val subX = w * 0.26f
    val topY = h * 0.22f
    val botY = h * 0.78f
    c.drawLine(subX, topY, subX, botY, p.main)
    c.drawCircle(subX, h * 0.34f, min(w, h) * 0.08f, p.main)
    val arrowY = h * 0.34f
    val endX = w * 0.86f
    c.drawLine(subX + min(w, h) * 0.10f, arrowY, endX, arrowY, p.main)
    val ah = min(w, h) * 0.04f
    c.drawLine(endX, arrowY, endX - ah, arrowY - ah, p.main)
    c.drawLine(endX, arrowY, endX - ah, arrowY + ah, p.main)
}

// Portrait Headroom & Eye Line: upper eye-level guide, headroom cap, and oval face guide.
private fun portraitEyeLine(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val headTop = h * 0.12f
    val eyeY = h * 0.36f
    val chinY = h * 0.58f
    val faceHalfW = min(w, h) * 0.20f
    c.drawLine(w * 0.2f, headTop, w * 0.8f, headTop, p.thin)
    c.drawLine(0f, eyeY, w, eyeY, p.main)
    c.drawOval(RectF(w / 2 - faceHalfW, headTop, w / 2 + faceHalfW, chinY), p.main)
    val shW = min(w, h) * 0.42f
    c.drawArc(RectF(w / 2 - shW, chinY, w / 2 + shW, h * 0.95f), 180f, 180f, false, p.thin)
}

// Foreground, Midground, Background: three horizontal depth zones with perspective rails.
private fun depthLayers(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val y1 = h * 0.35f
    val y2 = h * 0.68f
    c.drawLine(0f, y1, w, y1, p.main)
    c.drawLine(0f, y2, w, y2, p.main)
    c.drawLine(w * 0.15f, h, w * 0.44f, y1, p.thin)
    c.drawLine(w * 0.85f, h, w * 0.56f, y1, p.thin)
}

private fun leadingLines(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val cx = w / 2
    val cy = h * 0.58f
    val starts = listOf(0f to 0f, w * 0.3f to 0f, w * 0.7f to 0f, w to 0f, 0f to h, w to h)
    for ((sx, sy) in starts) c.drawLine(sx, sy, cx, cy, p.main)
}

// Vanishing Point / Radial: 8 rays converging to an off-center focal hub.
private fun vanishingPoint(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val vx = w * 0.62f
    val vy = h * 0.38f
    val anchors = listOf(
        0f to 0f, w / 2 to 0f, w to 0f,
        w to h / 2, w to h,
        w / 2 to h, 0f to h, 0f to h / 2,
    )
    for ((ax, ay) in anchors) c.drawLine(ax, ay, vx, vy, p.thin)
    c.drawCircle(vx, vy, min(w, h) * 0.05f, p.main)
    c.drawLine(0f, vy, w, vy, p.main)
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

// C-Curve: sweeping crescent arc guiding the eye around a negative-space pocket.
private fun cCurve(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val padX = w * 0.14f
    val padY = h * 0.12f
    val rect = RectF(padX, padY, w * 1.15f, h - padY)
    c.drawArc(rect, 110f, 140f, false, p.main)
    c.drawCircle(w * 0.62f, h * 0.50f, min(w, h) * 0.06f, p.thin)
}

// L-Composition: vertical anchor on one side + horizontal base, leaving an open upper quadrant.
private fun lComposition(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val lx = w * 0.30f
    val ly = h * 0.72f
    c.drawLine(lx, h * 0.08f, lx, ly, p.main)
    c.drawLine(lx, ly, w * 0.92f, ly, p.main)
    c.drawRect(w * 0.44f, h * 0.20f, w * 0.84f, h * 0.56f, p.thin)
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

// Fill the Frame: tight subject bounding box with corner brackets to eliminate distractions.
private fun fillTheFrame(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val m = min(w, h) * 0.08f
    val l = min(w, h) * 0.14f
    val r = w - m
    val b = h - m
    c.drawLine(m, m, m + l, m, p.main); c.drawLine(m, m, m, m + l, p.main)
    c.drawLine(r, m, r - l, m, p.main); c.drawLine(r, m, r, m + l, p.main)
    c.drawLine(m, b, m + l, b, p.main); c.drawLine(m, b, m, b - l, p.main)
    c.drawLine(r, b, r - l, b, p.main); c.drawLine(r, b, r, b - l, p.main)
    c.drawRect(m, m, r, b, p.thin)
}

// Figure to Ground: central subject silhouette circle inside a high-contrast background frame.
private fun figureToGround(c: Canvas, w: Float, h: Float, p: GuidePaints) {
    val cx = w / 2
    val cy = h / 2
    val r = min(w, h) * 0.24f
    c.drawCircle(cx, cy, r, p.main)
    for (i in 0 until 8) {
        val ang = (i * PI / 4).toFloat()
        val x1 = cx + cos(ang) * (r * 1.18f)
        val y1 = cy + sin(ang) * (r * 1.18f)
        val x2 = cx + cos(ang) * (r * 1.48f)
        val y2 = cy + sin(ang) * (r * 1.48f)
        c.drawLine(x1, y1, x2, y2, p.thin)
    }
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
    Technique("Phi Grid", "1:0.618:1 Golden Ratio grid. Tighter central lines prevent subjects from sitting too close to edges.", ::phiGrid),
    Technique("Golden Ratio", "Receding golden-ratio squares with a Fibonacci spiral curling toward the focal point.", ::goldenSpiral),
    Technique("Golden Triangles", "Main diagonal split with two perpendicular lines from opposite corners for dynamic tension.", ::goldenTriangles),
    Technique("Diagonal Method", "Westhoff's 45° corner bisectors. Key details frequently land on the four inner intersections.", ::diagonalMethod),
    Technique("Dynamic Symmetry", "Hambidge's Root-2 armature combining main and reciprocal diagonals for classical harmony.", ::dynamicSymmetry),
    Technique("Harmonic Armature", "14-line classical painting grid with an inner diamond for multi-subject balance.", ::harmonicArmature),
    Technique("Symmetry", "A centered guide for symmetric subjects and reflections.", ::symmetry),
    Technique("Center Target", "Bullseye rings and crosshairs for dead-center subjects and radial symmetry.", ::centerTarget),
    Technique("Diagonals", "Corner-to-corner diagonals for dynamic, angled compositions.", ::diagonals),
    Technique("Pyramid", "Triangular armature with a wide base and high apex for stability, portraits and architecture.", ::trianglePyramid),
    Technique("Rule of Odds", "Three staggered focal zones. Odd-numbered groupings feel more natural and dynamic than even ones.", ::ruleOfOdds),
    Technique("Negative Space", "Small lower-third subject box surrounded by expansive open space for minimalist impact.", ::negativeSpace),
    Technique("Rule of Space", "Places the subject on one side with ample lead room ahead for gaze or movement.", ::ruleOfSpace),
    Technique("Portrait Eye Line", "Aligns the subject's eyes on the upper third while regulating top headroom.", ::portraitEyeLine),
    Technique("Depth Layers", "Divides the scene into Foreground, Midground and Background planes to build 3D depth.", ::depthLayers),
    Technique("Leading Lines", "Converging lines that pull the viewer's eye toward a focal point.", ::leadingLines),
    Technique("Vanishing Point", "Off-center horizon hub with radial rays for single-point street and tunnel perspective.", ::vanishingPoint),
    Technique("S-Curve", "A flowing S-shaped path: roads, rivers and coastlines that wind through the frame.", ::sCurve),
    Technique("C-Curve", "Sweeping crescent arc that wraps around a subject or negative-space pocket.", ::cCurve),
    Technique("L-Composition", "Vertical anchor paired with a horizontal base to frame an open secondary area.", ::lComposition),
    Technique("Frame in Frame", "An inset arch for shooting through doorways, windows or foliage.", ::frameWithinFrame),
    Technique("Fill the Frame", "Tight outer boundary brackets encouraging you to move closer and eliminate background clutter.", ::fillTheFrame),
    Technique("Figure to Ground", "Central subject zone with contrast ticks — isolate a clear silhouette against a clean backdrop.", ::figureToGround),
    Technique("Repetition", "A repeating grid for scenes with patterns: windows, arches, columns.", ::repetition),
    Technique("Juxtaposition", "Two parallel diagonals marking a zone to contrast two subjects.", ::juxtaposition),
)
