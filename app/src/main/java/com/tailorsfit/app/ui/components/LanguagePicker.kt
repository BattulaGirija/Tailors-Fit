package com.tailorsfit.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.theme.Brand
import com.tailorsfit.pattern.i18n.Language
import com.tailorsfit.pattern.i18n.tr

/** Row of language names (each in its own script), for dark backgrounds such as the login page. */
@Composable
fun LanguageChips(current: Language, onPick: (Language) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Language.entries.forEach { l ->
            val selected = l == current
            Surface(
                color = if (selected) Brand.Gold else Color.White.copy(alpha = 0.10f),
                contentColor = if (selected) Brand.AubergineDeep else Brand.Ivory,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onPick(l) },
            ) {
                Text(l.nativeName, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun LanguageDialog(current: Language, onPick: (Language) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("app.language")) },
        text = {
            Column {
                Language.entries.forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(l) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = l == current, onClick = { onPick(l) })
                        Text(l.nativeName, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("app.close")) } },
    )
}
