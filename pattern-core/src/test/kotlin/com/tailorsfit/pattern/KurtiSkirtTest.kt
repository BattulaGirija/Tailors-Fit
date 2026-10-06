package com.tailorsfit.pattern

import com.tailorsfit.pattern.geom.pointInPolygon
import com.tailorsfit.pattern.kurti.KurtiCatalog
import com.tailorsfit.pattern.kurti.KurtiCut
import com.tailorsfit.pattern.layout.LayoutEngine
import com.tailorsfit.pattern.layout.LayoutOptions
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.EdgeKind
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.SizePreset
import com.tailorsfit.pattern.render.Illustration
import com.tailorsfit.pattern.skirt.SkirtCatalog
import com.tailorsfit.pattern.skirt.SkirtCut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KurtiSkirtTest {
    private val sizes = SizePreset.entries.map { it.measurements() }

    @Test
    fun categoriesAreOpen() {
        assertTrue(Catalog.category("kurti")!!.available && Catalog.category("lehenga")!!.available)
        assertEquals(14, Catalog.modelsIn("kurti").size)
        assertEquals(18, Catalog.modelsIn("lehenga").size)
        assertEquals((Catalog.models.map { it.id }).size, Catalog.models.map { it.id }.toSet().size, "ids are unique")
    }

    @Test
    fun everyKurtiDraftsForEverySize() {
        for (model in KurtiCatalog.models) for (m in sizes) {
            val p = model.draft(m)
            val front = p.pieces.first { it.id == "front" }
            val back = p.pieces.first { it.id == "back" }
            for (piece in p.pieces) {
                assertTrue(piece.seamOutline().none { it.x.isNaN() || it.y.isNaN() }, "${model.id}/${piece.id}")
                assertTrue(piece.area() > 20, "${model.id}/${piece.id} ${piece.area()}")
                for (d in piece.darts) assertTrue(pointInPolygon(d.tip, piece.seamOutline()), "${model.id}/${piece.id} dart")
            }
            // Shoulders match; side seams match once the side dart is closed.
            assertEquals(front.lengthOf(EdgeKind.SHOULDER), back.lengthOf(EdgeKind.SHOULDER), 0.01, model.id)
            val sideDart = front.darts.maxBy { it.legA.x }
            assertEquals(back.lengthOf(EdgeKind.SIDE), front.lengthOf(EdgeKind.SIDE) - sideDart.intake, 0.6, model.id)
            // Front + back armholes = armhole round + 1".
            val arm = front.lengthOf(EdgeKind.ARMHOLE) + back.lengthOf(EdgeKind.ARMHOLE)
            assertEquals(m[MeasurementField.ARMHOLE] + 2.54, arm, 0.5, model.id)
            if (model.cut == KurtiCut.ANARKALI) {
                val kali = p.pieces.first { it.id == "kali" }
                assertEquals(model.kalis, kali.cut.count)
                // The kali tops go round the waist with 4" ease.
                val top = kali.lengthOf(EdgeKind.BELT) * model.kalis
                assertEquals(m[MeasurementField.NATURAL_WAIST] + 4 * 2.54, top, 0.5, model.id)
            }
            assertEquals(2, Illustration.of(p).size)
        }
    }

    @Test
    fun everySkirtDraftsForEverySize() {
        for (model in SkirtCatalog.models) for (m in sizes) {
            val p = model.draft(m)
            assertTrue(p.pieces.any { it.id == "waistband" })
            for (piece in p.pieces) {
                assertTrue(piece.seamOutline().none { it.x.isNaN() || it.y.isNaN() }, "${model.id}/${piece.id}")
                assertTrue(piece.area() > 20, "${model.id}/${piece.id}")
                for (d in piece.darts) assertTrue(pointInPolygon(d.tip, piece.seamOutline()), "${model.id}/${piece.id} dart")
            }
            val waist = m[MeasurementField.NATURAL_WAIST] + 2.54
            when (model.cut) {
                SkirtCut.KALIDAR, SkirtCut.MERMAID -> {
                    val kali = p.pieces.first { it.id == "kali" }
                    assertEquals(waist, kali.lengthOf(EdgeKind.BELT) * kali.cut.count, 0.3, model.id)
                }
                SkirtCut.CIRCLE, SkirtCut.HALF_CIRCLE -> {
                    val s = p.pieces.first { it.id == "sector" }
                    assertEquals(waist, s.lengthOf(EdgeKind.BELT) * s.cut.count, 0.3, model.id)
                }
                SkirtCut.A_LINE, SkirtCut.PENCIL -> {
                    // Waist edges minus the darts = the waist.
                    val halves = p.pieces.filter { it.id == "front" || it.id == "back" }
                    val net = halves.sumOf { 2 * (it.lengthOf(EdgeKind.BELT) - it.darts.sumOf { d -> d.intake }) }
                    assertEquals(waist, net, 1.0, model.id)
                }
                else -> assertTrue(p.pieces.filter { it.id.startsWith("panel") || it.id.startsWith("tier") }.all { it.cut.count >= 2 })
            }
            val views = Illustration.of(p)
            assertEquals(2, views.size)
            val xs = views[0].panels.flatten().map { it.x }
            assertEquals(-xs.min(), xs.max(), 0.01, model.id)
        }
    }

    @Test
    fun manyKalisAreAllLaidOut() {
        val model = SkirtCatalog.models.first { it.id == "lehenga_kali12" }
        val p = model.draft(SizePreset.M.measurements())
        val folded = LayoutEngine.layout(p, LayoutOptions(fabricWidth = 112.0, folded = true, trials = 20))
        assertEquals(6, folded.placed.count { it.piece.id.startsWith("kali") })
        val open = LayoutEngine.layout(p, LayoutOptions(fabricWidth = 112.0, folded = false, trials = 20))
        assertEquals(12, open.placed.count { it.piece.id.startsWith("kali") })
    }
}
