package dev.antidroidvirus

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Token di "Arancio UI", il kit condiviso con Prendere Nota, MyVault, Gestionale e IPAM:
 * grigi caldi, arancione smorzato, stati teal / ambra / rosa.
 */
data class Palette(
    val background: Color,
    val panel: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val ok: Color,
    val info: Color,
    val warn: Color,
    val error: Color,
)

private val Dark = Palette(
    background = Color(0xFF1C1B1A),
    panel = Color(0xFF252422),
    raised = Color(0xFF2E2C2A),
    border = Color(0xFF3A3734),
    text = Color(0xFFEDEAE3),
    secondary = Color(0xFFA39E95),
    accent = Color(0xFFE3845A),
    ok = Color(0xFF5DB3A6),
    info = Color(0xFF7FA6DB),
    warn = Color(0xFFD8A84E),
    error = Color(0xFFE07368),
)

// Nel chiaro accento e stati scendono di luminosità per restare leggibili sul bianco.
private val Light = Palette(
    background = Color(0xFFF6F4EF),
    panel = Color(0xFFFFFFFF),
    raised = Color(0xFFEFECE6),
    border = Color(0xFFDDD8CF),
    text = Color(0xFF22201D),
    secondary = Color(0xFF6B665E),
    accent = Color(0xFFB4572F),
    ok = Color(0xFF2F7D71),
    info = Color(0xFF3F6CA8),
    warn = Color(0xFF8E6512),
    error = Color(0xFFB4443A),
)

@Composable
fun palette(): Palette = if (isSystemInDarkTheme()) Dark else Light

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = palette()
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.background, primaryContainer = p.accent.copy(alpha = 0.20f),
            onPrimaryContainer = p.text, secondary = p.secondary, background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.secondary,
            surfaceContainer = p.panel, outline = p.border, outlineVariant = p.border, error = p.error,
            secondaryContainer = p.accent.copy(alpha = 0.20f), onSecondaryContainer = p.text,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, primaryContainer = p.accent.copy(alpha = 0.14f),
            onPrimaryContainer = p.text, secondary = p.secondary, background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.secondary,
            surfaceContainer = p.panel, outline = p.border, outlineVariant = p.border, error = p.error,
            secondaryContainer = p.accent.copy(alpha = 0.14f), onSecondaryContainer = p.text,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
