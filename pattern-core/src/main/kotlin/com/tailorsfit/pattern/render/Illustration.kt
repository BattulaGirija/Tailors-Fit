package com.tailorsfit.pattern.render

import com.tailorsfit.pattern.i18n.tr

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.Piece

/**
 * A flat sketch of the finished garment built from its own pattern pieces, for showing the
 * customer what the design looks like (e.g. in different cloth colours).
 *
 * @param panels filled shapes (sleeves first, so the bodice is painted over them)
 * @param seams seam and dart lines drawn on top of the cloth
 * @param trims edges that can carry a border / piping (neckline, sleeve and bottom hems)
 */
data class GarmentView(
    val title: String,
    val panels: List<List<Pt>>,
    val seams: List<List<Pt>>,
    val trims: List<List<Pt>>,
)

object Illustration {
    /** Front and back views of a blouse pattern (as drafted by the blouse drafter). */
    fun blouse(pattern: Pattern): List<GarmentView> {
        val fronts = pattern.pieces.filter { it.id.startsWith("front") }
        val back = pattern.pieces.firstOrNull { it.id == "back" }
        val sleeve = pattern.pieces.firstOrNull { it.id == "sleeve" }
        return listOfNotNull(
            if (fronts.isNotEmpty()) view(tr("view.front"), fronts, sleeve) else null,
            back?.let { view(tr("view.back"), listOf(it), sleeve) },
        )
    }

    private fun view(title: String, halves: List<Piece>, sleeve: Piece?): GarmentView {
        val panels = ArrayList<List<Pt>>()
        val seams = ArrayList<List<Pt>>()
        val trims = ArrayList<List<Pt>>()

        // Armhole from underarm up to the shoulder tip (split over two panels on princess fronts).
        val armhole = halves
            .flatMap { it.edgesOf(EdgeKind.ARMHOLE) }
            .sortedByDescending { it.path.start.y }
            .flatMap { it.path.points() }
        if (sleeve != null && armhole.size >= 2) {
            val right = sleeveShape(sleeve, armhole)
            for (side in listOf(right, right.mirror())) {
                panels += side.outline
                trims += side.hem
            }
        }

        for (half in halves) {
            if (half.id == "front_side") {
                // Side panel: this half and its mirror image on the other side.
                panels += half.seamOutline()
                panels += half.seamOutline().map(Pt::mirroredX)
                trims += hems(half)
                trims += hems(half).map { l -> l.map(Pt::mirroredX) }
                continue
            }
            val opening = half.edges.any { it.kind == EdgeKind.OPENING }
            val full = asFold(half).unfolded()
            panels += full.seamOutline()
            trims += full.edgesOf(EdgeKind.NECK).map { it.path.points() }
            trims += hems(full)
            for (d in full.darts) seams += listOf(d.legA, d.tip, d.legB)
            if (opening) {
                // Hooks down the centre.
                val top = full.seamOutline().filter { kotlin.math.abs(it.x) < 1e-6 }.minByOrNull { it.y }
                val bottom = full.seamOutline().filter { kotlin.math.abs(it.x) < 1e-6 }.maxByOrNull { it.y }
                if (top != null && bottom != null) seams += listOf(top, bottom)
            }
        }
        return GarmentView(title, panels, seams, trims)
    }

    private fun hems(p: Piece) = p.edgesOf(EdgeKind.HEM).map { it.path.points() }

    private fun asFold(p: Piece) = p.copy(edges = p.edges.map { if (it.kind == EdgeKind.OPENING) it.copy(kind = EdgeKind.FOLD) else it })

    private class SleeveShape(val outline: List<Pt>, val hem: List<Pt>) {
        fun mirror() = SleeveShape(outline.map(Pt::mirroredX), hem.map(Pt::mirroredX))
    }

    /**
     * The sleeve as seen on the body: hanging from the armhole curve, angled out and down,
     * as long as the drafted underarm seam (plus part of the cap at the shoulder).
     */
    private fun sleeveShape(sleeve: Piece, armhole: List<Pt>): SleeveShape {
        val underarm = armhole.first()
        val shoulder = armhole.last()
        val capHeight = sleeve.points["capHeight"]?.y ?: 10.0
        val seamLength = sleeve.lengthOf(EdgeKind.UNDERARM) / 2
        val dir = Pt(1.0, 0.9).normalized()
        val outerEnd = shoulder + dir * (seamLength + capHeight * 0.5)
        val innerEnd = underarm + dir * seamLength
        return SleeveShape(armhole + listOf(outerEnd, innerEnd), listOf(outerEnd, innerEnd))
    }
}
