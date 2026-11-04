package dev.antidroidvirus

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icone di Arancio UI: 24×24, solo tratto 1.75, estremità e giunture arrotondate.
 * Sono le stesse SVG del kit condiviso con le altre app; il colore arriva dal `tint` di Icon().
 */
object Ico {
    private fun icon(name: String, d: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private const val SHIELD = "M12 3l8 3v6c0 4.5-3.4 8-8 9c-4.6-1-8-4.5-8-9V6z"

    // Navigazione
    val Home by lazy { icon("home", "M4 11l8-7l8 7M6 9.5V20h12V9.5") }
    val History by lazy { icon("registro", "M3.5 12a8.5 8.5 0 1 0 2.5-6M3.5 4v4h4M12 7.5V12l3 2") }
    val Policy by lazy { icon("controllo", "${SHIELD}M11 9a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5zM13 13.5l2 2") }
    val Lock by lazy { icon("lucchetto", "M6 11h12v9H6zM8 11V8a4 4 0 0 1 8 0v3") }

    // Permessi
    val LocationOn by lazy { icon("posizione", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zM12 7.5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z") }
    val PhotoCamera by lazy { icon("fotocamera", "M4 8h3l2-3h6l2 3h3v11H4zM12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z") }
    val Mic by lazy { icon("microfono", "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0zM5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21") }
    val Contacts by lazy { icon("rubrica", "M9 11a3.5 3.5 0 1 0 0-7a3.5 3.5 0 1 0 0 7zM3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 4.5a3.5 3.5 0 0 1 0 6.5M18 14c2 .6 3 2.8 3 6") }
    val Call by lazy { icon("chiamate", "M5 4h4l2 5l-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z") }
    val Sms by lazy { icon("sms", "M4 5h16v11H9l-5 4zM8 9.5h8M8 12.5h5") }
    val ContentPaste by lazy { icon("appunti", "M9 3h6v4H9zM9 5H5v16h14V5h-4") }
    val Photo by lazy { icon("foto", "M4 5h16v14H4zM4 16l5-5l4 4l2-2l5 5M15.5 8.5h.01") }
    val PhoneAndroid by lazy { icon("telefono", "M7 3h10v18H7zM11 18h2") }
    val Sensors by lazy { icon("sensori", "M3 12h4l3-7l4 14l3-7h4") }
    val Bluetooth by lazy { icon("dispositivi vicini", "M7 7l10 10l-5 4V3l5 4L7 17") }

    // Azioni e stato
    val Info by lazy { icon("informazioni", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 11v5M12 8h.01") }
    val Power by lazy { icon("spegni", "M12 3v8M7 6a7.5 7.5 0 1 0 10 0") }
    val Shield by lazy { icon("scudo", "${SHIELD}M9 12l2 2l4-4") }
    val ShieldAlert by lazy { icon("scudo avviso", "${SHIELD}M12 8v4.5M12 15.5h.01") }
    val OpenInNew by lazy { icon("apri", "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6") }
    val Key by lazy { icon("chiave", "M8 11a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM11 12l9-9M17 6l3 3M14.5 8.5l2 2") }
    val ContentCopy by lazy { icon("copia", "M9 9h11v11H9zM5 15H4V4h11v1") }
    val Insights by lazy { icon("utilizzo", "M3 17l6-6l4 4l8-8M15 7h6v6") }
    val Battery by lazy { icon("batteria", "M7 5h10v16H7zM10 3h4v2M12 9v4.5M12 16.5h.01") }
    val Download by lazy { icon("scarica", "M12 4v11M7 10l5 5l5-5M5 20h14") }
    val ArrowBack by lazy { icon("indietro", "M19 12H5M11 6l-6 6l6 6") }
    val Factory by lazy { icon("produttori", "M3 21V11l6 4v-4l6 4V4h5v17zM3 21h18M7 18h2M12 18h2") }
    val Restore by lazy { icon("ripristina", "M4 9h11a5 5 0 0 1 0 10H8M8 5L4 9l4 4") }
    val Import by lazy { icon("importa", "M3 12h11M10 8l4 4l-4 4M14 4h6v16h-6") }
    val Lightbulb by lazy { icon("idea", "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2V16h5v-.1c0-.8.4-1.5 1-2A6 6 0 0 0 12 3z") }
    val Search by lazy { icon("cerca", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4") }
    val Night by lazy { icon("notte", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Tune by lazy { icon("impostazioni", "M4 7h10M18 7h2M16 5v4M4 17h2M10 17h10M8 15v4") }
    val Delete by lazy { icon("elimina", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13") }
    val Pause by lazy { icon("sospendi", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM10 9v6M14 9v6") }
    val InstallMobile by lazy { icon("installa", "M13 3H7v18h10v-7M11 18h2M18 3v8M15 8l3 3l3-3") }
    val Check by lazy { icon("fatto", "M5 12l5 5l9-10") }
    val Palette by lazy { icon("colore", "M12 3a9 9 0 0 0 0 18c1.1 0 1.5-.8 1.5-1.5c0-1.4-1-1.6-1-3c0-1 .8-1.5 1.8-1.5H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8zM7.5 11h.01M10 7.5h.01M15 7.5h.01") }
}
