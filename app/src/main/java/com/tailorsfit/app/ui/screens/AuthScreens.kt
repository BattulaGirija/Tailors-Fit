package com.tailorsfit.app.ui.screens

import com.tailorsfit.pattern.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.tailorsfit.app.data.SecurityQuestions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.AppViewModel
import com.tailorsfit.app.ui.components.LanguageChips
import com.tailorsfit.app.ui.components.OrnamentDivider
import com.tailorsfit.pattern.i18n.Language
import com.tailorsfit.app.ui.components.TapeMeasure
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.geom.Pt
import com.tailorsfit.pattern.geom.Rect
import com.tailorsfit.pattern.model.Catalog
import com.tailorsfit.pattern.model.Measurements

/**
 * Aubergine backdrop with real pattern pieces traced faintly in gold, scattered like pieces
 * laid out on a cutting table.
 */
@Composable
fun PatternBackdrop(content: @Composable BoxScope.() -> Unit) {
    val outlines: List<List<Pt>> = remember {
        runCatching {
            listOf("blouse_princess_round", "blouse_boat", "blouse_sweetheart")
                .mapNotNull { Catalog.model(it) }
                .flatMap { m -> m.draft(Measurements.defaults()).pieces.map { it.seamOutline() } }
        }.getOrDefault(emptyList())
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Brand.Plum, Brand.Aubergine, Brand.AubergineDeep))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (outlines.isEmpty()) return@Canvas
            // Fixed placements (fraction of width/height, rotation, scale) so the page looks composed.
            val spots = listOf(
                Triple(0.02f, 0.02f, -12f), Triple(0.62f, 0.04f, 18f), Triple(0.70f, 0.40f, -8f),
                Triple(-0.06f, 0.46f, 10f), Triple(0.30f, 0.78f, -20f), Triple(0.78f, 0.80f, 6f),
                Triple(0.18f, 0.28f, 30f), Triple(0.45f, 0.58f, -35f),
            )
            val cmToPx = size.width / 70f
            spots.forEachIndexed { i, (fx, fy, angle) ->
                val poly = outlines[i % outlines.size]
                val b = Rect.of(poly)
                val path = Path().apply {
                    poly.forEachIndexed { k, p ->
                        val x = ((p.x - b.minX) * cmToPx).toFloat()
                        val y = ((p.y - b.minY) * cmToPx).toFloat()
                        if (k == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                translate(size.width * fx, size.height * fy) {
                    rotate(angle, pivot = Offset((b.width * cmToPx / 2).toFloat(), (b.height * cmToPx / 2).toFloat())) {
                        drawPath(path, Brand.GoldLight.copy(alpha = 0.26f), style = Stroke(width = 2f))
                    }
                }
            }
        }
        content()
    }
}

@Composable
private fun AuthCard(
    title: String,
    subtitle: String,
    body: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    header: @Composable () -> Unit = {},
) {
    PatternBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header()
            Text(tr("app.brand"), style = MaterialTheme.typography.labelSmall, color = Brand.GoldLight)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.displaySmall, color = Brand.Ivory, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            OrnamentDivider(color = Brand.Gold, width = 110)
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.Ivory.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                color = Brand.Ivory,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                Column {
                    TapeMeasure(Modifier.padding(horizontal = 20.dp), color = Brand.Gold.copy(alpha = 0.7f))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { body() }
                }
            }
            Spacer(Modifier.height(16.dp))
            footer()
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, last: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) tr("auth.hide") else tr("auth.show")) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NoticeText(notice: String?) {
    if (notice != null) {
        Surface(color = Brand.Emerald.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(notice, color = Brand.Emerald, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun LoginField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(tr("auth.id")) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Pick one of the [SecurityQuestions]; the answer lets the tailor reset a forgotten password. */
@Composable
private fun QuestionPicker(question: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (question.isEmpty()) "" else tr(question),
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("auth.question")) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The read-only field swallows taps, so a transparent layer on top opens the menu.
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SecurityQuestions.keys.forEach { key ->
                DropdownMenuItem(text = { Text(tr(key)) }, onClick = { onPick(key); open = false })
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Aubergine, contentColor = Brand.Ivory),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Log in / sign up for tailors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    vm: AppViewModel,
    onLoggedIn: () -> Unit,
    onAdmin: () -> Unit,
    onForgot: (String) -> Unit = {},
    onLanguage: (Language) -> Unit = {},
) {
    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var shop by rememberSaveable { mutableStateOf("") }
    // The phone / e-mail used last time on this phone, so tailors only type their password.
    var login by rememberSaveable { mutableStateOf(vm.lastLogin) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthCard(
        title = if (signUp) tr("auth.join") else tr("auth.welcome"),
        subtitle = if (signUp) tr("auth.join.text") else tr("auth.welcome.text"),
        body = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(tr("auth.login"), tr("auth.signup")).forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = signUp == (i == 1),
                        onClick = { signUp = i == 1; error = null },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                    ) { Text(label) }
                }
            }
            if (signUp) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text(tr("auth.name")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shop, onValueChange = { shop = it }, label = { Text(tr("auth.shop")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!signUp) NoticeText(vm.authNotice)
            LoginField(login) { login = it }
            PasswordField(password, { password = it }, tr("auth.password"), last = !signUp)
            if (signUp) {
                PasswordField(confirm, { confirm = it }, tr("auth.confirm"), last = false)
                QuestionPicker(question) { question = it }
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it }, label = { Text(tr("auth.answer")) }, singleLine = true,
                    supportingText = { Text(tr("auth.answer.help")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.authNotice = null; onForgot(login) }) { Text(tr("auth.forgot"), color = Brand.Plum) }
                }
            }
            ErrorText(error)
            PrimaryButton(if (signUp) tr("auth.create") else tr("auth.login")) {
                error = when {
                    signUp && password != confirm -> tr("auth.mismatch")
                    signUp && question.isEmpty() -> tr("err.question")
                    signUp -> vm.signUp(name, shop, login, password, question, answer)
                    else -> vm.logIn(login, password)
                }
                if (error == null) {
                    vm.authNotice = null
                    onLoggedIn()
                }
            }
            TextButton(onClick = { signUp = !signUp; error = null }, modifier = Modifier.fillMaxWidth()) {
                Text(if (signUp) tr("auth.have_account") else tr("auth.new_here"), color = Brand.Plum)
            }
        },
        footer = {
            TextButton(onClick = onAdmin) { Text(tr("auth.admin_login"), color = Brand.GoldLight) }
        },
        header = {
            LanguageChips(vm.language, onLanguage)
            Spacer(Modifier.height(20.dp))
        },
    )
}

/**
 * Reset a forgotten password on this phone: enter the phone / e-mail, answer the security
 * question chosen at sign-up, then choose a new password.
 */
@Composable
fun ForgotPasswordScreen(vm: AppViewModel, initialLogin: String, onDone: () -> Unit, onBack: () -> Unit) {
    var login by rememberSaveable { mutableStateOf(initialLogin) }
    var question by rememberSaveable { mutableStateOf<String?>(null) }
    var answer by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthCard(
        title = tr("forgot.title"),
        subtitle = if (question == null) tr("forgot.text") else tr("forgot.answer_text"),
        body = {
            val q = question
            if (q == null) {
                LoginField(login) { login = it; error = null }
                ErrorText(error)
                PrimaryButton(tr("forgot.next")) {
                    question = vm.securityQuestion(login)
                    error = if (question == null) tr("forgot.no_question") else null
                }
            } else {
                Text(tr("auth.id") + ": " + login.trim(), style = MaterialTheme.typography.bodySmall, color = Brand.Muted)
                Text(tr(q), style = MaterialTheme.typography.titleMedium, color = Brand.Aubergine)
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it }, label = { Text(tr("auth.answer")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
                )
                PasswordField(password, { password = it }, tr("forgot.new_password"), last = false)
                PasswordField(confirm, { confirm = it }, tr("auth.confirm"))
                ErrorText(error)
                PrimaryButton(tr("forgot.reset")) {
                    error = if (password != confirm) tr("auth.mismatch") else vm.resetPassword(login, answer, password)
                    if (error == null) onDone()
                }
                TextButton(onClick = { question = null; error = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("forgot.other_id"), color = Brand.Plum)
                }
            }
        },
        footer = {
            TextButton(onClick = onBack) { Text(tr("auth.back_to_login"), color = Brand.GoldLight) }
        },
    )
}

/** Admin password (set on first use). */
@Composable
fun AdminLoginScreen(vm: AppViewModel, onLoggedIn: () -> Unit, onBack: () -> Unit) {
    val setUp = remember { !vm.accounts.adminExists() }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AuthCard(
        title = tr("auth.admin"),
        subtitle = if (setUp) tr("auth.admin.setup") else tr("auth.admin.text"),
        body = {
            PasswordField(password, { password = it }, if (setUp) tr("auth.admin.new_password") else tr("auth.admin.password"), last = !setUp)
            if (setUp) PasswordField(confirm, { confirm = it }, tr("auth.confirm"))
            ErrorText(error)
            PrimaryButton(if (setUp) tr("auth.admin.set") else tr("auth.admin.login")) {
                error = if (setUp && password != confirm) tr("auth.mismatch") else vm.adminLogIn(password)
                if (error == null) onLoggedIn()
            }
        },
        footer = {
            Row { TextButton(onClick = onBack) { Text(tr("auth.back_to_login"), color = Brand.GoldLight) } }
        },
    )
}
