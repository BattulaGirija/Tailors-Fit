package com.tailorsfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.model.MeasurementField
import kotlin.math.min

/** True for measurements taken on the back of the body (the figure is drawn from behind). */
private val BACK_VIEW = setOf(MeasurementField.SHOULDER, MeasurementField.BACK_LENGTH, MeasurementField.BACK_NECK_DEPTH)

/** Optional fields that still have a picture. */
private val ADJUSTMENT_FIGURES = setOf(MeasurementField.FRONT_LENGTH, MeasurementField.APEX_TO_APEX)

/** Whether [MeasureFigure] has a picture for this field. */
fun hasMeasureFigure(f: MeasurementField) = !f.isAdjustment || f in ADJUSTMENT_FIGURES

/**
 * A simple upper-body figure (100 × 110 units) with the measurement for [field] drawn in gold:
 * a loop for rounds (bust, waist, armhole, arm), a line for lengths and widths.
 */
@Composable
fun MeasureFigure(field: MeasurementField, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val k = min(size.width / 100f, size.height / 110f)
        val ox = (size.width - 100f * k) / 2
        val oy = (size.height - 110f * k) / 2
        fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)
        val back = field in BACK_VIEW
        drawBody(::p, k, back)

        val gold = Brand.Gold
        val w = 2.6f * k
        fun line(vararg pts: Pair<Float, Float>) {
            val path = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(p(x, y).x, p(x, y).y) else lineTo(p(x, y).x, p(x, y).y) } }
            drawPath(path, gold, style = Stroke(w, cap = StrokeCap.Round))
            dot(p(pts.first().first, pts.first().second), k)
            dot(p(pts.last().first, pts.last().second), k)
        }
        fun loop(cx: Float, cy: Float, rx: Float, ry: Float) {
            drawOval(gold, topLeft = p(cx - rx, cy - ry), size = Size(2 * rx * k, 2 * ry * k), style = Stroke(w))
        }
        when (field) {
            MeasurementField.BUST -> loop(50f, 54f, 21f, 3.5f)
            MeasurementField.WAIST -> loop(50f, 75f, 17f, 3f)
            MeasurementField.UPPER_CHEST -> loop(50f, 46f, 20f, 3f)
            MeasurementField.SHOULDER_WIDTH -> line(36f to 28f, 23f to 33f)
            MeasurementField.CHEST_HEIGHT -> line(45f to 26f, 40f to 54f, 40f to 66f)
            MeasurementField.SHOULDER -> line(23f to 33f, 50f to 30f, 77f to 33f)
            MeasurementField.FRONT_LENGTH -> line(45f to 26f, 40f to 54f, 40f to 76f)
            MeasurementField.BACK_LENGTH -> line(45f to 26f, 45f to 76f)
            MeasurementField.ARMHOLE -> loop(27f, 39f, 6f, 8.5f)
            MeasurementField.APEX_LENGTH -> line(45f to 26f, 40f to 54f)
            MeasurementField.APEX_TO_APEX -> line(40f to 54f, 60f to 54f)
            MeasurementField.FRONT_NECK_DEPTH -> {
                neckline(::p, k, 36f)
                line(45f to 26f, 45f to 36f)
            }
            MeasurementField.BACK_NECK_DEPTH -> {
                neckline(::p, k, 42f)
                line(45f to 26f, 45f to 42f)
            }
            MeasurementField.SLEEVE_LENGTH -> line(23f to 33f, 19f to 52f)
            MeasurementField.SLEEVE_ROUND -> loop(24.5f, 45f, 6.5f, 2.2f)
            MeasurementField.SLEEVE_OPENING -> loop(22.5f, 52f, 6f, 2f)
            else -> Unit
        }
    }
}

private fun DrawScope.dot(c: Offset, k: Float) = drawCircle(Brand.Gold, radius = 2.4f * k, center = c)

/** Dashed neckline from both neck points down to [depth] at the centre. */
private fun DrawScope.neckline(p: (Float, Float) -> Offset, k: Float, depth: Float) {
    val path = Path().apply {
        moveTo(p(45f, 26f).x, p(45f, 26f).y)
        cubicTo(p(45f, depth - 4f).x, p(45f, depth - 4f).y, p(47f, depth).x, p(47f, depth).y, p(50f, depth).x, p(50f, depth).y)
        cubicTo(p(53f, depth).x, p(53f, depth).y, p(55f, depth - 4f).x, p(55f, depth - 4f).y, p(55f, 26f).x, p(55f, 26f).y)
    }
    drawPath(path, Brand.Plum, style = Stroke(1.6f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 3f * k))))
}

/** Head, neck, shoulders, arms and torso down to below the waist; bust lines on the front. */
private fun DrawScope.drawBody(p: (Float, Float) -> Offset, k: Float, back: Boolean) {
    val skin = Color(0xFFF3E6DA)
    val ink = Brand.Plum
    fun path(vararg pts: Pair<Float, Float>, close: Boolean = true) = Path().apply {
        pts.forEachIndexed { i, (x, y) -> val o = p(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        if (close) close()
    }
    // Torso with arms (left side, then mirrored on the right).
    val left = listOf(
        50f to 26f, 45f to 26f, 45f to 21f, 45f to 26f, 32f to 29f, 23f to 33f, 18f to 44f, 16f to 60f, 15f to 82f,
        21f to 83f, 23f to 62f, 26f to 50f, 30f to 46f, 30f to 56f, 33f to 66f, 34f to 77f, 32f to 92f, 32f to 108f, 50f to 108f,
    )
    val outline = left + left.reversed().map { (x, y) -> 100f - x to y }
    val body = path(*outline.toTypedArray())
    drawPath(body, skin)
    drawPath(body, ink, style = Stroke(1.4f * k))
    drawCircle(skin, radius = 9f * k, center = p(50f, 12f))
    drawCircle(ink, radius = 9f * k, center = p(50f, 12f), style = Stroke(1.4f * k))
    if (back) {
        drawPath(path(50f to 22f, 50f to 105f, close = false), ink.copy(alpha = 0.4f), style = Stroke(1f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * k, 3f * k))))
    } else {
        for (cx in listOf(40f, 60f)) {
            val arc = Path().apply {
                moveTo(p(cx - 8f, 52f).x, p(cx - 8f, 52f).y)
                quadraticBezierTo(p(cx, 62f).x, p(cx, 62f).y, p(cx + 8f, 52f).x, p(cx + 8f, 52f).y)
            }
            drawPath(arc, ink.copy(alpha = 0.6f), style = Stroke(1.2f * k))
        }
    }
}
