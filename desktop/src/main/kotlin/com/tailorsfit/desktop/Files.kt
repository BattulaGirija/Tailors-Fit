package com.tailorsfit.desktop

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.i18n.tr
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.layout.TilePlanner
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter
import com.tailorsfit.pattern.render.SvgExporter
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.io.File
import java.util.Properties

/** Customers and settings, kept in the user's folder (Documents\TailorsFit on Windows). */
object Store {
    val folder: File = File(System.getProperty("user.home"), "Documents/TailorsFit").apply { mkdirs() }
    private val customers = File(folder, "customers").apply { mkdirs() }
    private val settingsFile = File(folder, "settings.properties")

    data class Customer(val name: String, val phone: String, val measurements: Map<MeasurementField, Double>)

    private fun fileFor(name: String) = File(customers, name.replace(Regex("[^\\p{L}\\p{N} ._-]+"), "_").trim().ifEmpty { "customer" } + ".properties")

    fun save(c: Customer) {
        val p = Properties()
        p["name"] = c.name
        p["phone"] = c.phone
        for ((f, v) in c.measurements) if (!v.isNaN()) p["m." + f.key] = v.toString()
        fileFor(c.name).outputStream().use { p.store(it, "Tailors Fit customer") }
    }

    fun list(): List<Customer> = customers.listFiles { f -> f.extension == "properties" }.orEmpty().mapNotNull { f ->
        runCatching {
            val p = Properties().apply { f.inputStream().use { load(it) } }
            val m = p.stringPropertyNames().filter { it.startsWith("m.") }.mapNotNull { k ->
                MeasurementField.byKey(k.removePrefix("m."))?.let { it to p.getProperty(k).toDouble() }
            }.toMap()
            Customer(p.getProperty("name") ?: f.nameWithoutExtension, p.getProperty("phone") ?: "", m)
        }.getOrNull()
    }.sortedBy { it.name.lowercase() }

    fun delete(name: String) { fileFor(name).delete() }

    fun setting(key: String): String? = runCatching { Properties().apply { settingsFile.inputStream().use { load(it) } }.getProperty(key) }.getOrNull()

    fun setSetting(key: String, value: String) {
        val p = Properties()
        runCatching { settingsFile.inputStream().use { p.load(it) } }
        p[key] = value
        settingsFile.outputStream().use { p.store(it, "Tailors Fit settings") }
    }
}

/** Saving and printing patterns at true size. */
object Output {
    private const val PT_PER_CM = 72.0 / 2.54
    private const val OVERLAP_CM = 1.0
    private const val TEST_SQUARE_CM = 10.0

    fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "pattern" }

    /** Asks where to save, then writes the layout as SVG (1 unit = 1 cm). */
    fun saveSvg(pattern: Pattern, layout: Layout, options: PaintOptions): File? {
        val d = FileDialog(null as Frame?, tr("pattern.svg"), FileDialog.SAVE)
        d.file = safeName(pattern.title) + ".svg"
        d.isVisible = true
        val name = d.file ?: return null
        val f = File(d.directory, if (name.endsWith(".svg")) name else "$name.svg")
        f.writeText(SvgExporter.export(layout, options, pattern.title))
        return f
    }

    /**
     * Prints the layout at true size, tiled over pages: a first page with a 10 cm test square,
     * then the pieces with join marks. Choose "Microsoft Print to PDF" to get a PDF.
     */
    fun print(pattern: Pattern, layout: Layout, options: PaintOptions): Boolean {
        val job = PrinterJob.getPrinterJob()
        job.jobName = pattern.title
        var plan: com.tailorsfit.pattern.layout.TilePlan? = null
        job.setPrintable(object : Printable {
            override fun print(graphics: Graphics, pf: PageFormat, pageIndex: Int): Int {
                val wCm = pf.imageableWidth / PT_PER_CM
                val hCm = pf.imageableHeight / PT_PER_CM
                val tiles = plan ?: TilePlanner.plan(layout.bounds, wCm, hCm, OVERLAP_CM).also { plan = it }
                if (pageIndex > tiles.tiles.size) return Printable.NO_SUCH_PAGE
                val g = graphics.create() as Graphics2D
                g.translate(pf.imageableX, pf.imageableY)
                if (pageIndex == 0) {
                    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 14)
                    g.color = java.awt.Color.BLACK
                    g.drawString(pattern.title, 0, 16)
                    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 9)
                    val lines = listOf(
                        tr("pdf.pages", tiles.tiles.size, "${"%.1f".format(wCm)} × ${"%.1f".format(hCm)} cm", tiles.rows, tiles.cols, OVERLAP_CM.toInt()),
                        tr("pdf.step1"), tr("pdf.step2", TEST_SQUARE_CM.toInt()), tr("pdf.step3"), tr("pdf.step4"),
                    )
                    lines.forEachIndexed { i, s -> g.drawString(s, 0, 36 + i * 13) }
                    val sink = Graphics2DSink(g, PT_PER_CM, Pt(-1.0, -(36 + lines.size * 13 + 10) / PT_PER_CM))
                    PatternPainter.paintCalibrationSquare(sink, Pt(0.0, 0.0), TEST_SQUARE_CM)
                } else {
                    val tile = tiles.tiles[pageIndex - 1]
                    g.clipRect(0, 0, pf.imageableWidth.toInt(), pf.imageableHeight.toInt() - 10)
                    val sink = Graphics2DSink(g, PT_PER_CM, tile.origin)
                    PatternPainter.paintFabric(layout, sink)
                    PatternPainter.paintLayout(layout, sink, options)
                    PatternPainter.paintTileHelpers(tiles, tile, sink)
                    g.clip = null
                    g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 7)
                    g.color = java.awt.Color.DARK_GRAY
                    g.drawString(
                        tr("pdf.footer", tile.label, pageIndex + 1, tiles.tiles.size + 1, ('A' + tile.row).toString(), tile.col + 1, pattern.title),
                        0, pf.imageableHeight.toInt() - 2,
                    )
                }
                g.dispose()
                return Printable.PAGE_EXISTS
            }
        })
        if (!job.printDialog()) return false
        job.print()
        return true
    }
}
