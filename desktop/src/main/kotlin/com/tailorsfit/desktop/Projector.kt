package com.tailorsfit.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.model.Lengths
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter
import java.awt.GraphicsEnvironment

/** The screens connected to the computer (a projector shows up as a second screen). */
fun screens(): List<java.awt.GraphicsDevice> = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.toList()

/**
 * A borderless window covering the chosen screen: bright pattern lines on black at true size.
 * The size is set with [AppState.pxPerCm] (calibrated with the test square).
 */
@Composable
fun ProjectorWindow(state: AppState) {
    val device = screens().getOrElse(state.projectorScreen) { screens().last() }
    val b = device.defaultConfiguration.bounds
    val windowState = remember(state.projectorScreen) {
        WindowState(placement = WindowPlacement.Floating, position = WindowPosition(b.x.dp, b.y.dp), size = DpSize(b.width.dp, b.height.dp))
    }
    Window(
        onCloseRequest = { state.projecting = false },
        state = windowState,
        undecorated = true,
        alwaysOnTop = true,
        title = "Tailors Fit — projector",
        onKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@Window false
            val step = if (e.isShiftPressed) 10.0 else 1.0
            when (e.key) {
                Key.DirectionLeft -> state.panX -= step
                Key.DirectionRight -> state.panX += step
                Key.DirectionUp -> state.panY -= step
                Key.DirectionDown -> state.panY += step
                Key.Plus, Key.Equals, Key.NumPadAdd -> state.lineWidth = (state.lineWidth + 1f).coerceAtMost(12f)
                Key.Minus, Key.NumPadSubtract -> state.lineWidth = (state.lineWidth - 1f).coerceAtLeast(1f)
                Key.G -> state.projectorGrid = !state.projectorGrid
                Key.Escape -> state.projecting = false
                else -> return@Window false
            }
            true
        },
    ) {
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize().background(Color.Black)) {
            val layout = state.layout ?: return@Canvas
            // pxPerCm is in screen pixels of this window.
            val k = state.pxPerCm
            val origin = Pt(state.panX, state.panY)
            val sink = ComposeSink(this, measurer, k, origin, Offset.Zero, LineStyle.projector(state.lineWidth))
            if (state.projectorGrid) {
                val area = Rect(origin.x, origin.y, origin.x + size.width / k, origin.y + size.height / k)
                PatternPainter.paintGrid(sink, area, Lengths.gridStepCm)
            }
            if (state.showTestSquare) {
                val side = if (state.unit == com.tailorsfit.pattern.model.LengthUnit.INCH) 4 * 2.54 else 10.0
                PatternPainter.paintCalibrationSquare(sink, Pt(origin.x + 2.0, origin.y + 2.0), side, Lengths.format(side))
            } else {
                PatternPainter.paintFabric(layout, sink)
                PatternPainter.paintLayout(layout, sink, PaintOptions(showAllowance = state.allowance, textScale = 1.2))
            }
        }
    }
}
