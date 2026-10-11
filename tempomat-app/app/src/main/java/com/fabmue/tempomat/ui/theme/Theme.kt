package com.fabmue.tempomat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** State colours, chosen to read at a glance in daylight and at night. */
object CruiseColors {
    val Off = Color(0xFF6B7280)
    val Standby = Color(0xFFF5B301)
    val Active = Color(0xFF22C55E)
    val Override = Color(0xFF38BDF8)
    val Fault = Color(0xFFEF4444)
    val Unknown = Color(0xFF9CA3AF)
}

private val scheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF00210B),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF131A22),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1C2530),
    onSurfaceVariant = Color(0xFFB6C0CC),
    error = Color(0xFFEF4444),
)

/** Always dark: a bright screen in the cabin at night is a distraction. */
@Composable
fun TempomatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
