package dev.antidroidvirus

import android.content.Context

/**
 * App da cui dipendono account, aggiornamenti, telefono e tastiera. Togliere loro un permesso (anche solo
 * "Telefono" o "Contatti") porta a "sessione scaduta", account Google o Samsung da ricollegare, Play Store o
 * Galaxy Store che non aggiornano, notifiche di errore. Prima di revocare si chiede conferma.
 */
object CriticalApps {
    private val PACKAGES = setOf(
        // Google: servizi, framework, Play Store
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.ext.services",
        "com.google.android.gms.supervision",
        "com.google.android.apps.messaging",
        "com.google.android.dialer",
        "com.google.android.contacts",
        "com.google.android.inputmethod.latin",
        // Samsung: account, cloud, store, servizi
        "com.osp.app.signin",
        "com.samsung.android.mobileservice",
        "com.samsung.android.scloud",
        "com.sec.android.app.samsungapps",
        "com.samsung.android.fmm",
        "com.samsung.android.samsungpass",
        "com.samsung.android.authfw",
        "com.samsung.android.spayfw",
        "com.samsung.android.honeyboard",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.samsung.android.messaging",
        "com.samsung.android.app.contacts",
        "com.samsung.android.lool",
        "com.sec.android.app.launcher",
        "com.samsung.android.knox.attestation",
        // Android
        "android",
        "com.android.phone",
        "com.android.systemui",
        "com.android.settings",
        "com.android.providers.contacts",
        "com.android.providers.telephony",
        "com.android.server.telecom",
        "com.android.shell",
    )

    fun isCritical(pkg: String) = pkg in PACKAGES

    /** Perché revocare è rischioso, oppure null se l'app è una normale app installata. */
    fun warning(context: Context, pkg: String): String? = when {
        isCritical(pkg) ->
            "È un servizio da cui dipendono account, aggiornamenti o telefono. Togliergli un permesso può far " +
                "scadere la sessione di Google o Samsung, bloccare Play Store e Galaxy Store o riempirti di notifiche di errore."
        Apps.isSystem(context, pkg) ->
            "È un'app di sistema. Togliere permessi alle app preinstallate a volte rompe funzioni del telefono " +
                "(notifiche, sincronizzazione, account)."
        else -> null
    }

    fun installed(context: Context): List<String> = PACKAGES.filter {
        try {
            context.packageManager.getPackageInfo(it, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES)
            true
        } catch (e: Exception) {
            false
        }
    }
}
