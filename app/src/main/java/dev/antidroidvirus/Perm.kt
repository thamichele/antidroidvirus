package dev.antidroidvirus

import android.Manifest
import android.app.AppOpsManager

/** Permessi sorvegliati, raggruppati come li vede l'utente (posizione precisa e approssimativa = Posizione). */
enum class Perm(
    val title: String,
    val label: String,
    val ops: List<String>,
    val permissions: List<String>,
    /** Op da mettere su "ignore" quando non esiste un permesso runtime da revocare. */
    val appOps: List<String> = emptyList(),
) {
    LOCATION(
        "Posizione", "la posizione",
        listOf(AppOpsManager.OPSTR_FINE_LOCATION, AppOpsManager.OPSTR_COARSE_LOCATION),
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ),
    ),
    CAMERA("Fotocamera", "la fotocamera", listOf(AppOpsManager.OPSTR_CAMERA), listOf(Manifest.permission.CAMERA)),
    MICROPHONE("Microfono", "il microfono", listOf(AppOpsManager.OPSTR_RECORD_AUDIO), listOf(Manifest.permission.RECORD_AUDIO)),
    CONTACTS("Contatti", "i contatti", listOf(AppOpsManager.OPSTR_READ_CONTACTS), listOf(Manifest.permission.READ_CONTACTS)),
    CALL_LOG("Registro chiamate", "il registro chiamate", listOf(AppOpsManager.OPSTR_READ_CALL_LOG), listOf(Manifest.permission.READ_CALL_LOG)),
    SMS("SMS", "gli SMS", listOf(AppOpsManager.OPSTR_READ_SMS), listOf(Manifest.permission.READ_SMS)),
    CLIPBOARD("Appunti", "gli appunti", listOf("android:read_clipboard"), emptyList(), listOf("READ_CLIPBOARD"));

    companion object {
        val allOps: Array<String> = entries.flatMap { it.ops }.toTypedArray()

        fun fromOp(op: String): Perm? = entries.firstOrNull { op in it.ops }
    }
}
