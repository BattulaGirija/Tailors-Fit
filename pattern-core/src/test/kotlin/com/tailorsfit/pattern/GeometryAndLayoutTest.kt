package com.tailorsfit.pattern

import com.tailorsfit.pattern.blouse.BlouseCatalog
import com.tailorsfit.pattern.geom.PathD
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.geom.offsetPolygon
import com.tailorsfit.pattern.layout.LayoutEngine
import com.tailorsfit.pattern.layout.LayoutOptions
import com.tailorsfit.pattern.layout.TilePlanner
import com.tailorsfit.pattern.model.LengthUnit
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.SeamAllowances
import com.tailorsfit.pattern.render.SvgExporter
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryAndLayoutTest {
    private val square = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0))

    @Test
    fun offsetSquareGrowsOnEverySide() {
        for (poly in listOf(square, square.reversed())) {
            val r = Rect.of(offsetPolygon(poly, List(4) { 1.0 }))
            assertEquals(Rect(-1.0, -1.0, 11.0, 11.0), r)
        }
    }

    @Test
    fun zeroAllowanceEdgeStaysPut() {
        // Edge 3 runs from (0,10) to (0,0): the fold.
        val r = Rect.of(offsetPolygon(square, listOf(1.0, 1.0, 1.0, 0.0)))
        assertEquals(Rect(0.0, -1.0, 11.0, 11.0), r)
    }

    @Test
    fun cubicLengthApproximatesQuarterCircle() {
        val k = 0.5522847498 * 10
        val arc = PathD.build(Pt(10.0, 0.0)) { cubicTo(Pt(10.0, k), Pt(k, 10.0), Pt(0.0, 10.0)) }
        assertEquals(PI * 10 / 2, arc.length(), 0.02)
        val rev = arc.reversed()
        assertEquals(Pt(0.0, 10.0), rev.start)
        assertEquals(arc.length(), rev.length(), 1e-9)
    }

    @Test
    fun unfoldedPieceIsSymmetricAndDoubleTheArea() {
        val p = BlouseCatalog.models.first().draft(Measurements.defaults())
        val back = p.pieces.first { it.cut.onFold }
        val full = back.unfolded()
        assertFalse(full.hasFold)
        assertEquals(back.area() * 2, full.area(), 0.01)
        val b = Rect.of(full.seamOutline())
        assertEquals(-b.minX, b.maxX, 1e-6)
    }

    @Test
    fun foldedLayoutPutsFoldPiecesOnTheFoldWithoutOverlaps() {
        for (model in BlouseCatalog.models) {
            val pattern = model.draft(Measurements.defaults())
            val opts = LayoutOptions(fabricWidth = 90.0)
            val layout = LayoutEngine.layout(pattern, opts)
            assertEquals(pattern.pieces.size, layout.placed.size)
            val boxes = layout.placed.map { it.piece.bounds(opts.allowances).translated(it.offset.x, it.offset.y) }
            assertNoOverlaps(layout, model.id)
            for ((pp, box) in layout.placed.zip(boxes)) {
                assertTrue(box.minX >= -1e-6 && box.maxX <= layout.width + 1e-6)
                if (pp.piece.cut.onFold) {
                    val fold = pp.piece.edges.first { it.kind == com.tailorsfit.pattern.model.EdgeKind.FOLD }
                    assertEquals(0.0, pp.toLayout(fold.path.start).x, 1e-6)
                }
            }
            assertTrue(layout.length in 40.0..140.0, "${model.id} length ${layout.length}")
        }
    }

    @Test
    fun singleLayerLayoutHasEveryCopy() {
        val pattern = BlouseCatalog.models.first().draft(Measurements.defaults())
        val layout = LayoutEngine.layout(pattern, LayoutOptions(fabricWidth = 110.0, folded = false))
        // back (unfolded) + 2 fronts + 2 sleeves
        assertEquals(5, layout.placed.size)
        assertTrue(layout.placed.none { it.piece.cut.onFold })
    }

    @Test
    fun tilesCoverTheContentWithOverlap() {
        val content = Rect(0.0, 0.0, 45.0, 80.0)
        val plan = TilePlanner.plan(content, 19.8, 28.5, overlap = 1.0)
        assertEquals(3, plan.cols)
        assertEquals(3, plan.rows)
        val covered = plan.tiles.map { it.area }.reduce(Rect::union)
        assertTrue(covered.maxX >= content.maxX && covered.maxY >= content.maxY)
        assertEquals("A1", plan.tiles.first().label)
        assertEquals("C3", plan.tiles.last().label)
        assertTrue(plan.alignmentMarks().isNotEmpty())
        assertEquals(1, TilePlanner.plan(Rect(0.0, 0.0, 10.0, 10.0), 19.8, 28.5).tiles.size)
    }

    @Test
    fun svgIsTrueScale() {
        val pattern = BlouseCatalog.models.first().draft(Measurements.defaults())
        val layout = LayoutEngine.layout(pattern, LayoutOptions(allowances = SeamAllowances()))
        val svg = SvgExporter.export(layout, title = pattern.title, margin = 1.0)
        assertTrue(svg.contains("width=\"${fmt(layout.width + 2)}cm\""), svg.take(300))
        assertTrue(svg.contains("PLACE ON FOLD"))
    }

    @Test
    fun unitsConvert() {
        assertEquals(2.54, LengthUnit.INCH.toCm(1.0), 1e-9)
        assertEquals(36.0, LengthUnit.INCH.fromCm(91.44), 1e-9)
    }

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')

    private fun cutPolygons(layout: com.tailorsfit.pattern.layout.Layout) =
        layout.placed.map { pp -> pp.piece.cutOutline(layout.allowances).map(pp::toLayout) }

    private fun segmentsCross(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
        fun orient(p: Pt, q: Pt, r: Pt) = (q - p).cross(r - p)
        val d1 = orient(c, d, a)
        val d2 = orient(c, d, b)
        val d3 = orient(a, b, c)
        val d4 = orient(a, b, d)
        return ((d1 > 1e-9 && d2 < -1e-9) || (d1 < -1e-9 && d2 > 1e-9)) &&
            ((d3 > 1e-9 && d4 < -1e-9) || (d3 < -1e-9 && d4 > 1e-9))
    }

    private fun assertNoOverlaps(layout: com.tailorsfit.pattern.layout.Layout, what: String) {
        val polys = cutPolygons(layout)
        for (i in polys.indices) for (j in i + 1 until polys.size) {
            val a = polys[i]
            val b = polys[j]
            assertTrue(a.none { com.tailorsfit.pattern.geom.pointInPolygon(it, b) }, "$what: piece $i inside $j")
            assertTrue(b.none { com.tailorsfit.pattern.geom.pointInPolygon(it, a) }, "$what: piece $j inside $i")
            for (p in a.indices) for (q in b.indices) {
                assertFalse(
                    segmentsCross(a[p], a[(p + 1) % a.size], b[q], b[(q + 1) % b.size]),
                    "$what: cut lines of pieces $i and $j cross",
                )
            }
        }
    }

    @Test
    fun nestingNeverOverlapsAndStaysOnTheCloth() {
        for (model in BlouseCatalog.models) for (folded in listOf(true, false)) for (width in listOf(90.0, 110.0, 140.0)) {
            val pattern = model.draft(Measurements.defaults())
            val layout = LayoutEngine.layout(pattern, LayoutOptions(fabricWidth = width, folded = folded))
            val what = "${model.id} folded=$folded width=$width"
            assertNoOverlaps(layout, what)
            val usable = if (folded) width / 2 else width
            for ((pp, poly) in layout.placed.zip(cutPolygons(layout))) {
                if (pp.piece.id in layout.tooWide) continue // reported to the tailor instead
                assertTrue(poly.all { it.x >= -1e-6 && it.y >= -1e-6 && it.x <= usable + 1e-6 }, "$what: ${pp.piece.id} off the cloth")
            }
            for (id in layout.tooWide) {
                val piece = layout.placed.first { it.piece.id == id }.piece
                assertTrue(piece.bounds(layout.allowances).width > usable, "$what: $id wrongly reported too wide")
            }
            assertTrue(layout.efficiency in 0.3..1.0, "$what: efficiency ${layout.efficiency}")
        }
    }

    @Test
    fun oneWayPrintsKeepEveryPieceUpright() {
        val pattern = BlouseCatalog.models.first().draft(Measurements.defaults())
        val layout = LayoutEngine.layout(pattern, LayoutOptions(fabricWidth = 110.0, allowTurning = false))
        for (pp in layout.placed) {
            val original = pattern.pieces.first { it.id == pp.piece.id }
            // Same vertical order of points: the neck/cap stays towards the start of the cloth.
            val o = original.seamOutline()
            val n = pp.piece.seamOutline()
            assertEquals(o.minOf { it.y } - o.maxOf { it.y }, n.minOf { it.y } - n.maxOf { it.y }, 1e-6)
            assertEquals(o.indexOfFirst { it.y == o.minOf { p -> p.y } } >= 0, true)
            val topOriginal = o.minByOrNull { it.y }!!
            val topNow = n.minByOrNull { it.y }!!
            assertEquals(topOriginal.y - o.minOf { it.y }, topNow.y - n.minOf { it.y }, 1e-6)
        }
    }

    @Test
    fun nestingIsTighterThanPlainRows() {
        // Plain rows (the old layout): every fold piece starts a row; others sit side by side.
        for (model in BlouseCatalog.models) {
            val pattern = model.draft(Measurements.defaults())
            val opts = LayoutOptions(fabricWidth = 110.0)
            val nested = LayoutEngine.layout(pattern, opts)
            val rows = rowLayoutLength(pattern, opts)
            assertTrue(nested.length <= rows + 1e-6, "${model.id}: nested ${nested.length} vs rows $rows")
        }
    }

    private fun rowLayoutLength(pattern: com.tailorsfit.pattern.model.Pattern, opts: LayoutOptions): Double {
        val usable = opts.fabricWidth / 2
        val (fold, free) = pattern.pieces.partition { it.cut.onFold }
        var rowY = 0.0
        var rowX = 0.0
        var rowH = 0.0
        for (p in fold + free.sortedByDescending { it.bounds(opts.allowances).height }) {
            val b = p.bounds(opts.allowances)
            if (p.cut.onFold || (rowX > 0 && rowX + opts.gap + b.width > usable)) {
                if (rowH > 0) rowY += rowH + opts.gap
                rowX = 0.0
                rowH = 0.0
            }
            rowX += (if (rowX > 0) opts.gap else 0.0) + b.width
            rowH = maxOf(rowH, b.height)
        }
        return rowY + rowH
    }
}
