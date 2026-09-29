package com.tailorsfit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.tailorsfit.app.ui.theme.Brand

/** A hairline with a small diamond in the middle — the app's ornament. */
@Composable
fun OrnamentDivider(modifier: Modifier = Modifier, color: Color = Brand.Gold, width: Int = 120) {
    Canvas(modifier.width(width.dp).height(10.dp)) {
        val cy = size.height / 2
        val cx = size.width / 2
        val d = size.height / 2
        drawLine(color, Offset(0f, cy), Offset(cx - d * 1.8f, cy), strokeWidth = 1.2f)
        drawLine(color, Offset(cx + d * 1.8f, cy), Offset(size.width, cy), strokeWidth = 1.2f)
        val diamond = Path().apply {
            moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close()
        }
        drawPath(diamond, color)
    }
}

/** A tape-measure edge: a tick every "centimetre", longer ones every fifth. */
@Composable
fun TapeMeasure(modifier: Modifier = Modifier, color: Color = Brand.Gold) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val step = 8.dp.toPx()
        var i = 0
        var x = 0f
        drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
        while (x <= size.width) {
            val h = if (i % 5 == 0) size.height else size.height * 0.45f
            drawLine(color, Offset(x, size.height), Offset(x, size.height - h), strokeWidth = 1f)
            x += step
            i++
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            OrnamentDivider(width = 72)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Brand.Gold) }
    }
}

/** Serif numeral in a gold ring. */
@Composable
fun NumberBadge(n: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            drawCircle(Brand.Gold, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
        Text("$n", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun RowSpacer() = Spacer(Modifier.width(12.dp))

val SectionArrangement = Arrangement.spacedBy(12.dp)
