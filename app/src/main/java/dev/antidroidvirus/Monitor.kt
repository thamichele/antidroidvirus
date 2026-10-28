package dev.antidroidvirus

import android.content.Context
import android.util.Log

/**
 * Confronta lo stato AppOps con l'ultima lettura: ogni accesso con un orario più recente
 * di quello già visto diventa un evento. Grazie a lastAccessTime nessun uso va perso,
 * anche se la lettura arriva in ritardo (schermo spento, Doze).
 */
class Monitor(private val context: Context) {
    private val prefs = context.getSharedPreferences("last_seen", Context.MODE_PRIVATE)

    fun poll() {
        if (!AppOpsReader.hasPermission(context)) return
        val usages = try {
            AppOpsReader.read(context)
        } catch (e: Exception) {
            Log.e(TAG, "Lettura AppOps fallita", e)
            return
        }

        val firstRun = !prefs.getBoolean(KEY_BASELINE, false)
        val editor = prefs.edit()
        val store = EventStore.get(context)
        for (usage in usages) {
            if (usage.pkg == context.packageName) continue
            val key = "${usage.pkg}|${usage.op}"
            val previous = prefs.getLong(key, 0L)
            if (usage.lastAccess <= previous) continue
            editor.putLong(key, usage.lastAccess)
            // Alla prima esecuzione si registra solo il punto di partenza, senza avvisi sul passato.
            if (firstRun) continue

            val trusted = Whitelist.isTrusted(context, usage.pkg)
            store.add(UsageEvent(usage.lastAccess, usage.pkg, usage.op, trusted))
            if (!trusted) Alerts.alert(context, usage.pkg, usage.op, usage.lastAccess)
        }
        editor.putBoolean(KEY_BASELINE, true).apply()
        store.pruneOlderThan(System.currentTimeMillis() - RETENTION_MS)
    }

    companion object {
        private const val TAG = "Monitor"
        private const val KEY_BASELINE = "__baseline_done"
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
