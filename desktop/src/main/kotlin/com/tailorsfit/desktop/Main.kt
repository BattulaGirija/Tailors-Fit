package com.tailorsfit.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tailorsfit.pattern.blouse.BackNeck
import com.tailorsfit.pattern.blouse.BlouseModel
import com.tailorsfit.pattern.blouse.BlouseSpec
import com.tailorsfit.pattern.blouse.BodyStyle
import com.tailorsfit.pattern.blouse.FrontNeck
import com.tailorsfit.pattern.blouse.InvalidMeasurementsException
import com.tailorsfit.pattern.blouse.NeckDepth
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.blouse.YokeShape
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.i18n.I18n
import com.tailorsfit.pattern.i18n.Language
import com.tailorsfit.pattern.i18n.tr
import com.tailorsfit.pattern.kurti.KurtiModel
import com.tailorsfit.pattern.layout.Layout
import com.tailorsfit.pattern.layout.LayoutEngine
import com.tailorsfit.pattern.layout.LayoutOptions
import com.tailorsfit.pattern.layout.PlacedPiece
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.DraftOptions
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.LengthUnit
import com.tailorsfit.pattern.model.Lengths
import com.tailorsfit.pattern.model.MeasurementField
import com.tailorsfit.pattern.model.Measurements
import com.tailorsfit.pattern.model.Pattern
import com.tailorsfit.pattern.model.SeamAllowances
import com.tailorsfit.pattern.model.SizePreset
import com.tailorsfit.pattern.render.Illustration
import com.tailorsfit.pattern.render.PaintOptions
import com.tailorsfit.pattern.render.PatternPainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

fun main() = application {
    val state = remember { AppState() }
    Window(onCloseRequest = ::exitApplication, title = "Tailors Fit", state = rememberWindowState(width = 1380.dp, height = 880.dp)) {
        TailorsFitTheme {
            // Rebuilds every text when the language changes.
            key(state.language) { App(state) }
        }
    }
    if (state.projecting) ProjectorWindow(state)
}

/** Everything the screens share. */
class AppState {
    var language by mutableStateOf(Language.fromCode(Store.setting("language")) ?: Language.EN)
    var unit by mutableStateOf(if (Store.setting("unit") == "cm") LengthUnit.CM else LengthUnit.INCH)
    var category by mutableStateOf("blouse")
    var group by mutableStateOf(0)
    var query by mutableStateOf("")
    var modelId by mutableStateOf<String?>(null)
    var spec by mutableStateOf<BlouseSpec?>(null)
    val inputs = mutableStateMapOf<MeasurementField, String>()
    var customer by mutableStateOf("")
    var phone by mutableStateOf("")
    var helpField by mutableStateOf<MeasurementField?>(null)
    var message by mutableStateOf<String?>(null)

    // Cut layout.
    var clothWidth by mutableStateOf(110.0)
    var folded by mutableStateOf(true)
    var allowance by mutableStateOf(true)
    var oneWay by mutableStateOf(false)
    var layout by mutableStateOf<Layout?>(null)

    // Projector.
    var projecting by mutableStateOf(false)
    var projectorScreen by mutableStateOf(if (screens().size > 1) 1 else 0)
    var pxPerCm by mutableStateOf(Store.setting("pxPerCm")?.toFloatOrNull() ?: 12f)
    var lineWidth by mutableStateOf(3f)
    var panX by mutableStateOf(-2.0)
    var panY by mutableStateOf(-2.0)
    var projectorGrid by mutableStateOf(false)
    var showTestSquare by mutableStateOf(false)

    init {
        I18n.language = language
        Lengths.unit = unit
        fill(SizePreset.M.measurements())
    }

    fun chooseLanguage(l: Language) { language = l; I18n.language = l; Store.setSetting("language", l.code) }

    fun chooseUnit(u: LengthUnit) {
        val cm = measurements()
        unit = u
        Lengths.unit = u
        Store.setSetting("unit", if (u == LengthUnit.CM) "cm" else "in")
        fill(cm)
    }

    fun fill(m: Measurements) {
        inputs.clear()
        for ((f, v) in m.asMap()) if (!v.isNaN()) inputs[f] = fmt(unit.fromCm(v))
    }

    private fun fmt(v: Double) = if (unit == LengthUnit.INCH) {
        val q = Math.round(v * 4) / 4.0
        if (q == Math.floor(q)) q.toLong().toString() else q.toString()
    } else String.format(java.util.Locale.US, "%.1f", v).removeSuffix(".0")

    fun measurements() = Measurements(
        inputs.mapValues { (_, t) -> t.replace(',', '.').trim().toDoubleOrNull()?.let { unit.toCm(it) } ?: Double.NaN },
    )

    fun open(model: GarmentModel) {
        modelId = model.id
        spec = (model as? BlouseModel)?.let { BlouseSpec.of(it) }
        layout = null
    }

    /** The design being worked on, with the tailor's blouse choices applied. */
    fun model(): GarmentModel? = spec?.toModel() ?: modelId?.let { Catalog.model(it) }
}

@Composable
fun App(state: AppState) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(state)
        val model = state.model()
        if (model == null) CatalogView(state) else Workbench(state, model)
    }
    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { state.message = null },
            confirmButton = { TextButton(onClick = { state.message = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun TopBar(state: AppState) {
    var langMenu by remember { mutableStateOf(false) }
    var customers by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Brand.Aubergine).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Tailors Fit", color = Brand.GoldLight, fontFamily = FontFamily.Serif, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        for (c in Catalog.categories.filter { it.available }) {
            val selected = state.category == c.id && state.modelId == null
            TextButton(onClick = { state.category = c.id; state.group = 0; state.query = ""; state.modelId = null; state.spec = null }) {
                Text(c.name, color = if (selected) Brand.GoldLight else Brand.Ivory, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { customers = true }) { Text(tr("desktop.customers"), color = Brand.Ivory) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (u in LengthUnit.entries) {
                FilterChip(selected = state.unit == u, onClick = { state.chooseUnit(u) }, label = { Text(if (u == LengthUnit.INCH) tr("measure.inch") else tr("measure.cm"), color = if (state.unit == u) Brand.Ink else Brand.Ivory) })
                Spacer(Modifier.width(4.dp))
            }
        }
        Box {
            TextButton(onClick = { langMenu = true }) { Text(state.language.nativeName, color = Brand.Ivory) }
            DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                for (l in Language.entries) DropdownMenuItem(text = { Text(l.nativeName) }, onClick = { langMenu = false; state.chooseLanguage(l) })
            }
        }
    }
    if (customers) CustomersDialog(state) { customers = false }
}

@Composable
private fun CustomersDialog(state: AppState, onClose: () -> Unit) {
    var list by remember { mutableStateOf(Store.list()) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        title = { Text(tr("desktop.customers")) },
        text = {
            Column(Modifier.width(420.dp).height(380.dp).verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) Text(tr("desktop.none"), color = Brand.Muted)
                for (c in list) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            state.customer = c.name
                            state.phone = c.phone
                            state.fill(Measurements(c.measurements))
                            onClose()
                        }) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            if (c.phone.isNotBlank()) Text(c.phone, color = Brand.Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { Store.delete(c.name); list = Store.list() }) { Text(tr("desktop.delete")) }
                    }
                    HorizontalDivider(color = Brand.Line)
                }
            }
        },
    )
}

/** Sketch measurements: a standard size with the design's usual sleeve length. */
private fun sketchOf(model: GarmentModel): List<com.tailorsfit.pattern.render.GarmentView> {
    val sleeve = (model as? BlouseModel)?.sleeve ?: (model as? KurtiModel)?.sleeve
    val m = sleeve?.sketchMeasurements() ?: Measurements.defaults()
    return runCatching { Illustration.of(model.draft(m)) }.getOrElse { emptyList() }
}

@Composable
private fun Sketch(views: List<com.tailorsfit.pattern.render.GarmentView>, modifier: Modifier, cloth: Color = Color(0xFF7B1E2B)) {
    Canvas(modifier) {
        if (views.isEmpty()) return@Canvas
        val w = size.width / views.size
        views.forEachIndexed { i, v -> drawGarment(v, cloth, Rect(Offset(i * w, 0f), androidx.compose.ui.geometry.Size(w, size.height))) }
    }
}

@Composable
private fun CatalogView(state: AppState) {
    val all = Catalog.modelsIn(state.category)
    val groups = all.map { it.group }.filter { it.isNotBlank() }.distinct()
    val shown = all
        .filter { state.group == 0 || it.group == groups.getOrNull(state.group - 1) }
        .filter { m ->
            val words = state.query.lowercase().split(' ').filter { it.isNotBlank() }
            val text = (listOf(m.name, m.description) + m.tags).joinToString(" ").lowercase()
            words.all { it in text }
        }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (groups.size > 1) {
                ScrollableTabRow(selectedTabIndex = state.group, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.weight(1f)) {
                    Tab(selected = state.group == 0, onClick = { state.group = 0 }, text = { Text(tr("cat.all")) })
                    groups.forEachIndexed { i, g -> Tab(selected = state.group == i + 1, onClick = { state.group = i + 1 }, text = { Text(g) }) }
                }
            } else Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = state.query, onValueChange = { state.query = it }, singleLine = true,
                placeholder = { Text(tr("catalog.search")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.width(320.dp),
            )
        }
        if (shown.isEmpty()) Text(tr("catalog.none"), color = Brand.Muted, modifier = Modifier.padding(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shown, key = { it.id }) { model ->
                val views = remember(model.id) { sketchOf(model) }
                Surface(
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Brand.Line), color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable { state.open(model) },
                ) {
                    Column {
                        Sketch(views, Modifier.fillMaxWidth().height(140.dp).background(Brand.Parchment).padding(8.dp))
                        Column(Modifier.padding(12.dp)) {
                            if (model.group.isNotBlank()) Text(model.group.uppercase(), color = Brand.Gold, fontSize = 11.sp)
                            Text(model.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(model.tags.drop(1).joinToString(" · "), color = Brand.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, color = Brand.Plum, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }

@Composable
private fun Workbench(state: AppState, model: GarmentModel) {
    val measurements = state.measurements()
    val draft = remember(model.id, measurements.asMap(), state.customer, state.language, state.unit) {
        try {
            Result.success(model.draft(measurements, DraftOptions(customerName = state.customer)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val pattern = draft.getOrNull()
    val errors = (draft.exceptionOrNull() as? InvalidMeasurementsException)?.errors ?: emptyMap()
    LaunchedEffect(pattern, state.clothWidth, state.folded, state.allowance, state.oneWay) {
        state.layout = null
        if (pattern != null) {
            state.layout = withContext(Dispatchers.Default) {
                LayoutEngine.layout(
                    pattern,
                    LayoutOptions(
                        fabricWidth = state.clothWidth, folded = state.folded,
                        allowances = if (state.allowance) SeamAllowances() else SeamAllowances.NONE,
                        allowTurning = !state.oneWay,
                    ),
                )
            }
        }
    }
    Row(Modifier.fillMaxSize()) {
        DesignPanel(state, model)
        MeasurementPanel(state, model, errors)
        PatternPanel(state, model, pattern)
    }
}

@Composable
private fun DesignPanel(state: AppState, model: GarmentModel) {
    Column(Modifier.width(330.dp).fillMaxHeight().background(Color.White).verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = { state.modelId = null; state.spec = null }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Text(" " + tr("desktop.designs"))
        }
        Text(model.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Brand.Aubergine)
        Text(model.tags.joinToString(" · "), color = Brand.Muted, fontSize = 12.sp)
        if (model.description.isNotBlank()) Text(model.description, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        val spec = state.spec ?: return@Column
        fun set(s: BlouseSpec) { state.spec = s }
        val front = FrontNeck.entries.firstOrNull { it.shape == spec.front.shape }
        val back = BackNeck.entries.firstOrNull { it.detail == spec.backDetail && it.shape == spec.back.shape }
            ?: BackNeck.entries.firstOrNull { it.shape == spec.back.shape }

        SectionTitle(tr("custom.step.front"))
        Chips { FrontNeck.entries.forEach { n -> FilterChip(selected = front == n, onClick = { set(spec.withFront(n, NeckDepth.REGULAR)) }, label = { Text(n.label) }) } }
        Chips { NeckDepth.entries.forEach { d -> FilterChip(selected = false, onClick = { front?.let { set(spec.withFront(it, d)) } }, label = { Text(d.label) }) } }

        SectionTitle(tr("custom.step.back"))
        Chips { BackNeck.entries.forEach { n -> FilterChip(selected = back == n, onClick = { set(spec.withBack(n, NeckDepth.REGULAR)) }, label = { Text(n.label) }) } }
        Chips { NeckDepth.entries.forEach { d -> FilterChip(selected = false, onClick = { back?.let { set(spec.withBack(it, d)) } }, label = { Text(d.label) }) } }

        SectionTitle(tr("custom.step.sleeve"))
        Chips { SleeveStyle.entries.forEach { s -> FilterChip(selected = spec.sleeve == s, onClick = { set(spec.copy(sleeve = s)) }, label = { Text(s.label) }) } }

        SectionTitle(tr("custom.step.blouse"))
        Chips { BodyStyle.entries.forEach { b -> FilterChip(selected = spec.body == b, onClick = { set(spec.copy(body = b)) }, label = { Text(b.label) }) } }
        Text(tr("mix.hooks"), color = Brand.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Chips { Opening.entries.forEach { o -> FilterChip(selected = spec.effectiveOpening == o, onClick = { set(spec.copy(opening = o)) }, label = { Text(o.label) }) } }
        Text(tr("mix.style"), color = Brand.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Chips {
            FilterChip(selected = spec.halter, onClick = { set(spec.copy(halter = !spec.halter)) }, label = { Text(tr("tag.halter")) })
            FilterChip(selected = spec.bottomWaves, onClick = { set(spec.copy(bottomWaves = !spec.bottomWaves)) }, label = { Text(tr("tag.waves")) })
            FilterChip(selected = spec.patti, onClick = { set(spec.copy(patti = !spec.patti)) }, label = { Text(tr("mix.patti")) })
            FilterChip(selected = spec.bottomCurve, onClick = { set(spec.copy(bottomCurve = !spec.bottomCurve)) }, label = { Text(tr("tag.curve")) })
            if (spec.body == BodyStyle.PRINCESS) FilterChip(selected = spec.shoulderPrincess, onClick = { set(spec.copy(shoulderPrincess = !spec.shoulderPrincess)) }, label = { Text(tr("tag.shoulder_cut")) })
        }
        Text(tr("mix.back_yoke"), color = Brand.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Chips { YokeShape.entries.forEach { y -> FilterChip(selected = spec.backYoke == y, onClick = { set(spec.copy(backYoke = y)) }, label = { Text(y.label) }) } }
        Text(tr("mix.front_insert"), color = Brand.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Chips { YokeShape.entries.forEach { y -> FilterChip(selected = spec.frontInsert == y, onClick = { set(spec.copy(frontInsert = y)) }, label = { Text(y.label) }) } }
    }
}

@Composable
private fun MeasurementPanel(state: AppState, model: GarmentModel, errors: Map<MeasurementField, String>) {
    var showAdjust by remember { mutableStateOf(false) }
    Column(Modifier.width(380.dp).fillMaxHeight().background(Brand.Parchment).verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionTitle(tr("measure.customer"))
        OutlinedTextField(state.customer, { state.customer = it }, label = { Text(tr("measure.name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.phone, { state.phone = it }, label = { Text(tr("measure.phone")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = {
                if (state.customer.isBlank()) state.message = tr("measure.name")
                else {
                    Store.save(Store.Customer(state.customer.trim(), state.phone.trim(), state.measurements().asMap()))
                    state.message = tr("measure.saved", state.customer.trim())
                }
            }) { Text(tr("measure.save")) }
        }
        SectionTitle(tr("measure.start_size"))
        Chips { SizePreset.entries.forEach { p -> FilterChip(selected = false, onClick = { state.fill(p.measurements()) }, label = { Text(p.label) }) } }

        SectionTitle(tr("measure.title", state.unit.label))
        for (f in model.requiredMeasurements) MeasureRow(state, f, errors[f], placeholder = null)
        if (model is BlouseModel) MeasureRow(state, MeasurementField.NECK_BROAD, null, placeholder = tr("measure.auto"))

        if (model is BlouseModel) {
            TextButton(onClick = { showAdjust = !showAdjust }) { Text((if (showAdjust) "▾ " else "▸ ") + tr("measure.adjust.title")) }
            if (showAdjust) for (f in MeasurementField.adjustments - MeasurementField.NECK_BROAD) MeasureRow(state, f, null, placeholder = tr("measure.auto"))
        }
        state.helpField?.let { f ->
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Brand.Line), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(f.label, fontWeight = FontWeight.SemiBold, color = Brand.Aubergine)
                    if (hasMeasureFigure(f)) MeasureFigure(f, Modifier.fillMaxWidth().height(180.dp))
                    Text(f.help, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MeasureRow(state: AppState, f: MeasurementField, error: String?, placeholder: String?) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.label, modifier = Modifier.weight(1f), fontSize = 14.sp)
            OutlinedTextField(
                value = state.inputs[f] ?: "",
                onValueChange = { state.inputs[f] = it },
                singleLine = true,
                isError = error != null,
                placeholder = placeholder?.let { { Text(it, fontSize = 12.sp) } },
                modifier = Modifier.width(96.dp),
            )
            IconButton(onClick = { state.helpField = f }) { Icon(Icons.Filled.Info, null, tint = Brand.Gold) }
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

private enum class View { SKETCH, PIECES, LAYOUT }

@Composable
private fun PatternPanel(state: AppState, model: GarmentModel, pattern: Pattern?) {
    var view by remember { mutableStateOf(View.SKETCH) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            View.entries.forEach { v ->
                FilterChip(selected = view == v, onClick = { view = v }, label = {
                    Text(when (v) { View.SKETCH -> tr("desktop.sketch"); View.PIECES -> tr("pattern.pieces"); View.LAYOUT -> tr("desktop.layout") })
                })
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            val layout = state.layout
            OutlinedButton(enabled = pattern != null && layout != null, onClick = {
                if (pattern != null && layout != null) runCatching { Output.print(pattern, layout, PaintOptions(showAllowance = state.allowance, caption = listOf(state.customer).filter { it.isNotBlank() })) }
                    .onFailure { state.message = tr("pattern.export_failed", it.message ?: "") }
            }) { Text(tr("pattern.print")) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(enabled = pattern != null && layout != null, onClick = {
                if (pattern != null && layout != null) runCatching { Output.saveSvg(pattern, layout, PaintOptions(showAllowance = state.allowance)) }
                    .onSuccess { f -> if (f != null) state.message = tr("desktop.saved_to", f.absolutePath) }
                    .onFailure { state.message = tr("pattern.export_failed", it.message ?: "") }
            }) { Text(tr("pattern.svg")) }
            Spacer(Modifier.width(6.dp))
            Button(
                enabled = layout != null,
                onClick = { state.projecting = !state.projecting },
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Aubergine),
            ) { Text(if (state.projecting) tr("desktop.stop") else tr("pattern.project")) }
        }
        Text(tr("desktop.pdf_hint"), color = Brand.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        if (state.projecting) ProjectorControls(state)
        if (pattern == null) {
            Text(tr("projector.fix"), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            return@Column
        }
        when (view) {
            View.SKETCH -> SketchView(pattern)
            View.PIECES -> PiecesView(state, pattern)
            View.LAYOUT -> LayoutView(state)
        }
    }
}

@Composable
private fun SketchView(pattern: Pattern) {
    val views = remember(pattern) { runCatching { Illustration.of(pattern) }.getOrElse { emptyList() } }
    Row(Modifier.fillMaxSize()) {
        Sketch(views, Modifier.weight(1.4f).fillMaxHeight().background(Brand.Parchment, RoundedCornerShape(16.dp)).padding(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp)) {
            Text(pattern.title, fontWeight = FontWeight.SemiBold, color = Brand.Aubergine)
            SectionTitle(tr("pattern.details"))
            for ((k, v) in pattern.summary) Row { Text(k, Modifier.weight(1f), fontSize = 13.sp); Text(v, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            if (pattern.warnings.isNotEmpty()) {
                SectionTitle(tr("pattern.warnings"))
                for (w in pattern.warnings) Text("• $w", fontSize = 13.sp, color = Color(0xFF8A4B00))
            }
        }
    }
}

@Composable
private fun PiecesView(state: AppState, pattern: Pattern) {
    val allowances = if (state.allowance) SeamAllowances() else SeamAllowances.NONE
    val measurer = rememberTextMeasurer()
    LazyVerticalGrid(columns = GridCells.Adaptive(300.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(pattern.pieces, key = { it.id }) { piece ->
            Surface(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Brand.Line), color = Color.White) {
                Column(Modifier.padding(10.dp)) {
                    Text(piece.name, fontWeight = FontWeight.SemiBold, color = Brand.Aubergine)
                    Text(piece.cut.text, color = Brand.Gold, fontSize = 12.sp)
                    Canvas(Modifier.fillMaxWidth().height(300.dp).clipToBounds()) {
                        val b = piece.bounds(allowances)
                        val k = (min(size.width / b.width, size.height / b.height) * 0.92).toFloat()
                        val off = Offset((size.width - (b.width * k).toFloat()) / 2, (size.height - (b.height * k).toFloat()) / 2)
                        // Inch (or 5 cm) grid behind the piece.
                        val step = if (state.unit == LengthUnit.INCH) 2.54 else 5.0
                        var x = Math.floor(b.minX / step) * step
                        while (x <= b.maxX) {
                            val px = off.x + ((x - b.minX) * k).toFloat()
                            drawLine(Brand.Line, Offset(px, 0f), Offset(px, size.height), 1f)
                            x += step
                        }
                        var y = Math.floor(b.minY / step) * step
                        while (y <= b.maxY) {
                            val py = off.y + ((y - b.minY) * k).toFloat()
                            drawLine(Brand.Line, Offset(0f, py), Offset(size.width, py), 1f)
                            y += step
                        }
                        val sink = ComposeSink(this, measurer, k, Pt(b.minX, b.minY), off, LineStyle.screen)
                        PatternPainter.paintPiece(PlacedPiece(piece, Pt.ZERO), allowances, sink, PaintOptions(showAllowance = state.allowance, showLabels = false))
                    }
                    for (n in piece.notes) Text("• $n", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LayoutView(state: AppState) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (w in listOf(90.0, 110.0, 140.0)) {
                FilterChip(selected = state.clothWidth == w, onClick = { state.clothWidth = w }, label = { Text(tr("pattern.width", "${Lengths.inches(w)} (${w.toInt()} cm)")) })
                Spacer(Modifier.width(6.dp))
            }
            Switch(state.folded, { state.folded = it }); Text(" " + tr("pattern.folded") + "   ", fontSize = 13.sp)
            Switch(state.allowance, { state.allowance = it }); Text(" " + tr("pattern.allowance") + "   ", fontSize = 13.sp)
            Switch(state.oneWay, { state.oneWay = it }); Text(" " + tr("pattern.oneway"), fontSize = 13.sp)
        }
        val layout = state.layout
        if (layout == null) {
            Text(tr("pattern.arranging"), color = Brand.Muted, modifier = Modifier.padding(16.dp))
            return@Column
        }
        val metres = layout.length / 100.0
        Text(
            tr("pattern.needed", "%.2f".format(metres), "%.2f".format(metres / 0.9144)) + " · " +
                tr("pattern.efficiency", Math.round(layout.efficiency * 100).toString(), Math.round(100 - layout.efficiency * 100).toString()),
            fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("desktop.zoom") + " ", fontSize = 13.sp)
            Slider(zoom, { zoom = it }, valueRange = 0.5f..4f, modifier = Modifier.width(220.dp))
        }
        Canvas(
            Modifier.fillMaxSize().clipToBounds().background(Color(0xFF1E1A1D), RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectDragGestures { _, d -> pan += d } },
        ) {
            val k = (min(size.width / layout.width, size.height / layout.length) * 0.94f * zoom).toFloat()
            val sink = ComposeSink(this, measurer, k, Pt.ZERO, Offset(12f, 12f) + pan, LineStyle.projector(1.6f))
            PatternPainter.paintFabric(layout, sink)
            PatternPainter.paintLayout(layout, sink, PaintOptions(showAllowance = state.allowance))
        }
    }
}

@Composable
private fun ProjectorControls(state: AppState) {
    var measured by remember { mutableStateOf("") }
    val inch = state.unit == LengthUnit.INCH
    val side = if (inch) 4.0 else 10.0
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Brand.Line), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("desktop.screen") + "  ")
                screens().forEachIndexed { i, d ->
                    val b = d.defaultConfiguration.bounds
                    FilterChip(selected = state.projectorScreen == i, onClick = { state.projectorScreen = i }, label = { Text("${i + 1}: ${b.width}×${b.height}") })
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(12.dp))
                Switch(state.projectorGrid, { state.projectorGrid = it }); Text(" " + tr("projector.grid", Lengths.format(Lengths.gridStepCm)) + "   ")
                Text(tr("projector.line") + " ")
                Slider(state.lineWidth, { state.lineWidth = it }, valueRange = 1f..12f, modifier = Modifier.width(140.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(state.showTestSquare, { state.showTestSquare = it }); Text(" " + tr("desktop.square") + "   ")
                if (state.showTestSquare) {
                    Text(tr("desktop.calib_text", Lengths.format(side * (if (inch) 2.54 else 1.0))) + " ")
                    OutlinedTextField(measured, { measured = it }, singleLine = true, modifier = Modifier.width(90.dp))
                    Text(" ${state.unit.label}  ")
                    Button(onClick = {
                        measured.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { real ->
                            state.pxPerCm = (state.pxPerCm * side / real).toFloat()
                            Store.setSetting("pxPerCm", state.pxPerCm.toString())
                            measured = ""
                        }
                    }) { Text(tr("desktop.calib_apply")) }
                }
            }
            Text(tr("desktop.keys"), color = Brand.Muted, fontSize = 12.sp)
        }
    }
}
