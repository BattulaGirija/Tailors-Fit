package com.tailorsfit.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.pattern.model.Catalog

@Composable
fun HomeScreen(onCategory: (String) -> Unit, onCustomers: () -> Unit) {
    Scaffold(
        topBar = {
            AppBar("Tailors Fit") {
                IconButton(onClick = onCustomers) { Icon(Icons.Filled.Person, contentDescription = "Customers") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Pick a garment, choose a design, enter measurements — then print the pieces or project them straight onto the cloth.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(Catalog.categories) { cat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (cat.available) 1f else 0.5f)
                        .clickable(enabled = cat.available) { onCategory(cat.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (cat.available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(cat.name, style = MaterialTheme.typography.titleLarge)
                            val count = Catalog.modelsIn(cat.id).size
                            Text(
                                if (cat.available) "$count designs · ${cat.description}" else cat.description,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onCustomers)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Customers", style = MaterialTheme.typography.titleLarge)
                            Text("Saved measurements", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
