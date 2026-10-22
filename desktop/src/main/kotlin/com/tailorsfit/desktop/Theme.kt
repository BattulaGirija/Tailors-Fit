package com.tailorsfit.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The Tailors Fit colours (same as the Android app). */
object Brand {
    val Aubergine = Color(0xFF3E1A36)
    val AubergineDeep = Color(0xFF26101F)
    val Plum = Color(0xFF6B2D5C)
    val Gold = Color(0xFFB8913F)
    val GoldLight = Color(0xFFE6C98A)
    val Ivory = Color(0xFFFBF7F1)
    val Parchment = Color(0xFFF3ECE2)
    val Ink = Color(0xFF2A2027)
    val Muted = Color(0xFF75676F)
    val Line = Color(0xFFE2D6C6)
    val Emerald = Color(0xFF1E7A55)
}

private val Colors = lightColorScheme(
    primary = Brand.Aubergine,
    onPrimary = Brand.Ivory,
    primaryContainer = Color(0xFFF1E1EC),
    onPrimaryContainer = Brand.AubergineDeep,
    secondary = Brand.Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E8C8),
    onSecondaryContainer = Color(0xFF3A2A06),
    background = Brand.Ivory,
    surface = Brand.Ivory,
    onBackground = Brand.Ink,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Parchment,
    onSurfaceVariant = Brand.Muted,
    outline = Brand.Line,
)

@Composable
fun TailorsFitTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)
