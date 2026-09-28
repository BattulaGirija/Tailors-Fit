package com.tailorsfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Piece
import kotlin.math.min

/** Front and back of the design drawn from its real draft at a standard size. */
@Composable
fun ModelThumbnail(model: GarmentModel, modifier: Modifier = Modifier, fill: Color, line: Color) {
    // Each group is one garment view (front, back) made of one or more panels in a shared frame.
    val groups: List<List<List<Pt>>> = remember(model.id) {
        runCatching {
            val pieces = model.draft(Measurements.defaults()).pieces
            val front = pieces.filter { it.id.startsWith("front") }.flatMap { p ->
                if (p.id == "front_side") listOf(p.seamOutline(), p.mirrored().seamOutline()) else listOf(fullPiece(p).seamOutline())
            }
            val back = pieces.filter { it.id == "back" }.map { fullPiece(it).seamOutline() }
            listOf(front, back).filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }
    Canvas(modifier) {
        if (groups.isEmpty()) return@Canvas
        val gap = 4.0
        val boxes = groups.map { g -> Rect.of(g.flatten()) }
        val totalW = boxes.sumOf { it.width } + gap * (boxes.size - 1)
        val totalH = boxes.maxOf { it.height }
        val scale = (min(size.width / totalW, size.height / totalH) * 0.92).toFloat()
        var x = (size.width - totalW * scale) / 2
        for ((group, box) in groups.zip(boxes)) {
            val top = ((size.height - box.height * scale) / 2).toFloat()
            for (poly in group) drawOutline(poly, box, x.toFloat(), top, scale, fill, line)
            x += (box.width + gap) * scale
        }
    }
}

private fun DrawScope.drawOutline(poly: List<Pt>, box: Rect, left: Float, top: Float, scale: Float, fill: Color, line: Color) {
    fun o(p: Pt) = Offset(left + ((p.x - box.minX) * scale).toFloat(), top + ((p.y - box.minY) * scale).toFloat())
    val path = Path().apply {
        moveTo(o(poly[0]).x, o(poly[0]).y)
        for (i in 1 until poly.size) lineTo(o(poly[i]).x, o(poly[i]).y)
        close()
    }
    drawPath(path, fill)
    drawPath(path, line, style = Stroke(width = 2f))
}

/** Whole front/back (both halves) whether it is cut on the fold or opens at the centre. */
private fun fullPiece(p: Piece): Piece {
    val asFold = p.copy(edges = p.edges.map { if (it.kind == EdgeKind.OPENING) it.copy(kind = EdgeKind.FOLD) else it })
    return asFold.unfolded()
}
