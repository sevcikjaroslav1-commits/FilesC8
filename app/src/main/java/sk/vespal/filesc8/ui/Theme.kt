package sk.vespal.filesc8.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFFF3F5FA)
    val Surface = Color(0xFFFFFFFF)
    val Line = Color(0xFFE4E8F0)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF556070)
    val Faint = Color(0xFF94A3B8)
    val Accent = Color(0xFF4F46E5)
    val AccentSoft = Color(0xFFE6E8FD)
    val Green = Color(0xFF15803D)
    val Red = Color(0xFFDC2626)
    val Star = Color(0xFFF59E0B)
    val StarSoft = Color(0xFFFEF3C7)
}

private val scheme = lightColorScheme(
    primary = C.Accent,
    onPrimary = Color.White,
    primaryContainer = C.AccentSoft,
    onPrimaryContainer = C.Accent,
    background = C.Bg,
    onBackground = C.Ink,
    surface = C.Surface,
    onSurface = C.Ink,
    surfaceVariant = C.Bg,
    onSurfaceVariant = C.Muted,
    outline = C.Line,
)

@Composable
fun FilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
