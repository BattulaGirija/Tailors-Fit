package com.tailorsfit.app.ui.screens

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

private fun count(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

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
            AppBar("Admin") {
                IconButton(onClick = { vm.adminLogOut(); onLogOut() }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log out")
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = onNewDesign,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New design") },
                    containerColor = Brand.Gold,
                    contentColor = Brand.AubergineDeep,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Tailors", tailors.size.toString(), Modifier.weight(1f))
                Stat("Patterns made", tailors.sumOf { it.patternsGenerated }.toString(), Modifier.weight(1f))
                Stat("Designs", "${designs.size - hidden.size}/${designs.size}", Modifier.weight(1f))
            }
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Tailors") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Designs") })
            }
            if (tab == 0) {
                if (tailors.isEmpty()) {
                    Text(
                        "No tailor accounts on this device yet.",
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
                    "${count(customers, "customer")} · ${count(t.patternsGenerated, "pattern")} · active ${date(t.lastActiveAt)}",
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
                        Pill("Custom", Brand.Gold.copy(alpha = 0.18f), Brand.Plum)
                    }
                }
                Text(if (visible) "Shown to tailors" else "Hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (custom) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            Switch(checked = visible, onCheckedChange = onVisible)
        }
    }
}

@Composable
fun AdminTailorScreen(vm: AppViewModel, tailorId: String, onBack: () -> Unit) {
    val tailor = remember(vm.adminVersion) { vm.accounts.find(tailorId) }
    val customers = remember(tailorId) { vm.tailorCustomers(tailorId) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppBar(tailor?.name ?: "Tailor", onBack) }) { padding ->
        if (tailor == null) {
            Text("This account no longer exists.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Surface(color = Brand.Aubergine, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tailor.name, style = MaterialTheme.typography.headlineSmall, color = Brand.Ivory)
                        if (tailor.shopName.isNotBlank()) Text(tailor.shopName, color = Brand.GoldLight)
                        Text("Login: ${tailor.login}", color = Brand.Ivory.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        Text("Joined ${date(tailor.createdAt)} · last active ${date(tailor.lastActiveAt)}", color = Brand.Ivory.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        Text("${count(customers.size, "customer")} · ${count(tailor.patternsGenerated, "pattern")} generated", color = Brand.GoldLight, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Customers", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (customers.isEmpty()) item { Text("No customers saved yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(customers, key = { it.id }) { c ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Brand.Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(c.phone.takeIf { it.isNotBlank() }, count(c.measurements.asMap().size, "measurement"), "updated ${date(c.updatedAt)}").joinToString(" · "),
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
                    Text("  Delete this tailor account")
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${tailor?.name}?") },
            text = { Text("They will no longer be able to log in. Their saved customers stay on this phone.") },
            confirmButton = { TextButton(onClick = { vm.deleteTailor(tailorId); confirmDelete = false; onBack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Create or edit an admin design by combining the drafting options the engine supports. */
@Composable
fun DesignEditorScreen(vm: AppViewModel, designId: String?, onDone: () -> Unit) {
    val existing = remember(designId) { Catalog.custom.firstOrNull { it.id == designId } as? BlouseModel }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var frontShape by rememberSaveable { mutableStateOf(existing?.front?.shape ?: NeckShape.ROUND) }
    var frontDepth by rememberSaveable { mutableFloatStateOf(existing?.front?.depthFactor?.toFloat() ?: 1f) }
    var backShape by rememberSaveable { mutableStateOf(existing?.back?.shape ?: NeckShape.ROUND) }
    var backDepth by rememberSaveable { mutableFloatStateOf(existing?.back?.depthFactor?.toFloat() ?: 1f) }
    var widen by rememberSaveable { mutableFloatStateOf(existing?.let { maxOf(it.front.widen, it.back.widen).toFloat() } ?: 0f) }
    var sleeve by rememberSaveable { mutableStateOf(existing?.sleeve ?: SleeveStyle.SHORT) }
    var opening by rememberSaveable { mutableStateOf(existing?.opening ?: Opening.BACK) }
    var princess by rememberSaveable { mutableStateOf(existing?.princess ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val model = BlouseModel(
        id = existing?.id ?: "preview",
        name = name.ifBlank { "New design" },
        description = description,
        front = NeckSpec(frontShape, widen.toDouble(), frontDepth.toDouble()),
        back = NeckSpec(backShape, widen.toDouble(), backDepth.toDouble()),
        sleeve = sleeve,
        opening = opening,
        princess = princess,
    )
    val warnings = remember(model) { runCatching { model.draft(Measurements.defaults()).warnings }.getOrElse { listOf(it.message ?: "Cannot draft") } }

    Scaffold(topBar = { AppBar(if (existing == null) "New design" else "Edit design", onDone) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Brand.Parchment, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(170.dp)) {
                ModelThumbnail(model, Modifier.fillMaxSize().padding(16.dp), fill = MaterialTheme.colorScheme.secondaryContainer, line = Brand.Aubergine)
            }
            Text("Preview drafted at size 36. Front on the left, back on the right.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it }, label = { Text("Design name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("Description for tailors") }, modifier = Modifier.fillMaxWidth())

            Section("Front neck")
            Chips(NeckShape.entries, frontShape, { it.label }) { frontShape = it }
            LabeledSlider("Depth", frontDepth, 0.4f..1.8f) { frontDepth = it }
            Section("Back neck")
            Chips(NeckShape.entries, backShape, { it.label }) { backShape = it }
            LabeledSlider("Depth", backDepth, 0.4f..1.8f) { backDepth = it }
            Section("Neck width")
            LabeledSlider("Wider by", widen, 0f..5f, suffix = " cm") { widen = it }
            Section("Sleeves")
            Chips(SleeveStyle.entries, sleeve, { it.label }) { sleeve = it }
            Section("Opening")
            Chips(Opening.entries, opening, { it.label }) { opening = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Princess cut front", style = MaterialTheme.typography.titleMedium)
                    Text("Two front panels, no darts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = princess, onCheckedChange = { princess = it })
            }
            if (warnings.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Check at size 36:", style = MaterialTheme.typography.titleSmall)
                        warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (name.isBlank()) {
                        error = "Give the design a name"
                    } else {
                        vm.saveDesign(model.copy(id = existing?.id ?: "custom_" + UUID.randomUUID().toString().take(8), name = name.trim()))
                        onDone()
                    }
                }) { Text(if (existing == null) "Add design" else "Save changes") }
                if (existing != null) OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Tailors will no longer see this design.") },
            confirmButton = { TextButton(onClick = { vm.deleteDesign(existing.id); onDone() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
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
