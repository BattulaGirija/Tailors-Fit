package com.tailorsfit.pattern.render

import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.layout.Layout
import java.util.Locale

/** Collects drawing calls as SVG elements. Coordinates are centimetres. */
class SvgSink : DrawSink {
    private val body = StringBuilder()

    override fun polyline(points: List<Pt>, closed: Boolean, ink: Ink) {
        if (points.size < 2) return
        val tag = if (closed) "polygon" else "polyline"
        body.append('<').append(tag).append(" class=\"").append(ink.name.lowercase()).append("\" points=\"")
        points.joinTo(body, " ") { "${n(it.x)},${n(it.y)}" }
        body.append("\"/>\n")
    }

    override fun text(text: String, at: Pt, sizeCm: Double, ink: Ink, rotationDeg: Double, bold: Boolean) {
        body.append("<text class=\"").append(ink.name.lowercase()).append('"')
        body.append(" x=\"").append(n(at.x)).append("\" y=\"").append(n(at.y)).append('"')
        body.append(" font-size=\"").append(n(sizeCm)).append('"')
        if (bold) body.append(" font-weight=\"bold\"")
        if (rotationDeg != 0.0) body.append(" transform=\"rotate(${n(rotationDeg)} ${n(at.x)} ${n(at.y)})\"")
        body.append('>').append(escape(text)).append("</text>\n")
    }

    fun toSvg(minX: Double, minY: Double, width: Double, height: Double, title: String = ""): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${n(width)}cm\" height=\"${n(height)}cm\" ")
        append("viewBox=\"${n(minX)} ${n(minY)} ${n(width)} ${n(height)}\">\n")
        if (title.isNotEmpty()) append("<title>").append(escape(title)).append("</title>\n")
        append(
            """
            <style>
              polygon, polyline { fill: none; stroke: #000; stroke-linejoin: round; stroke-linecap: round; }
              .cut { stroke-width: 0.08; }
              .seam { stroke-width: 0.04; stroke-dasharray: 0.4 0.25; stroke: #444; }
              .fold { stroke-width: 0.1; stroke-dasharray: 1 0.3 0.2 0.3; stroke: #1a5fb4; }
              .dart { stroke-width: 0.05; stroke: #c01c28; }
              .grain, .notch { stroke-width: 0.05; }
              .guide { stroke-width: 0.04; stroke: #c01c28; }
              .helper { stroke-width: 0.03; stroke: #888; }
              text { font-family: sans-serif; text-anchor: middle; dominant-baseline: middle; fill: #000; }
              text.helper { fill: #888; }
            </style>
            """.trimIndent(),
        )
        append('\n')
        append(body)
        append("</svg>\n")
    }

    private fun n(v: Double) = String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

object SvgExporter {
    /** A true-scale SVG of the whole layout (1 unit = 1 cm), suitable for plotters and print shops. */
    fun export(layout: Layout, options: PaintOptions = PaintOptions(), title: String = "", margin: Double = 1.0): String {
        val sink = SvgSink()
        PatternPainter.paintLayout(layout, sink, options)
        return sink.toSvg(-margin, -margin, layout.width + 2 * margin, layout.length + 2 * margin, title)
    }
}
