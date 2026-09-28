package com.tailorsfit.pattern.geom

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A 2D point in centimetres. Y grows downwards (like paper and screens). */
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Double) = Pt(x * k, y * k)
    operator fun unaryMinus() = Pt(-x, -y)

    fun length(): Double = hypot(x, y)
    fun dist(o: Pt): Double = hypot(x - o.x, y - o.y)
    fun normalized(): Pt {
        val l = length()
        return if (l < 1e-12) Pt(0.0, 0.0) else Pt(x / l, y / l)
    }
    fun lerp(o: Pt, t: Double) = Pt(x + (o.x - x) * t, y + (o.y - y) * t)
    fun dot(o: Pt) = x * o.x + y * o.y
    fun cross(o: Pt) = x * o.y - y * o.x
    fun mirroredX() = Pt(-x, y)

    companion object {
        val ZERO = Pt(0.0, 0.0)
    }
}

/** A path segment that starts where the previous one ended. */
sealed interface Seg {
    val end: Pt
    fun flatten(start: Pt): List<Pt>
    fun map(f: (Pt) -> Pt): Seg
    fun reversed(start: Pt): Seg
}

data class LineTo(override val end: Pt) : Seg {
    override fun flatten(start: Pt): List<Pt> = listOf(end)
    override fun map(f: (Pt) -> Pt): Seg = LineTo(f(end))
    override fun reversed(start: Pt): Seg = LineTo(start)
}

data class CubicTo(val c1: Pt, val c2: Pt, override val end: Pt) : Seg {
    fun pointAt(start: Pt, t: Double): Pt {
        val u = 1 - t
        val a = u * u * u
        val b = 3 * u * u * t
        val c = 3 * u * t * t
        val d = t * t * t
        return Pt(
            a * start.x + b * c1.x + c * c2.x + d * end.x,
            a * start.y + b * c1.y + c * c2.y + d * end.y,
        )
    }

    override fun flatten(start: Pt): List<Pt> {
        val ctrlLen = start.dist(c1) + c1.dist(c2) + c2.dist(end)
        val n = ceil(ctrlLen / FLATTEN_STEP_CM).toInt().coerceIn(4, 200)
        return (1..n).map { pointAt(start, it.toDouble() / n) }
    }

    override fun map(f: (Pt) -> Pt): Seg = CubicTo(f(c1), f(c2), f(end))
    override fun reversed(start: Pt): Seg = CubicTo(c2, c1, start)

    /** De Casteljau split at [t]: the part up to t, and the rest (which starts at pointAt(t)). */
    fun split(start: Pt, t: Double): Pair<CubicTo, CubicTo> {
        val p01 = start.lerp(c1, t)
        val p12 = c1.lerp(c2, t)
        val p23 = c2.lerp(end, t)
        val a = p01.lerp(p12, t)
        val b = p12.lerp(p23, t)
        val mid = a.lerp(b, t)
        return CubicTo(p01, a, mid) to CubicTo(b, p23, end)
    }

    /** Splits so the first part is [length] cm long (measured on the flattened curve). */
    fun splitAtLength(start: Pt, length: Double): Pair<CubicTo, CubicTo> {
        var lo = 0.0
        var hi = 1.0
        repeat(40) {
            val mid = (lo + hi) / 2
            if (polylineLength(listOf(start) + split(start, mid).first.flatten(start)) < length) lo = mid else hi = mid
        }
        return split(start, (lo + hi) / 2)
    }
}

/** Maximum chord length used when turning curves into polylines. */
const val FLATTEN_STEP_CM = 0.25

/** An open path: a start point followed by segments. */
data class PathD(val start: Pt, val segs: List<Seg>) {
    val end: Pt get() = segs.lastOrNull()?.end ?: start

    /** Polyline including the start point. */
    fun points(): List<Pt> {
        val out = ArrayList<Pt>()
        out += start
        var cur = start
        for (s in segs) {
            out += s.flatten(cur)
            cur = s.end
        }
        return out
    }

    fun length(): Double = polylineLength(points())

    fun map(f: (Pt) -> Pt) = PathD(f(start), segs.map { it.map(f) })

    fun reversed(): PathD {
        if (segs.isEmpty()) return this
        val starts = ArrayList<Pt>(segs.size)
        var cur = start
        for (s in segs) {
            starts += cur
            cur = s.end
        }
        val rev = segs.indices.reversed().map { i -> segs[i].reversed(starts[i]) }
        return PathD(end, rev)
    }

    /** Point and unit tangent at [distance] cm along the path (clamped). */
    fun pointAtDistance(distance: Double): Pair<Pt, Pt> = polylinePointAt(points(), distance)

    class Builder(private val start: Pt) {
        private val segs = ArrayList<Seg>()
        fun lineTo(p: Pt) = apply { segs += LineTo(p) }
        fun lineTo(x: Double, y: Double) = lineTo(Pt(x, y))
        fun cubicTo(c1: Pt, c2: Pt, end: Pt) = apply { segs += CubicTo(c1, c2, end) }
        fun build() = PathD(start, segs.toList())
    }

    companion object {
        fun line(a: Pt, b: Pt) = PathD(a, listOf(LineTo(b)))
        fun build(start: Pt, block: Builder.() -> Unit): PathD = Builder(start).apply(block).build()
    }
}

fun polylineLength(pts: List<Pt>): Double {
    var l = 0.0
    for (i in 1 until pts.size) l += pts[i - 1].dist(pts[i])
    return l
}

fun polylinePointAt(pts: List<Pt>, distance: Double): Pair<Pt, Pt> {
    require(pts.size >= 2) { "Need at least two points" }
    var remaining = max(0.0, distance)
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val seg = a.dist(b)
        if (seg < 1e-12) continue
        if (remaining <= seg) {
            return a.lerp(b, remaining / seg) to (b - a).normalized()
        }
        remaining -= seg
    }
    val a = pts[pts.size - 2]
    val b = pts.last()
    return b to (b - a).normalized()
}

/** Shoelace signed area. Positive/negative depends on winding. */
fun signedArea(poly: List<Pt>): Double {
    var s = 0.0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        s += a.x * b.y - b.x * a.y
    }
    return s / 2
}

fun pointInPolygon(p: Pt, poly: List<Pt>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        if ((a.y > p.y) != (b.y > p.y)) {
            val xCross = (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
            if (p.x < xCross) inside = !inside
        }
        j = i
    }
    return inside
}

/** Axis-aligned bounding box. */
data class Rect(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    fun union(o: Rect) = Rect(min(minX, o.minX), min(minY, o.minY), max(maxX, o.maxX), max(maxY, o.maxY))
    fun translated(dx: Double, dy: Double) = Rect(minX + dx, minY + dy, maxX + dx, maxY + dy)
    fun intersects(o: Rect, eps: Double = 1e-6) =
        minX < o.maxX - eps && o.minX < maxX - eps && minY < o.maxY - eps && o.minY < maxY - eps

    companion object {
        fun of(points: Iterable<Pt>): Rect {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (p in points) {
                minX = min(minX, p.x); minY = min(minY, p.y)
                maxX = max(maxX, p.x); maxY = max(maxY, p.y)
            }
            return Rect(minX, minY, maxX, maxY)
        }
    }
}

/** Intersection of two infinite lines (p1 + t*d1, p2 + s*d2), or null if parallel. */
fun intersectLines(p1: Pt, d1: Pt, p2: Pt, d2: Pt): Pt? {
    val denom = d1.cross(d2)
    if (abs(denom) < 1e-9) return null
    val t = (p2 - p1).cross(d2) / denom
    return p1 + d1 * t
}

/**
 * Offsets a closed polygon outwards. [amounts] holds, for every vertex i, the offset of the
 * edge running from vertex i to vertex i+1. Amount 0 keeps that edge in place (e.g. a fold).
 */
fun offsetPolygon(poly: List<Pt>, amounts: List<Double>, miterLimit: Double = 3.0): List<Pt> {
    require(poly.size == amounts.size && poly.size >= 3)
    val n = poly.size
    val outwardSign = if (signedArea(poly) > 0) 1.0 else -1.0
    fun normal(i: Int): Pt {
        val d = (poly[(i + 1) % n] - poly[i]).normalized()
        return Pt(d.y, -d.x) * outwardSign
    }
    val out = ArrayList<Pt>(n + 8)
    for (i in 0 until n) {
        val prev = (i - 1 + n) % n
        val v = poly[i]
        val a0 = amounts[prev]
        val a1 = amounts[i]
        val n0 = normal(prev)
        val n1 = normal(i)
        val d0 = (v - poly[prev]).normalized()
        val d1 = (poly[(i + 1) % n] - v).normalized()
        val p0 = v + n0 * a0
        val p1 = v + n1 * a1
        if (a0 == 0.0 && a1 == 0.0) {
            out += v
            continue
        }
        val hit = intersectLines(p0, d0, p1, d1)
        val limit = miterLimit * max(a0, a1)
        if (a0 == 0.0 || a1 == 0.0) {
            // Next to a fold the cut line must end exactly on the fold line, so never bevel here.
            out += if (hit != null && hit.dist(v) <= 8 * max(a0, a1)) hit else v
            continue
        }
        when {
            hit == null -> {
                out += p0
                if (p0.dist(p1) > 1e-9) out += p1
            }
            hit.dist(v) > limit -> {
                out += p0
                out += p1
            }
            else -> out += hit
        }
    }
    return out
}

/**
 * Sutherland–Hodgman clip of a polygon to the vertical half-plane (x - lineX) * side >= 0.
 * Points exactly on the line are kept.
 */
fun clipToHalfPlane(poly: List<Pt>, lineX: Double, side: Double): List<Pt> {
    fun inside(p: Pt) = (p.x - lineX) * side >= -1e-9
    val out = ArrayList<Pt>(poly.size)
    for (i in poly.indices) {
        val cur = poly[i]
        val prev = poly[(i - 1 + poly.size) % poly.size]
        val curIn = inside(cur)
        val prevIn = inside(prev)
        if (curIn != prevIn) {
            val t = (lineX - prev.x) / (cur.x - prev.x)
            out += Pt(lineX, prev.y + (cur.y - prev.y) * t)
        }
        if (curIn) out += cur
    }
    return out
}

/** Circle-ish helper: distance from a point to a segment. */
fun distanceToSegment(p: Pt, a: Pt, b: Pt): Double {
    val ab = b - a
    val len2 = ab.dot(ab)
    if (len2 < 1e-12) return p.dist(a)
    val t = ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
    return p.dist(a + ab * t)
}

fun Double.sq() = this * this
fun hyp(a: Double, b: Double) = sqrt(a * a + b * b)
