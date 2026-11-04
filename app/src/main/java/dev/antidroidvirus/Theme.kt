package dev.antidroidvirus

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Token di "Arancio UI", il kit condiviso con Prendere Nota, MyVault, Gestionale e IPAM:
 * grigi caldi, colore principale a scelta (modalità colori), stati teal / ambra / rosa,
 * JetBrains Mono, raggio 4 per pulsanti e 12 per le card.
 */
data class Palette(
    val background: Color,
    val panel: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val secondary: Color,
    /** Colore principale pieno: pulsanti, interruttori, indicatori. */
    val accent: Color,
    /** Colore principale per testo e link (più chiaro nel tema scuro, per il contrasto). */
    val accentText: Color,
    /** Fondo tenue e bordo tenue del colore principale (pulsanti secondari, card selezionate). */
    val accentDeep: Color,
    val accentBorder: Color,
    /** Testo sopra un fondo pieno del colore principale. */
    val onAccent: Color,
    val ok: Color,
    val info: Color,
    val warn: Color,
    val error: Color,
)

/** Le sei modalità colori del kit. Grigi, layout e colori di stato restano uguali in tutte. */
enum class AccentTheme(
    val label: String,
    val main: Color,
    val text: Color,
    val border: Color,
    val deep: Color,
    val light: Color,
    /** Nome dell'activity-alias del launcher che porta l'icona di questo colore. */
    val alias: String,
) {
    ARANCIO("Arancio", Color(0xFFD96D00), Color(0xFFF08A2A), Color(0xFF6B4214), Color(0xFF36230F), Color(0xFFA85400), "LauncherArancio"),
    VIOLA("Viola", Color(0xFF9D6BFF), Color(0xFFB48CFF), Color(0xFF4A3577), Color(0xFF261E3A), Color(0xFF6D3FD9), "LauncherViola"),
    ROSSO("Rosso", Color(0xFFEC5A5A), Color(0xFFF27C7C), Color(0xFF6E2A2A), Color(0xFF3A1F1F), Color(0xFFC42B2B), "LauncherRosso"),
    GIALLO("Giallo", Color(0xFFE5B000), Color(0xFFF2C230), Color(0xFF6B5410), Color(0xFF332A0F), Color(0xFF8A6A00), "LauncherGiallo"),
    BLU("Blu", Color(0xFF4A90FF), Color(0xFF6BA6FF), Color(0xFF24426E), Color(0xFF17253A), Color(0xFF1F5FCC), "LauncherBlu"),
    VERDE("Verde", Color(0xFF2FB36B), Color(0xFF4FCB87), Color(0xFF1F5A3A), Color(0xFF15291F), Color(0xFF1A7F48), "LauncherVerde"),
}

/** Colore principale scelto dall'utente: salvato nelle preferenze, segue anche l'icona dell'app. */
object Accent {
    private const val PREFS = "aspetto"
    private const val KEY = "colore"

    var current by mutableStateOf(AccentTheme.ARANCIO)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current = AccentTheme.entries.firstOrNull { it.name == saved } ?: AccentTheme.ARANCIO
    }

    fun set(context: Context, theme: AccentTheme) {
        if (theme == current) return
        current = theme
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.name).apply()
        // L'icona nel launcher cambia attivando l'alias del nuovo colore e spegnendo gli altri.
        val pm = context.packageManager
        val ordered = listOf(theme) + AccentTheme.entries.filter { it != theme }
        for (t in ordered) {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "${context.packageName}.${t.alias}"),
                if (t == theme) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

private fun dark(a: AccentTheme) = Palette(
    background = Color(0xFF1C1B1A),
    panel = Color(0xFF252422),
    raised = Color(0xFF2E2C2A),
    border = Color(0xFF3A3734),
    text = Color(0xFFEDEAE3),
    secondary = Color(0xFFA39E95),
    accent = a.main,
    accentText = a.text,
    accentDeep = a.deep,
    accentBorder = a.border,
    onAccent = Color(0xFF1C1B1A),
    ok = Color(0xFF5DB3A6),
    info = Color(0xFF7FA6DB),
    warn = Color(0xFFE0B840),
    error = Color(0xFFE07368),
)

// Nel chiaro il colore principale e gli stati scendono di luminosità per restare leggibili sul bianco.
private fun light(a: AccentTheme) = Palette(
    background = Color(0xFFF6F4EF),
    panel = Color(0xFFFFFFFF),
    raised = Color(0xFFEFECE6),
    border = Color(0xFFDDD8CF),
    text = Color(0xFF22201D),
    secondary = Color(0xFF6B665E),
    accent = a.light,
    accentText = a.light,
    accentDeep = a.light.copy(alpha = 0.10f),
    accentBorder = a.light.copy(alpha = 0.30f),
    onAccent = Color.White,
    ok = Color(0xFF1F6B60),
    info = Color(0xFF3F6CA8),
    warn = Color(0xFF8A6A00),
    error = Color(0xFFA8362B),
)

/** JetBrains Mono (SIL OFL), il carattere di tutte le app del kit. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val AppTypography = Typography().let { t ->
    fun TextStyle.mono() = copy(fontFamily = Mono)
    Typography(
        displayLarge = t.displayLarge.mono(), displayMedium = t.displayMedium.mono(), displaySmall = t.displaySmall.mono(),
        headlineLarge = t.headlineLarge.mono(), headlineMedium = t.headlineMedium.mono(), headlineSmall = t.headlineSmall.mono(),
        titleLarge = t.titleLarge.mono(), titleMedium = t.titleMedium.mono(), titleSmall = t.titleSmall.mono(),
        bodyLarge = t.bodyLarge.mono(), bodyMedium = t.bodyMedium.mono(), bodySmall = t.bodySmall.mono(),
        labelLarge = t.labelLarge.mono(), labelMedium = t.labelMedium.mono(), labelSmall = t.labelSmall.mono(),
    )
}

// Pulsanti e campi squadrati (4), card e finestre a 12.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun palette(): Palette = if (isSystemInDarkTheme()) dark(Accent.current) else light(Accent.current)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = palette()
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentDeep,
            onPrimaryContainer = p.text, secondary = p.secondary, background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.secondary,
            surfaceContainer = p.panel, outline = p.border, outlineVariant = p.border, error = p.error,
            secondaryContainer = p.accent.copy(alpha = 0.20f), onSecondaryContainer = p.text,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentDeep,
            onPrimaryContainer = p.text, secondary = p.secondary, background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text, surfaceVariant = p.raised, onSurfaceVariant = p.secondary,
            surfaceContainer = p.panel, outline = p.border, outlineVariant = p.border, error = p.error,
            secondaryContainer = p.accent.copy(alpha = 0.14f), onSecondaryContainer = p.text,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
