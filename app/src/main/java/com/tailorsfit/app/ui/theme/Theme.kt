package com.tailorsfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF8E244D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF8A6A1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE2A6),
    onSecondaryContainer = Color(0xFF2B1F00),
    tertiary = Color(0xFF1A5FB4),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    errorContainer = Color(0xFFFFDAD6),
)

@Composable
fun TailorsFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
