package com.tailorsfit.app.projector

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.tailorsfit.app.render.CanvasSink
import com.tailorsfit.app.render.SinkStyle
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter

/** Everything the projector output needs to draw one frame. */
data class ProjectorFrame(
    val layout: Layout?,
    val options: PaintOptions,
    val pxPerCmX: Float,
    val pxPerCmY: Float,
    /** Layout point (cm) shown at the top-left corner of the display. */
    val panCm: Pt,
    val showGrid: Boolean,
    val calibrating: Boolean,
    val calibrationSizeCm: Double,
    val lineWidthPx: Float,
)

/**
 * Draws the pattern at real size: bright lines on black so only the cutting lines light up the
 * cloth. Used both full-screen on the phone and on an external projector (see [ProjectorPresentation]).
 */
class ProjectorView(context: Context) : View(context) {
    var frame: ProjectorFrame? = null
        set(value) {
            field = value
            invalidate()
        }

    init {
        keepScreenOn = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val f = frame
        val style = SinkStyle.projector(f?.lineWidthPx ?: 3f)
        canvas.drawColor(style.background)
        if (f == null) return

        if (f.calibrating) {
            // Square centred on the display, independent of panning.
            val sizeX = f.calibrationSizeCm * f.pxPerCmX
            val sizeY = f.calibrationSizeCm * f.pxPerCmY
            val left = (width - sizeX) / 2 / f.pxPerCmX
            val top = (height - sizeY) / 2 / f.pxPerCmY
            val sink = CanvasSink(canvas, f.pxPerCmX, f.pxPerCmY, 0.0, 0.0, style)
            PatternPainter.paintCalibrationSquare(sink, Pt(left, top), f.calibrationSizeCm)
            return
        }

        val layout = f.layout ?: return
        val sink = CanvasSink(canvas, f.pxPerCmX, f.pxPerCmY, f.panCm.x, f.panCm.y, style)
        if (f.showGrid) {
            val visible = Rect(f.panCm.x, f.panCm.y, f.panCm.x + width / f.pxPerCmX, f.panCm.y + height / f.pxPerCmY)
            PatternPainter.paintGrid(sink, visible)
        }
        PatternPainter.paintFabric(layout, sink)
        PatternPainter.paintLayout(layout, sink, f.options)
    }
}
