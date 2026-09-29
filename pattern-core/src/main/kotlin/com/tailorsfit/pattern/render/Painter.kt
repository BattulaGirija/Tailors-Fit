package com.tailorsfit.pattern.render

import com.tailorsfit.pattern.i18n.tr

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.layout.PlacedPiece
import com.tailorsfit.pattern.layout.Tile
import com.tailorsfit.pattern.layout.TilePlan
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.Marking
import com.tailorsfit.pattern.model.SeamAllowances
import kotlin.math.max

/** Semantic line/text types. Each output (screen, PDF, projector, SVG) picks its own look. */
enum class Ink {
    /** Cutting line (outermost, thick). */
    CUT,
    /** Sewing line (dashed). */
    SEAM,
    FOLD,
    DART,
    GRAIN,
    NOTCH,
    GUIDE,
    TEXT,
    /** Page / tile helpers: alignment crosses, fabric edges. */
    HELPER,
}

/** Something that can draw lines and text given in centimetres. */
interface DrawSink {
    fun polyline(points: List<Pt>, closed: Boolean, ink: Ink)

    /** Draws [text] centred on [at]. [sizeCm] is the letter height. */
    fun text(text: String, at: Pt, sizeCm: Double, ink: Ink = Ink.TEXT, rotationDeg: Double = 0.0, bold: Boolean = false)
}

data class PaintOptions(
    val showAllowance: Boolean = true,
    val showSeamLine: Boolean = true,
    val showLabels: Boolean = true,
    val showMarkings: Boolean = true,
    /** Extra caption lines under each piece's name, e.g. customer and design. */
    val caption: List<String> = emptyList(),
    val textScale: Double = 1.0,
)

object PatternPainter {
    const val NOTCH_LENGTH = 0.8

    fun paintLayout(layout: Layout, sink: DrawSink, options: PaintOptions = PaintOptions()) {
        for (p in layout.placed) paintPiece(p, layout.allowances, sink, options)
    }

    /** Outline of the cloth the layout needs; on folded cloth the left edge is the fold. */
    fun paintFabric(layout: Layout, sink: DrawSink) {
        val w = layout.width
        val l = layout.length
        sink.polyline(listOf(Pt(0.0, 0.0), Pt(w, 0.0), Pt(w, l), Pt(0.0, l)), true, Ink.HELPER)
        if (layout.folded) {
            sink.polyline(listOf(Pt(0.0, 0.0), Pt(0.0, l)), false, Ink.FOLD)
            sink.text(tr("paint.cloth_fold"), Pt(-0.8, l / 2), 0.8, Ink.HELPER, rotationDeg = -90.0, bold = true)
            sink.text(tr("paint.selvedges"), Pt(w + 0.8, l / 2), 0.8, Ink.HELPER, rotationDeg = 90.0, bold = true)
        }
    }

    fun paintPiece(placed: PlacedPiece, allowances: SeamAllowances, sink: DrawSink, options: PaintOptions = PaintOptions()) {
        val piece = placed.piece
        val t: (Pt) -> Pt = placed::toLayout
        val effective = if (options.showAllowance) allowances else SeamAllowances.NONE
        val seam = piece.seamOutline().map(t)
        val cut = piece.cutOutline(effective).map(t)

        sink.polyline(cut, closed = true, ink = Ink.CUT)
        for (hole in piece.cutouts) sink.polyline(hole.map(t), closed = true, ink = Ink.CUT)
        if (options.showAllowance && options.showSeamLine) sink.polyline(seam, closed = true, ink = Ink.SEAM)

        val bounds = Rect.of(seam)
        val textScale = options.textScale * (bounds.width / 18.0).coerceIn(0.55, 1.0)

        for (e in piece.edges.filter { it.kind == EdgeKind.FOLD }) {
            val pts = e.path.points().map(t)
            sink.polyline(pts, closed = false, ink = Ink.FOLD)
            val a = pts.first()
            val b = pts.last()
            val top = if (a.y < b.y) a else b
            val bottom = if (a.y < b.y) b else a
            val span = bottom.y - top.y
            // "Place on fold" bracket: a line parallel to the fold with legs pointing at it.
            val bx = top.x + 2.5
            val y1 = top.y + span * 0.2
            val y2 = bottom.y - span * 0.2
            sink.polyline(listOf(Pt(top.x + 0.3, y1), Pt(bx, y1), Pt(bx, y2), Pt(top.x + 0.3, y2)), false, Ink.GRAIN)
            arrowHead(sink, Pt(top.x + 0.3, y1), Pt(-1.0, 0.0), Ink.GRAIN)
            arrowHead(sink, Pt(top.x + 0.3, y2), Pt(-1.0, 0.0), Ink.GRAIN)
            sink.text(tr("paint.place_on_fold"), Pt(bx + 1.0, (y1 + y2) / 2), 0.7 * textScale, Ink.TEXT, rotationDeg = -90.0, bold = true)
        }

        if (options.showMarkings) {
            for (d in piece.darts) sink.polyline(listOf(t(d.legA), t(d.tip), t(d.legB)), false, Ink.DART)
            for (m in piece.markings) {
                val a = t(m.from)
                val b = t(m.to)
                when (m.kind) {
                    Marking.Kind.GRAIN -> {
                        sink.polyline(listOf(a, b), false, Ink.GRAIN)
                        val dir = (b - a).normalized()
                        arrowHead(sink, a, -dir, Ink.GRAIN)
                        arrowHead(sink, b, dir, Ink.GRAIN)
                    }
                    Marking.Kind.GUIDE -> sink.polyline(listOf(a, b), false, Ink.GUIDE)
                }
            }
            for (n in piece.notches) {
                val at = t(n.at)
                val tip = t(n.at + n.outward)
                val out = (tip - at).normalized()
                val along = Pt(-out.y, out.x)
                val offsets = if (n.double) listOf(-0.3, 0.3) else listOf(0.0)
                for (o in offsets) {
                    val base = at + along * o
                    sink.polyline(listOf(base - out * 0.2, base + out * NOTCH_LENGTH), false, Ink.NOTCH)
                }
            }
        }

        if (options.showLabels) {
            val c = t(piece.labelAt)
            val lines = ArrayList<Triple<String, Double, Boolean>>()
            lines += Triple(piece.name.uppercase(), 1.3 * textScale, true)
            lines += Triple(piece.cut.text, 0.8 * textScale, true)
            for (cap in options.caption) lines += Triple(cap, 0.6 * textScale, false)
            for (note in piece.notes) lines += Triple(note, 0.55 * textScale, false)
            val lineGap = 0.35 * textScale
            val total = lines.sumOf { it.second } + lineGap * (lines.size - 1)
            var y = c.y - total / 2
            for ((text, size, bold) in lines) {
                sink.text(text, Pt(c.x, y + size / 2), size, Ink.TEXT, bold = bold)
                y += size + lineGap
            }
        }
    }

    private fun arrowHead(sink: DrawSink, tip: Pt, dir: Pt, ink: Ink, size: Double = 0.7) {
        val d = dir.normalized()
        val side = Pt(-d.y, d.x)
        val back = tip - d * size
        sink.polyline(listOf(back + side * (size * 0.5), tip, back - side * (size * 0.5)), false, ink)
    }

    /** Crosses to line up neighbouring printed pages, plus the page label. */
    fun paintTileHelpers(plan: TilePlan, tile: Tile, sink: DrawSink) {
        val area = tile.area
        for (m in plan.alignmentMarks()) {
            if (m.x < area.minX || m.x > area.maxX || m.y < area.minY || m.y > area.maxY) continue
            val s = 1.2
            sink.polyline(listOf(Pt(m.x - s, m.y), Pt(m.x + s, m.y)), false, Ink.HELPER)
            sink.polyline(listOf(Pt(m.x, m.y - s), Pt(m.x, m.y + s)), false, Ink.HELPER)
            circle(sink, m, 0.5, Ink.HELPER)
        }
        sink.text(tile.label, Pt(area.minX + 2.0, area.minY + 1.5), 1.0, Ink.HELPER, bold = true)
    }

    /** A square of known size used to check print or projector scale. */
    fun paintCalibrationSquare(sink: DrawSink, topLeft: Pt, sizeCm: Double, label: String = "${fmtCm(sizeCm)} cm") {
        val a = topLeft
        val b = Pt(a.x + sizeCm, a.y)
        val c = Pt(a.x + sizeCm, a.y + sizeCm)
        val d = Pt(a.x, a.y + sizeCm)
        sink.polyline(listOf(a, b, c, d), true, Ink.CUT)
        val tick = max(0.5, sizeCm / 20)
        for (i in 1 until sizeCm.toInt()) {
            val len = if (i % 5 == 0) tick * 2 else tick
            sink.polyline(listOf(Pt(a.x + i, a.y), Pt(a.x + i, a.y + len)), false, Ink.HELPER)
            sink.polyline(listOf(Pt(a.x, a.y + i), Pt(a.x + len, a.y + i)), false, Ink.HELPER)
        }
        sink.text(label, Pt(a.x + sizeCm / 2, a.y + sizeCm / 2), max(0.6, sizeCm / 10), Ink.TEXT, bold = true)
    }

    /** Light 10 cm grid, used on the projector to line the cloth up. */
    fun paintGrid(sink: DrawSink, area: Rect, step: Double = 10.0) {
        var x = kotlin.math.floor(area.minX / step) * step
        while (x <= area.maxX) {
            sink.polyline(listOf(Pt(x, area.minY), Pt(x, area.maxY)), false, Ink.HELPER)
            x += step
        }
        var y = kotlin.math.floor(area.minY / step) * step
        while (y <= area.maxY) {
            sink.polyline(listOf(Pt(area.minX, y), Pt(area.maxX, y)), false, Ink.HELPER)
            y += step
        }
    }

    fun circle(sink: DrawSink, c: Pt, r: Double, ink: Ink) {
        val n = 24
        sink.polyline((0 until n).map {
            val a = 2 * Math.PI * it / n
            Pt(c.x + r * kotlin.math.cos(a), c.y + r * kotlin.math.sin(a))
        }, true, ink)
    }

    private fun fmtCm(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)
}
