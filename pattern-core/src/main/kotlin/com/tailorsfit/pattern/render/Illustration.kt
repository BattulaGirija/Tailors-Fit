package com.tailorsfit.pattern.render

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.i18n.tr
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.Piece
import kotlin.math.sin

/**
 * A flat sketch of the finished garment built from its own pattern pieces, for showing the
 * customer what the design looks like (e.g. in different cloth colours).
 *
 * @param panels filled shapes (sleeves first, so the bodice is painted over them)
 * @param seams seam and dart lines drawn on top of the cloth
 * @param trims edges that can carry a border / piping (neckline, sleeve and bottom hems)
 * @param holes openings through the garment (e.g. a keyhole), drawn in the background colour
 * @param ties strings hanging from the garment (e.g. a dori back)
 */
data class GarmentView(
    val title: String,
    val panels: List<List<Pt>>,
    val seams: List<List<Pt>>,
    val trims: List<List<Pt>>,
    val holes: List<List<Pt>> = emptyList(),
    val ties: List<List<Pt>> = emptyList(),
)

object Illustration {
    /** Front and back views of a blouse pattern (as drafted by the blouse drafter). */
    fun blouse(pattern: Pattern): List<GarmentView> {
        val fronts = asWorn(pattern.pieces).filter { it.id.startsWith("front") }
        val back = pattern.pieces.firstOrNull { it.id == "back" }
        val sleeve = pattern.pieces.firstOrNull { it.id == "sleeve" }
        val style = Style(
            sleeve = pattern.meta["sleeve"] ?: "SHORT",
            collar = pattern.meta["collar"] == "true",
            ties = pattern.meta["back"] == "DORI",
        )
        return listOfNotNull(
            if (fronts.isNotEmpty()) view(tr("view.front"), fronts, sleeve, style, isBack = false) else null,
            back?.let { view(tr("view.back"), listOf(it), sleeve, style, isBack = true) },
        )
    }

    /**
     * Pieces as they look sewn: a patti is cut shorter than its seam (the darts are closed in
     * it), so for drawing it is stretched back to the width of the front above it.
     */
    fun asWorn(pieces: List<Piece>): List<Piece> {
        val front = pieces.firstOrNull { it.id == "front" } ?: return pieces
        val sideTop = front.edgesOf(EdgeKind.SIDE).firstOrNull()?.path?.start ?: return pieces
        return pieces.map { p ->
            val top = p.edgesOf(EdgeKind.BELT).firstOrNull()?.path?.start
            if (p.id != "front_patti" || top == null || top.x <= 0) p
            else p.map({ Pt(it.x * sideTop.x / top.x, it.y) })
        }
    }

    private class Style(val sleeve: String, val collar: Boolean, val ties: Boolean)

    private fun view(title: String, halves: List<Piece>, sleeve: Piece?, style: Style, isBack: Boolean): GarmentView {
        val panels = ArrayList<List<Pt>>()
        val seams = ArrayList<List<Pt>>()
        val trims = ArrayList<List<Pt>>()
        val holes = ArrayList<List<Pt>>()
        val ties = ArrayList<List<Pt>>()

        // Armhole from underarm up to the shoulder tip (split over two panels on princess fronts).
        val armhole = halves
            .flatMap { it.edgesOf(EdgeKind.ARMHOLE) }
            .sortedByDescending { it.path.start.y }
            .flatMap { it.path.points() }
        if (sleeve != null && armhole.size >= 2) {
            val right = sleeveShape(sleeve, armhole, style.sleeve)
            for (side in listOf(right, right.mirror())) {
                panels += side.outline
                panels += side.extras
                trims += side.hem
            }
        }

        var neckline: List<Pt> = emptyList()
        for (half in halves) {
            for (b in half.edgesOf(EdgeKind.BELT)) {
                seams += b.path.points()
                seams += b.path.points().map(Pt::mirroredX)
            }
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
            holes += full.cutouts
            full.edgesOf(EdgeKind.NECK).flatMap { it.path.points() }.sortedBy { it.x }.takeIf { it.isNotEmpty() }?.let { neckline = it }
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

        if (style.collar && neckline.size >= 2) {
            // Band standing up along the neckline.
            val up = neckline.map { Pt(it.x, it.y - COLLAR_HEIGHT) }
            panels += neckline + up.reversed()
            trims += up
        }
        if (isBack && style.ties && neckline.size >= 2) {
            for (end in listOf(neckline.first(), neckline.last())) {
                val bottom = Pt(end.x * 0.25, end.y + 22.0)
                ties += listOf(end, end.lerp(bottom, 0.5) + Pt(end.x * 0.08, 0.0), bottom)
            }
        }
        return GarmentView(title, panels, seams, trims, holes, ties)
    }

    private const val COLLAR_HEIGHT = 3.5

    private fun hems(p: Piece) = p.edgesOf(EdgeKind.HEM).map { it.path.points() }

    private fun asFold(p: Piece) = p.copy(edges = p.edges.map { if (it.kind == EdgeKind.OPENING) it.copy(kind = EdgeKind.FOLD) else it })

    private class SleeveShape(val outline: List<Pt>, val hem: List<Pt>, val extras: List<List<Pt>>) {
        fun mirror() = SleeveShape(outline.map(Pt::mirroredX), hem.map(Pt::mirroredX), extras.map { e -> e.map(Pt::mirroredX) })
    }

    /**
     * The sleeve as seen on the body: hanging from the armhole curve, angled out and down,
     * as long as the drafted underarm seam (plus part of the cap at the shoulder).
     */
    private fun sleeveShape(sleeve: Piece, armhole: List<Pt>, style: String): SleeveShape {
        val underarm = armhole.first()
        val shoulder = armhole.last()
        val capHeight = sleeve.points["capHeight"]?.y ?: 10.0
        val seamLength = sleeve.lengthOf(EdgeKind.UNDERARM) / 2
        val dir = Pt(1.0, 0.9).normalized()
        val out = Pt(dir.y, -dir.x) // across the sleeve, away from the body
        var outerEnd = shoulder + dir * (seamLength + capHeight * 0.5)
        var innerEnd = underarm + dir * seamLength
        return when (style) {
            "PUFF" -> {
                // Balloon: pushed out beyond the shoulder, gathered into a narrower band.
                outerEnd += dir * 3.5
                innerEnd += dir * 3.5
                val bulgeOut = shoulder.lerp(outerEnd, 0.45) + out * 6.5 + Pt(0.0, -2.0)
                val bulgeIn = underarm.lerp(innerEnd, 0.6) - out * 3.0
                val bandOut = outerEnd.lerp(innerEnd, 0.22)
                val bandIn = innerEnd.lerp(outerEnd, 0.22)
                val band = listOf(bandOut, bandIn, bandIn + dir * 2.2, bandOut + dir * 2.2)
                SleeveShape(armhole + listOf(bulgeOut, bandOut, bandIn, bulgeIn), listOf(band[3], band[2]), listOf(band))
            }
            "BELL" -> {
                outerEnd += out * 3.0
                innerEnd -= out * 3.0
                SleeveShape(armhole + listOf(outerEnd, innerEnd), listOf(outerEnd, innerEnd), emptyList())
            }
            "FRILL" -> {
                // Wavy frill below the hem.
                val n = 12
                val edge = (0..n).map { i ->
                    val t = i.toDouble() / n
                    innerEnd.lerp(outerEnd, t) + dir * (3.0 + 0.7 * sin(t * Math.PI * 6))
                }
                val frill = listOf(outerEnd, innerEnd) + edge
                SleeveShape(armhole + listOf(outerEnd, innerEnd), edge, listOf(frill))
            }
            else -> SleeveShape(armhole + listOf(outerEnd, innerEnd), listOf(outerEnd, innerEnd), emptyList())
        }
    }
}
