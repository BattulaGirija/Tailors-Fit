package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.tailorsfit.app.ui.components.BlouseSketch
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.blouse.BackNeck
import com.tailorsfit.pattern.blouse.BlouseMix
import com.tailorsfit.pattern.blouse.FrontNeck
import com.tailorsfit.pattern.blouse.SleeveStyle
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.app.ui.components.ModelThumbnail
import com.tailorsfit.pattern.model.Catalog

@Composable
fun CatalogScreen(categoryId: String, onBack: () -> Unit, onModel: (String) -> Unit, onDesignOwn: () -> Unit = {}) {
    val category = Catalog.category(categoryId)
    val models = Catalog.modelsIn(categoryId)
    Scaffold(topBar = { AppBar(category?.name ?: tr("catalog.title"), onBack) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (categoryId == "blouse") {
                item(span = { GridItemSpan(maxLineSpan) }) { DesignOwnCard(onDesignOwn) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(tr("mix.ready"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
            }
            items(models, key = { it.id }) { model ->
                Card(Modifier.fillMaxWidth().clickable { onModel(model.id) }) {
                    ModelThumbnail(
                        model,
                        Modifier.fillMaxWidth().height(120.dp).padding(8.dp),
                        fill = MaterialTheme.colorScheme.secondaryContainer,
                        line = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(model.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            model.tags.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}

/** Entry to the three "Design your own" pages: front neck, back neck, sleeves. */
@Composable
private fun DesignOwnCard(onClick: () -> Unit) {
    val sample = remember { BlouseMix(front = FrontNeck.SWEETHEART, back = BackNeck.POT, sleeve = SleeveStyle.PUFF).toModel() }
    Surface(
        color = Brand.Aubergine,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("mix.card.title"), style = MaterialTheme.typography.titleLarge, color = Brand.Ivory)
                Text(tr("mix.card.text"), style = MaterialTheme.typography.bodyMedium, color = Brand.Ivory.copy(alpha = 0.85f))
                Text(
                    listOf(tr("mix.front.title"), tr("mix.back.title"), tr("mix.sleeve.title")).joinToString("  →  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand.GoldLight,
                )
            }
            Box(Modifier.size(width = 120.dp, height = 96.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Parchment).padding(6.dp)) {
                Row(Modifier.fillMaxSize()) {
                    BlouseSketch(sample, back = false, modifier = Modifier.weight(1f).fillMaxHeight())
                    BlouseSketch(sample, back = true, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}
