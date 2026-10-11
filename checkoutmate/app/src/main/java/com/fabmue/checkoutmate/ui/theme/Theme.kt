package com.fabmue.checkoutmate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Scoreboard theme ids stored in settings. */
object BoardThemes {
    const val DARK = "dark"
    const val AMOLED = "amoled"
    const val FELT = "felt"
}

private fun scheme(bg: androidx.compose.ui.graphics.Color, surface: androidx.compose.ui.graphics.Color, surfaceVariant: androidx.compose.ui.graphics.Color) =
    darkColorScheme(
        primary = GreenPrimary,
        onPrimary = Color0E,
        primaryContainer = GreenDark,
        onPrimaryContainer = OnDark,
        secondary = Amber,
        onSecondary = Color0E,
        tertiary = RedAccent,
        onTertiary = OnDark,
        background = bg,
        onBackground = OnDark,
        surface = surface,
        onSurface = OnDark,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = OnDarkMuted,
        outline = OnDarkMuted,
        error = RedAccent,
    )

private val Color0E = androidx.compose.ui.graphics.Color(0xFF0B120F)

private val DarkScheme = scheme(DarkBg, DarkSurface, DarkSurfaceVariant)
private val AmoledScheme = scheme(AmoledBg, AmoledSurface, AmoledSurfaceVariant)
private val FeltScheme = scheme(FeltBg, FeltSurface, FeltSurfaceVariant)

@Composable
fun CheckoutMateTheme(
    boardTheme: String = BoardThemes.DARK,
    content: @Composable () -> Unit,
) {
    // The app is dark by design (pub-friendly); the theme id picks the palette.
    val colors = when (boardTheme) {
        BoardThemes.AMOLED -> AmoledScheme
        BoardThemes.FELT -> FeltScheme
        else -> DarkScheme
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
