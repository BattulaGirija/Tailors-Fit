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
                val sleeves = if (model.sleeve == SleeveStyle.SLEEVELESS) 0 else 2
                val bodice = if (model.princess && v.title == "Front") 3 else 1
                assertEquals(sleeves + bodice, v.panels.size, "${model.id} ${v.title}")
                assertTrue(v.trims.isNotEmpty())
                assertTrue(v.panels.flatten().none { it.x.isNaN() || it.y.isNaN() })
                // Symmetric garment: as far left as right.
                val xs = v.panels.flatten().map { it.x }
                assertEquals(-xs.min(), xs.max(), 0.01, "${model.id} ${v.title}")
            }
        }
    }
}
