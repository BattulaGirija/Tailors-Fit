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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.tailorsfit.pattern.blouse.BlouseMix
import com.tailorsfit.pattern.blouse.FrontNeck
import com.tailorsfit.pattern.blouse.NeckDepth
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle

/*
 * "Design your own": the front neck, the back neck and the sleeves are chosen on three separate
 * pages, the way a customer orders a blouse. The choices live in AppViewModel.mix; the result is
 * an ordinary design id (BlouseMix.id) that the measurement and pattern screens open.
 */

private const val STEPS = 3

/** Page 1: front neck shape, depth and princess cut. */
@Composable
fun MixFrontScreen(vm: AppViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val mix = vm.mix
    MixPage(1, tr("mix.front.title"), tr("mix.front.text"), onBack, tr("mix.next"), onNext) {
        // First the kind of front: normal (shaped with darts) or princess cut (curved seams).
        controls {
            SectionTitle(tr("mix.cut.title"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((princess, label) in listOf(false to tr("mix.cut.normal"), true to tr("mix.cut.princess"))) {
                    Box(Modifier.weight(1f)) {
                        OptionCard(label, mix.princess == princess, onClick = { vm.mix = mix.copy(princess = princess) }) {
                            Sketch(mix.copy(princess = princess), back = false)
                        }
                    }
                }
            }
            SectionTitle(tr("mix.neck_shape"))
        }
        options(FrontNeck.entries) { option ->
            OptionCard(option.label, option == mix.front, onClick = { vm.mix = mix.copy(front = option) }) {
                Sketch(mix.copy(front = option), back = false)
            }
        }
        controls {
            DepthChips(mix.frontDepth) { vm.mix = mix.copy(frontDepth = it) }
        }
    }
}

/** Page 2: back neck (including keyhole and dori), depth and where the hooks go. */
@Composable
fun MixBackScreen(vm: AppViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val mix = vm.mix
    MixPage(2, tr("mix.back.title"), tr("mix.back.text"), onBack, tr("mix.next"), onNext) {
        options(BackNeck.entries) { option ->
            OptionCard(option.label, option == mix.back, onClick = { vm.mix = mix.copy(back = option) }) {
                Sketch(mix.copy(back = option), back = true)
            }
        }
        controls {
            if (mix.back.hasDepthChoice) DepthChips(mix.backDepth) { vm.mix = mix.copy(backDepth = it) }
            Text(tr("mix.hooks"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Opening.entries.forEach { o ->
                    FilterChip(
                        selected = mix.effectiveOpening == o,
                        enabled = !mix.back.needsFrontOpening,
                        onClick = { vm.mix = mix.copy(opening = o) },
                        label = { Text(o.label) },
                    )
                }
            }
            if (mix.back.needsFrontOpening) {
                Text(tr("mix.front_hooks_needed", mix.back.label), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
            }
        }
    }
}

/** Page 3: sleeves, then on to measurements. */
@Composable
fun MixSleeveScreen(vm: AppViewModel, onBack: () -> Unit, onDone: (String) -> Unit) {
    val mix = vm.mix
    MixPage(3, tr("mix.sleeve.title"), tr("mix.sleeve.text"), onBack, tr("mix.measure"), onNext = { onDone(mix.id) }) {
        options(SleeveStyle.entries) { option ->
            OptionCard(option.label, option == mix.sleeve, onClick = { vm.mix = mix.copy(sleeve = option) }) {
                Sketch(mix.copy(sleeve = option), back = false)
            }
        }
        controls {
            // The whole blouse as chosen, front and back.
            Surface(color = Brand.Aubergine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("mix.your_design"), style = MaterialTheme.typography.labelLarge, color = Brand.GoldLight)
                    val model = remember(mix) { mix.toModel() }
                    Text(model.name, style = MaterialTheme.typography.titleMedium, color = Brand.Ivory)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brand.Parchment).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BlouseSketch(model, back = false, modifier = Modifier.weight(1f).height(110.dp))
                        BlouseSketch(model, back = true, modifier = Modifier.weight(1f).height(110.dp))
                    }
                    Text(model.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Brand.Ivory.copy(alpha = 0.85f))
                }
            }
        }
    }
}

/** The pieces a page puts in its grid: option cards and full-width rows of controls. */
private class MixPageScope(val grid: LazyGridScope) {
    fun <T : Enum<T>> options(items: List<T>, card: @Composable (T) -> Unit) {
        grid.items(items, key = { it.name }) { card(it) }
    }

    fun controls(content: @Composable () -> Unit) {
        grid.item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun MixPage(
    step: Int,
    title: String,
    text: String,
    onBack: () -> Unit,
    nextLabel: String,
    onNext: () -> Unit,
    content: MixPageScope.() -> Unit,
) {
    Scaffold(
        topBar = { AppBar(title, onBack) },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Aubergine, contentColor = Brand.Ivory),
                ) { Text(nextLabel, style = MaterialTheme.typography.labelLarge) }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StepHeader(step, text) }
            MixPageScope(this).content()
        }
    }
}

/** "Step 2 of 3" with three dots, and what to do on this page. */
@Composable
private fun StepHeader(step: Int, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..STEPS) {
                Box(
                    Modifier.size(if (i == step) 22.dp else 10.dp, 10.dp).clip(CircleShape)
                        .background(if (i <= step) Brand.Gold else Brand.Line),
                )
            }
            Text("  " + tr("mix.step", step, STEPS), style = MaterialTheme.typography.labelLarge, color = Brand.Plum)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionCard(label: String, selected: Boolean, onClick: () -> Unit, sketch: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) Brand.Gold else Brand.Line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Box {
            Column {
                Box(Modifier.fillMaxWidth().height(130.dp).background(Brand.Parchment).padding(10.dp)) { sketch() }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) Brand.Plum else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                )
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape).background(Brand.Gold),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Brand.AubergineDeep, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun Sketch(mix: BlouseMix, back: Boolean) {
    val model = remember(mix) { mix.toModel() }
    BlouseSketch(model, back, Modifier.fillMaxSize())
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun DepthChips(selected: NeckDepth, onPick: (NeckDepth) -> Unit) {
    Text(tr("mix.depth"), style = MaterialTheme.typography.labelLarge, color = Brand.Muted)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeckDepth.entries.forEach { d -> FilterChip(selected = d == selected, onClick = { onPick(d) }, label = { Text(d.label) }) }
    }
}
