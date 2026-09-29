package com.tailorsfit.pattern.layout

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.model.Piece
import com.tailorsfit.pattern.model.SeamAllowances
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Ways a piece may be turned on the cloth without spoiling the grain: the grain line always
 * stays along the length of the cloth, so only flips are allowed, never a quarter turn.
 */
enum class Orientation {
    NORMAL,
    /** Turned upside down (180°). */
    ROTATED,
    /** Flipped left-right. */
    MIRRORED,
    /** Flipped top-bottom. */
    FLIPPED;

    fun apply(p: Piece): Piece = when (this) {
        NORMAL -> p
        ROTATED -> p.map({ Pt(-it.x, -it.y) })
        MIRRORED -> p.map({ Pt(-it.x, it.y) }, mirrors = true)
        FLIPPED -> p.map({ Pt(it.x, -it.y) }, mirrors = true)
    }

    /** True when the piece's top now points down the cloth (matters for one-way prints and nap). */
    val upsideDown get() = this == ROTATED || this == FLIPPED
}

/** A piece to place, with the orientations it may take and whether it must touch the fold. */
class NestItem(val piece: Piece, val orientations: List<Orientation>, val onFold: Boolean)

/**
 * Places pieces on a strip of cloth [width] cm wide so that the used length is as short as
 * possible ("marker making"). Pieces are handled by their true outline on a fine grid, so
 * curved pieces can nest into each other's hollows (a sleeve cap into a neckline, etc.).
 *
 * Method: greedy bottom-left fill — each piece goes where its far edge ends up closest to the
 * start of the cloth — repeated for several piece orders; the shortest result wins.
 */
class Nester(
    private val width: Double,
    private val allowances: SeamAllowances,
    private val gap: Double,
    private val cell: Double = 0.5,
    private val trials: Int = 16,
    private val seed: Int = 7,
) {
    /** A piece in one orientation, normalised to start at (0,0), rasterised to row spans. */
    private class Variant(val piece: Piece, val orientation: Orientation, val widthCells: Int, val rows: Array<IntArray>) {
        val heightCells get() = rows.size
    }

    companion object {
        /** Up to this many pieces every order is tried (5! = 120). */
        const val MAX_EXHAUSTIVE = 5

        fun permutations(items: List<Int>): List<List<Int>> =
            if (items.size <= 1) listOf(items)
            else items.flatMap { first -> permutations(items - first).map { listOf(first) + it } }
    }

    private val cols = max(1, floor(width / cell + 1e-9).toInt())
    private val gapCells = max(0, ceil(gap / cell - 1e-9).toInt())

    fun nest(items: List<NestItem>): List<PlacedPiece> {
        if (items.isEmpty()) return emptyList()
        val variants = items.map { item -> item.orientations.distinct().map { variant(item.piece, it) } }
        val maxRows = variants.sumOf { vs -> vs.maxOf { it.heightCells } + gapCells } + 4

        val orders = ArrayList<List<Int>>()
        val idx = items.indices.toList()
        fun area(i: Int) = variants[i][0].rows.sumOf { spanWidth(it) }
        orders += idx.sortedByDescending { area(it) }
        orders += idx.sortedByDescending { variants[it][0].heightCells }
        orders += idx.sortedByDescending { variants[it][0].widthCells }
        orders += idx.sortedWith(compareByDescending<Int> { items[it].onFold }.thenByDescending { area(it) })
        if (items.size <= MAX_EXHAUSTIVE) {
            // Few pieces (a blouse has at most five to place): simply try every order.
            orders += permutations(idx)
        } else {
            val rnd = Random(seed)
            while (orders.size < trials) orders += idx.shuffled(rnd)
        }

        var best: List<Pair<Variant, Pt>>? = null
        var bestLength = Int.MAX_VALUE
        var bestSum = Long.MAX_VALUE
        for (order in orders.distinct()) {
            val result = runOrder(order, items, variants, maxRows, bestLength) ?: continue
            val length = result.maxOf { (v, at) -> at.y.toInt() + v.heightCells }
            val sum = result.sumOf { (_, at) -> at.y.toLong() }
            if (length < bestLength || (length == bestLength && sum < bestSum)) {
                best = result
                bestLength = length
                bestSum = sum
            }
        }
        val chosen = best ?: runOrder(idx, items, variants, maxRows, Int.MAX_VALUE)!!
        // Keep the original piece order in the output for stable screens.
        return items.indices.map { i ->
            val (v, at) = chosen.first { it.first.piece.id == variants[i][0].piece.id }
            val b = v.piece.bounds(allowances)
            PlacedPiece(v.piece, Pt(at.x * cell - b.minX, at.y * cell - b.minY))
        }
    }

    /** Places pieces in [order]; returns null early when it cannot beat [cutoff] rows. */
    private fun runOrder(
        order: List<Int>,
        items: List<NestItem>,
        variants: List<List<Variant>>,
        maxRows: Int,
        cutoff: Int,
    ): List<Pair<Variant, Pt>>? {
        val grid = Grid(cols, maxRows)
        val placed = ArrayList<Pair<Variant, Pt>>()
        var length = 0
        for (i in order) {
            var bestScore = Long.MAX_VALUE
            var bestVar: Variant? = null
            var bx = 0
            var by = 0
            for (v in variants[i]) {
                val xs = if (items[i].onFold) listOf(0) else (0..max(0, cols - v.widthCells)).toList()
                for (x in xs) {
                    var y = 0
                    while (y + v.heightCells <= maxRows) {
                        val bottom = y + v.heightCells
                        val score = bottom.toLong() * 100_000 + x
                        if (score >= bestScore) break
                        if (grid.fits(v, x, y)) {
                            bestScore = score
                            bestVar = v
                            bx = x
                            by = y
                            break
                        }
                        y++
                    }
                }
            }
            val v = bestVar ?: return null
            grid.mark(v, bx, by, gapCells)
            placed += v to Pt(bx.toDouble(), by.toDouble())
            length = max(length, by + v.heightCells)
            if (length > cutoff) return null
        }
        return placed
    }

    private fun variant(p: Piece, o: Orientation): Variant {
        val piece = o.apply(p)
        val poly = piece.cutOutline(allowances)
        val b = Rect.of(poly)
        val shifted = poly.map { Pt(it.x - b.minX, it.y - b.minY) }
        val h = max(1, ceil(b.height / cell - 1e-9).toInt())
        val w = max(1, ceil(b.width / cell - 1e-9).toInt())
        val rows = Array(h) { r -> rowSpans(shifted, r * cell, (r + 1) * cell, w) }
        return Variant(piece, o, w, rows)
    }

    /**
     * Cells covered by the polygon within the horizontal strip [y0, y1]: the union of the
     * polygon's interior on several scan lines, widened to include vertices in the strip.
     */
    private fun rowSpans(poly: List<Pt>, y0: Double, y1: Double, w: Int): IntArray {
        val intervals = ArrayList<DoubleArray>()
        val samples = 5
        for (k in 0..samples) {
            val y = (y0 + (y1 - y0) * k / samples).coerceIn(1e-6, Double.MAX_VALUE)
            val xs = ArrayList<Double>()
            for (i in poly.indices) {
                val a = poly[i]
                val c = poly[(i + 1) % poly.size]
                if ((a.y > y) != (c.y > y)) xs += a.x + (y - a.y) * (c.x - a.x) / (c.y - a.y)
            }
            xs.sort()
            var j = 0
            while (j + 1 < xs.size) {
                intervals += doubleArrayOf(xs[j], xs[j + 1])
                j += 2
            }
        }
        for (i in poly.indices) {
            val a = poly[i]
            val c = poly[(i + 1) % poly.size]
            // Edge pieces that pass through the strip without crossing a sample line.
            if (max(a.y, c.y) >= y0 && min(a.y, c.y) <= y1) {
                val t0 = if (c.y == a.y) 0.0 else ((y0 - a.y) / (c.y - a.y)).coerceIn(0.0, 1.0)
                val t1 = if (c.y == a.y) 1.0 else ((y1 - a.y) / (c.y - a.y)).coerceIn(0.0, 1.0)
                val xa = a.x + (c.x - a.x) * t0
                val xb = a.x + (c.x - a.x) * t1
                intervals += doubleArrayOf(min(xa, xb), max(xa, xb))
            }
        }
        if (intervals.isEmpty()) return IntArray(0)
        val cellsSpans = intervals
            .map { intArrayOf(floor(it[0] / cell).toInt().coerceIn(0, w - 1), (ceil(it[1] / cell).toInt() - 1).coerceIn(0, w - 1)) }
            .sortedBy { it[0] }
        val merged = ArrayList<IntArray>()
        for (s in cellsSpans) {
            val last = merged.lastOrNull()
            if (last != null && s[0] <= last[1] + 1) last[1] = max(last[1], s[1]) else merged += intArrayOf(s[0], s[1])
        }
        return merged.flatMap { listOf(it[0], it[1]) }.toIntArray()
    }

    private fun spanWidth(spans: IntArray): Int {
        var s = 0
        var i = 0
        while (i < spans.size) {
            s += spans[i + 1] - spans[i] + 1
            i += 2
        }
        return s
    }

    /** Occupancy grid with per-row prefix sums, so "is this span free?" is O(1). */
    private class Grid(val cols: Int, val rows: Int) {
        private val cells = Array(rows) { BooleanArray(cols) }
        private val prefix = Array(rows) { IntArray(cols + 1) }

        fun fits(v: Variant, x: Int, y: Int): Boolean {
            if (x + v.widthCells > cols && x != 0) return false
            for (r in v.rows.indices) {
                val spans = v.rows[r]
                val pre = prefix[y + r]
                var i = 0
                while (i < spans.size) {
                    val a = spans[i] + x
                    val b = min(spans[i + 1] + x, cols - 1)
                    if (a < cols && pre[b + 1] - pre[a] != 0) return false
                    i += 2
                }
            }
            return true
        }

        fun mark(v: Variant, x: Int, y: Int, gap: Int) {
            val touched = HashSet<Int>()
            for (r in v.rows.indices) {
                val spans = v.rows[r]
                var i = 0
                while (i < spans.size) {
                    val a = (spans[i] + x - gap).coerceAtLeast(0)
                    val b = (spans[i + 1] + x + gap).coerceAtMost(cols - 1)
                    for (rr in (y + r - gap).coerceAtLeast(0)..(y + r + gap).coerceAtMost(rows - 1)) {
                        val row = cells[rr]
                        for (c in a..b) row[c] = true
                        touched += rr
                    }
                    i += 2
                }
            }
            for (rr in touched) {
                val row = cells[rr]
                val pre = prefix[rr]
                for (c in 0 until cols) pre[c + 1] = pre[c] + if (row[c]) 1 else 0
            }
        }
    }
}
