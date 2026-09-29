package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.DraftResult
import com.tailorsfit.app.data.Calibration
import com.tailorsfit.app.projector.ProjectorFrame
import com.tailorsfit.app.projector.ProjectorPresentation
import com.tailorsfit.app.projector.ProjectorView
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.render.PaintOptions

private const val STEP_CM = 5.0

@Composable
fun ProjectorScreen(vm: AppViewModel, modelId: String, onBack: () -> Unit) {
    val model = vm.model(modelId) ?: return
    val context = LocalContext.current
    val draft by produceState<DraftResult?>(null, model) { value = vm.draftAsync(model) }
    val result = draft
    if (result == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }
    if (result !is DraftResult.Ok) {
        Scaffold(topBar = { AppBar(tr("projector.title"), onBack) }) { p ->
            Text(tr("projector.fix"), Modifier.padding(p).padding(16.dp))
        }
        return
    }
    val layout = result.layout

    val external = remember { ProjectorPresentation.findExternalDisplay(context) }
    val displayKey = external?.name ?: "this-device"
    val defaultPxPerCm = if (external == null) context.resources.displayMetrics.xdpi / 2.54f else 12f
    var calibration by remember { mutableStateOf(vm.calibrationFor(displayKey, defaultPxPerCm)) }
    val calibSize = if (external == null) 5.0 else 20.0

    val frame = ProjectorFrame(
        layout = layout,
        options = PaintOptions(
            showAllowance = vm.showAllowance,
            caption = listOfNotNull(vm.customerName.trim().takeIf { it.isNotEmpty() }, model.name),
        ),
        pxPerCmX = calibration.pxPerCmX,
        pxPerCmY = calibration.pxPerCmY,
        panCm = vm.projectorPan,
        showGrid = vm.projectorGrid,
        calibrating = vm.projectorCalibrating,
        calibrationSizeCm = calibSize,
        lineWidthPx = vm.projectorLineWidth,
    )
    val onCalibrated: (Calibration) -> Unit = {
        calibration = it
        vm.saveCalibration(displayKey, it)
    }

    if (external != null) {
        ExternalProjector(vm, external, frame, layout, calibSize, onCalibrated, onBack)
    } else {
        PhoneProjector(vm, frame, layout, calibSize, onCalibrated, onBack)
    }
}

/** Pattern on a projector / TV; the phone shows a preview and the controls. */
@Composable
private fun ExternalProjector(
    vm: AppViewModel,
    display: Display,
    frame: ProjectorFrame,
    layout: Layout,
    calibSize: Double,
    onCalibrated: (Calibration) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val presentation = remember(display) { ProjectorPresentation(context, display) }
    DisposableEffect(presentation) {
        runCatching { presentation.show() }
        onDispose { presentation.dismiss() }
    }
    SideEffect { presentation.view.frame = frame }

    val size = remember(display) {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(dm)
        dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
    }
    Scaffold(topBar = { AppBar(tr("projector.on", display.name), onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            // Same picture as the projector, scaled down to the phone width.
            var previewWidthPx by remember { mutableStateOf(1) }
            val k = previewWidthPx.toFloat() / size.first
            val preview = frame.copy(
                pxPerCmX = frame.pxPerCmX * k,
                pxPerCmY = frame.pxPerCmY * k,
                lineWidthPx = (frame.lineWidthPx * k).coerceAtLeast(1f),
            )
            AndroidView(
                factory = { ProjectorView(it) },
                update = { v -> v.frame = preview },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(size.first.toFloat() / size.second)
                    .onSizeChanged { previewWidthPx = it.width.coerceAtLeast(1) }
                    .pointerInput(frame.pxPerCmX, k) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            vm.projectorPan = vm.projectorPan - Pt(drag.x / (frame.pxPerCmX * k).toDouble(), drag.y / (frame.pxPerCmY * k).toDouble())
                        }
                    },
            )
            Controls(vm, frame, layout, calibSize, onCalibrated, dark = false)
        }
    }
}

/** No projector attached: draw full screen on this device (for phone projectors / screen mirroring). */
@Composable
private fun PhoneProjector(
    vm: AppViewModel,
    frame: ProjectorFrame,
    layout: Layout,
    calibSize: Double,
    onCalibrated: (Calibration) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ProjectorView(it) },
            update = { it.frame = frame },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(frame.pxPerCmX, frame.pxPerCmY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        vm.projectorPan = vm.projectorPan - Pt(drag.x / frame.pxPerCmX.toDouble(), drag.y / frame.pxPerCmY.toDouble())
                    }
                }
                .pointerInput(Unit) { detectTapGestures { showControls = !showControls } },
        )
        if (showControls) {
            Surface(
                color = Color(0xE6202020),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(Modifier.navigationBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(tr("projector.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = tr("app.close"), tint = Color.White) }
                    }
                    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFB0C8), secondaryContainer = Color(0xFF5A4300))) {
                        Controls(vm, frame, layout, calibSize, onCalibrated, dark = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(
    vm: AppViewModel,
    frame: ProjectorFrame,
    layout: Layout,
    calibSize: Double,
    onCalibrated: (Calibration) -> Unit,
    dark: Boolean,
) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(onClick = { vm.projectorPan = vm.projectorPan + Pt(-STEP_CM, 0.0) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = tr("projector.left"))
            }
            FilledTonalIconButton(onClick = { vm.projectorPan = vm.projectorPan + Pt(0.0, -STEP_CM) }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = tr("projector.up"))
            }
            FilledTonalIconButton(onClick = { vm.projectorPan = vm.projectorPan + Pt(0.0, STEP_CM) }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("projector.down"))
            }
            FilledTonalIconButton(onClick = { vm.projectorPan = vm.projectorPan + Pt(STEP_CM, 0.0) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = tr("projector.right"))
            }
            FilterChip(selected = vm.projectorGrid, onClick = { vm.projectorGrid = !vm.projectorGrid }, label = { Text(tr("projector.grid")) })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.projectorPan = Pt(-2.0, -2.0) }, label = { Text(tr("projector.start")) })
            layout.placed.forEach { p ->
                AssistChip(
                    onClick = {
                        val b = p.piece.bounds(layout.allowances)
                        vm.projectorPan = Pt(p.offset.x + b.minX - 2.0, p.offset.y + b.minY - 2.0)
                    },
                    label = { Text(p.piece.name) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("projector.line"), Modifier.width(88.dp))
            Slider(value = vm.projectorLineWidth, onValueChange = vm::setLineWidth, valueRange = 1f..10f, modifier = Modifier.weight(1f))
        }
        FilterChip(
            selected = vm.projectorCalibrating,
            onClick = { vm.projectorCalibrating = !vm.projectorCalibrating },
            label = { Text(if (vm.projectorCalibrating) tr("projector.calibrating") else tr("projector.calibrate")) },
        )
        if (vm.projectorCalibrating) CalibrationPanel(vm, frame, calibSize, onCalibrated, dark)
    }
}

@Composable
private fun CalibrationPanel(
    vm: AppViewModel,
    frame: ProjectorFrame,
    calibSize: Double,
    onCalibrated: (Calibration) -> Unit,
    dark: Boolean,
) {
    var measuredW by remember { mutableStateOf("") }
    var measuredH by remember { mutableStateOf("") }
    val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tr("projector.calib.text", vm.formatCm(calibSize)),
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = measuredW, onValueChange = { measuredW = it }, label = { Text(tr("projector.calib.width", vm.unit.label)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = measuredH, onValueChange = { measuredH = it }, label = { Text(tr("projector.calib.height", vm.unit.label)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val w = measuredW.replace(',', '.').toDoubleOrNull()?.let { vm.unit.toCm(it) }
                val h = measuredH.replace(',', '.').toDoubleOrNull()?.let { vm.unit.toCm(it) } ?: w
                if (w != null && w > 0 && h != null && h > 0) {
                    onCalibrated(
                        Calibration(
                            pxPerCmX = (frame.pxPerCmX * calibSize / w).toFloat(),
                            pxPerCmY = (frame.pxPerCmY * calibSize / h).toFloat(),
                        ),
                    )
                    measuredW = ""
                    measuredH = ""
                }
            }) { Text(tr("projector.calib.apply")) }
            Text(tr("projector.calib.fine"), color = textColor)
            TextButton(onClick = { onCalibrated(Calibration(frame.pxPerCmX * 0.99f, frame.pxPerCmY * 0.99f)) }) { Text("−1%") }
            TextButton(onClick = { onCalibrated(Calibration(frame.pxPerCmX * 1.01f, frame.pxPerCmY * 1.01f)) }) { Text("+1%") }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
