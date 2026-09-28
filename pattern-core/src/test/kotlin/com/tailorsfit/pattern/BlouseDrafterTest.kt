package com.tailorsfit.pattern

import com.tailorsfit.pattern.blouse.BlouseCatalog
import com.tailorsfit.pattern.blouse.BlouseDrafter
import com.tailorsfit.pattern.blouse.InvalidMeasurementsException
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.geom.pointInPolygon
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.SeamAllowances
import com.tailorsfit.pattern.model.SizePreset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlouseDrafterTest {
    private val sizes = SizePreset.entries.map { it.measurements() }

    @Test
    fun everyModelDraftsForEverySize() {
        for (model in BlouseCatalog.models) for (m in sizes) {
            val pattern = model.draft(m)
            val expected = if (model.sleeve == SleeveStyle.SLEEVELESS) 2 else 3
            assertEquals(expected, pattern.pieces.size, model.id)
            for (piece in pattern.pieces) {
                val outline = piece.seamOutline()
                assertTrue(outline.all { !it.x.isNaN() && !it.y.isNaN() }, "${model.id}/${piece.id} has NaN")
                assertTrue(piece.area() > 100, "${model.id}/${piece.id} area ${piece.area()}")
                // Darts, notches and markings live on or inside the piece.
                for (d in piece.darts) assertTrue(pointInPolygon(d.tip, outline), "${model.id}/${piece.id} dart tip outside")
            }
        }
    }

    @Test
    fun shoulderSeamsMatch() {
        for (model in BlouseCatalog.models) {
            val p = model.draft(Measurements.defaults())
            val front = p.pieces.first { it.id == "front" }
            val back = p.pieces.first { it.id == "back" }
            assertEquals(front.lengthOf(EdgeKind.SHOULDER), back.lengthOf(EdgeKind.SHOULDER), 0.01, model.id)
        }
    }

    @Test
    fun sideSeamsMatchOnceTheSideDartIsClosed() {
        for (m in sizes) {
            val p = BlouseCatalog.models.first().draft(m)
            val front = p.pieces.first { it.id == "front" }
            val back = p.pieces.first { it.id == "back" }
            val sideDart = front.darts.maxByOrNull { it.legA.x }!!
            val frontClosed = front.lengthOf(EdgeKind.SIDE) - sideDart.intake
            assertEquals(back.lengthOf(EdgeKind.SIDE), frontClosed, 0.05)
        }
    }

    @Test
    fun finishedBustIsMeasurementPlusEase() {
        val m = Measurements.defaults()
        val p = BlouseCatalog.models.first().draft(m)
        val front = p.pieces.first { it.id == "front" }
        val back = p.pieces.first { it.id == "back" }
        val finished = 2 * (front.points.getValue("underarm").x + back.points.getValue("underarm").x)
        assertEquals(m[MeasurementField.BUST] + BlouseDrafter.BUST_EASE, finished, 0.01)
    }

    @Test
    fun sleeveCapMatchesArmholePlusEase() {
        for (m in sizes) {
            val p = BlouseCatalog.models.first().draft(m)
            val arm = p.pieces.filter { it.id != "sleeve" }.sumOf { it.lengthOf(EdgeKind.ARMHOLE) }
            val cap = p.pieces.first { it.id == "sleeve" }.lengthOf(EdgeKind.SLEEVE_CAP)
            assertEquals(arm + BlouseDrafter.CAP_EASE, cap, 0.1, "size bust=${m[MeasurementField.BUST]}")
        }
    }

    @Test
    fun armholeLengthIsCloseToTheMeasurement() {
        for (m in sizes) {
            val p = BlouseCatalog.models.first().draft(m)
            val arm = p.pieces.filter { it.id != "sleeve" }.sumOf { it.lengthOf(EdgeKind.ARMHOLE) }
            val measured = m[MeasurementField.ARMHOLE]
            assertTrue(abs(arm - measured) / measured < 0.08, "armhole $arm vs $measured")
        }
    }

    @Test
    fun openingDecidesWhichCentreIsOnTheFold() {
        for (model in BlouseCatalog.models) {
            val p = model.draft(Measurements.defaults())
            val front = p.pieces.first { it.id == "front" }
            val back = p.pieces.first { it.id == "back" }
            if (model.opening == Opening.FRONT) {
                assertTrue(back.cut.onFold && !front.cut.onFold && front.cut.count == 2)
                assertTrue(front.edges.any { it.kind == EdgeKind.OPENING })
            } else {
                assertTrue(front.cut.onFold && !back.cut.onFold && back.cut.count == 2)
            }
        }
    }

    @Test
    fun cutLineSurroundsSewingLine() {
        for (model in BlouseCatalog.models) {
            val p = model.draft(Measurements.defaults())
            for (piece in p.pieces) {
                val cut = piece.cutOutline(SeamAllowances())
                val seam = piece.seamOutline()
                // Points of the sewing line that are not on a fold must be strictly inside the cut line.
                val foldX = if (piece.hasFold) 0.0 else Double.NaN
                for (pt in seam) {
                    if (abs(pt.x - foldX) < 1e-6) continue
                    assertTrue(pointInPolygon(pt, cut), "${model.id}/${piece.id}: $pt outside cut line")
                }
                assertTrue(com.tailorsfit.pattern.geom.Rect.of(cut).width > com.tailorsfit.pattern.geom.Rect.of(seam).width)
            }
        }
    }

    @Test
    fun notchesPointOutOfThePiece() {
        val p = BlouseCatalog.models.first().draft(Measurements.defaults())
        for (piece in p.pieces) {
            val outline = piece.seamOutline()
            for (n in piece.notches) {
                assertTrue(!pointInPolygon(n.at + n.outward * 0.5, outline), "${piece.id} notch at ${n.at} points inwards")
            }
        }
    }

    @Test
    fun invalidMeasurementsAreRejected() {
        val bad = Measurements.defaults().with(MeasurementField.BUST, 10.0)
        assertFailsWith<InvalidMeasurementsException> { BlouseCatalog.models.first().draft(bad) }
    }

    @Test
    fun capSleeveIgnoresSleeveLength() {
        val model = BlouseCatalog.models.first { it.sleeve == SleeveStyle.CAP }
        assertTrue(MeasurementField.SLEEVE_LENGTH !in model.requiredMeasurements)
        val p = model.draft(Measurements.defaults().with(MeasurementField.SLEEVE_LENGTH, Double.NaN))
        assertEquals(3, p.pieces.size)
    }
}
