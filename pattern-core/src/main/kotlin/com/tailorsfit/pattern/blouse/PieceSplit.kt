package com.tailorsfit.pattern.blouse

import com.tailorsfit.pattern.geom.LineTo
import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.pointInPolygon
import com.tailorsfit.pattern.model.Dart
import com.tailorsfit.pattern.model.Edge
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.Marking
import com.tailorsfit.pattern.model.Piece

/**
 * Cuts a piece in two along the line y = [line] (x): the part above it and the part below,
 * each closed by a new seam ([EdgeKind.BELT]) that follows the line. Used for pattis, yokes
 * and inserts. Returns null if the line does not cross the piece exactly once from side to side.
 */
internal object PieceSplit {
    private class Run(val kind: EdgeKind, val above: Boolean, val pts: List<Pt>, val whole: Edge?) {
        val start get() = pts.first()
        val end get() = pts.last()
        fun edge() = whole ?: Edge(kind, PathD(pts.first(), pts.drop(1).map { LineTo(it) }))
    }

    class Result(val upper: Piece, val lower: Piece)

    fun split(piece: Piece, line: (Double) -> Double, minDartLength: Double = 2.5): Result? {
        fun f(p: Pt) = p.y - line(p.x)
        fun above(p: Pt) = f(p) < 0
        fun cross(a: Pt, b: Pt): Pt {
            var lo = 0.0
            var hi = 1.0
            val aAbove = above(a)
            repeat(50) {
                val mid = (lo + hi) / 2
                if (above(a.lerp(b, mid)) == aAbove) lo = mid else hi = mid
            }
            return a.lerp(b, (lo + hi) / 2)
        }

        val runs = ArrayList<Run>()
        for (e in piece.edges) {
            val pts = e.path.points()
            val sides = pts.map(::above)
            if (sides.all { it == sides.first() }) {
                runs += Run(e.kind, sides.first(), pts, e)
                continue
            }
            var cur = mutableListOf(pts.first())
            for (i in 1 until pts.size) {
                if (sides[i] != sides[i - 1]) {
                    val c = cross(pts[i - 1], pts[i])
                    cur += c
                    runs += Run(e.kind, sides[i - 1], cur, null)
                    cur = mutableListOf(c)
                }
                cur += pts[i]
            }
            runs += Run(e.kind, sides.last(), cur, null)
        }
        val starts = runs.indices.filter { i -> runs[i].above && !runs[(i - 1 + runs.size) % runs.size].above }
        val ends = runs.indices.filter { i -> !runs[i].above && runs[(i - 1 + runs.size) % runs.size].above }
        if (starts.size != 1 || ends.size != 1) return null
        val rotated = runs.indices.map { runs[(starts.single() + it) % runs.size] }
        val up = rotated.takeWhile { it.above }
        val down = rotated.drop(up.size)
        if (up.isEmpty() || down.isEmpty()) return null
        val c1 = up.first().start // below -> above
        val c2 = up.last().end // above -> below

        // The seam follows the line from c2 back to c1.
        val n = 24
        val seamPts = (0..n).map { i ->
            val t = i.toDouble() / n
            when (i) {
                0 -> c2
                n -> c1
                else -> {
                    val x = c2.x + (c1.x - c2.x) * t
                    Pt(x, line(x))
                }
            }
        }
        val seam = PathD(seamPts.first(), seamPts.drop(1).map { LineTo(it) })
        val upperEdges = up.map { it.edge() } + Edge(EdgeKind.BELT, seam)
        val lowerEdges = down.map { it.edge() } + Edge(EdgeKind.BELT, seam.reversed())

        // Darts belong to the part their tip is in; one reaching across is shortened to stop
        // [minDartLength] short of the seam, or dropped if that leaves too little.
        fun clipDart(d: Dart, upperPart: Boolean): Dart? {
            val legsAbove = above(d.legA) && above(d.legB)
            val legsBelow = !above(d.legA) && !above(d.legB)
            val tipAbove = above(d.tip)
            if (upperPart && legsAbove && tipAbove) return d
            if (!upperPart && legsBelow && !tipAbove) return d
            if (!upperPart && legsBelow && tipAbove) {
                // Bottom dart reaching into the part above: stop it below the seam.
                val mid = d.legA.lerp(d.legB, 0.5)
                var t = 0.0
                while (t < 1.0 && f(mid.lerp(d.tip, t)) > minDartLength) t += 0.01
                val tip = mid.lerp(d.tip, (t - 0.01).coerceAtLeast(0.0))
                return if (mid.dist(tip) >= minDartLength) Dart(d.legA, tip, d.legB) else null
            }
            if (upperPart && legsAbove && !tipAbove) {
                val mid = d.legA.lerp(d.legB, 0.5)
                var t = 0.0
                while (t < 1.0 && f(mid.lerp(d.tip, t)) < -minDartLength) t += 0.01
                val tip = mid.lerp(d.tip, (t - 0.01).coerceAtLeast(0.0))
                return if (mid.dist(tip) >= minDartLength) Dart(d.legA, tip, d.legB) else null
            }
            return null
        }

        fun part(edges: List<Edge>, upperPart: Boolean, id: String): Piece {
            val outline = edges.flatMap { it.path.points() }
            fun inside(p: Pt) = above(p) == upperPart
            val markings = piece.markings.mapNotNull { m ->
                when {
                    inside(m.from) && inside(m.to) -> m
                    m.kind == Marking.Kind.GRAIN -> {
                        // Grain line crossing the seam: keep the part on this side.
                        val a = if (inside(m.from)) m.from else m.to
                        val b = if (inside(m.from)) m.to else m.from
                        val c = cross(a, b)
                        val end = a.lerp(c, 0.85)
                        if (a.dist(end) > 2.0) Marking(a, end, m.kind) else null
                    }
                    else -> null
                }
            }
            val label = if (pointInPolygon(piece.labelAt, outline)) piece.labelAt else {
                val xs = outline.map { it.x }
                val ys = outline.map { it.y }
                Pt((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
            }
            return piece.copy(
                id = id,
                edges = edges,
                darts = piece.darts.mapNotNull { clipDart(it, upperPart) },
                notches = piece.notches.filter { inside(it.at) },
                markings = markings,
                labelAt = label,
                cutouts = piece.cutouts.filter { c -> c.all(::inside) },
            )
        }
        return Result(part(upperEdges, true, piece.id), part(lowerEdges, false, piece.id))
    }
}
