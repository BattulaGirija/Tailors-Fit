package com.tailorsfit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.data.Customer
import com.tailorsfit.app.ui.components.ModelThumbnail
import com.tailorsfit.app.ui.components.NumberBadge
import com.tailorsfit.app.ui.components.OrnamentDivider
import com.tailorsfit.app.ui.components.Pill
import com.tailorsfit.app.ui.components.SectionHeader
import com.tailorsfit.app.ui.components.TapeMeasure
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.GarmentCategory

private val FEATURED = listOf(
    "blouse_princess_round",
    "blouse_boat",
    "blouse_sweetheart",
    "blouse_deep_back_u",
    "blouse_v",
    "blouse_princess_sweetheart",
)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onMenu: () -> Unit,
    onCategory: (String) -> Unit,
    onModel: (String) -> Unit,
    onCustomers: () -> Unit,
    onCustomer: (Customer) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { Hero(onMenu, onStart = { onCategory("blouse") }, onCustomers) }

        item { SectionHeader("How it works") }
        item { Steps() }

        item { SectionHeader("Collections", "Every design is drafted from your customer's own measurements.") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(Catalog.categories, key = { it.id }) { CollectionCard(it, onCategory) }
            }
        }

        item { SectionHeader("Featured designs", action = "See all", onAction = { onCategory("blouse") }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(FEATURED.mapNotNull { Catalog.model(it) }, key = { it.id }) { model ->
                    Card(
                        onClick = { onModel(model.id) },
                        modifier = Modifier.width(168.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        border = BorderStroke(1.dp, Brand.Line),
                    ) {
                        Box(Modifier.fillMaxWidth().height(112.dp).background(Brand.Parchment)) {
                            ModelThumbnail(
                                model,
                                Modifier.fillMaxSize().padding(12.dp),
                                fill = Color(0xFFF7EBD2),
                                line = Brand.Aubergine,
                            )
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text(model.tags.first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (vm.customers.isNotEmpty()) {
            item { SectionHeader("Recent customers", action = "All", onAction = onCustomers) }
            items(vm.customers.take(3), key = { it.id }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onCustomer(c) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Brand.Gold)
                }
            }
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 36.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OrnamentDivider()
                Spacer(Modifier.height(10.dp))
                Text("Measure twice, cut once.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Hero(onMenu: () -> Unit, onStart: () -> Unit, onCustomers: () -> Unit) {
    val heroModel = Catalog.model("blouse_princess_round")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(Brand.Plum, Brand.Aubergine, Brand.AubergineDeep))),
    ) {
        // A real draft, traced in gold, as the backdrop.
        if (heroModel != null) {
            ModelThumbnail(
                heroModel,
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 52.dp)
                    .offset(x = 56.dp)
                    .width(210.dp)
                    .height(130.dp)
                    .alpha(0.22f),
                fill = Color.Transparent,
                line = Brand.GoldLight,
            )
        }
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Brand.Ivory) }
                Text(
                    "TAILORS FIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = Brand.GoldLight,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCustomers) { Icon(Icons.Filled.Person, contentDescription = "Customers", tint = Brand.Ivory) }
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp)) {
                Text("Patterns,\ncut to measure", style = MaterialTheme.typography.displaySmall, color = Brand.Ivory)
                Spacer(Modifier.height(12.dp))
                OrnamentDivider(color = Brand.Gold, width = 96)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Pick a design, enter measurements, then print the pieces or project them straight onto the cloth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.Ivory.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth(0.82f),
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Gold, contentColor = Brand.AubergineDeep),
                    ) { Text("Start a pattern") }
                    OutlinedButton(
                        onClick = onCustomers,
                        border = BorderStroke(1.dp, Brand.Ivory.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand.Ivory),
                    ) { Text("Customers") }
                }
            }
            TapeMeasure(Modifier.padding(bottom = 0.dp).alpha(0.7f), color = Brand.Gold)
        }
    }
}

@Composable
private fun Steps() {
    val steps = listOf(
        "Choose a design" to "Round, boat, sweetheart, princess cut and more.",
        "Enter measurements" to "In inches or cm — saved for each customer.",
        "Print or project" to "True-size pages, or lines projected on the cloth.",
    )
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, (title, text) ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Brand.Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberBadge(i + 1)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(cat: GarmentCategory, onCategory: (String) -> Unit) {
    val models = Catalog.modelsIn(cat.id)
    Card(
        onClick = { onCategory(cat.id) },
        enabled = cat.available,
        modifier = Modifier.width(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cat.available) Brand.Aubergine else MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            if (cat.available && models.isNotEmpty()) {
                ModelThumbnail(models.first(), Modifier.fillMaxSize().padding(14.dp), fill = Color.Transparent, line = Brand.GoldLight)
            } else {
                Text(
                    cat.name.take(1),
                    style = MaterialTheme.typography.displayLarge,
                    color = Brand.Gold.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (!cat.available) {
                Pill("Coming soon", Brand.Gold.copy(alpha = 0.18f), Color(0xFF7A5A1C), Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            Text(
                cat.name,
                style = MaterialTheme.typography.titleLarge,
                color = if (cat.available) Brand.Ivory else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (cat.available) "${models.size} designs" else cat.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (cat.available) Brand.GoldLight else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
