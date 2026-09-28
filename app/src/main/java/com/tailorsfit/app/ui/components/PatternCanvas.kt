package com.tailorsfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.tailorsfit.app.render.CanvasSink
import com.tailorsfit.app.render.SinkStyle
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter
import kotlin.math.min

/** Zoomable, pannable preview of the whole layout (pinch to zoom, drag to move). */
@Composable
fun PatternCanvas(layout: Layout, options: PaintOptions, modifier: Modifier = Modifier) {
    var zoom by remember(layout) { mutableFloatStateOf(1f) }
    var offset by remember(layout) { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(layout) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val newZoom = (zoom * gestureZoom).coerceIn(0.5f, 20f)
                    val factor = newZoom / zoom
                    // Keep the point under the fingers still while zooming.
                    offset = centroid - (centroid - offset) * factor + pan
                    zoom = newZoom
                }
            },
    ) {
        drawRect(Color.White)
        val margin = 3.0
        val fit = min(size.width / (layout.width + 2 * margin), size.height / (layout.length + 2 * margin)).toFloat()
        val scale = fit * zoom
        val baseX = ((size.width - layout.width * fit) / 2).toFloat()
        val baseY = ((size.height - layout.length * fit) / 2).toFloat()
        drawIntoCanvas { canvas ->
            val sink = CanvasSink(
                canvas.nativeCanvas, scale, scale, 0.0, 0.0,
                SinkStyle.paper(unit = (scale / 12f).coerceIn(0.6f, 3f)),
                offsetPxX = baseX * zoom + offset.x,
                offsetPxY = baseY * zoom + offset.y,
            )
            PatternPainter.paintFabric(layout, sink)
            PatternPainter.paintLayout(layout, sink, options)
        }
    }
}
