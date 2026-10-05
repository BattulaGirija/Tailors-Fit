package com.tailorsfit.pattern

import com.tailorsfit.pattern.blouse.BlouseCatalog
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.render.Illustration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IllustrationTest {
    @Test
    fun everyDesignHasAFrontAndBackSketch() {
        for (model in BlouseCatalog.models) {
            val views = Illustration.blouse(model.draft(Measurements.defaults()))
            assertEquals(listOf("Front", "Back"), views.map { it.title }, model.id)
            for (v in views) {
                val sleeveParts = if (model.sleeve == SleeveStyle.PUFF || model.sleeve == SleeveStyle.FRILL) 2 else 1
                val sleeves = if (model.sleeve == SleeveStyle.SLEEVELESS) 0 else 2 * sleeveParts
                val bodice = if (v.title != "Front") 1 else when { model.body.belted -> 4; model.body.panelled -> 3; model.hasPatti -> 2; else -> 1 }
                val collar = if (model.collar) 1 else 0
                assertEquals(sleeves + bodice + collar, v.panels.size, "${model.id} ${v.title}")
                if (v.title == "Back") {
                    assertEquals(if (model.backDetail == com.tailorsfit.pattern.blouse.BackDetail.KEYHOLE) 1 else 0, v.holes.size, model.id)
                    assertEquals(if (model.backDetail == com.tailorsfit.pattern.blouse.BackDetail.DORI) 2 else 0, v.ties.size, model.id)
                }
                assertTrue(v.trims.isNotEmpty())
                assertTrue(v.panels.flatten().none { it.x.isNaN() || it.y.isNaN() })
                // Symmetric garment: as far left as right.
                val xs = v.panels.flatten().map { it.x }
                assertEquals(-xs.min(), xs.max(), 0.01, "${model.id} ${v.title}")
            }
        }
    }
}
