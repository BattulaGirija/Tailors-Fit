package com.tailorsfit.app.export

import com.tailorsfit.pattern.i18n.tr
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tailorsfit.app.render.CanvasSink
import com.tailorsfit.app.render.SinkStyle
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.layout.TilePlan
import com.tailorsfit.pattern.layout.TilePlanner
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.render.Ink
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter
import java.io.File
import kotlin.math.ceil
import kotlin.math.min

enum class PaperSize(private val key: String?, val widthCm: Double, val heightCm: Double) {
    A4(null, 21.0, 29.7),
    LETTER("paper.letter", 21.59, 27.94),
    A3(null, 29.7, 42.0),
    /** One page as big as the whole layout, for plotters / print shops. */
    FULL("paper.full", 0.0, 0.0),
    ;

    val label: String get() = key?.let { tr(it) } ?: name
}

/**
 * Writes patterns as true-scale PDFs. PDF units are points: 1 cm = 72 / 2.54 pt, so printing
 * at 100% ("actual size") gives the exact centimetre sizes.
 */
object PdfExporter {
    const val PT_PER_CM = 72.0 / 2.54
    private const val MARGIN_CM = 0.7
    private const val OVERLAP_CM = 1.0
    private const val TEST_SQUARE_CM = 10.0

    fun export(pattern: Pattern, layout: Layout, paper: PaperSize, options: PaintOptions, out: File): File {
        val doc = PdfDocument()
        try {
            if (paper == PaperSize.FULL) writeSingleSheet(doc, pattern, layout, options) else writeTiles(doc, pattern, layout, paper, options)
            out.parentFile?.mkdirs()
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return out
    }

    private fun pt(cm: Double) = (cm * PT_PER_CM).toFloat()

    private fun writeTiles(doc: PdfDocument, pattern: Pattern, layout: Layout, paper: PaperSize, options: PaintOptions) {
        val printableW = paper.widthCm - 2 * MARGIN_CM
        val printableH = paper.heightCm - 2 * MARGIN_CM
        val plan = TilePlanner.plan(layout.bounds, printableW, printableH, OVERLAP_CM)
        val pageW = ceil(pt(paper.widthCm).toDouble()).toInt()
        val pageH = ceil(pt(paper.heightCm).toDouble()).toInt()
        val total = plan.tiles.size + 1

        // Cover page: instructions, scale check and a map of how to join the pages.
        doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()).also { page ->
            drawCover(page.canvas, pattern, layout, plan, paper, pageW, pageH)
            doc.finishPage(page)
        }

        val style = SinkStyle.paper()
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = Color.DKGRAY }
        plan.tiles.forEachIndexed { i, tile ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 2).create())
            val c = page.canvas
            val m = pt(MARGIN_CM)
            c.save()
            c.clipRect(m, m, m + pt(printableW), m + pt(printableH))
            val sink = CanvasSink(
                c, PT_PER_CM.toFloat(), PT_PER_CM.toFloat(),
                originX = tile.origin.x - MARGIN_CM, originY = tile.origin.y - MARGIN_CM, style = style,
            )
            PatternPainter.paintFabric(layout, sink)
            PatternPainter.paintLayout(layout, sink, options)
            PatternPainter.paintTileHelpers(plan, tile, sink)
            c.restore()
            c.drawText(
                tr("pdf.footer", tile.label, i + 2, total, ('A' + tile.row).toString(), tile.col + 1, pattern.title),
                m, pageH - m / 2.5f, footer,
            )
            doc.finishPage(page)
        }
    }

    private fun drawCover(c: Canvas, pattern: Pattern, layout: Layout, plan: TilePlan, paper: PaperSize, pageW: Int, pageH: Int) {
        val m = pt(MARGIN_CM + 0.5)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        var y = m + 18f
        c.drawText(pattern.title, m, y, title)
        y += 20f
        val lines = mutableListOf(
            tr("pdf.pages", plan.tiles.size, paper.label, plan.rows, plan.cols, OVERLAP_CM.toInt()),
            tr("pdf.step1"),
            tr("pdf.step2", TEST_SQUARE_CM.toInt()),
            tr("pdf.step3"),
            tr("pdf.step4"),
            tr(
                if (layout.folded) "pdf.cloth_folded" else "pdf.cloth_single",
                "%.2f".format(layout.length / 100),
                com.tailorsfit.pattern.model.Lengths.format(if (layout.folded) layout.width * 2 else layout.width),
            ),
        )
        for (l in lines) {
            c.drawText(l, m, y, body)
            y += 15f
        }
        y += 4f
        for ((k, v) in pattern.summary) {
            c.drawText("$k: ", m, y, bold)
            c.drawText(v, m + bold.measureText("$k: "), y, body)
            y += 13f
        }
        for (w in pattern.warnings) {
            c.drawText("⚠ $w", m, y, body)
            y += 13f
        }

        // True-size test square.
        y += 10f
        val sink = CanvasSink(c, PT_PER_CM.toFloat(), PT_PER_CM.toFloat(), 0.0, 0.0, SinkStyle.paper(), offsetPxX = m, offsetPxY = y)
        PatternPainter.paintCalibrationSquare(sink, Pt(0.0, 0.0), TEST_SQUARE_CM)

        // Page map next to or below the square.
        val mapLeft = m + pt(TEST_SQUARE_CM) + 24f
        val mapTop = y
        val availW = pageW - mapLeft - m
        val availH = pageH - mapTop - m
        val contentW = plan.cols * (plan.pageWidth - plan.overlap) + plan.overlap
        val contentH = plan.rows * (plan.pageHeight - plan.overlap) + plan.overlap
        val scale = min(availW / contentW, availH / contentH).toFloat()
        val mapSink = CanvasSink(c, scale, scale, 0.0, 0.0, SinkStyle.paper(0.5f), offsetPxX = mapLeft, offsetPxY = mapTop)
        PatternPainter.paintLayout(layout, mapSink, PaintOptions(showLabels = false, showMarkings = false, showSeamLine = false))
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(26, 95, 180); strokeWidth = 0.8f }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 95, 180); textSize = 11f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        for (t in plan.tiles) {
            val r = RectF(
                mapLeft + (t.origin.x * scale).toFloat(), mapTop + (t.origin.y * scale).toFloat(),
                mapLeft + ((t.origin.x + t.width) * scale).toFloat(), mapTop + ((t.origin.y + t.height) * scale).toFloat(),
            )
            c.drawRect(r, box)
            c.drawText(t.label, r.centerX(), r.centerY(), label)
        }
    }

    private fun writeSingleSheet(doc: PdfDocument, pattern: Pattern, layout: Layout, options: PaintOptions) {
        val margin = 2.0
        val extra = TEST_SQUARE_CM + 3.0
        val wCm = layout.width + 2 * margin + 2.0
        val hCm = layout.length + 2 * margin + extra
        val page = doc.startPage(PdfDocument.PageInfo.Builder(ceil(pt(wCm).toDouble()).toInt(), ceil(pt(hCm).toDouble()).toInt(), 1).create())
        val c = page.canvas
        val sink = CanvasSink(c, PT_PER_CM.toFloat(), PT_PER_CM.toFloat(), -margin - 1.0, -margin, SinkStyle.paper())
        PatternPainter.paintFabric(layout, sink)
        PatternPainter.paintLayout(layout, sink, options)
        PatternPainter.paintCalibrationSquare(sink, Pt(0.0, layout.length + 2.0), TEST_SQUARE_CM)
        sink.text(pattern.title, Pt(layout.width / 2, -margin / 2), 1.0, Ink.TEXT, bold = true)
        doc.finishPage(page)
    }
}
