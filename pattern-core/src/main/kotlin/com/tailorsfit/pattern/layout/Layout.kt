package com.tailorsfit.pattern.layout

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.geom.signedArea
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.Piece
import com.tailorsfit.pattern.model.SeamAllowances
import kotlin.math.ceil
import kotlin.math.max

/** A piece moved to its place on the cloth / paper. */
data class PlacedPiece(val piece: Piece, val offset: Pt) {
    fun toLayout(p: Pt) = p + offset
}

/**
 * Pieces arranged on cloth. [width] runs across the cloth (x), [length] along it (y).
 * When [folded] is true the cloth is folded lengthwise and x = 0 is the fold.
 */
data class Layout(
    val placed: List<PlacedPiece>,
    val width: Double,
    val length: Double,
    val allowances: SeamAllowances,
    val folded: Boolean,
) {
    val bounds get() = Rect(0.0, 0.0, width, length)

    /** Share of the cloth area (0–1) that ends up inside pieces; the rest is waste. */
    val efficiency: Double
        get() {
            val used = placed.sumOf { kotlin.math.abs(signedArea(it.piece.cutOutline(allowances))) }
            return if (width <= 0 || length <= 0) 0.0 else (used / (width * length)).coerceAtMost(1.0)
        }
}

data class LayoutOptions(
    /** Usable cloth width in cm (selvedge to selvedge). */
    val fabricWidth: Double = 90.0,
    /** Fold the cloth lengthwise and cut both layers together (the usual way). */
    val folded: Boolean = true,
    val allowances: SeamAllowances = SeamAllowances(),
    /** Space kept between pieces. */
    val gap: Double = 1.0,
    /**
     * Pieces may be turned upside down to save cloth. Switch off for one-way prints, velvet
     * and other napped cloth, where every piece must point the same way.
     */
    val allowTurning: Boolean = true,
    /** How many piece orders the nesting tries; more = tighter but slower. */
    val trials: Int = 16,
)

object LayoutEngine {
    /**
     * Arranges the pieces to use as little cloth length as possible (see [Nester]). On folded
     * cloth pieces cut on the fold keep their centre line exactly on the fold, and pieces cut
     * as pairs get both copies from the two layers. On single-layer cloth fold pieces are opened
     * out and pairs are placed twice (the second copy mirrored). Grain always runs along the
     * cloth.
     */
    fun layout(pattern: Pattern, options: LayoutOptions = LayoutOptions()): Layout {
        val usableWidth = if (options.folded) options.fabricWidth / 2 else options.fabricWidth
        val turn = options.allowTurning
        val items: List<NestItem> = if (options.folded) {
            pattern.pieces.map { p ->
                if (p.cut.onFold) {
                    NestItem(p, listOfNotNull(Orientation.NORMAL, Orientation.FLIPPED.takeIf { turn }), onFold = true)
                } else {
                    // Two layers give a left and a right piece whichever way round it lies.
                    NestItem(
                        p,
                        listOfNotNull(Orientation.NORMAL, Orientation.MIRRORED, Orientation.ROTATED.takeIf { turn }, Orientation.FLIPPED.takeIf { turn }),
                        onFold = false,
                    )
                }
            }
        } else {
            pattern.pieces.flatMap { p ->
                when {
                    p.cut.onFold -> listOf(p.unfolded())
                    p.cut.count >= 2 -> listOf(p, p.mirrored().copy(id = p.id + "_2"))
                    else -> listOf(p)
                }
            }.map { NestItem(it, listOfNotNull(Orientation.NORMAL, Orientation.ROTATED.takeIf { turn }), onFold = false) }
        }

        val placed = Nester(usableWidth, options.allowances, options.gap, trials = options.trials).nest(items)
        val box = placed
            .map { it.piece.bounds(options.allowances).translated(it.offset.x, it.offset.y) }
            .reduceOrNull(Rect::union) ?: Rect(0.0, 0.0, 0.0, 0.0)
        return Layout(placed, max(usableWidth, box.maxX), box.maxY, options.allowances, options.folded)
    }
}

/** One printed page: shows the layout area starting at [origin] with the page's printable size. */
data class Tile(val row: Int, val col: Int, val origin: Pt, val width: Double, val height: Double) {
    val label: String get() = "${('A' + row)}${col + 1}"
    val area get() = Rect(origin.x, origin.y, origin.x + width, origin.y + height)
}

data class TilePlan(
    val origin: Pt,
    val tiles: List<Tile>,
    val rows: Int,
    val cols: Int,
    val overlap: Double,
    val pageWidth: Double,
    val pageHeight: Double,
) {
    /** Positions (in layout cm) of the alignment crosses printed in the overlap strips. */
    fun alignmentMarks(): List<Pt> {
        val marks = ArrayList<Pt>()
        val stepX = pageWidth - overlap
        val stepY = pageHeight - overlap
        for (c in 1 until cols) {
            val x = origin.x + c * stepX + overlap / 2
            for (r in 0 until rows) {
                val y0 = origin.y + r * stepY
                marks += Pt(x, y0 + pageHeight * 0.25)
                marks += Pt(x, y0 + pageHeight * 0.75)
            }
        }
        for (r in 1 until rows) {
            val y = origin.y + r * stepY + overlap / 2
            for (c in 0 until cols) {
                val x0 = origin.x + c * stepX
                marks += Pt(x0 + pageWidth * 0.25, y)
                marks += Pt(x0 + pageWidth * 0.75, y)
            }
        }
        return marks
    }
}

object TilePlanner {
    /**
     * Splits [content] into pages of printable size [pageWidth] × [pageHeight] cm that overlap
     * by [overlap] cm so they can be taped together.
     */
    fun plan(content: Rect, pageWidth: Double, pageHeight: Double, overlap: Double = 1.0): TilePlan {
        require(pageWidth > overlap * 2 && pageHeight > overlap * 2)
        val stepX = pageWidth - overlap
        val stepY = pageHeight - overlap
        val cols = max(1, ceil((content.width - overlap) / stepX - 1e-9).toInt())
        val rows = max(1, ceil((content.height - overlap) / stepY - 1e-9).toInt())
        val tiles = ArrayList<Tile>()
        for (r in 0 until rows) for (c in 0 until cols) {
            tiles += Tile(r, c, Pt(content.minX + c * stepX, content.minY + r * stepY), pageWidth, pageHeight)
        }
        return TilePlan(Pt(content.minX, content.minY), tiles, rows, cols, overlap, pageWidth, pageHeight)
    }
}
