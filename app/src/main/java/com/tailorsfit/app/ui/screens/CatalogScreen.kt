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
fun CatalogScreen(categoryId: String, onBack: () -> Unit, onModel: (String) -> Unit) {
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
