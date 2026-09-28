package com.tailorsfit.app.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.render.DrawSink
import com.tailorsfit.pattern.render.Ink

/**
 * Look of each [Ink] for one kind of output. Widths are in output pixels, dash lengths in cm
 * so dashes look the same at any zoom.
 */
data class SinkStyle(
    val background: Int,
    val colors: Map<Ink, Int>,
    val widthsPx: Map<Ink, Float>,
    val dashesCm: Map<Ink, FloatArray> = mapOf(
        Ink.SEAM to floatArrayOf(0.4f, 0.25f),
        Ink.FOLD to floatArrayOf(1.0f, 0.3f, 0.2f, 0.3f),
        Ink.HELPER to floatArrayOf(0.3f, 0.3f),
    ),
) {
    companion object {
        /** Black on white, for paper. [unit] is the size of one point/pixel on the output. */
        fun paper(unit: Float = 1f) = SinkStyle(
            background = Color.WHITE,
            colors = mapOf(
                Ink.CUT to Color.BLACK,
                Ink.SEAM to Color.DKGRAY,
                Ink.FOLD to Color.rgb(26, 95, 180),
                Ink.DART to Color.rgb(192, 28, 40),
                Ink.GRAIN to Color.BLACK,
                Ink.NOTCH to Color.BLACK,
                Ink.GUIDE to Color.rgb(192, 28, 40),
                Ink.TEXT to Color.BLACK,
                Ink.HELPER to Color.GRAY,
            ),
            widthsPx = mapOf(
                Ink.CUT to 1.6f * unit,
                Ink.SEAM to 0.8f * unit,
                Ink.FOLD to 1.6f * unit,
                Ink.DART to 1.0f * unit,
                Ink.GRAIN to 0.8f * unit,
                Ink.NOTCH to 1.2f * unit,
                Ink.GUIDE to 0.8f * unit,
                Ink.TEXT to 0f,
                Ink.HELPER to 0.6f * unit,
            ),
        )

        /** Bright lines on black so only the lines light up the cloth. */
        fun projector(lineWidthPx: Float) = SinkStyle(
            background = Color.BLACK,
            colors = mapOf(
                Ink.CUT to Color.WHITE,
                Ink.SEAM to Color.rgb(160, 160, 160),
                Ink.FOLD to Color.rgb(120, 190, 255),
                Ink.DART to Color.rgb(255, 120, 120),
                Ink.GRAIN to Color.rgb(255, 230, 120),
                Ink.NOTCH to Color.WHITE,
                Ink.GUIDE to Color.rgb(255, 120, 120),
                Ink.TEXT to Color.rgb(255, 230, 120),
                Ink.HELPER to Color.rgb(90, 90, 90),
            ),
            widthsPx = mapOf(
                Ink.CUT to lineWidthPx,
                Ink.SEAM to lineWidthPx * 0.5f,
                Ink.FOLD to lineWidthPx,
                Ink.DART to lineWidthPx * 0.7f,
                Ink.GRAIN to lineWidthPx * 0.6f,
                Ink.NOTCH to lineWidthPx,
                Ink.GUIDE to lineWidthPx * 0.6f,
                Ink.TEXT to 0f,
                Ink.HELPER to 1f,
            ),
        )
    }
}

/**
 * Draws pattern primitives given in cm onto an Android [Canvas]:
 * screen = (cm - origin) * pxPerCm.
 */
class CanvasSink(
    private val canvas: Canvas,
    private val pxPerCmX: Float,
    private val pxPerCmY: Float,
    private val originX: Double,
    private val originY: Double,
    private val style: SinkStyle,
    private val offsetPxX: Float = 0f,
    private val offsetPxY: Float = 0f,
) : DrawSink {
    private val strokePaints = HashMap<Ink, Paint>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    private fun sx(x: Double) = ((x - originX) * pxPerCmX).toFloat() + offsetPxX
    private fun sy(y: Double) = ((y - originY) * pxPerCmY).toFloat() + offsetPxY

    private fun strokePaint(ink: Ink): Paint = strokePaints.getOrPut(ink) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = this@CanvasSink.style.colors[ink] ?: Color.BLACK
            strokeWidth = this@CanvasSink.style.widthsPx[ink] ?: 1f
            this@CanvasSink.style.dashesCm[ink]?.let { dashes ->
                val px = FloatArray(dashes.size) { dashes[it] * pxPerCmX }
                if (px.all { it >= 1f }) pathEffect = DashPathEffect(px, 0f)
            }
        }
    }

    override fun polyline(points: List<Pt>, closed: Boolean, ink: Ink) {
        if (points.size < 2) return
        path.reset()
        path.moveTo(sx(points[0].x), sy(points[0].y))
        for (i in 1 until points.size) path.lineTo(sx(points[i].x), sy(points[i].y))
        if (closed) path.close()
        canvas.drawPath(path, strokePaint(ink))
    }

    override fun text(text: String, at: Pt, sizeCm: Double, ink: Ink, rotationDeg: Double, bold: Boolean) {
        val size = (sizeCm * pxPerCmY).toFloat()
        if (size < 3f) return // unreadably small: skip
        textPaint.textSize = size
        textPaint.color = style.colors[ink] ?: Color.BLACK
        textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val x = sx(at.x)
        val y = sy(at.y)
        val baseline = y - (textPaint.descent() + textPaint.ascent()) / 2
        if (rotationDeg != 0.0) {
            canvas.save()
            canvas.rotate(rotationDeg.toFloat(), x, y)
            canvas.drawText(text, x, baseline, textPaint)
            canvas.restore()
        } else {
            canvas.drawText(text, x, baseline, textPaint)
        }
    }
}
