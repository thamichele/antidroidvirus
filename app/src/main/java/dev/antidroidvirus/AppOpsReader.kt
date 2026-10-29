package dev.antidroidvirus

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

data class OpUsage(
    val pkg: String,
    val op: String,
    val lastAccess: Long,
    val running: Boolean,
    /** L'ultimo accesso è avvenuto con l'app in background (nessuna sua schermata visibile). */
    val background: Boolean,
)

/** Legge lo stato AppOps di tutte le app. Richiede GET_APP_OPS_STATS concesso via ADB. */
object AppOpsReader {
    const val PERMISSION = "android.permission.GET_APP_OPS_STATS"
    private const val TAG = "AppOpsReader"

    // AppOpsManager.OP_FLAGS_ALL: accessi propri, tramite proxy e di attribuzione.
    private const val OP_FLAGS_ALL = 0x1F

    /** Solo le op che questa versione di Android conosce: un nome sconosciuto farebbe fallire tutta la lettura. */
    val WATCHED_OPS: Array<String> by lazy { Perm.allOps.filter(::isSupported).toTypedArray() }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun isSupported(op: String): Boolean = try {
        AppOpsManager::class.java.getMethod("strOpToOp", String::class.java).invoke(null, op)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Op non supportata: $op")
        false
    }

    fun read(context: Context): List<OpUsage> {
        val result = ArrayList<OpUsage>()
        forEachEntry(context, WATCHED_OPS) { pkg, op, entry ->
            val entryClass = entry.javaClass
            val running = entryClass.getMethod("isRunning").invoke(entry) as Boolean
            val lastAccess = entryClass
                .getMethod("getLastAccessTime", Int::class.javaPrimitiveType)
                .invoke(entry, OP_FLAGS_ALL) as Long
            val lastBackground = try {
                entryClass
                    .getMethod("getLastAccessBackgroundTime", Int::class.javaPrimitiveType)
                    .invoke(entry, OP_FLAGS_ALL) as Long
            } catch (e: Exception) {
                -1L
            }
            if (lastAccess > 0 || running) {
                result += OpUsage(pkg, op, lastAccess, running, lastBackground > 0 && lastBackground >= lastAccess)
            }
        }
        return result
    }

    /**
     * Pacchetti per cui una op ha modalità "consentita" (es. overlay, installazione app).
     * AppOps restituisce solo le app con una modalità impostata, che è proprio il caso di un accesso speciale concesso.
     */
    fun allowedPackages(context: Context, op: String): Set<String> {
        if (!isSupported(op)) return emptySet()
        val result = HashSet<String>()
        try {
            forEachEntry(context, arrayOf(op)) { pkg, _, entry ->
                val mode = entry.javaClass.getMethod("getMode").invoke(entry) as Int
                if (mode == AppOpsManager.MODE_ALLOWED) result += pkg
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lettura modalità $op fallita", e)
        }
        return result
    }

    private inline fun forEachEntry(context: Context, ops: Array<String>, block: (String, String, Any) -> Unit) {
        val aom = context.getSystemService(AppOpsManager::class.java)
        val getPackagesForOps = AppOpsManager::class.java
            .getMethod("getPackagesForOps", Array<String>::class.java)
        val packages = getPackagesForOps.invoke(aom, ops as Any) as? List<*> ?: return
        for (pkgOps in packages) {
            if (pkgOps == null) continue
            val pkgClass = pkgOps.javaClass
            val pkg = pkgClass.getMethod("getPackageName").invoke(pkgOps) as String
            val entries = pkgClass.getMethod("getOps").invoke(pkgOps) as List<*>
            for (entry in entries) {
                if (entry == null) continue
                val op = entry.javaClass.getMethod("getOpStr").invoke(entry) as String
                block(pkg, op, entry)
            }
        }
    }

    fun describe(op: String): String = Perm.fromOp(op)?.label ?: op.removePrefix("android:")
}
