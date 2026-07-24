package com.mateof.tfmtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

val Blue = Color(0xFF4DA8FF)
val Teal = Color(0xFF37D6C0)
val Amber = Color(0xFFFFC24D)
val Background = Color(0xFF0B0F17)
val Surface = Color(0xFF131A28)
val SurfaceVariant = Color(0xFF1C2637)
val OnSurfaceMuted = Color(0xFFA8B4C8)

private val tvColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF04121F),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFD5E9FF),
    secondary = Teal,
    onSecondary = Color(0xFF00201B),
    tertiary = Amber,
    onTertiary = Color(0xFF231A00),
    background = Background,
    onBackground = Color(0xFFE6ECF5),
    surface = Surface,
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000)
)

private val m3Colors = m3DarkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF04121F),
    secondary = Teal,
    tertiary = Amber,
    background = Background,
    onBackground = Color(0xFFE6ECF5),
    surface = Surface,
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    error = Color(0xFFFF6B6B)
)

/**
 * tv-material drives the focus-aware components; the classic Material 3 theme
 * is nested for the few controls tv-material does not ship (text fields).
 */
@Composable
fun TfmTvTheme(content: @Composable () -> Unit) {
    M3Theme(colorScheme = m3Colors) {
        MaterialTheme(colorScheme = tvColors, content = content)
    }
}
