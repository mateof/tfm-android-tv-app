package com.mateof.tfmtv.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme as M3Theme
import androidx.compose.material3.LocalContentColor as M3ContentColor
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.mateof.tfmtv.data.prefs.AccentChoice
import com.mateof.tfmtv.data.prefs.EffectsMode

/** Near black, so the panel does not glow in a dark room. */
val Background = Color(0xFF070A12)
val Surface = Color(0xFF111726)
val SurfaceVariant = Color(0xFF1B2336)
val OnBackground = Color(0xFFEAF0FA)
val OnSurfaceMuted = Color(0xFFA3AFC4)
val Star = Color(0xFFFFC857)
private val ErrorRed = Color(0xFFFF7A7A)

@Immutable
data class Palette(
    val accent: Color,
    val accentEnd: Color,
    val onAccent: Color,
    /** Glows, moving backdrops and transitions; off on devices that cannot afford them. */
    val effects: Boolean
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(accent, accentEnd))
    val horizontal: Brush get() = Brush.horizontalGradient(listOf(accent, accentEnd))
}

fun paletteOf(choice: AccentChoice, effects: Boolean): Palette = when (choice) {
    AccentChoice.OCEAN -> Palette(Color(0xFF5AB4FF), Color(0xFF37D6C0), Color(0xFF04121F), effects)
    AccentChoice.AURORA -> Palette(Color(0xFFAE94FF), Color(0xFFF778BA), Color(0xFF160A2E), effects)
    AccentChoice.EMERALD -> Palette(Color(0xFF3EDB9B), Color(0xFFB4E84C), Color(0xFF04190F), effects)
    AccentChoice.SUNSET -> Palette(Color(0xFFFFB454), Color(0xFFFF6F61), Color(0xFF241000), effects)
    AccentChoice.RUBY -> Palette(Color(0xFFFF6F86), Color(0xFFB978FF), Color(0xFF26030B), effects)
}

private val LocalPalette = staticCompositionLocalOf { paletteOf(AccentChoice.OCEAN, true) }

/** The colours Material does not have a slot for. */
val palette: Palette
    @Composable
    @ReadOnlyComposable
    get() = LocalPalette.current

/** The sticks of 2015-2018 (API 22-25) and anything that says it is short of memory. */
fun effectsFor(mode: EffectsMode, context: Context): Boolean = when (mode) {
    EffectsMode.FULL -> true
    EffectsMode.REDUCED -> false
    EffectsMode.AUTO -> {
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        !lowRam && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}

private val base = Typography()
private val appTypography = Typography(
    displaySmall = base.displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = base.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = base.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
)

/**
 * tv-material drives the focus-aware components; the classic Material 3 theme
 * is nested for the few controls tv-material does not ship (text fields).
 */
@Composable
fun TfmTvTheme(
    accent: AccentChoice = AccentChoice.OCEAN,
    effects: EffectsMode = EffectsMode.AUTO,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = remember(accent, effects) { paletteOf(accent, effectsFor(effects, context)) }
    val container = remember(accent) { mix(Surface, colors.accent, 0.22f) }

    val tvColors = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = container,
        onPrimaryContainer = mix(Color.White, colors.accent, 0.2f),
        secondary = colors.accentEnd,
        onSecondary = colors.onAccent,
        tertiary = Star,
        onTertiary = Color(0xFF231A00),
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnBackground,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceMuted,
        error = ErrorRed,
        onError = Color(0xFF2A0000)
    )
    val m3Colors = m3DarkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        secondary = colors.accentEnd,
        tertiary = Star,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnBackground,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = OnSurfaceMuted,
        outline = Color(0xFF3A465E),
        error = ErrorRed
    )

    M3Theme(colorScheme = m3Colors) {
        MaterialTheme(colorScheme = tvColors, typography = appTypography) {
            // Without a Surface around, both libraries draw text in black.
            CompositionLocalProvider(
                LocalPalette provides colors,
                LocalContentColor provides OnBackground,
                M3ContentColor provides OnBackground,
                content = content
            )
        }
    }
}

fun mix(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount
)

/** The screen behind everything: dark, with a faint light of the accent in two corners. */
@Composable
fun Modifier.appBackground(): Modifier {
    val colors = palette
    return drawWithCache {
        val top = Brush.radialGradient(
            listOf(colors.accent.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(size.width * 0.82f, -size.height * 0.12f),
            radius = size.width * 0.55f
        )
        val bottom = Brush.radialGradient(
            listOf(colors.accentEnd.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.1f, size.height * 1.1f),
            radius = size.width * 0.5f
        )
        onDrawBehind {
            drawRect(Background)
            drawRect(top)
            drawRect(bottom)
        }
    }
}

/** A quiet gradient that always comes out the same for the same text. */
fun seedBrush(seed: String): Brush {
    val hue = (seed.hashCode() and 0x7fffffff) % 360
    return Brush.linearGradient(
        listOf(Color.hsv(hue.toFloat(), 0.45f, 0.36f), Color.hsv((hue + 40f) % 360f, 0.55f, 0.16f))
    )
}
