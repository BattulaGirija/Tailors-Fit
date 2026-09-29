package com.tailorsfit.pattern.model

import com.tailorsfit.pattern.i18n.tr

import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.geom.clipToHalfPlane
import com.tailorsfit.pattern.geom.offsetPolygon
import com.tailorsfit.pattern.geom.signedArea

/** What an outline edge is, which decides its seam allowance and how it is drawn. */
enum class EdgeKind(val label: String) {
    NECK("Neck"),
    SHOULDER("Shoulder"),
    ARMHOLE("Armhole"),
    SIDE("Side seam"),
    HEM("Bottom"),
    FOLD("Fold"),
    OPENING("Opening"),
    SLEEVE_CAP("Sleeve cap"),
    UNDERARM("Underarm seam"),
    SLEEVE_HEM("Sleeve hem"),
    PRINCESS("Princess seam"),
    /** Straight edges of bands, collars, frills and tie strings. */
    BAND("Band"),
}

/** Seam allowances in cm per edge kind. A fold never gets an allowance. */
data class SeamAllowances(
    // Tailors' usual allowances in inches: ⅜" neck/armhole, ⅝" shoulder, 1" side (room to
    // let out), ¾" hems, 1" hook overlap.
    val neck: Double = 0.375 * 2.54,
    val shoulder: Double = 0.625 * 2.54,
    val armhole: Double = 0.375 * 2.54,
    val side: Double = 1.0 * 2.54,
    val hem: Double = 0.75 * 2.54,
    val opening: Double = 1.0 * 2.54,
    val sleeveCap: Double = 0.375 * 2.54,
    val underarm: Double = 0.625 * 2.54,
    val sleeveHem: Double = 0.75 * 2.54,
    val princess: Double = 0.625 * 2.54,
    val band: Double = 0.375 * 2.54,
) {
    fun of(kind: EdgeKind): Double = when (kind) {
        EdgeKind.NECK -> neck
        EdgeKind.SHOULDER -> shoulder
        EdgeKind.ARMHOLE -> armhole
        EdgeKind.SIDE -> side
        EdgeKind.HEM -> hem
        EdgeKind.FOLD -> 0.0
        EdgeKind.OPENING -> opening
        EdgeKind.SLEEVE_CAP -> sleeveCap
        EdgeKind.UNDERARM -> underarm
        EdgeKind.SLEEVE_HEM -> sleeveHem
        EdgeKind.PRINCESS -> princess
        EdgeKind.BAND -> band
    }

    companion object {
        val NONE = SeamAllowances(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class Edge(val kind: EdgeKind, val path: PathD) {
    fun map(f: (Pt) -> Pt) = copy(path = path.map(f))
    fun reversed() = copy(path = path.reversed())
}

/** A dart: two legs on the seam line meeting at a tip inside the piece. */
data class Dart(val legA: Pt, val tip: Pt, val legB: Pt) {
    fun map(f: (Pt) -> Pt) = Dart(f(legA), f(tip), f(legB))
    val intake: Double get() = legA.dist(legB)
}

/** A notch on the seam line; [outward] is the unit normal pointing out of the piece. */
data class Notch(val at: Pt, val outward: Pt, val double: Boolean = false)

/** A straight marking line with an optional caption (bust point, grain line ...). */
data class Marking(val from: Pt, val to: Pt, val kind: Kind) {
    enum class Kind { GRAIN, GUIDE }
    fun map(f: (Pt) -> Pt) = copy(from = f(from), to = f(to))
}

data class CutInstruction(val count: Int, val onFold: Boolean) {
    val text: String
        get() = when {
            onFold -> tr("cut.fold", count)
            count == 2 -> tr("cut.pair")
            else -> tr("cut.n", count)
        }
}

/**
 * One pattern piece. The outline is a closed loop of [edges] (each edge ends where the next
 * begins) drawn on the sewing line; seam allowances are added around it by [cutOutline].
 */
data class Piece(
    val id: String,
    val name: String,
    val cut: CutInstruction,
    val edges: List<Edge>,
    val darts: List<Dart> = emptyList(),
    val notches: List<Notch> = emptyList(),
    val markings: List<Marking> = emptyList(),
    val points: Map<String, Pt> = emptyMap(),
    val labelAt: Pt,
    val notes: List<String> = emptyList(),
    /**
     * Holes cut inside the piece (e.g. a keyhole). On a piece cut on the fold, a hole that
     * touches the fold is stored as its half: a path from the fold, round, back to the fold.
     */
    val cutouts: List<List<Pt>> = emptyList(),
) {
    init {
        require(edges.isNotEmpty())
        for (i in edges.indices) {
            val a = edges[i].path.end
            val b = edges[(i + 1) % edges.size].path.start
            require(a.dist(b) < 1e-6) { "Piece $id: edge $i (${edges[i].kind}) does not meet edge ${(i + 1) % edges.size}" }
        }
    }

    /** Closed sewing-line polygon (no duplicated closing point). */
    fun seamOutline(): List<Pt> = seamVerticesWithKinds().map { it.first }

    /** Vertices together with the kind of the edge that starts at each vertex. */
    fun seamVerticesWithKinds(): List<Pair<Pt, EdgeKind>> {
        val out = ArrayList<Pair<Pt, EdgeKind>>()
        for (e in edges) {
            val pts = e.path.points()
            for (j in 0 until pts.size - 1) {
                val p = pts[j]
                if (out.isNotEmpty() && out.last().first.dist(p) < 1e-9) {
                    out[out.size - 1] = p to e.kind
                } else {
                    out += p to e.kind
                }
            }
        }
        if (out.size > 1 && out.first().first.dist(out.last().first) < 1e-9) out.removeAt(out.size - 1)
        return out
    }

    /** Cutting line = sewing line + allowances. */
    fun cutOutline(allowances: SeamAllowances): List<Pt> {
        val v = seamVerticesWithKinds()
        val cut = offsetPolygon(v.map { it.first }, v.map { allowances.of(it.second) })
        // Allowances of edges meeting the fold at a sharp angle (e.g. a V back) can poke across
        // the fold; nothing may lie beyond it, so clip there.
        val fold = edges.firstOrNull { it.kind == EdgeKind.FOLD } ?: return cut
        val foldX = fold.path.start.x
        val inside = if (seamOutline().sumOf { it.x - foldX } >= 0) 1.0 else -1.0
        return clipToHalfPlane(cut, foldX, inside)
    }

    fun bounds(allowances: SeamAllowances): Rect = Rect.of(cutOutline(allowances))

    fun area(): Double = kotlin.math.abs(signedArea(seamOutline()))

    fun edgesOf(kind: EdgeKind) = edges.filter { it.kind == kind }
    fun lengthOf(kind: EdgeKind) = edgesOf(kind).sumOf { it.path.length() }
    val hasFold get() = edges.any { it.kind == EdgeKind.FOLD }

    fun map(f: (Pt) -> Pt, mirrors: Boolean = false): Piece {
        val mappedEdges = edges.map { it.map(f) }
        return copy(
            edges = if (mirrors) mappedEdges.reversed().map { it.reversed() } else mappedEdges,
            darts = darts.map { it.map(f) },
            notches = notches.map { n ->
                val base = f(n.at)
                val tip = f(n.at + n.outward)
                n.copy(at = base, outward = (tip - base).normalized())
            },
            markings = markings.map { it.map(f) },
            points = points.mapValues { f(it.value) },
            labelAt = f(labelAt),
            cutouts = cutouts.map { c -> c.map(f) },
        )
    }

    /** Horizontally flipped copy (for the second piece of a pair, or projecting mirrored). */
    fun mirrored(): Piece = map({ it.mirroredX() }, mirrors = true)

    /**
     * Opens a piece that is cut on the fold into the full symmetric piece, for cutting on a
     * single layer of cloth. The fold must lie on x = 0.
     */
    fun unfolded(): Piece {
        val foldIdx = edges.indexOfFirst { it.kind == EdgeKind.FOLD }
        if (foldIdx < 0) return this
        // Edges after the fold, in order, until we come back to it.
        val right = (1 until edges.size).map { edges[(foldIdx + it) % edges.size] }
        val left = right.reversed().map { it.map(Pt::mirroredX).reversed() }
        val half = this
        val mirroredHalf = half.map(Pt::mirroredX)
        return copy(
            id = id,
            cut = CutInstruction(count = cut.count, onFold = false),
            edges = right + left,
            darts = darts + mirroredHalf.darts,
            notches = notches + mirroredHalf.notches,
            markings = markings.map { m ->
                if (m.kind == Marking.Kind.GRAIN) m.copy(from = Pt(0.0, m.from.y), to = Pt(0.0, m.to.y)) else m
            } + mirroredHalf.markings.filter { it.kind != Marking.Kind.GRAIN },
            points = points + mirroredHalf.points.mapKeys { it.key + "_mirror" },
            labelAt = Pt(0.0, labelAt.y),
            cutouts = cutouts.map { c ->
                // Half hole on the fold + its mirror image = the whole hole.
                if (c.first().x < 1e-6 && c.last().x < 1e-6) c + c.reversed().drop(1).dropLast(1).map(Pt::mirroredX)
                else c
            } + cutouts.filter { c -> !(c.first().x < 1e-6 && c.last().x < 1e-6) }.map { c -> c.map(Pt::mirroredX) },
        )
    }
}

data class Pattern(
    val title: String,
    val pieces: List<Piece>,
    val warnings: List<String> = emptyList(),
    val summary: List<Pair<String, String>> = emptyList(),
    /** Design details for sketches, e.g. "sleeve" -> "PUFF", "collar" -> "true", "back" -> "DORI". */
    val meta: Map<String, String> = emptyMap(),
)
