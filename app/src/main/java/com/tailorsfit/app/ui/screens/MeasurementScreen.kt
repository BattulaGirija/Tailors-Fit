package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.tailorsfit.app.ui.components.GarmentPreviewCard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.pattern.model.LengthUnit
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.SizePreset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import com.tailorsfit.app.ui.components.MeasureFigure
import com.tailorsfit.app.ui.components.hasMeasureFigure
import com.tailorsfit.app.ui.theme.Brand
import kotlinx.coroutines.launch

/** Customization details (optional drafting adjustments), grouped the same way. */
private val ADJUSTMENT_SECTIONS = listOf(
    "measure.section.front" to listOf(
        MeasurementField.FRONT_LENGTH, MeasurementField.APEX_TO_APEX,
        MeasurementField.FRONT_DART_WIDTH, MeasurementField.SIDE_DART_WIDTH, MeasurementField.HOOK_DART_DISTANCE,
        MeasurementField.FRONT_ARM_CURVE, MeasurementField.NECK_BROAD,
    ),
    "measure.section.back" to listOf(MeasurementField.BACK_ARM_CURVE),
    "measure.section.shoulder" to listOf(MeasurementField.SHOULDER_DROP, MeasurementField.ARMHOLE_DEPTH),
)

@Composable
fun MeasurementScreen(vm: AppViewModel, modelId: String, onBack: () -> Unit, onGenerate: () -> Unit, onGuide: () -> Unit = {}) {
    val model = vm.model(modelId) ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var errors by remember { mutableStateOf<Map<MeasurementField, String>>(emptyMap()) }
    var help by remember { mutableStateOf<MeasurementField?>(null) }
    var showAdjustments by rememberSaveable { mutableStateOf(MeasurementField.adjustments.any { !vm.inputs[it].isNullOrBlank() }) }

    fun onEdit(f: MeasurementField, text: String) {
        vm.editMeasurement(f, text.filter { it.isDigit() || it == '.' || it == ',' })
        if (errors.containsKey(f)) errors = errors - f
    }

    Scaffold(
        topBar = { AppBar(model.name, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GarmentPreviewCard(model, vm.currentMeasurements())

            CustomerCard(vm)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("measure.units"), style = MaterialTheme.typography.titleSmall)
                LengthUnit.entries.forEach { u ->
                    FilterChip(selected = vm.unit == u, onClick = { vm.changeUnit(u) }, label = { Text(if (u == LengthUnit.CM) tr("measure.cm") else tr("measure.inch")) })
                }
            }

            Text(tr("measure.start_size"), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SizePreset.entries.forEach { p ->
                    val selected = vm.selectedPreset == p
                    FilterChip(
                        selected = selected,
                        onClick = { vm.applyPreset(p) },
                        label = { Text(p.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
            val preset = vm.selectedPreset
            Surface(
                color = if (preset != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        preset != null -> tr("measure.size_selected", preset.label)
                        vm.customerId != null -> tr("measure.using_customer", vm.customerName)
                        else -> tr("measure.custom")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("measure.title", vm.unit.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onGuide) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" " + tr("measure.how_to"))
                }
            }
            // The measurement sheet, in the usual order (sleeve rows only when the design has sleeves).
            MeasureSection(tr("measure.details")) {
                FieldGrid(model.requiredMeasurements) { f ->
                    MeasureField(f, vm.inputs[f] ?: "", vm.unit.label, errors[f], onHelp = { help = f }) { onEdit(f, it) }
                }
            }

            // Customization details: optional changes to how the pattern is drafted.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Brand.Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { showAdjustments = !showAdjustments }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("measure.adjust.title"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(tr("measure.adjust.text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (showAdjustments) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    if (showAdjustments) {
                        for ((title, fields) in ADJUSTMENT_SECTIONS) {
                            Text(tr(title), style = MaterialTheme.typography.labelLarge, color = Brand.Plum)
                            FieldGrid(fields) { f ->
                                MeasureField(f, vm.inputs[f] ?: "", vm.unit.label, null, placeholder = tr("measure.auto"), onHelp = { help = f }) { onEdit(f, it) }
                            }
                        }
                        TextButton(onClick = { MeasurementField.adjustments.forEach { vm.editMeasurement(it, "") } }) { Text(tr("measure.adjust.reset")) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val err = vm.saveCustomer()
                    scope.launch { snackbar.showSnackbar(err ?: tr("measure.saved", vm.customerName)) }
                }) { Text(tr("measure.save")) }
                Button(onClick = {
                    val found = vm.currentMeasurements().validate(model.requiredMeasurements)
                    errors = found
                    if (found.isEmpty()) {
                        onGenerate()
                    } else {
                        scope.launch { snackbar.showSnackbar(tr("measure.check", found.size)) }
                    }
                }) { Text(tr("measure.generate")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    help?.let { f -> MeasureHelpDialog(f) { help = null } }
}

@Composable
private fun MeasureSection(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Brand.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Two fields side by side, like the columns of an order sheet. */
@Composable
private fun FieldGrid(fields: List<MeasurementField>, field: @Composable (MeasurementField) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { f -> Box(Modifier.weight(1f)) { field(f) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MeasureField(
    f: MeasurementField,
    value: String,
    unit: String,
    error: String?,
    placeholder: String? = null,
    onHelp: () -> Unit,
    onChange: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.label, style = MaterialTheme.typography.labelLarge, maxLines = 2, modifier = Modifier.weight(1f))
            IconButton(onClick = onHelp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Info, contentDescription = tr("measure.how_to"), tint = Brand.Gold, modifier = Modifier.size(18.dp))
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder?.let { { Text(it) } },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            suffix = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How to take one measurement: the figure with the tape line, and the instructions. */
@Composable
fun MeasureHelpDialog(f: MeasurementField, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(f.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasMeasureFigure(f)) {
                    Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Parchment).padding(8.dp)) {
                        MeasureFigure(f, Modifier.fillMaxSize())
                    }
                }
                Text(f.help, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("app.close")) } },
    )
}

@Composable
private fun CustomerCard(vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("measure.customer"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = vm.customers.isNotEmpty() || vm.customerId != null) {
                        Text(tr("measure.load"))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("measure.new_customer")) }, onClick = { vm.startNewCustomer(); menu = false })
                        vm.customers.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(if (c.phone.isBlank()) c.name else "${c.name} · ${c.phone}") },
                                onClick = { vm.selectCustomer(c); menu = false },
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = vm.customerName,
                onValueChange = { vm.customerName = it },
                label = { Text(tr("measure.name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.customerPhone,
                onValueChange = { vm.customerPhone = it },
                label = { Text(tr("measure.phone")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
