package com.tailorsfit.pattern

import com.tailorsfit.pattern.blouse.BlouseCatalog
import com.tailorsfit.pattern.blouse.BlouseDrafter
import com.tailorsfit.pattern.blouse.InvalidMeasurementsException
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.geom.Pt
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
            val expected = (if (model.sleeve == SleeveStyle.SLEEVELESS) 2 else 3) +
                (if (model.princess) 1 else 0) +
                (if (model.sleeve == SleeveStyle.PUFF || model.sleeve == SleeveStyle.FRILL) 1 else 0) +
                (if (model.collar) 1 else 0) +
                (if (model.backDetail == com.tailorsfit.pattern.blouse.BackDetail.DORI) 1 else 0)
            assertEquals(expected, pattern.pieces.size, model.id)
            for (piece in pattern.pieces) {
                val outline = piece.seamOutline()
                assertTrue(outline.all { !it.x.isNaN() && !it.y.isNaN() }, "${model.id}/${piece.id} has NaN")
                val minArea = if (piece.edges.any { it.kind == EdgeKind.BAND }) 20.0 else 100.0
                assertTrue(piece.area() > minArea, "${model.id}/${piece.id} area ${piece.area()}")
                // Darts, notches and markings live on or inside the piece.
                for (d in piece.darts) assertTrue(pointInPolygon(d.tip, outline), "${model.id}/${piece.id} dart tip outside")
            }
        }
    }

    @Test
    fun shoulderSeamsMatch() {
        for (model in BlouseCatalog.models) {
            val p = model.draft(Measurements.defaults())
            val front = p.pieces.first { it.id == "front" || it.id == "front_centre" }
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
    fun armholeIsTheMeasurementPlusEase() {
        for (m in sizes) {
            val p = BlouseCatalog.models.first().draft(m)
            val arm = p.pieces.filter { it.id != "sleeve" }.sumOf { it.lengthOf(EdgeKind.ARMHOLE) }
            assertEquals(m[MeasurementField.ARMHOLE] + BlouseDrafter.ARMHOLE_EASE, arm, 0.05)
        }
    }

    @Test
    fun followsTheTraditionalInchDraft() {
        // Size 36 as in a tailor's inch draft: chest 36", waist 32", shoulder 12", armhole 16",
        // lengths 14". Chest and waist quarters are +1", the armhole is about 6" deep.
        val inch = BlouseDrafter.INCH
        val m = Measurements.defaults()
            .with(MeasurementField.BUST, 36 * inch).with(MeasurementField.WAIST, 32 * inch)
            .with(MeasurementField.SHOULDER, 12 * inch).with(MeasurementField.ARMHOLE, 16 * inch)
            .with(MeasurementField.FRONT_LENGTH, 14.5 * inch).with(MeasurementField.BACK_LENGTH, 14 * inch)
        val p = BlouseCatalog.models.first { it.id == "blouse_round_classic" }.draft(m)
        val front = p.pieces.first { it.id == "front" }
        val back = p.pieces.first { it.id == "back" }
        val underarm = front.points.getValue("underarm")
        assertEquals(10.0, underarm.x / inch, 0.01)
        assertEquals(6.0, (underarm.y - front.points.getValue("shoulder").y) / inch, 0.5)
        assertEquals(6.0, front.points.getValue("shoulder").x / inch, 0.01)
        assertEquals(0.5, front.points.getValue("shoulder").y / inch, 0.01)
        // Front ½" longer than back: taken by a ½" side dart, no lift needed.
        assertEquals(0.5, front.darts.last().intake / inch, 0.05)
        assertEquals(back.lengthOf(EdgeKind.SIDE), front.lengthOf(EdgeKind.SIDE) - front.darts.last().intake, 0.05)
        // Sleeve cap about 3½–4½", like a traditional blouse sleeve.
        val cap = p.pieces.first { it.id == "sleeve" }.points.getValue("capHeight").y / inch
        assertTrue(cap in 3.5..4.5, "cap $cap")
    }

    @Test
    fun extraFrontLengthLiftsTheFrontBottomAtTheSide() {
        val inch = BlouseDrafter.INCH
        val m = Measurements.defaults().with(MeasurementField.FRONT_LENGTH, 16.5 * inch).with(MeasurementField.BACK_LENGTH, 14 * inch)
        val front = BlouseCatalog.models.first { it.id == "blouse_round_classic" }.draft(m).pieces.first { it.id == "front" }
        val hem = front.edgesOf(EdgeKind.HEM).single().path.points()
        // Centre stays at the full front length; the side comes up by 2½" − 1¼" side dart.
        assertEquals(16.5, hem.first().y / inch, 0.01)
        assertEquals(14 + 1.25, hem.last().y / inch, 0.01)
        assertEquals(1.25, front.darts.last().intake / inch, 0.05)
    }

    @Test
    fun potNeckIsNarrowAtTheTopAndWideBelow() {
        val back = BlouseCatalog.models.first { it.id == "blouse_pot_neck" }.draft(Measurements.defaults()).pieces.first { it.id == "back" }
        val neck = back.edgesOf(EdgeKind.NECK).single().path.points()
        val top = neck.first()
        val waist = neck.filter { it.y < top.y + (neck.maxOf { p -> p.y } - top.y) * 0.35 }.minOf { it.x }
        val belly = neck.maxOf { it.x }
        assertTrue(waist < top.x && belly > top.x + 1.0, "top ${top.x}, narrowest $waist, widest $belly")
    }

    @Test
    fun openingDecidesWhichCentreIsOnTheFold() {
        for (model in BlouseCatalog.models) {
            val p = model.draft(Measurements.defaults())
            val front = p.pieces.first { it.id == "front" || it.id == "front_centre" }
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

    @Test
    fun princessCutSplitsTheFrontIntoTwoMatchingPanels() {
        val princessModels = BlouseCatalog.models.filter { it.princess }
        assertTrue(princessModels.size >= 3)
        for (model in princessModels) for (m in sizes) {
            val p = model.draft(m)
            val centre = p.pieces.first { it.id == "front_centre" }
            val side = p.pieces.first { it.id == "front_side" }
            val back = p.pieces.first { it.id == "back" }
            assertTrue(p.pieces.none { it.id == "front" })
            assertTrue(centre.darts.isEmpty() && side.darts.isEmpty(), "princess fronts have no darts")
            assertEquals(2, side.cut.count)
            // The two edges of the princess seam are sewn together, so they must be the same length.
            assertEquals(centre.lengthOf(EdgeKind.PRINCESS), side.lengthOf(EdgeKind.PRINCESS), 0.3, model.id)
            // Side seam matches the back without a side dart.
            assertEquals(back.lengthOf(EdgeKind.SIDE), side.lengthOf(EdgeKind.SIDE), 0.05, model.id)
            // Seam passes through the bust point, and both panels meet the armhole at the same point.
            val apex = centre.points.getValue("apex")
            assertTrue(centre.seamOutline().any { it.dist(apex) < 0.01 } && side.seamOutline().any { it.dist(apex) < 0.01 })
            assertEquals(centre.points.getValue("princessTop"), side.points.getValue("princessTop"))
            // Panels do not overlap: the side panel lies to the side of the centre panel at every height.
            val sideMinX = side.seamOutline().minOf { it.x }
            assertTrue(sideMinX > 0.0)
            assertTrue(centre.area() > 100 && side.area() > 100)
        }
    }

    @Test
    fun princessArmholeEqualsDartedArmhole() {
        val darted = BlouseCatalog.models.first { it.id == "blouse_round_back_open" }.draft(Measurements.defaults())
        val princess = BlouseCatalog.models.first { it.id == "blouse_princess_round" }.draft(Measurements.defaults())
        val a = darted.pieces.first { it.id == "front" }.lengthOf(EdgeKind.ARMHOLE)
        val b = princess.pieces.filter { it.id.startsWith("front") }.sumOf { it.lengthOf(EdgeKind.ARMHOLE) }
        assertEquals(a, b, 0.05)
    }

    @Test
    fun everyDesignTheAdminEditorAllowsCanBeDrafted() {
        // The admin design editor combines these options; each combination must draft cleanly.
        val depths = listOf(0.4, 1.0, 1.8)
        for (front in com.tailorsfit.pattern.blouse.NeckShape.entries) for (back in com.tailorsfit.pattern.blouse.NeckShape.entries)
            for (princess in listOf(false, true)) for (d in depths) for (widen in listOf(0.0, 5.0)) {
                val model = com.tailorsfit.pattern.blouse.BlouseModel(
                    "t", "t", "",
                    com.tailorsfit.pattern.blouse.NeckSpec(front, widen, d),
                    com.tailorsfit.pattern.blouse.NeckSpec(back, widen, d),
                    SleeveStyle.entries[(front.ordinal + back.ordinal) % SleeveStyle.entries.size],
                    Opening.entries[(front.ordinal + (if (princess) 1 else 0)) % 2],
                    princess,
                    com.tailorsfit.pattern.blouse.BackDetail.entries[(front.ordinal + back.ordinal + d.toInt()) % 3],
                    collar = (front.ordinal + back.ordinal) % 4 == 0,
                )
                for (m in listOf(SizePreset.S.measurements(), SizePreset.XXL.measurements())) {
                    val p = model.draft(m)
                    for (piece in p.pieces) {
                        assertTrue(piece.area() > 50, "$front/$back/$princess/$d/$widen ${piece.id}")
                        assertTrue(piece.seamOutline().none { it.x.isNaN() || it.y.isNaN() })
                    }
                }
            }
    }

    @Test
    fun trendyDetailsAreDrafted() {
        val m = Measurements.defaults()
        fun model(id: String) = BlouseCatalog.models.first { it.id == id }

        // Puff: wider, taller cap than the plain sleeve, gathered into a band.
        val plain = model("blouse_round_classic").draft(m).pieces.first { it.id == "sleeve" }
        val puff = model("blouse_puff_sweetheart").draft(m)
        val puffSleeve = puff.pieces.first { it.id == "sleeve" }
        assertTrue(puffSleeve.lengthOf(EdgeKind.SLEEVE_CAP) > plain.lengthOf(EdgeKind.SLEEVE_CAP) * 1.2)
        val band = puff.pieces.first { it.id == "sleeve_band" }
        assertEquals(m[MeasurementField.SLEEVE_OPENING] + BlouseDrafter.SLEEVE_HEM_EASE, band.lengthOf(EdgeKind.BAND) / 2 - BlouseDrafter.SLEEVE_BAND_WIDTH, 0.01)

        // Bell: hem much wider than the arm.
        val bell = model("blouse_boat_bell").draft(m).pieces.first { it.id == "sleeve" }
        assertTrue(bell.lengthOf(EdgeKind.SLEEVE_HEM) > m[MeasurementField.SLEEVE_ROUND] * 1.4)

        // Frill strips, collar band, tie strings.
        assertEquals(4, model("blouse_v_frill").draft(m).pieces.first { it.id == "frill" }.cut.count)
        val collarPattern = model("blouse_mandarin_collar").draft(m)
        val collar = collarPattern.pieces.first { it.id == "collar" }
        assertTrue(collar.cut.onFold)
        val neck = collarPattern.pieces.filter { it.id.startsWith("front") || it.id == "back" }.sumOf { it.lengthOf(EdgeKind.NECK) }
        assertEquals(neck + 0.5 * BlouseDrafter.INCH, collar.edges.first().path.length(), 0.01)
        assertEquals(2, model("blouse_dori_back").draft(m).pieces.first { it.id == "tie" }.cut.count)

        // Keyhole: a hole inside the back, touching the fold; whole when unfolded.
        val back = model("blouse_keyhole_back").draft(m).pieces.first { it.id == "back" }
        assertEquals(1, back.cutouts.size)
        val hole = back.cutouts.single()
        assertTrue(hole.first().x < 1e-6 && hole.last().x < 1e-6 && hole.all { com.tailorsfit.pattern.geom.pointInPolygon(Pt(it.x + 0.01, it.y), back.seamOutline()) })
        val full = back.unfolded().cutouts.single()
        assertEquals(-full.minOf { it.x }, full.maxOf { it.x }, 1e-6)

        // Paan and pot necks reach the centre line at the requested depth.
        for (id in listOf("blouse_paan_back", "blouse_pot_neck")) {
            val b = model(id).draft(m).pieces.first { it.id == "back" }
            assertTrue(b.edgesOf(EdgeKind.NECK).single().path.end.x < 1e-9, id)
        }
    }
}
