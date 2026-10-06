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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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

/** The customisation steps, one window each, in the order a tailor decides them. */
private enum class Step { FRONT, BACK, SLEEVE, BLOUSE }
private enum class SketchView { BOTH, FRONT, BACK }

/**
 * Customise a design before measuring, one step at a time: front neck, then back neck, then
 * sleeves, then the blouse details (type, hooks, patti, net ...), with a sketch that follows
 * every choice. Choices live in [AppViewModel.spec]; the last step opens the measurements.
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
    var step by rememberSaveable { mutableStateOf(Step.FRONT) }
    var view by rememberSaveable { mutableStateOf(SketchView.FRONT) }
    var frontDepth by rememberSaveable { mutableStateOf(NeckDepth.REGULAR) }
    var backDepth by rememberSaveable { mutableStateOf(NeckDepth.REGULAR) }
    fun go(to: Step) {
        step = to
        view = when (to) {
            Step.FRONT -> SketchView.FRONT
            Step.BACK -> SketchView.BACK
            else -> SketchView.BOTH
        }
    }
    val back = { if (step.ordinal > 0) go(Step.entries[step.ordinal - 1]) else onBack() }
    androidx.activity.compose.BackHandler(enabled = step.ordinal > 0) { back() }

    Scaffold(
        topBar = { AppBar(tr("custom.title"), back) },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (step.ordinal > 0) {
                        OutlinedButton(onClick = back, modifier = Modifier.weight(1f).height(50.dp)) {
                            Text(tr("custom.prev"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    val last = step == Step.entries.last()
                    Button(
                        onClick = { if (last) onNext(model.id) else go(Step.entries[step.ordinal + 1]) },
                        modifier = Modifier.weight(2f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Aubergine, contentColor = Brand.Ivory),
                    ) {
                        Text(
                            if (last) tr("mix.measure") else tr("custom.next", tr("custom.step.${Step.entries[step.ordinal + 1].name.lowercase()}")),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
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
            full { StepBar(step, ::go) }
            full {
                SketchPanel(model, view) { view = it }
            }
            full {
                // Breadcrumb like "3 Dart · Elbow sleeve · Round front · U back".
                Text(model.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Plum)
            }
            when (step) {
                Step.FRONT, Step.BACK -> {
                    val neckBack = step == Step.BACK
                    full {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Step.SLEEVE -> items(SleeveStyle.entries, key = { "sleeve" + it.name }) { s ->
                    val option = remember(spec, s) { spec.copy(sleeve = s).toModel() }
                    OptionTile(s.label, spec.sleeve == s, onClick = { vm.spec = spec.copy(sleeve = s) }) {
                        BlouseSketch(option, back = false, modifier = Modifier.fillMaxSize())
                    }
                }
                Step.BLOUSE -> {
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
                            Text(tr("mix.style"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = spec.halter,
                                    onClick = { vm.spec = spec.copy(halter = !spec.halter) },
                                    label = { Text(tr("tag.halter")) },
                                )
                                FilterChip(
                                    selected = spec.bottomWaves,
                                    enabled = !spec.body.panelled,
                                    onClick = { vm.spec = spec.copy(bottomWaves = !spec.bottomWaves) },
                                    label = { Text(tr("tag.waves")) },
                                )
                                FilterChip(
                                    selected = spec.patti,
                                    enabled = !spec.body.belted,
                                    onClick = { vm.spec = spec.copy(patti = !spec.patti) },
                                    label = { Text(tr("mix.patti")) },
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = spec.bottomCurve,
                                    enabled = !spec.body.belted,
                                    onClick = { vm.spec = spec.copy(bottomCurve = !spec.bottomCurve) },
                                    label = { Text(tr("tag.curve")) },
                                )
                                if (spec.body == BodyStyle.PRINCESS) {
                                    FilterChip(
                                        selected = spec.shoulderPrincess,
                                        onClick = { vm.spec = spec.copy(shoulderPrincess = !spec.shoulderPrincess) },
                                        label = { Text(tr("tag.shoulder_cut")) },
                                    )
                                }
                            }
                            Text(tr("mix.back_yoke"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.tailorsfit.pattern.blouse.YokeShape.entries.forEach { y ->
                                    FilterChip(
                                        selected = spec.backYoke == y,
                                        enabled = spec.backDetail != com.tailorsfit.pattern.blouse.BackDetail.KEYHOLE,
                                        onClick = { vm.spec = spec.copy(backYoke = y) },
                                        label = { Text(y.label) },
                                    )
                                }
                            }
                            Text(tr("mix.front_insert"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                com.tailorsfit.pattern.blouse.YokeShape.entries.forEach { y ->
                                    FilterChip(
                                        selected = spec.frontInsert == y,
                                        onClick = { vm.spec = spec.copy(frontInsert = y) },
                                        label = { Text(y.label) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Numbered steps; a step can be tapped to go straight to it. */
@Composable
private fun StepBar(step: Step, onStep: (Step) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Step.entries.forEachIndexed { i, s ->
            val current = s == step
            val done = s.ordinal < step.ordinal
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable { onStep(s) }
                    .background(if (current) Brand.Aubergine else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (current || done) Brand.Gold else Brand.Line),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Brand.AubergineDeep, modifier = Modifier.size(14.dp))
                    else Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = Brand.AubergineDeep)
                }
                Text(
                    tr("custom.step.${s.name.lowercase()}"),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (current) Brand.Ivory else MaterialTheme.colorScheme.onSurface,
                )
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
