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
    /** Parts cut in net (yokes, inserts), drawn see-through over the panels. */
    val sheer: List<List<Pt>> = emptyList(),
)

object Illustration {
    /** Front and back views of any garment drafted by this library. */
    fun of(pattern: Pattern): List<GarmentView> = when (pattern.meta["category"]) {
        "kurti" -> kurti(pattern)
        "skirt" -> skirt(pattern)
        else -> blouse(pattern)
    }

    /** A kurti: bodice and sleeves like a blouse; an anarkali adds its flared skirt below the waist. */
    fun kurti(pattern: Pattern): List<GarmentView> {
        val views = blouse(pattern.copy(pieces = pattern.pieces.filter { it.id != "kali" && it.id != "collar" }))
        if (pattern.meta["cut"] != "ANARKALI") return views.map { slits(it, pattern) }
        val waistX = pattern.meta["waistX"]?.toDoubleOrNull() ?: return views
        val length = pattern.meta["length"]?.toDoubleOrNull() ?: return views
        val kalis = pattern.meta["kalis"]?.toIntOrNull() ?: 12
        return views.map { v ->
            // The bodice bottom is the lowest point of the bodice panel at the centre.
            val bodice = v.panels.maxBy { p -> p.count { kotlin.math.abs(it.x) < 1e-6 } }
            val top = bodice.filter { kotlin.math.abs(it.x) < 1e-6 }.maxOf { it.y }
            val skirtLen = length - (pattern.meta["waistY"]?.toDoubleOrNull() ?: top)
            val hemHalf = waistX + skirtLen * 0.6
            val n = 24
            val hem = (0..n).map { i ->
                val t = -1.0 + 2.0 * i / n
                Pt(hemHalf * t, top + skirtLen + 1.2 * sin(i * Math.PI / 2).let { it * it } + 2.0 * (1 - t * t))
            }
            val skirt = listOf(Pt(-waistX, top)) + hem + Pt(waistX, top)
            val seams = (1 until kalis / 2).map { i ->
                val t = -1.0 + 2.0 * i / (kalis / 2)
                listOf(Pt(waistX * t, top), Pt(hemHalf * t, top + skirtLen))
            }
            v.copy(panels = listOf(skirt) + v.panels, seams = v.seams + seams + listOf(listOf(Pt(-waistX, top), Pt(waistX, top))), trims = v.trims + listOf(hem))
        }
    }

    /** Short dashes at the side seams where the slits open. */
    private fun slits(v: GarmentView, pattern: Pattern): GarmentView {
        val slitY = pattern.meta["slitY"]?.toDoubleOrNull() ?: return v
        val xs = v.panels.flatten().filter { it.y > slitY }
        if (xs.isEmpty()) return v
        val bottom = xs.maxOf { it.y }
        val right = v.panels.flatten().filter { kotlin.math.abs(it.y - slitY) < 4.0 }.maxOfOrNull { it.x } ?: return v
        return v.copy(trims = v.trims + listOf(listOf(Pt(right, slitY), Pt(right, bottom)), listOf(Pt(-right, slitY), Pt(-right, bottom))))
    }

    /** A lehenga or skirt seen from the front and the back. */
    fun skirt(pattern: Pattern): List<GarmentView> {
        val meta = pattern.meta
        fun d(key: String) = meta[key]?.toDoubleOrNull() ?: 0.0
        val waist = d("waistHalf")
        val hip = d("hipHalf")
        val hipY = d("hipY")
        val hem = d("hemHalf")
        val length = d("length")
        val band = d("band")
        val cut = meta["cut"] ?: "A_LINE"
        val kalis = meta["kalis"]?.toIntOrNull() ?: 8
        val wavy = cut in setOf("KALIDAR", "CIRCLE", "HALF_CIRCLE", "GATHERED", "TIERED", "MERMAID", "PLEATED")
        val n = 32
        val hemLine = (0..n).map { i ->
            val t = -1.0 + 2.0 * i / n
            val wave = if (wavy) 1.5 * sin(i * Math.PI / 2).let { it * it } else 0.0
            Pt(hem * t, length + wave + (if (wavy) 2.5 else 0.8) * (1 - t * t))
        }
        val mermaid = cut == "MERMAID"
        val kneeY = hipY + (length - hipY) * 0.5
        val rightSide = if (mermaid) listOf(Pt(waist, 0.0), Pt(hip, hipY), Pt(hip * 0.92, kneeY)) else listOf(Pt(waist, 0.0), Pt(hip, hipY))
        val outline = rightSide.reversed().map { Pt(-it.x, it.y) } + hemLine + rightSide.reversed()
        val waistband = listOf(Pt(-waist, -band), Pt(waist, -band), Pt(waist, 0.0), Pt(-waist, 0.0))
        val seams = ArrayList<List<Pt>>()
        when (cut) {
            "KALIDAR", "CIRCLE", "HALF_CIRCLE", "MERMAID" -> {
                val visible = maxOf(2, kalis / 2)
                for (i in 1 until visible) {
                    val t = -1.0 + 2.0 * i / visible
                    seams += if (mermaid) listOf(Pt(waist * t, 0.0), Pt(hip * t, hipY), Pt(hip * 0.92 * t, kneeY), Pt(hem * t, length))
                    else listOf(Pt(waist * t, 0.0), Pt(hip * t, hipY), Pt(hem * t, length))
                }
            }
            "TIERED" -> for (k in 1..2) {
                val y = length * k / 3
                val w = hip + (hem - hip) * (y - hipY).coerceAtLeast(0.0) / (length - hipY)
                seams += listOf(Pt(-w, y), Pt(w, y))
            }
            "PLEATED" -> for (i in 1 until 8) {
                val t = -1.0 + 2.0 * i / 8
                seams += listOf(Pt(waist * t, 0.0), Pt(waist * t * 1.05, 14.0))
            }
            "A_LINE", "PENCIL" -> for (sgn in listOf(-1.0, 1.0)) seams += listOf(Pt(sgn * waist * 0.5, 0.0), Pt(sgn * waist * 0.52, 10.0))
        }
        val front = GarmentView(tr("view.front"), listOf(outline, waistband), seams, listOf(hemLine, listOf(Pt(-waist, -band), Pt(waist, -band))))
        // Skirts close with a zip at the centre back; lehengas tie at the side.
        val backSeams = if (meta["lehenga"] == "true") seams else seams + listOf(listOf(Pt(0.0, -band), Pt(0.0, 18.0)))
        val back = front.copy(title = tr("view.back"), seams = backSeams)
        return listOf(front, back)
    }

    /** Front and back views of a blouse pattern (as drafted by the blouse drafter). */
    fun blouse(pattern: Pattern): List<GarmentView> {
        val fronts = asWorn(pattern.pieces).filter { it.id.startsWith("front") }
        val backs = pattern.pieces.filter { it.id == "back" || it.id == "back_yoke" }
        val sleeve = pattern.pieces.firstOrNull { it.id == "sleeve" }
        val style = Style(
            sleeve = pattern.meta["sleeve"] ?: "SHORT",
            collar = pattern.meta["collar"] == "true",
            ties = pattern.meta["back"] == "DORI",
        )
        return listOfNotNull(
            if (fronts.isNotEmpty()) view(tr("view.front"), fronts, sleeve, style, isBack = false) else null,
            if (backs.isNotEmpty()) view(tr("view.back"), backs, sleeve, style, isBack = true) else null,
        )
    }

    /**
     * Pieces as they look sewn: a patti is cut shorter than its seam (the darts are closed in
     * it), so for drawing it is stretched back to the width of the front above it.
     */
    fun asWorn(pieces: List<Piece>): List<Piece> {
        val front = pieces.firstOrNull { it.id == "front_side" } ?: pieces.firstOrNull { it.id == "front" } ?: return pieces
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
        val sheer = ArrayList<List<Pt>>()

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
            if (half.id == "back_yoke" || half.id == "front_insert") sheer += full.seamOutline()
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
        return GarmentView(title, panels, seams, trims, holes, ties, sheer)
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
