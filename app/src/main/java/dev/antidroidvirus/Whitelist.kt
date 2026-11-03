package dev.antidroidvirus

import android.content.Context

/**
 * Whitelist per app e per permesso. Ogni voce è "pacchetto|PERMESSO", oppure "pacchetto|*"
 * per fidarsi dell'app su tutti i permessi. Due ambiti a parte silenziano le euristiche:
 * [ANOMALY] gli avvisi di comportamento insolito, [CHECKS] la pagina Controllo.
 *
 * Le voci che iniziano con [Vendors.PREFIX] valgono per un produttore: al posto del pacchetto c'è la sua
 * chiave di firma, e la fiducia copre tutte le app firmate con quella chiave.
 */
object Whitelist {
    const val ALL = "*"
    const val ANOMALY = "INSOLITO"
    const val CHECKS = "CONTROLLI"
    private const val PREFS = "whitelist"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_LEGACY_PACKAGES = "packages"
    private const val KEY_TRUST_SYSTEM = "trust_system"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun entries(context: Context): Set<String> {
        val prefs = prefs(context)
        prefs.getStringSet(KEY_ENTRIES, null)?.let { return it.toSet() }
        // v0.1 salvava solo pacchetti interi: diventano "fidata su tutto".
        val migrated = prefs.getStringSet(KEY_LEGACY_PACKAGES, emptySet())!!.map { "$it|$ALL" }.toSet()
        prefs.edit().putStringSet(KEY_ENTRIES, migrated).apply()
        return migrated
    }

    /** Ambiti fidati di un'app: nomi di [Perm] oppure [ALL]. */
    fun scopes(context: Context, pkg: String): Set<String> =
        entries(context).filter { it.startsWith("$pkg|") }.map { it.substringAfter('|') }.toSet()

    fun setTrusted(context: Context, pkg: String, scope: String, trusted: Boolean) {
        val updated = entries(context).toMutableSet()
        if (trusted) updated += "$pkg|$scope" else updated -= "$pkg|$scope"
        prefs(context).edit().putStringSet(KEY_ENTRIES, updated).apply()
    }

    fun trustSystemApps(context: Context): Boolean = prefs(context).getBoolean(KEY_TRUST_SYSTEM, true)

    fun setTrustSystemApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRUST_SYSTEM, value).apply()
    }

    fun isTrusted(context: Context, pkg: String, perm: Perm): Boolean {
        if (pkg == context.packageName) return true
        val entries = entries(context)
        return "$pkg|$ALL" in entries ||
            "$pkg|${perm.name}" in entries ||
            (trustSystemApps(context) && Apps.isSystem(context, pkg)) ||
            vendorTrusts(context, entries, pkg, ALL, perm.name)
    }

    fun ignoresAnomalies(context: Context, pkg: String): Boolean {
        val entries = entries(context)
        return "$pkg|$ANOMALY" in entries || vendorTrusts(context, entries, pkg, ANOMALY)
    }

    fun ignoresChecks(context: Context, pkg: String): Boolean {
        val entries = entries(context)
        return "$pkg|$CHECKS" in entries || vendorTrusts(context, entries, pkg, CHECKS)
    }

    /** true se il produttore (la chiave di firma) di [pkg] è fidato su uno degli ambiti. */
    private fun vendorTrusts(context: Context, entries: Set<String>, pkg: String, vararg scopes: String): Boolean {
        // Si legge la firma solo se c'è almeno un produttore in whitelist.
        if (entries.none { it.startsWith(Vendors.PREFIX) }) return false
        val key = Vendors.entryKey(Vendors.signerOf(context, pkg) ?: return false)
        return scopes.any { "$key|$it" in entries }
    }

    /** Ambiti fidati comuni a tutte le chiavi di un produttore. */
    fun vendorScopes(entries: Set<String>, vendor: Vendor): Set<String> =
        vendor.keys.map { key ->
            entries.filter { it.startsWith("${Vendors.entryKey(key)}|") }.map { it.substringAfter('|') }.toSet()
        }.reduceOrNull { a, b -> a intersect b } ?: emptySet()

    fun setVendorTrusted(context: Context, vendor: Vendor, scope: String, trusted: Boolean) {
        val updated = entries(context).toMutableSet()
        for (key in vendor.keys) {
            val entry = "${Vendors.entryKey(key)}|$scope"
            if (trusted) updated += entry else updated -= entry
        }
        prefs(context).edit().putStringSet(KEY_ENTRIES, updated).apply()
    }

    /**
     * Copia nella whitelist i permessi che Android ha già concesso: ogni app diventa fidata sui permessi
     * sorvegliati che le hai dato. Non toglie nulla. Ritorna quante voci ha aggiunto.
     */
    fun importGranted(context: Context, granted: Map<String, Set<String>>): Int {
        val updated = entries(context).toMutableSet()
        val before = updated.size
        for ((pkg, permissions) in granted) {
            if (pkg == context.packageName) continue
            for (perm in Perm.entries) {
                if (perm.permissions.any { it in permissions }) updated += "$pkg|${perm.name}"
            }
        }
        prefs(context).edit().putStringSet(KEY_ENTRIES, updated).apply()
        return updated.size - before
    }
}
