package dev.antidroidvirus

import android.content.Context
import android.util.Log

/**
 * Confronta lo stato AppOps con l'ultima lettura: ogni accesso con un orario più recente
 * di quello già visto diventa un evento. Grazie a lastAccessTime nessun uso va perso,
 * anche se la lettura arriva in ritardo (schermo spento, Doze).
 *
 * Ogni evento porta con sé il contesto (background, schermo spento, notte, frequenza insolita):
 * le app fuori whitelist avvisano sempre, quelle fidate solo per comportamenti insoliti.
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
        // Posizione precisa e approssimativa arrivano insieme: un solo evento per app e permesso.
        val fresh = HashMap<String, Pair<OpUsage, Perm>>()
        for (usage in usages) {
            if (usage.pkg == context.packageName) continue
            // Le copie nel profilo di lavoro hanno una chiave a parte: stessa app, permessi diversi.
            val key = if (usage.user == Profiles.myUserId()) "${usage.pkg}|${usage.op}" else "${usage.pkg}@${usage.user}|${usage.op}"
            val previous = prefs.getLong(key, 0L)
            if (usage.lastAccess <= previous) continue
            editor.putLong(key, usage.lastAccess)
            // Alla prima esecuzione si registra solo il punto di partenza, senza avvisi sul passato.
            if (firstRun) continue
            val perm = Perm.fromOp(usage.op) ?: continue
            val group = "${usage.pkg}@${usage.user}|${perm.name}"
            val current = fresh[group]
            if (current == null || usage.lastAccess > current.first.lastAccess) fresh[group] = usage to perm
        }

        for ((usage, perm) in fresh.values) {
            val trusted = Whitelist.isTrusted(context, usage.pkg, perm)
            val flags = contextFlags(store, usage, perm)
            store.add(UsageEvent(usage.lastAccess, usage.pkg, usage.op, trusted, flags, usage.user))
            when {
                !trusted -> Alerts.alert(context, usage.pkg, perm, usage.lastAccess, flags, usage.user)
                isAnomalous(usage.pkg, perm, flags) -> Alerts.anomaly(context, usage.pkg, perm, usage.lastAccess, flags, usage.user)
            }
        }
        editor.putBoolean(KEY_BASELINE, true).apply()
        store.pruneOlderThan(System.currentTimeMillis() - RETENTION_MS)
    }

    private fun contextFlags(store: EventStore, usage: OpUsage, perm: Perm): Int {
        var flags = 0
        if (usage.background) flags = flags or UsageEvent.FLAG_BACKGROUND
        if (ScreenLog.wasOff(context, usage.lastAccess)) flags = flags or UsageEvent.FLAG_SCREEN_OFF
        if (ScreenLog.isNight(usage.lastAccess)) flags = flags or UsageEvent.FLAG_NIGHT
        if (isFrequent(store, usage, perm)) flags = flags or UsageEvent.FLAG_FREQUENT
        return flags
    }

    /**
     * Ultime 24 ore contro la media giornaliera dei 7 giorni prima. Le letture sono al massimo una al minuto,
     * quindi il conteggio vale come "minuti con almeno un uso": approssimato, ma confrontabile nel tempo.
     */
    private fun isFrequent(store: EventStore, usage: OpUsage, perm: Perm): Boolean {
        val now = usage.lastAccess
        val today = store.count(usage.pkg, usage.user, perm.ops, now - DAY_MS, now + 1) + 1
        val before = store.count(usage.pkg, usage.user, perm.ops, now - 8 * DAY_MS, now - DAY_MS)
        if (before == 0) return false
        val average = before / 7.0
        return today >= FREQUENT_MIN && today >= average * FREQUENT_FACTOR
    }

    /** Per le app fidate: fotocamera o microfono di notte a schermo spento, oppure molto più spesso del solito. */
    private fun isAnomalous(pkg: String, perm: Perm, flags: Int): Boolean {
        if (Apps.isSystem(context, pkg) || Whitelist.ignoresAnomalies(context, pkg)) return false
        val offAtNight = flags and (UsageEvent.FLAG_SCREEN_OFF or UsageEvent.FLAG_NIGHT) ==
            (UsageEvent.FLAG_SCREEN_OFF or UsageEvent.FLAG_NIGHT)
        return (perm.sensitive && offAtNight) || flags and UsageEvent.FLAG_FREQUENT != 0
    }

    companion object {
        private const val TAG = "Monitor"
        private const val KEY_BASELINE = "__baseline_done"
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val FREQUENT_MIN = 20
        private const val FREQUENT_FACTOR = 3.0

        /** Descrizione leggibile del contesto di un uso, es. "a schermo spento, di notte". */
        fun describeFlags(flags: Int): String = buildList {
            if (flags and UsageEvent.FLAG_SCREEN_OFF != 0) add("a schermo spento")
            else if (flags and UsageEvent.FLAG_BACKGROUND != 0) add("in background")
            if (flags and UsageEvent.FLAG_NIGHT != 0) add("di notte")
            if (flags and UsageEvent.FLAG_FREQUENT != 0) add("molto più spesso del solito")
        }.joinToString(", ")
    }
}
