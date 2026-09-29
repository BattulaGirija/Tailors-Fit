package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.blouse.BackDetail
import com.tailorsfit.pattern.i18n.I18n
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.data.Account
import com.tailorsfit.app.ui.components.AppBar
import com.tailorsfit.app.ui.components.ModelThumbnail
import com.tailorsfit.app.ui.components.Pill
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.blouse.BlouseModel
import com.tailorsfit.pattern.blouse.NeckShape
import com.tailorsfit.pattern.blouse.NeckSpec
import com.tailorsfit.pattern.blouse.Opening
import com.tailorsfit.pattern.blouse.SleeveStyle
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.GarmentModel
import com.tailorsfit.pattern.model.Measurements
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private fun count(n: Int, word: String) = I18n.plural("count.$word", n)

private fun date(ms: Long) = if (ms <= 0) "—" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
fun AdminHomeScreen(
    vm: AppViewModel,
    onTailor: (String) -> Unit,
    onNewDesign: () -> Unit,
    onEditDesign: (String) -> Unit,
    onLogOut: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val version = vm.adminVersion
    val tailors = remember(version) { vm.tailors() }
    val customerCounts = remember(version, tailors) { tailors.associate { it.id to vm.tailorCustomers(it.id).size } }
    val designs = remember(version) { Catalog.allModels }
    val hidden = remember(version) { Catalog.hidden }

    Scaffold(
        topBar = {
            AppBar(tr("admin.title")) {
                IconButton(onClick = { vm.adminLogOut(); onLogOut() }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = tr("admin.logout"))
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = onNewDesign,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(tr("admin.new_design")) },
                    containerColor = Brand.Gold,
                    contentColor = Brand.AubergineDeep,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(tr("admin.tailors"), tailors.size.toString(), Modifier.weight(1f))
                Stat(tr("admin.patterns"), tailors.sumOf { it.patternsGenerated }.toString(), Modifier.weight(1f))
                Stat(tr("admin.designs"), "${designs.size - hidden.size}/${designs.size}", Modifier.weight(1f))
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("admin.tailors")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("admin.designs")) })
            }
            if (tab == 0) {
                if (tailors.isEmpty()) {
                    Text(
                        tr("admin.no_tailors"),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tailors, key = { it.id }) { t -> TailorRow(t, customerCounts[t.id] ?: 0) { onTailor(t.id) } }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(designs, key = { it.id }) { m ->
                        DesignRow(
                            model = m,
                            custom = Catalog.custom.any { it.id == m.id },
                            visible = m.id !in hidden,
                            onVisible = { vm.setDesignHidden(m.id, !it) },
                            onEdit = { onEditDesign(m.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = Brand.Aubergine, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = Brand.Ivory)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Brand.GoldLight)
        }
    }
}

@Composable
private fun TailorRow(t: Account, customers: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Brand.Line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(t.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(t.shopName, t.login).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${count(customers, "customer")} · ${count(t.patternsGenerated, "pattern")} · ${tr("admin.active", date(t.lastActiveAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.Plum,
                )
            }
        }
    }
}

@Composable
private fun DesignRow(model: GarmentModel, custom: Boolean, visible: Boolean, onVisible: (Boolean) -> Unit, onEdit: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Brand.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(Brand.Parchment)) {
                ModelThumbnail(model, Modifier.fillMaxSize().padding(6.dp), fill = MaterialTheme.colorScheme.secondaryContainer, line = Brand.Aubergine)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (custom) {
                        Spacer(Modifier.width(6.dp))
                        Pill(tr("admin.custom"), Brand.Gold.copy(alpha = 0.18f), Brand.Plum)
                    }
                }
                Text(if (visible) tr("admin.shown") else tr("admin.hidden"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (custom) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = tr("app.edit")) }
            Switch(checked = visible, onCheckedChange = onVisible)
        }
    }
}

@Composable
fun AdminTailorScreen(vm: AppViewModel, tailorId: String, onBack: () -> Unit) {
    val tailor = remember(vm.adminVersion) { vm.accounts.find(tailorId) }
    val customers = remember(tailorId) { vm.tailorCustomers(tailorId) }
    var confirmDelete by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppBar(tailor?.name ?: tr("admin.tailor"), onBack) }) { padding ->
        if (tailor == null) {
            Text(tr("admin.gone"), Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Surface(color = Brand.Aubergine, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tailor.name, style = MaterialTheme.typography.headlineSmall, color = Brand.Ivory)
                        if (tailor.shopName.isNotBlank()) Text(tailor.shopName, color = Brand.GoldLight)
                        Text(tr("admin.login_id", tailor.login), color = Brand.Ivory.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        Text(tr("admin.joined", date(tailor.createdAt), date(tailor.lastActiveAt)), color = Brand.Ivory.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        Text("${count(customers.size, "customer")} · ${count(tailor.patternsGenerated, "pattern")} ${tr("admin.generated")}", color = Brand.GoldLight, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (tailor.securityQuestion.isNotEmpty()) tr("admin.question_set") else tr("admin.question_missing"),
                            color = Brand.Ivory.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { resetOpen = true; resetDone = false }) { Text(tr("admin.reset_password")) }
                if (resetDone) Text(tr("admin.reset_done"), color = Brand.Emerald, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text(tr("admin.customers"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (customers.isEmpty()) item { Text(tr("admin.no_customers"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(customers, key = { it.id }) { c ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Brand.Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(c.phone.takeIf { it.isNotBlank() }, count(c.measurements.asMap().size, "measurement"), tr("admin.updated", date(c.updatedAt))).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("  " + tr("admin.delete_tailor"))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("customers.delete", tailor?.name)) },
            text = { Text(tr("admin.delete_tailor.text")) },
            confirmButton = { TextButton(onClick = { vm.deleteTailor(tailorId); confirmDelete = false; onBack() }) { Text(tr("app.delete")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("app.cancel")) } },
        )
    }
    if (resetOpen) {
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text(tr("admin.reset_password")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("admin.reset_password.text", tailor?.name))
                    OutlinedTextField(value = password, onValueChange = { password = it; error = null }, label = { Text(tr("forgot.new_password")) }, singleLine = true)
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = vm.adminResetPassword(tailorId, password)
                    if (error == null) { resetOpen = false; resetDone = true }
                }) { Text(tr("forgot.reset")) }
            },
            dismissButton = { TextButton(onClick = { resetOpen = false }) { Text(tr("app.cancel")) } },
        )
    }
}

/** Create or edit an admin design by combining the drafting options the engine supports. */
@Composable
fun DesignEditorScreen(vm: AppViewModel, designId: String?, onDone: () -> Unit) {
    val existing = remember(designId) { Catalog.custom.firstOrNull { it.id == designId } as? BlouseModel }
    var name by rememberSaveable { mutableStateOf(existing?.baseName ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.baseDescription ?: "") }
    var frontShape by rememberSaveable { mutableStateOf(existing?.front?.shape ?: NeckShape.ROUND) }
    var frontDepth by rememberSaveable { mutableFloatStateOf(existing?.front?.depthFactor?.toFloat() ?: 1f) }
    var backShape by rememberSaveable { mutableStateOf(existing?.back?.shape ?: NeckShape.ROUND) }
    var backDepth by rememberSaveable { mutableFloatStateOf(existing?.back?.depthFactor?.toFloat() ?: 1f) }
    var widen by rememberSaveable { mutableFloatStateOf(existing?.let { maxOf(it.front.widen, it.back.widen).toFloat() } ?: 0f) }
    var sleeve by rememberSaveable { mutableStateOf(existing?.sleeve ?: SleeveStyle.SHORT) }
    var opening by rememberSaveable { mutableStateOf(existing?.opening ?: Opening.BACK) }
    var princess by rememberSaveable { mutableStateOf(existing?.princess ?: false) }
    var backDetail by rememberSaveable { mutableStateOf(existing?.backDetail ?: BackDetail.NONE) }
    var collar by rememberSaveable { mutableStateOf(existing?.collar ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val model = BlouseModel(
        id = existing?.id ?: "preview",
        baseName = name.ifBlank { tr("admin.new_design") },
        baseDescription = description,
        front = NeckSpec(frontShape, widen.toDouble(), frontDepth.toDouble()),
        back = NeckSpec(backShape, widen.toDouble(), backDepth.toDouble()),
        sleeve = sleeve,
        opening = opening,
        princess = princess,
        backDetail = backDetail,
        collar = collar,
    )
    val warnings = remember(model) { runCatching { model.draft(Measurements.defaults()).warnings }.getOrElse { listOf(it.message ?: tr("admin.cannot_draft")) } }

    Scaffold(topBar = { AppBar(if (existing == null) tr("admin.new_design") else tr("admin.edit_design"), onDone) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Brand.Parchment, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(170.dp)) {
                ModelThumbnail(model, Modifier.fillMaxSize().padding(16.dp), fill = MaterialTheme.colorScheme.secondaryContainer, line = Brand.Aubergine)
            }
            Text(tr("admin.preview_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text(tr("admin.design_name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(tr("admin.design_desc")) }, modifier = Modifier.fillMaxWidth())

            Section(tr("admin.front_neck"))
            Chips(NeckShape.entries, frontShape, { it.label }) { frontShape = it }
            LabeledSlider(tr("admin.depth"), frontDepth, 0.4f..1.8f) { frontDepth = it }
            Section(tr("admin.back_neck"))
            Chips(NeckShape.entries, backShape, { it.label }) { backShape = it }
            LabeledSlider(tr("admin.depth"), backDepth, 0.4f..1.8f) { backDepth = it }
            Section(tr("admin.neck_width"))
            LabeledSlider(tr("admin.wider_by"), widen, 0f..5f, suffix = " cm") { widen = it }
            Section(tr("admin.sleeves"))
            Chips(SleeveStyle.entries, sleeve, { it.label }) { sleeve = it }
            Section(tr("admin.opening"))
            Chips(Opening.entries, opening, { it.label }) { opening = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("admin.princess"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("admin.princess.text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = princess, onCheckedChange = { princess = it })
            }
            Section(tr("admin.back_detail"))
            Chips(BackDetail.entries, backDetail, { it.label }) { backDetail = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("admin.collar"), style = MaterialTheme.typography.titleMedium)
                    Text(tr("admin.collar.text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = collar, onCheckedChange = { collar = it })
            }
            if (warnings.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(tr("admin.check36"), style = MaterialTheme.typography.titleSmall)
                        warnings.forEach { Text(tr("app.bullet", it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (name.isBlank()) {
                        error = tr("admin.need_name")
                    } else {
                        vm.saveDesign(model.copy(id = existing?.id ?: "custom_" + UUID.randomUUID().toString().take(8), baseName = name.trim()))
                        onDone()
                    }
                }) { Text(if (existing == null) tr("admin.add") else tr("admin.save")) }
                if (existing != null) OutlinedButton(onClick = { confirmDelete = true }) { Text(tr("app.delete")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("customers.delete", existing.name)) },
            text = { Text(tr("admin.delete_design.text")) },
            confirmButton = { TextButton(onClick = { vm.deleteDesign(existing.id); onDone() }) { Text(tr("app.delete")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("app.cancel")) } },
        )
    }
}

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, suffix: String = "×", onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label  ${"%.1f".format(value)}$suffix", Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
