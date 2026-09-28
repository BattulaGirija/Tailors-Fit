package com.tailorsfit.app.ui.screens

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
import kotlinx.coroutines.launch

@Composable
fun MeasurementScreen(vm: AppViewModel, modelId: String, onBack: () -> Unit, onGenerate: () -> Unit) {
    val model = vm.model(modelId) ?: return
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var errors by remember { mutableStateOf<Map<MeasurementField, String>>(emptyMap()) }

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
            Text(model.description, style = MaterialTheme.typography.bodyMedium)

            CustomerCard(vm)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Units", style = MaterialTheme.typography.titleSmall)
                LengthUnit.entries.forEach { u ->
                    FilterChip(selected = vm.unit == u, onClick = { vm.changeUnit(u) }, label = { Text(if (u == LengthUnit.CM) "cm" else "inch") })
                }
            }

            Text("Start from a standard size", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SizePreset.entries.forEach { p -> AssistChip(onClick = { vm.applyPreset(p) }, label = { Text(p.label) }) }
            }

            Text("Measurements (${vm.unit.label})", style = MaterialTheme.typography.titleMedium)
            model.requiredMeasurements.forEach { f ->
                OutlinedTextField(
                    value = vm.inputs[f] ?: "",
                    onValueChange = { text ->
                        vm.inputs[f] = text.filter { it.isDigit() || it == '.' || it == ',' }
                        if (errors.containsKey(f)) errors = errors - f
                    },
                    label = { Text(f.label) },
                    supportingText = { Text(errors[f] ?: f.help) },
                    isError = errors.containsKey(f),
                    suffix = { Text(vm.unit.label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val err = vm.saveCustomer()
                    scope.launch { snackbar.showSnackbar(err ?: "Saved ${vm.customerName}") }
                }) { Text("Save customer") }
                Button(onClick = {
                    val found = vm.currentMeasurements().validate(model.requiredMeasurements)
                    errors = found
                    if (found.isEmpty()) {
                        onGenerate()
                    } else {
                        scope.launch { snackbar.showSnackbar("Please check ${found.size} measurement(s)") }
                    }
                }) { Text("Generate pattern") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CustomerCard(vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Customer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = vm.customers.isNotEmpty() || vm.customerId != null) {
                        Text("Load saved")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New customer") }, onClick = { vm.startNewCustomer(); menu = false })
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
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.customerPhone,
                onValueChange = { vm.customerPhone = it },
                label = { Text("Phone (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
