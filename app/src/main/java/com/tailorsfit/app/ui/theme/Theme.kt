package com.tailorsfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tailorsfit.app.R

/** Brand colours: deep aubergine and antique gold on warm ivory. */
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
}

val SerifDisplay = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))
val Sans = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold),
)

private val Colors = lightColorScheme(
    primary = Brand.Aubergine,
    onPrimary = Brand.Ivory,
    primaryContainer = Color(0xFFF1E1EC),
    onPrimaryContainer = Brand.AubergineDeep,
    secondary = Brand.Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E8C8),
    onSecondaryContainer = Color(0xFF3A2A06),
    tertiary = Color(0xFF2F5D62),
    background = Brand.Ivory,
    onBackground = Brand.Ink,
    surface = Brand.Ivory,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Parchment,
    onSurfaceVariant = Brand.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F4ED),
    surfaceContainer = Color(0xFFF6F0E7),
    surfaceContainerHigh = Color(0xFFF4EDE3),
    surfaceContainerHighest = Color(0xFFF1E9DD),
    outline = Color(0xFFCDBFAE),
    outlineVariant = Brand.Line,
    errorContainer = Color(0xFFFBE3DF),
)

private fun serif(size: Int, line: Int) = TextStyle(fontFamily = SerifDisplay, fontSize = size.sp, lineHeight = line.sp)
private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) =
    TextStyle(fontFamily = Sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp)

private val Type = Typography(
    displayLarge = serif(52, 60),
    displayMedium = serif(42, 50),
    displaySmall = serif(34, 42),
    headlineLarge = serif(30, 38),
    headlineMedium = serif(26, 34),
    headlineSmall = serif(23, 30),
    titleLarge = serif(21, 28),
    titleMedium = sans(15, 22, FontWeight.SemiBold, 0.1),
    titleSmall = sans(13, 20, FontWeight.Medium, 0.1),
    bodyLarge = sans(15, 23),
    bodyMedium = sans(13, 20),
    bodySmall = sans(12, 17),
    labelLarge = sans(14, 20, FontWeight.Medium, 0.2),
    labelMedium = sans(12, 16, FontWeight.Medium, 0.4),
    labelSmall = sans(11, 16, FontWeight.Medium, 0.8),
)

@Composable
fun TailorsFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
