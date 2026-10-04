package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.app.ui.components.BlouseSketch
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.blouse.BlouseModel
import com.tailorsfit.pattern.blouse.BlouseSpec
import com.tailorsfit.pattern.blouse.BodyStyle
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.GarmentModel

/**
 * Designs of a category. Blouses are split into tabs by type (All, 3 Dart, 4 Dart, Princess,
 * Katori, Sabyasachi) with a search box; tapping a design opens it for customising.
 */
@Composable
fun CatalogScreen(categoryId: String, onBack: () -> Unit, onModel: (String) -> Unit, onDesignOwn: () -> Unit = {}) {
    val category = Catalog.category(categoryId)
    val all = Catalog.modelsIn(categoryId)
    val isBlouse = categoryId == "blouse"
    var tab by rememberSaveable { mutableStateOf(0) } // 0 = All, then BodyStyle.entries
    var query by rememberSaveable { mutableStateOf("") }
    val bodies = BodyStyle.entries
    val models = all
        .filter { tab == 0 || (it as? BlouseModel)?.body == bodies[tab - 1] }
        .filter { m -> query.isBlank() || matches(m, query) }

    Scaffold(topBar = { AppBar(category?.name ?: tr("catalog.title"), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isBlouse) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("cat.all")) })
                    bodies.forEachIndexed { i, b -> Tab(selected = tab == i + 1, onClick = { tab = i + 1 }, text = { Text(b.label) }) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr("catalog.search")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = tr("app.clear")) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isBlouse && tab == 0 && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { DesignOwnCard(onDesignOwn) }
                }
                if (models.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(tr("catalog.none"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                }
                items(models, key = { it.id }) { model -> DesignCard(model) { onModel(model.id) } }
            }
        }
    }
}

private fun matches(m: GarmentModel, query: String): Boolean {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val text = (listOf(m.name, m.description) + m.tags).joinToString(" ").lowercase()
    return words.all { it in text }
}

/** A design with its front and back sketch, drawn from its real pattern. */
@Composable
private fun DesignCard(model: GarmentModel, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Brand.Line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(116.dp).background(Brand.Parchment).padding(8.dp)) {
                BlouseSketch(model, back = false, modifier = Modifier.weight(1f).fillMaxHeight())
                BlouseSketch(model, back = true, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                (model as? BlouseModel)?.let {
                    Text(it.body.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
                }
                Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    model.tags.drop(1).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Start from a plain blouse and choose the body, sleeves and necks. */
@Composable
private fun DesignOwnCard(onClick: () -> Unit) {
    val sample = remember { BlouseSpec.BASIC.copy(body = BodyStyle.KATORI).toModel() }
    Surface(
        color = Brand.Aubergine,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("mix.card.title"), style = MaterialTheme.typography.titleLarge, color = Brand.Ivory)
                Text(tr("mix.card.text"), style = MaterialTheme.typography.bodyMedium, color = Brand.Ivory.copy(alpha = 0.85f))
            }
            Box(Modifier.size(width = 120.dp, height = 90.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Parchment).padding(6.dp)) {
                Row(Modifier.fillMaxSize()) {
                    BlouseSketch(sample, back = false, modifier = Modifier.weight(1f).fillMaxHeight())
                    BlouseSketch(sample, back = true, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
