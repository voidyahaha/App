package com.jeecommandcenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF131E30)
private val Teal = Color(0xFF55D6BE)
private val DayBlue = Color(0xFF245B73)

@Composable
fun JeeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Teal, onPrimary = Navy, secondary = Color(0xFF9BDDCB),
        background = Navy, surface = Panel, surfaceVariant = Color(0xFF1D2B40),
        onSurface = Color(0xFFE7EDF5), onBackground = Color(0xFFE7EDF5),
        error = Color(0xFFFF8A80)
    ) else lightColorScheme(
        primary = DayBlue, onPrimary = Color.White, secondary = Color(0xFF2B766C),
        background = Color(0xFFF4F7FA), surface = Color.White,
        surfaceVariant = Color(0xFFE6EEF3), onSurface = Color(0xFF192A38),
        onBackground = Color(0xFF192A38)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
