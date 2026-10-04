package com.tailorsfit.app.ui.components

import android.graphics.Color as AColor
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.tailorsfit.app.render.CanvasSink
import com.tailorsfit.app.render.SinkStyle
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.geom.pointInPolygon
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.layout.PlacedPiece
import com.tailorsfit.pattern.model.LengthUnit
import com.tailorsfit.pattern.model.Lengths
import com.tailorsfit.pattern.model.Piece
import com.tailorsfit.pattern.model.SeamAllowances
import com.tailorsfit.pattern.render.Ink
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/** Grid spacing in cm: fine lines, strong lines and how often a number is written. */
private class GridSpec(val minor: Double, val major: Double, val label: Double) {
    fun unitsOf(cm: Double) = if (Lengths.unit == LengthUnit.INCH) cm / Lengths.INCH else cm

    companion object {
        /** Graph paper for one piece: ½" / 1" lines, a number every inch (or 1 / 5 / 5 cm). */
        fun piece() = if (Lengths.unit == LengthUnit.INCH) GridSpec(Lengths.INCH / 2, Lengths.INCH, Lengths.INCH) else GridSpec(1.0, 5.0, 5.0)

        /** Cutting table: 1" / 5" lines, a number every 5" (or 1 / 10 / 10 cm). */
        fun cloth() = if (Lengths.unit == LengthUnit.INCH) GridSpec(Lengths.INCH, 5 * Lengths.INCH, 5 * Lengths.INCH) else GridSpec(2.0, 10.0, 10.0)
    }
}

private class TapMapping {
    var toCm: ((Offset) -> Pt)? = null
}

/** Pinch-zoom and drag state shared by both graphs. */
private class Viewport {
    var zoom by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
}

private fun Modifier.zoomable(key: Any, vp: Viewport) = pointerInput(key) {
    detectTransformGestures { centroid, pan, gestureZoom, _ ->
        val newZoom = (vp.zoom * gestureZoom).coerceIn(0.6f, 12f)
        val factor = newZoom / vp.zoom
        vp.offset = centroid - (centroid - vp.offset) * factor + pan
        vp.zoom = newZoom
    }
}

/**
 * Draws a grid over [area] (cm) with numbered axes along the left and bottom edges, like graph
 * paper. [toPx] maps cm to screen pixels.
 */
private fun DrawScope.paintGrid(area: Rect, grid: GridSpec, toPx: (Pt) -> Offset, minorColor: Int, majorColor: Int, textColor: Int, axisAt: Pt) {
    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        val minor = Paint().apply { color = minorColor; strokeWidth = 1f }
        val major = Paint().apply { color = majorColor; strokeWidth = 2f }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor; textSize = 26f; textAlign = Paint.Align.CENTER }
        fun lines(step: Double, paint: Paint) {
            var x = floor(area.minX / step) * step
            while (x <= area.maxX + 1e-6) {
                val a = toPx(Pt(x, area.minY)); val b = toPx(Pt(x, area.maxY))
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                x += step
            }
            var y = floor(area.minY / step) * step
            while (y <= area.maxY + 1e-6) {
                val a = toPx(Pt(area.minX, y)); val b = toPx(Pt(area.maxX, y))
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                y += step
            }
        }
        lines(grid.minor, minor)
        lines(grid.major, major)
        // Numbers along the bottom (across) and the left (down), counted from the axis origin.
        var x = ceil(area.minX / grid.label) * grid.label
        while (x <= area.maxX + 1e-6) {
            val p = toPx(Pt(x, area.maxY))
            canvas.drawText(fmt(grid.unitsOf(x - axisAt.x)), p.x, p.y - 8f, text)
            x += grid.label
        }
        text.textAlign = Paint.Align.LEFT
        var y = ceil(area.minY / grid.label) * grid.label
        while (y <= area.maxY + 1e-6) {
            val p = toPx(Pt(area.minX, y))
            canvas.drawText(fmt(grid.unitsOf(y - axisAt.y)), p.x + 6f, p.y - 6f, text)
            y += grid.label
        }
    }
}

private fun fmt(v: Double): String = if (kotlin.math.abs(v - Math.round(v)) < 0.01) Math.round(v).toString() else "%.1f".format(v)

/** One pattern piece on graph paper in the tailor's unit (pinch to zoom, drag to move). */
@Composable
fun PieceGraph(piece: Piece, allowances: SeamAllowances, options: PaintOptions, modifier: Modifier = Modifier) {
    val vp = remember(piece) { Viewport() }
    val grid = remember(Lengths.unit) { GridSpec.piece() }
    Canvas(modifier.clipToBounds().zoomable(piece, vp)) {
        drawRect(Color.White)
        val cut = Rect.of(piece.cutOutline(allowances))
        // Grid starts a whole unit before the piece so numbers line up with the lines.
        val start = Pt(floor(cut.minX / grid.major) * grid.major - grid.major, floor(cut.minY / grid.major) * grid.major - grid.major)
        val area = Rect(start.x, start.y, ceil(cut.maxX / grid.major) * grid.major + grid.major, ceil(cut.maxY / grid.major) * grid.major + grid.major)
        val fit = min(size.width / area.width, size.height / area.height).toFloat()
        val scale = fit * vp.zoom
        val baseX = ((size.width - area.width * fit) / 2).toFloat() * vp.zoom + vp.offset.x
        val baseY = ((size.height - area.height * fit) / 2).toFloat() * vp.zoom + vp.offset.y
        fun toPx(p: Pt) = Offset(baseX + ((p.x - area.minX) * scale).toFloat(), baseY + ((p.y - area.minY) * scale).toFloat())
        paintGrid(area, grid, ::toPx, AColor.rgb(222, 232, 246), AColor.rgb(160, 186, 222), AColor.rgb(70, 90, 120), axisAt = start)
        drawIntoCanvas { c ->
            val sink = CanvasSink(
                c.nativeCanvas, scale, scale, area.minX, area.minY,
                SinkStyle.paper(unit = (scale / 12f).coerceIn(0.8f, 3f)),
                offsetPxX = baseX, offsetPxY = baseY,
            )
            PatternPainter.paintPiece(PlacedPiece(piece, Pt.ZERO), allowances, sink, options)
        }
    }
}

/** Lines in red on the dark cutting table; the chosen piece in yellow. */
private fun cuttingStyle(color: Int, unit: Float) = SinkStyle.projector(2.2f * unit).let { base ->
    base.copy(colors = base.colors.mapValues { (ink, c) -> if (ink == Ink.CUT || ink == Ink.NOTCH) color else c })
}

/**
 * All pieces laid on the cloth on a dark cutting table with a green grid and rulers in the
 * tailor's unit. Tap a piece to pick it (shown in yellow); pinch to zoom, drag to move.
 */
@Composable
fun CutGraph(layout: Layout, options: PaintOptions, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val vp = remember(layout) { Viewport() }
    val grid = remember(Lengths.unit) { GridSpec.cloth() }
    // Kept from the last draw so taps can be turned back into cm (a plain holder: not state,
    // so drawing does not trigger recomposition).
    val mapping = remember { TapMapping() }
    Canvas(
        modifier
            .clipToBounds()
            .zoomable(layout, vp)
            .pointerInput(layout) {
                detectTapGestures { tap ->
                    val p = mapping.toCm?.invoke(tap) ?: return@detectTapGestures
                    val hit = layout.placed.indexOfFirst { placed -> pointInPolygon(p, placed.piece.cutOutline(layout.allowances).map { placed.toLayout(it) }) }
                    onSelect(hit.takeIf { it >= 0 && it != selected })
                }
            },
    ) {
        drawRect(Color(0xFF1E1A22))
        val margin = 2.0
        val area = Rect(-margin, -margin, layout.width + margin, layout.length + margin)
        val fit = min(size.width / area.width, size.height / area.height).toFloat()
        val scale = fit * vp.zoom
        val baseX = ((size.width - area.width * fit) / 2).toFloat() * vp.zoom + vp.offset.x
        val baseY = ((size.height - area.height * fit) / 2).toFloat() * vp.zoom + vp.offset.y
        fun toPx(p: Pt) = Offset(baseX + ((p.x - area.minX) * scale).toFloat(), baseY + ((p.y - area.minY) * scale).toFloat())
        mapping.toCm = { o -> Pt((o.x - baseX) / scale + area.minX, (o.y - baseY) / scale + area.minY) }
        paintGrid(
            Rect(0.0, 0.0, layout.width, layout.length), grid, ::toPx,
            AColor.argb(70, 60, 200, 90), AColor.argb(170, 80, 230, 110), AColor.rgb(170, 230, 180), axisAt = Pt.ZERO,
        )
        drawIntoCanvas { c ->
            val unit = (scale / 14f).coerceIn(0.6f, 2.5f)
            fun sink(color: Int) = CanvasSink(c.nativeCanvas, scale, scale, area.minX, area.minY, cuttingStyle(color, unit), offsetPxX = baseX, offsetPxY = baseY)
            val red = sink(AColor.rgb(240, 80, 80))
            PatternPainter.paintFabric(layout, red)
            layout.placed.forEachIndexed { i, p ->
                PatternPainter.paintPiece(p, layout.allowances, if (i == selected) sink(AColor.rgb(250, 225, 60)) else red, options)
            }
        }
    }
}
