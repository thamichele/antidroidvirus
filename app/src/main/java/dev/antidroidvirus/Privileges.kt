package dev.antidroidvirus

import android.content.Context
import android.os.Environment
import android.os.PowerManager

/**
 * Concede all'app i permessi che altrimenti richiedono ADB dal PC, usando Shizuku (che gira come
 * utente shell). I permessi restano anche dopo un riavvio e anche con Shizuku spento.
 */
object Privileges {
    /** true se manca almeno uno dei permessi che Shizuku può concedere. */
    fun missing(context: Context): Boolean =
        !AppOpsReader.hasPermission(context) || !RiskCheck.hasUsageAccess(context) ||
            !Environment.isExternalStorageManager() || !batteryUnrestricted(context)

    fun batteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Va chiamato fuori dal main thread. Ritorna i permessi che non è riuscito a concedere. */
    fun grantAll(context: Context): List<String> {
        val pkg = context.packageName
        val failed = ArrayList<String>()
        if (!AppOpsReader.hasPermission(context) && Blocker.exec("pm", "grant", pkg, AppOpsReader.PERMISSION) != 0) {
            failed += "permesso AppOps"
        }
        if (!RiskCheck.hasUsageAccess(context) && Blocker.exec("appops", "set", pkg, "GET_USAGE_STATS", "allow") != 0) {
            failed += "dati di utilizzo"
        }
        if (!Environment.isExternalStorageManager() &&
            Blocker.exec("appops", "set", pkg, "MANAGE_EXTERNAL_STORAGE", "allow") != 0
        ) {
            failed += "accesso ai file"
        }
        // Equivale a "App mai in sospensione": One UI non ferma più il servizio.
        if (!batteryUnrestricted(context) && Blocker.exec("dumpsys", "deviceidle", "whitelist", "+$pkg") != 0) {
            failed += "batteria senza restrizioni"
        }
        Blocker.exec("pm", "grant", pkg, "android.permission.POST_NOTIFICATIONS")
        return failed
    }
}
