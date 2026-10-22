package com.tailorsfit.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.render.DrawSink
import com.tailorsfit.pattern.render.GarmentView
import com.tailorsfit.pattern.render.Ink
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import kotlin.math.min

/** Colours and line widths (in pixels) of each kind of pattern line. */
class LineStyle(val colors: Map<Ink, Color>, val widths: Map<Ink, Float>) {
    companion object {
        val DASHES_CM = mapOf(
            Ink.SEAM to floatArrayOf(0.4f, 0.25f),
            Ink.FOLD to floatArrayOf(1.0f, 0.3f, 0.2f, 0.3f),
            Ink.HELPER to floatArrayOf(0.3f, 0.3f),
        )

        /** Dark lines on a light screen. */
        val screen = LineStyle(
            colors = mapOf(
                Ink.CUT to Color(0xFF2A2027), Ink.SEAM to Color(0xFF75676F), Ink.FOLD to Color(0xFF1A5FB4),
                Ink.DART to Color(0xFFC01C28), Ink.GRAIN to Color(0xFF2A2027), Ink.NOTCH to Color(0xFF2A2027),
                Ink.GUIDE to Color(0xFFC01C28), Ink.TEXT to Color(0xFF3E1A36), Ink.HELPER to Color(0xFFB0A49A),
            ),
            widths = mapOf(Ink.CUT to 2f, Ink.SEAM to 1f, Ink.FOLD to 2f, Ink.DART to 1.4f, Ink.GRAIN to 1f, Ink.NOTCH to 1.5f, Ink.GUIDE to 1f, Ink.HELPER to 1f),
        )

        /** Bright lines on black, so only the lines light up the cloth. */
        fun projector(width: Float) = LineStyle(
            colors = mapOf(
                Ink.CUT to Color.White, Ink.SEAM to Color(0xFFA0A0A0), Ink.FOLD to Color(0xFF78BEFF),
                Ink.DART to Color(0xFFFF7878), Ink.GRAIN to Color(0xFFFFE678), Ink.NOTCH to Color.White,
                Ink.GUIDE to Color(0xFFFF7878), Ink.TEXT to Color(0xFFFFE678), Ink.HELPER to Color(0xFF5A5A5A),
            ),
            widths = mapOf(
                Ink.CUT to width, Ink.SEAM to width * 0.5f, Ink.FOLD to width, Ink.DART to width * 0.7f,
                Ink.GRAIN to width * 0.6f, Ink.NOTCH to width, Ink.GUIDE to width * 0.6f, Ink.HELPER to 1f,
            ),
        )
    }
}

/** Draws pattern lines given in cm onto a Compose canvas: screen = (cm - origin) * pxPerCm + offset. */
class ComposeSink(
    private val scope: DrawScope,
    private val measurer: TextMeasurer,
    private val pxPerCm: Float,
    private val origin: Pt,
    private val offset: Offset,
    private val style: LineStyle,
) : DrawSink {
    private fun p(pt: Pt) = Offset(((pt.x - origin.x) * pxPerCm).toFloat() + offset.x, ((pt.y - origin.y) * pxPerCm).toFloat() + offset.y)

    override fun polyline(points: List<Pt>, closed: Boolean, ink: Ink) {
        if (points.size < 2) return
        val path = Path().apply {
            val a = p(points[0])
            moveTo(a.x, a.y)
            for (i in 1 until points.size) { val b = p(points[i]); lineTo(b.x, b.y) }
            if (closed) close()
        }
        val dash = LineStyle.DASHES_CM[ink]?.map { it * pxPerCm }?.takeIf { d -> d.all { it >= 1.5f } }
        scope.drawPath(
            path, style.colors[ink] ?: Color.Black,
            style = Stroke(
                width = style.widths[ink] ?: 1f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = dash?.let { PathEffect.dashPathEffect(it.toFloatArray()) },
            ),
        )
    }

    override fun text(text: String, at: Pt, sizeCm: Double, ink: Ink, rotationDeg: Double, bold: Boolean) {
        val sizePx = (sizeCm * pxPerCm).toFloat()
        if (sizePx < 6f) return
        val textStyle = with(scope) {
            TextStyle(color = style.colors[ink] ?: Color.Black, fontSize = sizePx.toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        }
        val layout = measurer.measure(AnnotatedString(text), textStyle)
        val c = p(at)
        val topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f)
        if (rotationDeg != 0.0) scope.rotate(rotationDeg.toFloat(), c) { drawText(layout, topLeft = topLeft) }
        else scope.drawText(layout, topLeft = topLeft)
    }
}

/** Draws pattern lines given in cm with Java2D (printing): 1 cm = [unitsPerCm] device units. */
class Graphics2DSink(private val g: Graphics2D, private val unitsPerCm: Double, private val origin: Pt) : DrawSink {
    init {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    private fun x(v: Double) = (v - origin.x) * unitsPerCm
    private fun y(v: Double) = (v - origin.y) * unitsPerCm

    private val widthsPt = mapOf(Ink.CUT to 1.4f, Ink.SEAM to 0.7f, Ink.FOLD to 1.4f, Ink.DART to 0.9f, Ink.GRAIN to 0.7f, Ink.NOTCH to 1.0f, Ink.GUIDE to 0.7f, Ink.HELPER to 0.5f)
    private val colors = mapOf(
        Ink.CUT to java.awt.Color.BLACK, Ink.SEAM to java.awt.Color.DARK_GRAY, Ink.FOLD to java.awt.Color(26, 95, 180),
        Ink.DART to java.awt.Color(192, 28, 40), Ink.GRAIN to java.awt.Color.BLACK, Ink.NOTCH to java.awt.Color.BLACK,
        Ink.GUIDE to java.awt.Color(192, 28, 40), Ink.TEXT to java.awt.Color.BLACK, Ink.HELPER to java.awt.Color.GRAY,
    )

    override fun polyline(points: List<Pt>, closed: Boolean, ink: Ink) {
        if (points.size < 2) return
        val path = Path2D.Double()
        path.moveTo(x(points[0].x), y(points[0].y))
        for (i in 1 until points.size) path.lineTo(x(points[i].x), y(points[i].y))
        if (closed) path.closePath()
        val dash = LineStyle.DASHES_CM[ink]?.map { (it * unitsPerCm).toFloat() }?.toFloatArray()
        g.stroke = if (dash != null) BasicStroke(widthsPt[ink] ?: 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f)
        else BasicStroke(widthsPt[ink] ?: 0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = colors[ink] ?: java.awt.Color.BLACK
        g.draw(path)
    }

    override fun text(text: String, at: Pt, sizeCm: Double, ink: Ink, rotationDeg: Double, bold: Boolean) {
        val size = (sizeCm * unitsPerCm * 1.35).toFloat()
        if (size < 3f) return
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, if (bold) java.awt.Font.BOLD else java.awt.Font.PLAIN, 1).deriveFont(size)
        g.color = colors[ink] ?: java.awt.Color.BLACK
        val fm = g.fontMetrics
        val w = fm.stringWidth(text)
        val old = g.transform
        g.translate(x(at.x), y(at.y))
        if (rotationDeg != 0.0) g.rotate(Math.toRadians(rotationDeg))
        g.drawString(text, -w / 2f, (fm.ascent - fm.descent) / 2f)
        g.transform = old
    }
}

/** A front or back view of the finished garment in a cloth colour, fitted into the canvas. */
fun DrawScope.drawGarment(view: GarmentView, cloth: Color, area: Rect = Rect(Offset.Zero, size)) {
    val pts = (view.panels + view.trims + view.ties).flatten()
    if (pts.isEmpty()) return
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val k = (min(area.width / (maxX - minX), area.height / (maxY - minY)) * 0.92).toFloat()
    val left = area.left + (area.width - ((maxX - minX) * k).toFloat()) / 2
    val top = area.top + (area.height - ((maxY - minY) * k).toFloat()) / 2
    fun path(points: List<Pt>, close: Boolean) = Path().apply {
        points.forEachIndexed { i, p ->
            val x = left + ((p.x - minX) * k).toFloat()
            val y = top + ((p.y - minY) * k).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        if (close) close()
    }
    val dark = Color(cloth.red * 0.55f, cloth.green * 0.55f, cloth.blue * 0.55f)
    for (panel in view.panels) {
        val p = path(panel, true)
        drawPath(p, cloth)
        clipPath(p) {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.15f))))
        }
        drawPath(p, dark, style = Stroke(1.2f))
    }
    for (s in view.sheer) {
        val p = path(s, true)
        drawPath(p, Color(0xFFF7F1E8).copy(alpha = 0.75f))
        drawPath(p, dark, style = Stroke(1.2f))
    }
    for (h in view.holes) {
        val p = path(h, true)
        drawPath(p, Color(0xFFF2EBDF))
        drawPath(p, dark, style = Stroke(1.2f))
    }
    for (s in view.seams) drawPath(path(s, false), dark.copy(alpha = 0.7f), style = Stroke(1f, cap = StrokeCap.Round))
    for (t in view.ties) drawPath(path(t, false), dark, style = Stroke(2f, cap = StrokeCap.Round))
    for (t in view.trims) drawPath(path(t, false), Brand.Gold, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
