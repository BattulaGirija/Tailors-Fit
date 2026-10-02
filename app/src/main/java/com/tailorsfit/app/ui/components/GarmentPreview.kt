package com.tailorsfit.app.ui.components

import com.tailorsfit.pattern.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.render.GarmentView
import com.tailorsfit.pattern.render.Illustration
import kotlin.math.min

class ClothColour(private val key: String, val color: Color) {
    val name: String get() = tr("colour.$key")
}

val CLOTH_COLOURS = listOf(
    ClothColour("maroon", Color(0xFF7B1E2B)),
    ClothColour("ruby", Color(0xFFB0203A)),
    ClothColour("rani", Color(0xFFD1307A)),
    ClothColour("peach", Color(0xFFF2A07B)),
    ClothColour("mustard", Color(0xFFD9A21B)),
    ClothColour("parrot", Color(0xFF5DAA2E)),
    ClothColour("emerald", Color(0xFF1E7A55)),
    ClothColour("peacock", Color(0xFF0E6E7E)),
    ClothColour("royal", Color(0xFF25459A)),
    ClothColour("lavender", Color(0xFF9C86C9)),
    ClothColour("black", Color(0xFF1C1B1F)),
    ClothColour("ivory", Color(0xFFEFE6D2)),
)

enum class ClothFinish {
    PLAIN, SILK, BUTTIS, STRIPES;

    val label: String get() = tr("finish.${name.lowercase()}")
}

/**
 * Sketch of the finished blouse (front and back), drawn from the design's real pattern and the
 * customer's measurements, with a choice of cloth colour, finish and gold border.
 */
@Composable
fun GarmentPreviewCard(model: GarmentModel, measurements: Measurements, modifier: Modifier = Modifier) {
    var colourIndex by rememberSaveable { mutableStateOf(0) }
    var finish by rememberSaveable { mutableStateOf(ClothFinish.SILK) }
    var border by rememberSaveable { mutableStateOf(true) }
    val colour = CLOTH_COLOURS[colourIndex]

    // Use the customer's measurements when they are complete, otherwise a standard size.
    val views = remember(model, measurements) {
        val pattern = runCatching { model.draft(measurements) }.getOrNull()
            ?: runCatching { model.draft(Measurements.defaults()) }.getOrNull()
        pattern?.let { Illustration.blouse(it) } ?: emptyList()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Brand.Line),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr("preview.title"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text("${colour.name} · ${finish.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF7F1E8), Color(0xFFEDE3D3)))),
            ) {
                Canvas(Modifier.fillMaxWidth().height(210.dp).padding(12.dp)) {
                    drawViews(views, colour.color, finish, border)
                }
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 6.dp)) {
                    views.forEach { v ->
                        Text(v.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Brand.Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CLOTH_COLOURS.forEachIndexed { i, c ->
                    val selected = i == colourIndex
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c.color)
                            .border(if (selected) 3.dp else 1.dp, if (selected) Brand.Gold else Brand.Line, CircleShape)
                            .clickable { colourIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, contentDescription = c.name, tint = if (c.color.luminance() > 0.5f) Brand.Ink else Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClothFinish.entries.forEach { f -> FilterChip(selected = f == finish, onClick = { finish = f }, label = { Text(f.label) }) }
                FilterChip(selected = border, onClick = { border = !border }, label = { Text(tr("preview.border")) })
            }
        }
    }
}

/**
 * One view of the finished blouse (front or back) in a cloth colour, drawn from the design's
 * real pattern at a standard size. Used to show design choices.
 */
@Composable
fun BlouseSketch(model: GarmentModel, back: Boolean, modifier: Modifier = Modifier, cloth: Color = Color(0xFF7B1E2B)) {
    val view = remember(model.id, back) {
        runCatching { Illustration.blouse(model.draft(Measurements.defaults())) }.getOrNull()
            ?.let { views -> views.getOrNull(if (back) 1 else 0) }
    }
    Canvas(modifier) {
        if (view != null) drawViews(listOf(view), cloth, ClothFinish.SILK, border = true, labelRoom = false)
    }
}

private fun DrawScope.drawViews(views: List<GarmentView>, cloth: Color, finish: ClothFinish, border: Boolean, labelRoom: Boolean = true) {
    if (views.isEmpty()) return
    val gap = size.width * 0.08f
    val cellW = (size.width - gap * (views.size - 1)) / views.size
    val cellH = size.height - if (labelRoom) 16.dp.toPx() else 0f // room for the labels
    val boxes = views.map { v -> Rect.of((v.panels + v.trims + v.ties).flatten()) }
    // One scale for both views so front and back match.
    val scale = boxes.minOf { b -> min(cellW / b.width, cellH / b.height) }.toFloat() * 0.94f
    val dark = lerp(cloth, Color.Black, 0.45f)
    val highlight = lerp(cloth, Color.White, 0.35f)

    views.forEachIndexed { i, v ->
        val b = boxes[i]
        val left = i * (cellW + gap) + (cellW - b.width.toFloat() * scale) / 2
        val top = (cellH - b.height.toFloat() * scale) / 2
        fun path(points: List<Pt>, close: Boolean) = Path().apply {
            points.forEachIndexed { k, p ->
                val x = ((p.x - b.minX) * scale).toFloat()
                val y = ((p.y - b.minY) * scale).toFloat()
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            if (close) close()
        }
        translate(left, top) {
            val panels = v.panels.map { path(it, true) }
            // Soft shadow under the garment.
            translate(3f, 5f) { panels.forEach { drawPath(it, Color.Black.copy(alpha = 0.10f)) } }
            for (p in panels) {
                drawPath(p, cloth)
                clipPath(p) {
                    when (finish) {
                        ClothFinish.PLAIN -> Unit
                        ClothFinish.SILK -> drawRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.18f), Color.White.copy(alpha = 0.12f)),
                                start = Offset.Zero,
                                end = Offset(size.width * 0.5f, size.height),
                            ),
                        )
                        ClothFinish.BUTTIS -> {
                            val step = 2.6f * scale
                            var y = 0f
                            var row = 0
                            while (y < size.height) {
                                var x = if (row % 2 == 0) 0f else step / 2
                                while (x < size.width) {
                                    drawCircle(Brand.GoldLight, radius = 0.32f * scale, center = Offset(x, y))
                                    x += step
                                }
                                y += step * 0.8f
                                row++
                            }
                        }
                        ClothFinish.STRIPES -> {
                            val step = 1.6f * scale
                            var x = 0f
                            while (x < size.width) {
                                drawLine(highlight.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.45f * scale)
                                x += step
                            }
                        }
                    }
                }
                drawPath(p, dark, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
            }
            // Openings show the backdrop through the cloth.
            for (h in v.holes) {
                val hp = path(h, true)
                drawPath(hp, Color(0xFFF2EBDF))
                drawPath(hp, dark, style = Stroke(width = 1.2f, join = StrokeJoin.Round))
            }
            for (s in v.seams) drawPath(path(s, false), dark.copy(alpha = 0.7f), style = Stroke(width = 1f, cap = StrokeCap.Round))
            // Tie strings (dori) with a small tassel.
            for (t in v.ties) {
                drawPath(path(t, false), dark, style = Stroke(width = 0.35f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val end = t.last()
                drawCircle(Brand.Gold, radius = 0.9f * scale, center = Offset(((end.x - b.minX) * scale).toFloat(), ((end.y - b.minY) * scale).toFloat()))
            }
            if (border) {
                for (t in v.trims) drawPath(path(t, false), Brand.Gold, style = Stroke(width = 0.55f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
