package dev.antidroidvirus

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager

data class OpUsage(
    val pkg: String,
    val op: String,
    val lastAccess: Long,
    val running: Boolean,
)

/** Legge lo stato AppOps di tutte le app. Richiede GET_APP_OPS_STATS concesso via ADB. */
object AppOpsReader {
    const val PERMISSION = "android.permission.GET_APP_OPS_STATS"

    val WATCHED_OPS = arrayOf(
        AppOpsManager.OPSTR_FINE_LOCATION,
        AppOpsManager.OPSTR_COARSE_LOCATION,
        AppOpsManager.OPSTR_CAMERA,
        AppOpsManager.OPSTR_RECORD_AUDIO,
        AppOpsManager.OPSTR_READ_CONTACTS,
        AppOpsManager.OPSTR_READ_CALL_LOG,
        AppOpsManager.OPSTR_READ_SMS,
        "android:read_clipboard",
    )

    // AppOpsManager.OP_FLAGS_ALL: accessi propri, tramite proxy e di attribuzione.
    private const val OP_FLAGS_ALL = 0x1F

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): List<OpUsage> {
        val aom = context.getSystemService(AppOpsManager::class.java)
        val getPackagesForOps = AppOpsManager::class.java
            .getMethod("getPackagesForOps", Array<String>::class.java)
        val packages = getPackagesForOps.invoke(aom, WATCHED_OPS as Any) as? List<*> ?: return emptyList()

        val result = ArrayList<OpUsage>()
        for (pkgOps in packages) {
            if (pkgOps == null) continue
            val pkgClass = pkgOps.javaClass
            val pkg = pkgClass.getMethod("getPackageName").invoke(pkgOps) as String
            val ops = pkgClass.getMethod("getOps").invoke(pkgOps) as List<*>
            for (entry in ops) {
                if (entry == null) continue
                val entryClass = entry.javaClass
                val op = entryClass.getMethod("getOpStr").invoke(entry) as String
                val running = entryClass.getMethod("isRunning").invoke(entry) as Boolean
                val lastAccess = entryClass
                    .getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                    .invoke(entry, OP_FLAGS_ALL) as Long
                if (lastAccess > 0 || running) {
                    result += OpUsage(pkg, op, lastAccess, running)
                }
            }
        }
        return result
    }

    fun describe(op: String): String = when (op) {
        AppOpsManager.OPSTR_FINE_LOCATION -> "la posizione precisa"
        AppOpsManager.OPSTR_COARSE_LOCATION -> "la posizione"
        AppOpsManager.OPSTR_CAMERA -> "la fotocamera"
        AppOpsManager.OPSTR_RECORD_AUDIO -> "il microfono"
        AppOpsManager.OPSTR_READ_CONTACTS -> "i contatti"
        AppOpsManager.OPSTR_READ_CALL_LOG -> "il registro chiamate"
        AppOpsManager.OPSTR_READ_SMS -> "gli SMS"
        "android:read_clipboard" -> "gli appunti"
        else -> op.removePrefix("android:")
    }
}
