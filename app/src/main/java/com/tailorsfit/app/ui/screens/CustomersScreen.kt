package com.tailorsfit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.data.Customer
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.pattern.model.MeasurementField
import java.text.DateFormat
import java.util.Date

@Composable
fun CustomersScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (Customer) -> Unit) {
    var toDelete by remember { mutableStateOf<Customer?>(null) }
    Scaffold(topBar = { AppBar("Customers", onBack) }) { padding ->
        if (vm.customers.isEmpty()) {
            Text(
                "No saved customers yet. Enter measurements for a design and tap \"Save customer\".",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.customers, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(c) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            val bust = c.measurements[MeasurementField.BUST]
                            val details = listOfNotNull(
                                c.phone.takeIf { it.isNotBlank() },
                                if (bust.isNaN()) null else "Bust ${vm.formatCm(bust)}",
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.updatedAt)),
                            )
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { toDelete = c }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }
    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("Their saved measurements will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { vm.deleteCustomer(c.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
