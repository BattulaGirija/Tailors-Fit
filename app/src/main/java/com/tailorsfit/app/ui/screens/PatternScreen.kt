package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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

    val draft by produceState<DraftResult?>(
        null, model, vm.fabricWidthCm, vm.foldedCloth, vm.showAllowance, vm.allowTurning, vm.customerName,
    ) { value = vm.draftAsync(model) }

    Scaffold(topBar = { AppBar(model.name, onBack) }) { padding ->
        when (val result = draft) {
            null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(tr("pattern.arranging"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is DraftResult.Invalid -> Column(Modifier.padding(padding).padding(16.dp)) {
                Text(tr("pattern.check"), style = MaterialTheme.typography.titleMedium)
                result.errors.values.forEach { Text(tr("app.bullet", it)) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text(tr("pattern.back")) }
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
                        Text(tr("pattern.zoom"), style = MaterialTheme.typography.bodySmall)
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
            Text(tr("pattern.cloth"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FABRIC_WIDTHS.forEach { w ->
                    FilterChip(
                        selected = vm.fabricWidthCm == w,
                        onClick = { vm.setFabricWidth(w) },
                        label = { Text(tr("pattern.width", w.toInt(), vm.format(w / 2.54).substringBefore('.'))) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("pattern.folded"), Modifier.weight(1f))
                Switch(checked = vm.foldedCloth, onCheckedChange = vm::setFolded)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("pattern.allowance"), Modifier.weight(1f))
                Switch(checked = vm.showAllowance, onCheckedChange = vm::setAllowance)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("pattern.oneway"))
                    Text(
                        tr("pattern.oneway.text"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = !vm.allowTurning, onCheckedChange = { vm.changeAllowTurning(!it) })
            }
            Text(
                tr("pattern.needed", "%.2f".format(layout.length / 100), vm.format(layout.length / 2.54 / 36)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                tr("pattern.efficiency", "%.0f".format(layout.efficiency * 100), "%.0f".format((1 - layout.efficiency) * 100)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text("  " + tr("pattern.project"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { printMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("pattern.print")) }
                PaperMenu(printMenu, { printMenu = false }, includeFull = false) { onPrint(it) }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { pdfMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(" " + tr("pattern.pdf"))
                }
                PaperMenu(pdfMenu, { pdfMenu = false }, includeFull = true) { onSharePdf(it) }
            }
            OutlinedButton(onClick = onShareSvg, enabled = !busy, modifier = Modifier.weight(1f)) { Text(tr("pattern.svg")) }
        }
    }
}

@Composable
private fun PaperMenu(expanded: Boolean, onDismiss: () -> Unit, includeFull: Boolean, onPick: (PaperSize) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        PaperSize.entries.filter { includeFull || it != PaperSize.FULL }.forEach { p ->
            DropdownMenuItem(
                text = { Text(if (p == PaperSize.FULL) tr("pattern.plotter", p.label) else tr("pattern.pages", p.label)) },
                onClick = { onDismiss(); onPick(p) },
            )
        }
    }
}

@Composable
private fun SummaryCard(vm: AppViewModel, pattern: Pattern, layout: Layout) {
    val tooWide = layout.placed.filter { it.piece.id in layout.tooWide }.map { it.piece.name }.distinct()
    val warnings = (if (tooWide.isEmpty()) emptyList() else listOf(tr("pattern.too_wide", tooWide.joinToString(", ")))) + pattern.warnings
    if (warnings.isNotEmpty()) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null)
                    Text("  " + tr("pattern.warnings"), style = MaterialTheme.typography.titleSmall)
                }
                warnings.forEach { Text(tr("app.bullet", it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("pattern.pieces"), style = MaterialTheme.typography.titleMedium)
            layout.placed.map { it.piece }.distinctBy { it.name + it.cut.text }.forEach { p ->
                Text("${p.name}: ${p.cut.text}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("pattern.details"), style = MaterialTheme.typography.titleMedium)
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
            Toast.makeText(context, tr("pattern.export_failed", e.message), Toast.LENGTH_LONG).show()
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
