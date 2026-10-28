package dev.antidroidvirus

import android.content.Context

object Whitelist {
    private const val PREFS = "whitelist"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_TRUST_SYSTEM = "trust_system"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun packages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PACKAGES, emptySet())!!.toSet()

    fun setTrusted(context: Context, pkg: String, trusted: Boolean) {
        val updated = packages(context).toMutableSet()
        if (trusted) updated += pkg else updated -= pkg
        prefs(context).edit().putStringSet(KEY_PACKAGES, updated).apply()
    }

    fun trustSystemApps(context: Context): Boolean = prefs(context).getBoolean(KEY_TRUST_SYSTEM, true)

    fun setTrustSystemApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRUST_SYSTEM, value).apply()
    }

    fun isTrusted(context: Context, pkg: String): Boolean =
        pkg == context.packageName ||
            pkg in packages(context) ||
            (trustSystemApps(context) && Apps.isSystem(context, pkg))
}
