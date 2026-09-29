package com.tailorsfit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.app.ui.components.NumberBadge
import com.tailorsfit.app.ui.components.OrnamentDivider
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.model.MeasurementField

@Composable
fun MeasurementGuideScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppBar("How to measure", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Measure over a well-fitting blouse or thin garment. Keep the tape snug, not tight. " +
                        "\"Shoulder next to neck\" is the point where the shoulder seam meets the neck.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(MeasurementField.entries) { i, f ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Brand.Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp)) {
                        NumberBadge(i + 1)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(f.label, style = MaterialTheme.typography.titleMedium)
                            Text(f.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { AppBar("About", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tailors Fit", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            OrnamentDivider()
            Text(
                "Tailors Fit drafts sewing patterns from each customer's measurements, so pieces can be printed " +
                    "at true size or projected straight onto the cloth — no separate cutting master needed.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Always check the first garment of a new design before cutting expensive cloth.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("Credits", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Fonts: DM Serif Display and Poppins, used under the SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
