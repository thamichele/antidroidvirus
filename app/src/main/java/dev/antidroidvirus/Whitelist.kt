package dev.antidroidvirus

import android.content.Context

/**
 * Whitelist per app e per permesso. Ogni voce è "pacchetto|PERMESSO", oppure "pacchetto|*"
 * per fidarsi dell'app su tutti i permessi.
 */
object Whitelist {
    const val ALL = "*"
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
            (trustSystemApps(context) && Apps.isSystem(context, pkg))
    }
}
