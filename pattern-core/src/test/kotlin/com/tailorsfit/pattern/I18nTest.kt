package com.tailorsfit.pattern

import com.tailorsfit.pattern.blouse.BlouseCatalog
import com.tailorsfit.pattern.i18n.I18n
import com.tailorsfit.pattern.i18n.Language
import com.tailorsfit.pattern.i18n.StringsEn
import com.tailorsfit.pattern.layout.LayoutEngine
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.render.Illustration
import com.tailorsfit.pattern.render.SvgExporter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    private val placeholder = Regex("%(\\d+\\$)?[sd]")

    @AfterTest
    fun reset() {
        I18n.language = Language.EN
    }

    @Test
    fun everyLanguageHasEveryText() {
        for ((lang, table) in I18n.tables) {
            assertEquals(StringsEn.strings.keys - table.keys, emptySet(), "$lang is missing texts")
            assertEquals(table.keys - StringsEn.strings.keys, emptySet(), "$lang has unknown texts")
            for ((key, value) in table) assertTrue(value.isNotBlank(), "$lang $key is empty")
        }
    }

    @Test
    fun placeholdersMatchEnglish() {
        for ((lang, table) in I18n.tables) for ((key, en) in StringsEn.strings) {
            val expected = placeholder.findAll(en).map { it.value }.sorted().toList()
            val actual = placeholder.findAll(table.getValue(key)).map { it.value }.sorted().toList()
            assertEquals(expected, actual, "$lang $key placeholders")
        }
    }

    @Test
    fun everyTemplateFormats() {
        for (lang in Language.entries) {
            I18n.language = lang
            for ((key, en) in StringsEn.strings) {
                val specs = placeholder.findAll(en).map { it.value.last() }.toList()
                val args = specs.map { if (it == 'd') 3 else "x" }.toTypedArray()
                I18n.t(key, *args) // must not throw
            }
        }
    }

    @Test
    fun everyDesignDraftsInEveryLanguage() {
        for (lang in Language.entries) {
            I18n.language = lang
            for (model in BlouseCatalog.models) {
                val pattern = model.draft(Measurements.defaults())
                val texts = pattern.pieces.flatMap { listOf(it.name, it.cut.text) + it.notes } +
                    pattern.warnings + pattern.summary.map { it.first } + model.name + model.description + model.tags
                for (t in texts) assertTrue(!Regex("^[a-z_]+\\.[a-z_.]+$").matches(t), "$lang untranslated key: $t")
                val svg = SvgExporter.export(LayoutEngine.layout(pattern))
                assertTrue(svg.contains(I18n.t("paint.place_on_fold")))
                assertEquals(2, Illustration.blouse(pattern).size)
            }
            for (f in MeasurementField.entries) {
                assertTrue(f.label.isNotBlank() && f.help.isNotBlank())
                assertTrue(f.validate(f.minCm - 1.0)!!.contains(f.label))
            }
        }
    }

    @Test
    fun switchesLanguage() {
        I18n.language = Language.TE
        assertEquals("ముందు", I18n.t("piece.front"))
        I18n.language = Language.HI
        assertEquals("आगे", I18n.t("piece.front"))
        assertEquals("5 ग्राहक", I18n.plural("count.customer", 5))
        I18n.language = Language.EN
        assertEquals("1 customer", I18n.plural("count.customer", 1))
        assertEquals("Print with scale 100% / \"Actual size\" (turn off \"Fit to page\").", I18n.t("pdf.step1").removePrefix("1. "))
    }
}
