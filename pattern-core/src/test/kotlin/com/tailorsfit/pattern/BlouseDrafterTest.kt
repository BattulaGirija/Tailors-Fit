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
                (when { model.body.belted -> 2; model.body.panelled -> 1; else -> 0 }) +
                (if (model.sleeve == SleeveStyle.PUFF || model.sleeve == SleeveStyle.FRILL) 1 else 0) +
                (if (model.collar) 1 else 0) +
                (if (model.hasPatti) 1 else 0) +
                (if (model.frontInsert != com.tailorsfit.pattern.blouse.YokeShape.NONE) 1 else 0) +
                (if (model.backYoke != com.tailorsfit.pattern.blouse.YokeShape.NONE) 1 else 0) +
                (if (model.backDetail == com.tailorsfit.pattern.blouse.BackDetail.DORI) 1 else 0)
            assertEquals(expected, pattern.pieces.size, model.id)
            for (piece in pattern.pieces) {
                val outline = piece.seamOutline()
                assertTrue(outline.all { !it.x.isNaN() && !it.y.isNaN() }, "${model.id}/${piece.id} has NaN")
                val minArea = when {
                    piece.edges.any { it.kind == EdgeKind.BAND } -> 20.0
                    piece.id == "front_belt" || piece.id == "front_patti" -> 40.0
                    piece.id == "front_insert" -> 20.0
                    else -> 100.0
                }
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
            val front = p.pieces.filter { it.id.startsWith("front") }.sumOf { it.lengthOf(EdgeKind.SHOULDER) }
            val back = p.pieces.filter { it.id.startsWith("back") }.sumOf { it.lengthOf(EdgeKind.SHOULDER) }
            assertEquals(front, back, 0.01, model.id)
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
            val patti = p.pieces.firstOrNull { it.id == "front_patti" }?.lengthOf(EdgeKind.SIDE) ?: 0.0
            assertEquals(back.lengthOf(EdgeKind.SIDE), side.lengthOf(EdgeKind.SIDE) + patti, 0.05, model.id)
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

    @Test
    fun everyCustomisedDesignDrafts() {
        val m = Measurements.defaults()
        var n = 0
        val base = com.tailorsfit.pattern.blouse.BlouseSpec.BASIC
        for (body in com.tailorsfit.pattern.blouse.BodyStyle.entries) for (front in com.tailorsfit.pattern.blouse.FrontNeck.entries)
            for (back in com.tailorsfit.pattern.blouse.BackNeck.entries) {
                val depth = com.tailorsfit.pattern.blouse.NeckDepth.entries[n % 3]
                val sleeve = SleeveStyle.entries[n++ % SleeveStyle.entries.size]
                val spec = base.copy(body = body, sleeve = sleeve, opening = Opening.BACK).withFront(front, depth).withBack(back, depth)
                // The id brings back the same design, through the catalog like any saved design.
                assertEquals(spec.copy(opening = spec.effectiveOpening), com.tailorsfit.pattern.blouse.BlouseSpec.parse(spec.id))
                val model = com.tailorsfit.pattern.model.Catalog.model(spec.id) as com.tailorsfit.pattern.blouse.BlouseModel
                assertEquals(body, model.body)
                val p = model.draft(m)
                if (back.needsFrontOpening) assertTrue(p.pieces.first { it.id == "back" }.cut.onFold, spec.id)
                for (piece in p.pieces) {
                    assertTrue(piece.area() > 20, "${spec.id}/${piece.id}")
                    assertTrue(piece.seamOutline().none { it.x.isNaN() || it.y.isNaN() }, spec.id)
                    for (d in piece.darts) assertTrue(pointInPolygon(d.tip, piece.seamOutline()), "${spec.id}/${piece.id} dart tip outside")
                }
            }
        // A ready design keeps its own id and name when nothing was changed.
        val ready = BlouseCatalog.models.first { it.id == "blouse_pot_neck" }
        assertEquals(ready.id, com.tailorsfit.pattern.blouse.BlouseSpec.of(ready).toModel().id)
        assertEquals(null, com.tailorsfit.pattern.blouse.BlouseSpec.parse("blouse_round_classic"))
    }

    @Test
    fun dartCountsFollowTheBlouseType() {
        val m = Measurements.defaults()
        fun front(id: String) = BlouseCatalog.models.first { it.id == id }.draft(m).pieces.first { it.id == "front" }
        assertEquals(3, front("blouse_round_classic").darts.size)
        assertEquals(4, front("blouse_4dart_round").darts.size)
        // The 4-dart side darts together take what the single side dart takes.
        val three = front("blouse_round_classic").darts.maxBy { it.legA.x }.intake
        val four = front("blouse_4dart_round").darts.sortedByDescending { it.legA.x }.take(2).sumOf { it.intake }
        assertEquals(three, four, 0.05)
    }

    @Test
    fun beltedFrontsHaveCupsAndABeltThatFit() {
        for (id in listOf("blouse_katori_sweetheart", "blouse_katori_round", "blouse_sabyasachi_square", "blouse_sabyasachi_v", "blouse_saby_new3", "blouse_saby_new4", "blouse_saby_new5")) for (m in sizes) {
            val p = BlouseCatalog.models.first { it.id == id }.draft(m)
            val centre = p.pieces.first { it.id == "front_centre" }
            val side = p.pieces.first { it.id == "front_side" }
            val belt = p.pieces.first { it.id == "front_belt" }
            val back = p.pieces.first { it.id == "back" }
            assertTrue(centre.darts.isEmpty() && side.darts.isEmpty() && belt.darts.isEmpty(), id)
            assertEquals(centre.lengthOf(EdgeKind.PRINCESS), side.lengthOf(EdgeKind.PRINCESS), 0.4, id)
            // Belt top = both cup bottoms; side seams match the back.
            assertEquals(centre.lengthOf(EdgeKind.BELT) + side.lengthOf(EdgeKind.BELT), belt.lengthOf(EdgeKind.BELT), 0.05, id)
            assertEquals(back.lengthOf(EdgeKind.SIDE), side.lengthOf(EdgeKind.SIDE) + belt.lengthOf(EdgeKind.SIDE), 0.05, id)
            assertTrue(belt.area() > 50 && centre.area() > 100 && side.area() > 100, id)
        }
    }

    @Test
    fun adjustmentsChangeTheDraft() {
        val inch = BlouseDrafter.INCH
        val m = Measurements.defaults()
            .with(MeasurementField.NECK_BROAD, 3.0 * inch)
            .with(MeasurementField.ARMHOLE_DEPTH, 6.5 * inch)
            .with(MeasurementField.SHOULDER_DROP, 0.75 * inch)
            .with(MeasurementField.FRONT_DART_WIDTH, 0.5 * inch)
        val front = BlouseCatalog.models.first { it.id == "blouse_round_classic" }.draft(m).pieces.first { it.id == "front" }
        assertEquals(3.0, front.points.getValue("neck").x / inch, 0.01)
        assertEquals(6.5, front.points.getValue("underarm").y / inch, 0.01)
        assertEquals(0.75, front.points.getValue("shoulder").y / inch, 0.01)
        // A ½" front dart is too small to split: one dart under the bust plus the side dart.
        assertEquals(2, front.darts.size)
        // Body measurements alone are what the design asks for; adjustments are optional.
        assertTrue(BlouseCatalog.models.all { model -> model.requiredMeasurements.none { it.isAdjustment } })
    }

    @Test
    fun draftsFromATailorsMeasurementSheet() {
        // Size 36 sheet: Length 14, Upper chest 36, Center chest 36, Shoulder width 2, Sleeve
        // length 6, Sleeve round 12, Middle hand round 13, Front neck 7, Back neck 10, Waist
        // loose 30, Front dart point 9.5, Chest height 13.5, Full shoulder 15, Armhole 16.
        val inch = BlouseDrafter.INCH
        val sheet = mapOf(
            MeasurementField.BACK_LENGTH to 14.0, MeasurementField.UPPER_CHEST to 36.0, MeasurementField.BUST to 36.0,
            MeasurementField.SHOULDER_WIDTH to 2.0, MeasurementField.SLEEVE_LENGTH to 6.0, MeasurementField.SLEEVE_OPENING to 12.0,
            MeasurementField.SLEEVE_ROUND to 13.0, MeasurementField.FRONT_NECK_DEPTH to 7.0, MeasurementField.BACK_NECK_DEPTH to 10.0,
            MeasurementField.WAIST to 30.0, MeasurementField.APEX_LENGTH to 9.5, MeasurementField.CHEST_HEIGHT to 13.5,
            MeasurementField.SHOULDER to 15.0, MeasurementField.ARMHOLE to 16.0,
        ).mapValues { it.value * inch }
        val m = Measurements(sheet)
        // The sheet is everything a design needs.
        assertEquals(MeasurementField.blouse.toSet(), sheet.keys)
        for (model in BlouseCatalog.models) {
            val p = model.draft(m)
            assertTrue(p.pieces.isNotEmpty(), model.id)
            for (piece in p.pieces) assertTrue(piece.area() > 20, "${model.id}/${piece.id}")
        }
        val front = BlouseCatalog.models.first { it.id == "blouse_round_classic" }.draft(m).pieces.first { it.id == "front" }
        // Neck edge at full shoulder / 2 - shoulder width; shoulder tip at 7½".
        assertEquals(5.5, front.points.getValue("neck").x / inch, 0.01)
        assertEquals(7.5, front.points.getValue("shoulder").x / inch, 0.01)
        // Bust points 3.6" from the centre; chest quarter 10".
        assertEquals(3.6, front.points.getValue("apex").x / inch, 0.01)
        assertEquals(10.0, front.points.getValue("underarm").x / inch, 0.01)
        // Front is Length + ½" (upper chest = center chest).
        assertEquals(14.5, front.edges.first { it.kind == EdgeKind.OPENING }.path.end.y / inch, 0.01)
        // Katori belt: at the chest height, but at least 1½" tall at the centre (front 14½").
        val belt = BlouseCatalog.models.first { it.id == "blouse_katori_round" }.draft(m).pieces.first { it.id == "front_belt" }
        assertEquals(13.0, belt.edges.first { it.kind == EdgeKind.OPENING }.path.start.y / inch, 0.01)
        val roomy = Measurements(sheet + (MeasurementField.CHEST_HEIGHT to 12.0 * inch))
        val belt2 = BlouseCatalog.models.first { it.id == "blouse_katori_round" }.draft(roomy).pieces.first { it.id == "front_belt" }
        assertEquals(12.0, belt2.edges.first { it.kind == EdgeKind.OPENING }.path.start.y / inch, 0.01)
    }

    @Test
    fun threeDartCollectionHalterAndWaves() {
        val inch = BlouseDrafter.INCH
        fun model(id: String) = BlouseCatalog.models.first { it.id == id }
        val ids = listOf(
            "blouse_3d_basic_fo", "blouse_3d_basic_bo", "blouse_3d_boat_fo", "blouse_3d_boat_bo", "blouse_3d_close_fo",
            "blouse_3d_close_bo", "blouse_3d_halter_fo", "blouse_3d_halter_bo", "blouse_3d_high_fo", "blouse_3d_high_bo",
            "blouse_3d_basic_fo_bw", "blouse_3d_basic_bo_bw", "blouse_bengaluru_fo", "blouse_3d_model1",
        )
        for (id in ids) assertEquals(com.tailorsfit.pattern.blouse.BodyStyle.THREE_DART, model(id).body, id)
        for (m in sizes) {
            // Halter: the shoulder ends 1¾" from the neck point, front and back, and there is no sleeve.
            val halter = model("blouse_3d_halter_fo").draft(m)
            assertTrue(halter.pieces.none { it.id == "sleeve" })
            for (id in listOf("front", "back")) {
                val pc = halter.pieces.first { it.id == id }
                val sh = pc.points["shoulder"] ?: continue
                assertEquals(1.75, (sh.x - pc.points.getValue("neck").x) / inch, 0.01, id)
            }
            // Waves: the bottom edge goes up and down; the darts still open on it.
            val waves = model("blouse_3d_basic_fo_bw").draft(m)
            val plain = model("blouse_3d_basic_fo").draft(m)
            for (id in listOf("front", "back")) {
                val w = waves.pieces.first { it.id == id }
                val pl = plain.pieces.first { it.id == id }
                val hem = w.edgesOf(EdgeKind.HEM).flatMap { it.path.points() }
                assertTrue(hem.size > pl.edgesOf(EdgeKind.HEM).sumOf { it.path.points().size } + 10, id)
                val ys = hem.map { it.y }
                assertTrue(ys.max() - ys.min() > 0.3 * inch, id)
                assertEquals(pl.lengthOf(EdgeKind.SIDE), w.lengthOf(EdgeKind.SIDE), 0.5 * inch, id)
            }
        }
        // A customised design keeps halter and waves through its id.
        val spec = com.tailorsfit.pattern.blouse.BlouseSpec.of(model("blouse_3d_halter_bo")).copy(bottomWaves = true)
        val back = com.tailorsfit.pattern.blouse.BlouseSpec.parse(spec.id)!!
        assertTrue(back.halter && back.bottomWaves)
        val again = com.tailorsfit.pattern.model.Catalog.model(spec.id) as com.tailorsfit.pattern.blouse.BlouseModel
        assertTrue(again.halter && again.bottomWaves && again.effectiveSleeve == SleeveStyle.SLEEVELESS)
    }

    @Test
    fun pattiIsCutOffTheBottomOfTheFront() {
        val inch = BlouseDrafter.INCH
        fun model(id: String) = BlouseCatalog.models.first { it.id == id }
        assertEquals(21, BlouseCatalog.models.count { it.id.startsWith("blouse_4d_") || it.id == "blouse_bengaluru_4d_fo" })
        for (id in listOf("blouse_4d_basic_fo_wp", "blouse_4d_basic_bo_wp", "blouse_4d_halter_fo_wp", "blouse_4d_close_bo_wp")) for (m in sizes) {
            val p = model(id).draft(m)
            val plain = model(id.replace("_wp", "_wop")).draft(m).pieces.first { it.id == "front" }
            val front = p.pieces.first { it.id == "front" }
            val patti = p.pieces.first { it.id == "front_patti" }
            val back = p.pieces.first { it.id == "back" }
            // Side seams: upper front + patti = the front without a patti.
            assertEquals(plain.lengthOf(EdgeKind.SIDE), front.lengthOf(EdgeKind.SIDE) + patti.lengthOf(EdgeKind.SIDE), 0.15 * inch, id)
            // The patti seam is the front's seam with the darts closed.
            val seamDarts = front.darts.filter { d -> listOf(d.legA, d.legB).all { l -> front.edgesOf(EdgeKind.BELT).single().path.points().any { it.dist(l) < 0.05 } || kotlin.math.abs(l.y - front.edgesOf(EdgeKind.BELT).single().path.start.y) < 0.05 } }
            assertTrue(seamDarts.isNotEmpty(), id)
            assertEquals(front.lengthOf(EdgeKind.BELT) - seamDarts.sumOf { it.intake }, patti.lengthOf(EdgeKind.BELT), 0.05, id)
            assertEquals(BlouseDrafter.PATTI_HEIGHT, patti.edges.first().path.length(), 0.01, id)
            assertTrue(patti.darts.isEmpty())
            // Darts stay inside the upper front; the hem is on the patti only.
            for (d in front.darts) assertTrue(pointInPolygon(d.tip, front.seamOutline()), id)
            assertTrue(front.edgesOf(EdgeKind.HEM).isEmpty() && patti.edgesOf(EdgeKind.HEM).isNotEmpty(), id)
        }
        // Belted fronts have no patti; the patti survives a customised design id.
        assertTrue(!com.tailorsfit.pattern.blouse.BlouseSpec.BASIC.copy(body = com.tailorsfit.pattern.blouse.BodyStyle.KATORI, patti = true).toModel().hasPatti)
        val spec = com.tailorsfit.pattern.blouse.BlouseSpec.BASIC.copy(patti = true)
        assertTrue(com.tailorsfit.pattern.blouse.BlouseSpec.parse(spec.id)!!.patti)
        // Sketch: the patti is drawn as wide as the front above it.
        val sketch = com.tailorsfit.pattern.render.Illustration.asWorn(model("blouse_4d_basic_fo_wp").draft(Measurements.defaults()).pieces)
        val f = sketch.first { it.id == "front" }
        val pt = sketch.first { it.id == "front_patti" }
        assertEquals(f.edgesOf(EdgeKind.SIDE).single().path.start.x, pt.edgesOf(EdgeKind.BELT).single().path.start.x, 1e-6)
    }

    @Test
    fun princessCollection() {
        val inch = BlouseDrafter.INCH
        fun model(id: String) = BlouseCatalog.models.first { it.id == id }
        assertEquals(54, BlouseCatalog.models.count { it.id.startsWith("blouse_pc_") })
        assertTrue(BlouseCatalog.models.filter { it.id.startsWith("blouse_pc_") }.all { it.body == com.tailorsfit.pattern.blouse.BodyStyle.PRINCESS })
        for (m in sizes) {
            // Princess patti: both panels cut, the band as long as both cut edges.
            val p = model("blouse_pc_basic_fo_wp").draft(m)
            val centre = p.pieces.first { it.id == "front_centre" }
            val side = p.pieces.first { it.id == "front_side" }
            val patti = p.pieces.first { it.id == "front_patti" }
            val plainSide = model("blouse_pc_basic_fo_wop").draft(m).pieces.first { it.id == "front_side" }
            assertEquals(centre.lengthOf(EdgeKind.BELT) + side.lengthOf(EdgeKind.BELT), patti.lengthOf(EdgeKind.BELT), 0.05)
            assertEquals(plainSide.lengthOf(EdgeKind.SIDE), side.lengthOf(EdgeKind.SIDE) + patti.lengthOf(EdgeKind.SIDE), 0.05)
            assertEquals(centre.lengthOf(EdgeKind.PRINCESS), side.lengthOf(EdgeKind.PRINCESS), 0.5)

            // Shoulder cut: the princess seam starts halfway along the shoulder.
            val sc = model("blouse_pc_close_shoulder_bo").draft(m)
            val scC = sc.pieces.first { it.id == "front_centre" }
            val scS = sc.pieces.first { it.id == "front_side" }
            assertTrue(scC.edgesOf(EdgeKind.ARMHOLE).isEmpty())
            assertEquals(scC.lengthOf(EdgeKind.SHOULDER), scS.lengthOf(EdgeKind.SHOULDER), 0.01)
            assertEquals(scC.lengthOf(EdgeKind.PRINCESS), scS.lengthOf(EdgeKind.PRINCESS), 0.5)

            // Bottom curve: the centre is 1" longer, the side seam unchanged.
            val curved = model("blouse_pc_bottom_curve_fo").draft(m).pieces.first { it.id == "front_centre" }
            val straight = model("blouse_pc_basic_fo_wop").draft(m).pieces.first { it.id == "front_centre" }
            assertEquals(straight.edges.first().path.length() + inch, curved.edges.first().path.length(), 0.01)

            // Net back yoke and front insert: the cut edges match and the pieces fit back together.
            val net = model("blouse_pc_boat_net1").draft(m)
            val yoke = net.pieces.first { it.id == "back_yoke" }
            val back = net.pieces.first { it.id == "back" }
            assertEquals(yoke.lengthOf(EdgeKind.BELT), back.lengthOf(EdgeKind.BELT), 0.01)
            val whole = model("blouse_pc_boat_model1").draft(m).pieces.first { it.id == "back" }
            assertEquals(whole.area(), yoke.area() + back.area(), 1.0)
            assertTrue(yoke.edgesOf(EdgeKind.NECK).isNotEmpty() && back.edgesOf(EdgeKind.HEM).isNotEmpty())
            for (d in back.darts) assertTrue(pointInPolygon(d.tip, back.seamOutline()))
            val ins = model("blouse_pc_boat_insert1").draft(m)
            val insert = ins.pieces.first { it.id == "front_insert" }
            val rest = ins.pieces.first { it.id == "front_centre" }
            assertEquals(insert.lengthOf(EdgeKind.BELT), rest.lengthOf(EdgeKind.BELT), 0.01)
            assertTrue(insert.edgesOf(EdgeKind.NECK).isNotEmpty() && rest.edgesOf(EdgeKind.NECK).isEmpty())
            assertTrue(ins.warnings.none { it.contains("insert") })
        }
        // The new options survive a customised design id.
        val spec = com.tailorsfit.pattern.blouse.BlouseSpec.BASIC.copy(
            body = com.tailorsfit.pattern.blouse.BodyStyle.PRINCESS, backYoke = com.tailorsfit.pattern.blouse.YokeShape.V,
            frontInsert = com.tailorsfit.pattern.blouse.YokeShape.SCALLOP, bottomCurve = true, shoulderPrincess = true,
        )
        assertEquals(spec, com.tailorsfit.pattern.blouse.BlouseSpec.parse(spec.id))
        val sketch = com.tailorsfit.pattern.render.Illustration.blouse(model("blouse_pc_boat_net1").draft(Measurements.defaults()))
        assertEquals(1, sketch[1].sheer.size)
    }

    @Test
    fun newOptionsDraftOnEveryBlouseType() {
        val m = Measurements.defaults()
        val base = com.tailorsfit.pattern.blouse.BlouseSpec.BASIC
        var n = 0
        for (body in com.tailorsfit.pattern.blouse.BodyStyle.entries) for (front in com.tailorsfit.pattern.blouse.FrontNeck.entries)
            for (y in com.tailorsfit.pattern.blouse.YokeShape.entries) {
                val k = n++
                val spec = base.copy(
                    body = body, backYoke = y, frontInsert = com.tailorsfit.pattern.blouse.YokeShape.entries[k % 6],
                    bottomCurve = k % 2 == 0, patti = k % 3 == 0, shoulderPrincess = k % 4 == 0, halter = k % 5 == 0,
                ).withFront(front, com.tailorsfit.pattern.blouse.NeckDepth.entries[k % 3])
                val p = spec.toModel().draft(m)
                for (piece in p.pieces) {
                    assertTrue(piece.area() > 10, "${spec.id}/${piece.id}")
                    assertTrue(piece.seamOutline().none { it.x.isNaN() || it.y.isNaN() }, spec.id)
                    for (d in piece.darts) assertTrue(pointInPolygon(d.tip, piece.seamOutline()), "${spec.id}/${piece.id} dart tip outside")
                }
                com.tailorsfit.pattern.render.Illustration.blouse(p)
            }
    }
}
