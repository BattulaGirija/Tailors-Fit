package com.tailorsfit.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.DraftResult
import com.tailorsfit.app.export.PaperSize
import com.tailorsfit.app.export.PdfExporter
import com.tailorsfit.app.export.Sharing
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.app.ui.components.PatternCanvas
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.SvgExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val FABRIC_WIDTHS = listOf(90.0, 110.0, 140.0)

@Composable
fun PatternScreen(vm: AppViewModel, modelId: String, onBack: () -> Unit, onProject: () -> Unit) {
    val model = vm.model(modelId) ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val result = remember(model, vm.fabricWidthCm, vm.foldedCloth, vm.showAllowance, vm.customerName) { vm.draft(model) }

    Scaffold(topBar = { AppBar(model.name, onBack) }) { padding ->
        when (result) {
            is DraftResult.Invalid -> Column(Modifier.padding(padding).padding(16.dp)) {
                Text("Some measurements need checking:", style = MaterialTheme.typography.titleMedium)
                result.errors.values.forEach { Text("• $it") }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Back to measurements") }
            }
            is DraftResult.Ok -> {
                val pattern = result.pattern
                val layout = result.layout
                val paintOptions = PaintOptions(
                    showAllowance = vm.showAllowance,
                    caption = listOfNotNull(vm.customerName.trim().takeIf { it.isNotEmpty() }, model.name),
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    PatternCanvas(layout, paintOptions, Modifier.fillMaxWidth().height(440.dp))
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Pinch to zoom, drag to move.", style = MaterialTheme.typography.bodySmall)
                        ClothOptions(vm, layout)
                        Actions(
                            busy = busy,
                            onPrint = { paper ->
                                runExport(scope, context, { busy = it }, { exportPdf(context, pattern, layout, paper, paintOptions) }) {
                                    Sharing.print(context, it, pattern.title, paper)
                                }
                            },
                            onSharePdf = { paper ->
                                runExport(scope, context, { busy = it }, { exportPdf(context, pattern, layout, paper, paintOptions) }) {
                                    Sharing.share(context, it, "application/pdf")
                                }
                            },
                            onShareSvg = {
                                runExport(scope, context, { busy = it }, { exportSvg(context, pattern, layout, paintOptions) }) {
                                    Sharing.share(context, it, "image/svg+xml")
                                }
                            },
                            onProject = onProject,
                        )
                        SummaryCard(vm, pattern, layout)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClothOptions(vm: AppViewModel, layout: Layout) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cloth", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FABRIC_WIDTHS.forEach { w ->
                    FilterChip(
                        selected = vm.fabricWidthCm == w,
                        onClick = { vm.setFabricWidth(w) },
                        label = { Text("${w.toInt()} cm (${vm.format(w / 2.54).substringBefore('.')}\") wide") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cloth folded lengthwise (cut 2 layers)", Modifier.weight(1f))
                Switch(checked = vm.foldedCloth, onCheckedChange = vm::setFolded)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Include seam allowance", Modifier.weight(1f))
                Switch(checked = vm.showAllowance, onCheckedChange = vm::setAllowance)
            }
            Text(
                "Cloth needed: about ${"%.2f".format(layout.length / 100)} m (${vm.format(layout.length / 2.54 / 36)} yd)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Actions(
    busy: Boolean,
    onPrint: (PaperSize) -> Unit,
    onSharePdf: (PaperSize) -> Unit,
    onShareSvg: () -> Unit,
    onProject: () -> Unit,
) {
    var printMenu by remember { mutableStateOf(false) }
    var pdfMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onProject, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text("  Project onto cloth")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { printMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Print…") }
                PaperMenu(printMenu, { printMenu = false }, includeFull = false) { onPrint(it) }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { pdfMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(" PDF")
                }
                PaperMenu(pdfMenu, { pdfMenu = false }, includeFull = true) { onSharePdf(it) }
            }
            OutlinedButton(onClick = onShareSvg, enabled = !busy, modifier = Modifier.weight(1f)) { Text("SVG") }
        }
    }
}

@Composable
private fun PaperMenu(expanded: Boolean, onDismiss: () -> Unit, includeFull: Boolean, onPick: (PaperSize) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        PaperSize.entries.filter { includeFull || it != PaperSize.FULL }.forEach { p ->
            DropdownMenuItem(
                text = { Text(if (p == PaperSize.FULL) p.label + " (plotter)" else "${p.label} pages, true size") },
                onClick = { onDismiss(); onPick(p) },
            )
        }
    }
}

@Composable
private fun SummaryCard(vm: AppViewModel, pattern: Pattern, layout: Layout) {
    if (pattern.warnings.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Text("  Check before cutting", style = MaterialTheme.typography.titleSmall)
                }
                pattern.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pieces", style = MaterialTheme.typography.titleMedium)
            layout.placed.map { it.piece }.distinctBy { it.name + it.cut.text }.forEach { p ->
                Text("${p.name}: ${p.cut.text}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Details", style = MaterialTheme.typography.titleMedium)
            pattern.summary.forEach { (k, v) ->
                val cm = v.substringBefore(" cm").toDoubleOrNull()
                Text("$k: ${if (cm != null) vm.formatCm(cm) else v}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Builds the file off the main thread, then hands it to [then] on the main thread. */
private fun runExport(
    scope: CoroutineScope,
    context: Context,
    setBusy: (Boolean) -> Unit,
    produce: () -> File,
    then: (File) -> Unit,
) {
    scope.launch {
        setBusy(true)
        try {
            val file = withContext(Dispatchers.IO) { produce() }
            then(file)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            setBusy(false)
        }
    }
}

private fun exportPdf(context: Context, pattern: Pattern, layout: Layout, paper: PaperSize, options: PaintOptions): File {
    val name = Sharing.safeFileName(pattern.title) + "-" + paper.name.lowercase() + ".pdf"
    return PdfExporter.export(pattern, layout, paper, options, File(Sharing.exportDir(context), name))
}

private fun exportSvg(context: Context, pattern: Pattern, layout: Layout, options: PaintOptions): File {
    val file = File(Sharing.exportDir(context), Sharing.safeFileName(pattern.title) + ".svg")
    file.writeText(SvgExporter.export(layout, options, pattern.title))
    return file
}
