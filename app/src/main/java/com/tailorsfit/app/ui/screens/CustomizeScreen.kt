package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.app.ui.components.BlouseSketch
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.blouse.BackNeck
import com.tailorsfit.pattern.blouse.BlouseModel
import com.tailorsfit.pattern.blouse.BlouseSpec
import com.tailorsfit.pattern.blouse.BodyStyle
import com.tailorsfit.pattern.blouse.FrontNeck
import com.tailorsfit.pattern.blouse.NeckDepth
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle

private enum class CustomTab { BLOUSE, SLEEVE, NECK }
private enum class SketchView { BOTH, FRONT, BACK }

/**
 * Customise a design before measuring: the blouse type, the sleeves, and the front and back
 * necks, with a sketch that follows every choice. Choices live in [AppViewModel.spec]; "Next"
 * opens the measurements for the resulting design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(vm: AppViewModel, modelId: String, onBack: () -> Unit, onNext: (String) -> Unit) {
    // Start from the design that was tapped (kept while the tailor goes to measurements and back).
    LaunchedEffect(modelId) {
        if (vm.specStart != modelId) {
            val m = vm.model(modelId) as? BlouseModel
            vm.spec = if (m != null) BlouseSpec.of(m) else BlouseSpec.BASIC
            vm.specStart = modelId
        }
    }
    val spec = vm.spec
    val model = remember(spec) { spec.toModel() }
    var tab by rememberSaveable { mutableStateOf(CustomTab.BLOUSE) }
    var neckBack by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(SketchView.BOTH) }
    var frontDepth by rememberSaveable { mutableStateOf(NeckDepth.REGULAR) }
    var backDepth by rememberSaveable { mutableStateOf(NeckDepth.REGULAR) }

    Scaffold(
        topBar = { AppBar(tr("custom.title"), onBack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onNext(model.id) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Aubergine, contentColor = Brand.Ivory),
                ) { Text(tr("mix.measure"), style = MaterialTheme.typography.labelLarge) }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            full {
                SketchPanel(model, view) { view = it }
            }
            full {
                // Breadcrumb like "3 Dart · Elbow sleeve · Round front · U back".
                Text(model.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Plum)
            }
            full {
                TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    CustomTab.entries.forEach { t ->
                        Tab(selected = tab == t, onClick = { tab = t }, text = { Text(tr("custom.tab.${t.name.lowercase()}")) })
                    }
                }
            }
            when (tab) {
                CustomTab.BLOUSE -> {
                    items(BodyStyle.entries, key = { "body" + it.name }) { b ->
                        val option = remember(spec, b) { spec.copy(body = b).toModel() }
                        OptionTile(b.label, spec.body == b, onClick = { vm.spec = spec.copy(body = b) }) {
                            BlouseSketch(option, back = false, modifier = Modifier.fillMaxSize())
                        }
                    }
                    full {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tr("mix.hooks"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Opening.entries.forEach { o ->
                                    FilterChip(
                                        selected = spec.effectiveOpening == o,
                                        enabled = spec.backDetail == com.tailorsfit.pattern.blouse.BackDetail.NONE,
                                        onClick = { vm.spec = spec.copy(opening = o) },
                                        label = { Text(o.label) },
                                    )
                                }
                            }
                        }
                    }
                }
                CustomTab.SLEEVE -> items(SleeveStyle.entries, key = { "sleeve" + it.name }) { s ->
                    val option = remember(spec, s) { spec.copy(sleeve = s).toModel() }
                    OptionTile(s.label, spec.sleeve == s, onClick = { vm.spec = spec.copy(sleeve = s) }) {
                        BlouseSketch(option, back = false, modifier = Modifier.fillMaxSize())
                    }
                }
                CustomTab.NECK -> {
                    full {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf(false to tr("custom.neck.front"), true to tr("custom.neck.back")).forEachIndexed { i, (isBack, label) ->
                                    SegmentedButton(
                                        selected = neckBack == isBack,
                                        onClick = { neckBack = isBack; view = if (isBack) SketchView.BACK else SketchView.FRONT },
                                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                                    ) { Text(label) }
                                }
                            }
                            Text(tr("mix.depth"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                            val depth = if (neckBack) backDepth else frontDepth
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NeckDepth.entries.forEach { d ->
                                    FilterChip(selected = d == depth, onClick = {
                                        // Re-apply the current neck shape at the new depth.
                                        if (neckBack) {
                                            backDepth = d
                                            currentBack(spec)?.let { vm.spec = spec.withBack(it, d) }
                                        } else {
                                            frontDepth = d
                                            currentFront(spec)?.let { vm.spec = spec.withFront(it, d) }
                                        }
                                    }, label = { Text(d.label) })
                                }
                            }
                        }
                    }
                    if (neckBack) {
                        items(BackNeck.entries, key = { "back" + it.name }) { n ->
                            val option = remember(spec, n, backDepth) { spec.withBack(n, backDepth).toModel() }
                            OptionTile(n.label, currentBack(spec) == n, onClick = { vm.spec = spec.withBack(n, backDepth) }) {
                                BlouseSketch(option, back = true, modifier = Modifier.fillMaxSize())
                            }
                        }
                        if (spec.backDetail != com.tailorsfit.pattern.blouse.BackDetail.NONE) full {
                            Text(tr("mix.front_hooks_needed", spec.backDetail.label), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                        }
                    } else {
                        items(FrontNeck.entries, key = { "front" + it.name }) { n ->
                            val option = remember(spec, n, frontDepth) { spec.withFront(n, frontDepth).toModel() }
                            OptionTile(n.label, currentFront(spec) == n, onClick = { vm.spec = spec.withFront(n, frontDepth) }) {
                                BlouseSketch(option, back = false, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.full(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** The front neck choice matching the design's current front neck, if any. */
private fun currentFront(spec: BlouseSpec) = FrontNeck.entries.firstOrNull { it.shape == spec.front.shape }

/** The back neck choice matching the design's current back (shape and keyhole / dori). */
private fun currentBack(spec: BlouseSpec) = BackNeck.entries.firstOrNull { it.detail == spec.backDetail && it.shape == spec.back.shape }
    ?: BackNeck.entries.firstOrNull { it.shape == spec.back.shape && it.detail == com.tailorsfit.pattern.blouse.BackDetail.NONE }

/** Front and back sketch of the design, or just one side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SketchPanel(model: BlouseModel, view: SketchView, onView: (SketchView) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Brand.Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SketchView.entries.forEachIndexed { i, v ->
                    SegmentedButton(selected = view == v, onClick = { onView(v) }, shape = SegmentedButtonDefaults.itemShape(i, SketchView.entries.size)) {
                        Text(tr("custom.view.${v.name.lowercase()}"))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Parchment).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (view != SketchView.BACK) Labeled(tr("view.front"), Modifier.weight(1f)) { BlouseSketch(model, back = false, modifier = Modifier.fillMaxSize()) }
                if (view != SketchView.FRONT) Labeled(tr("view.back"), Modifier.weight(1f)) { BlouseSketch(model, back = true, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun Labeled(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Brand.Plum)
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun OptionTile(label: String, selected: Boolean, onClick: () -> Unit, sketch: @Composable () -> Unit) {
    Surface(
        color = if (selected) Brand.Aubergine else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Brand.Gold else Brand.Line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box {
            Column {
                Box(Modifier.fillMaxWidth().height(86.dp).padding(6.dp).clip(RoundedCornerShape(10.dp)).background(Brand.Parchment).padding(4.dp)) { sketch() }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Brand.Ivory else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(Brand.Gold),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Brand.AubergineDeep, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
