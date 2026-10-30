package dev.antidroidvirus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Process
import android.os.UserHandle

/**
 * Protezione all'installazione:
 * - ogni app appena installata viene analizzata prima che tu la apra (un'app non può partire da sola
 *   finché non la avvii la prima volta) e, se è sospetta e Shizuku è attivo, viene sospesa;
 * - un aggiornamento che aggiunge permessi pericolosi viene segnalato;
 * - solo le app nella lista possono avere il permesso "Installa app sconosciute".
 *
 * Le installazioni nel profilo di lavoro di Shelter arrivano da LauncherApps, che avvisa dei cambiamenti
 * negli altri profili dell'utente.
 */
class InstallGuard(private val context: Context, private val handler: Handler) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            handler.post { check(pkg, replacing) }
        }
    }

    private val profileCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = onProfile(packageName, user, false)
        override fun onPackageChanged(packageName: String, user: UserHandle) = onProfile(packageName, user, true)
        override fun onPackageRemoved(packageName: String, user: UserHandle) {}
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {}
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {}
    }

    private fun onProfile(pkg: String, user: UserHandle, replacing: Boolean) {
        if (user == Process.myUserHandle()) return
        handler.post { check(pkg, replacing, Profiles.userId(user)) }
    }

    fun start() {
        context.getSystemService(LauncherApps::class.java).registerCallback(profileCallback, handler)
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            },
            null,
            handler,
        )
        handler.post { catchUp() }
    }

    fun stop() {
        context.getSystemService(LauncherApps::class.java).unregisterCallback(profileCallback)
        context.unregisterReceiver(receiver)
    }

    /** Installazioni avvenute mentre il servizio era spento (es. dopo un riavvio). */
    private fun catchUp() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sequence = prefs.getInt(KEY_SEQUENCE, -1)
        val changed = context.packageManager.getChangedPackages(maxOf(sequence, 0))
        if (sequence >= 0 && changed != null) {
            for (pkg in changed.packageNames) {
                val known = prefs.contains("perms|$pkg")
                check(pkg, replacing = known)
            }
        }
        prefs.edit().putInt(KEY_SEQUENCE, changed?.sequenceNumber ?: maxOf(sequence, 0)).apply()
        checkInstallers()
    }

    /** [profileUser]: utente del profilo di lavoro se l'installazione è avvenuta lì. */
    fun check(pkg: String, replacing: Boolean, profileUser: Int? = null) {
        if (pkg == context.packageName || !Blocker.isPackageName(pkg)) return
        val pm = context.packageManager
        val info = if (profileUser == null) {
            try {
                pm.getPackageInfo(pkg, AppAnalyzer.FLAGS)
            } catch (e: PackageManager.NameNotFoundException) {
                return
            }
        } else {
            // Un'app presente anche nel profilo principale ha lo stesso APK, già controllato lì (es. clonata da Shelter).
            if (Profiles.installedHere(context).contains(pkg)) return
            Profiles.packageInfo(context, pkg, profileUser, AppAnalyzer.FLAGS) ?: return
        }
        if (Apps.isSystem(context, pkg)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val requested = info.requestedPermissions?.toSet() ?: emptySet()
        val previous = prefs.getStringSet("perms|$pkg", null)
        prefs.edit().putStringSet("perms|$pkg", requested).apply()

        if (replacing) {
            val added = requested - (previous ?: requested)
            val dangerous = added.filter { it.substringAfterLast('.') in DANGEROUS_PERMISSIONS }
            if (dangerous.isNotEmpty()) {
                Alerts.update(context, pkg, dangerous.map { it.substringAfterLast('.') })
            }
            return
        }

        val findings = ArrayList<Finding>()
        val hasLauncher = if (profileUser == null) {
            AppAnalyzer.hasLauncher(context, pkg)
        } else {
            Profiles.hasLauncher(context, pkg, profileUser)
        }
        findings += AppAnalyzer.analyze(info, hasLauncher)
        val installer = if (profileUser == null) {
            try {
                pm.getInstallSourceInfo(pkg).installingPackageName
            } catch (e: Exception) {
                null
            }
        } else {
            Profiles.installer(profileUser, pkg)
        }
        if (installer != null && installer !in AppAnalyzer.TRUSTED_INSTALLERS) {
            findings += Finding("Installata fuori dagli store, da ${Apps.label(context, installer)}", 15)
        }
        findings += lookalike(pkg)
        val report = RiskReport(pkg, findings.sortedByDescending { it.weight }, profileUser)
        if (report.level == RiskLevel.LOW) return

        val suspend = report.level == RiskLevel.HIGH && autoSuspend(context) && Blocker.shizukuGranted() &&
            Blocker.exec("pm", "suspend", *userArgs(profileUser), pkg) == 0
        Alerts.installed(context, pkg, report, suspend)
    }

    /** Stesso nome visibile di un'altra app già installata: tipico delle copie contraffatte. */
    private fun lookalike(pkg: String): List<Finding> {
        val pm = context.packageManager
        val label = Apps.label(context, pkg).trim().lowercase()
        if (label.length < 3) return emptyList()
        val twin = pm.getInstalledApplications(0).firstOrNull {
            it.packageName != pkg && pm.getApplicationLabel(it).toString().trim().lowercase() == label
        } ?: return emptyList()
        return listOf(Finding("Ha lo stesso nome di un'altra app già installata (${twin.packageName})", 40))
    }

    /** Chi ha il permesso di installare app senza essere nella lista viene segnalato una volta. */
    fun checkInstallers() {
        val allowed = allowedInstallers(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val warned = prefs.getStringSet(KEY_WARNED, emptySet())!!.toMutableSet()
        val current = AppOpsReader.allowedPackages(context, OP_INSTALL)
            .filter { it != context.packageName && it !in allowed && !Apps.isSystem(context, it) }
        for (pkg in current) {
            if (pkg in warned) continue
            warned += pkg
            Alerts.installer(context, pkg)
        }
        warned.retainAll(current.toSet())
        prefs.edit().putStringSet(KEY_WARNED, warned).apply()
    }

    companion object {
        private const val PREFS = "install_guard"
        private const val KEY_SEQUENCE = "sequence"
        private const val KEY_WARNED = "warned"
        private const val KEY_ALLOWED = "allowed_installers"
        private const val KEY_AUTO_SUSPEND = "auto_suspend"
        const val OP_INSTALL = "android:request_install_packages"

        private val DANGEROUS_PERMISSIONS = setOf(
            "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "RECORD_AUDIO", "CAMERA", "ACCESS_FINE_LOCATION",
            "ACCESS_BACKGROUND_LOCATION", "READ_CONTACTS", "READ_CALL_LOG", "REQUEST_INSTALL_PACKAGES",
            "SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE", "BIND_ACCESSIBILITY_SERVICE", "READ_PHONE_STATE",
        )

        val DEFAULT_INSTALLERS = setOf("org.fdroid.fdroid", "org.fdroid.basic")

        fun allowedInstallers(context: Context): Set<String> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_ALLOWED, DEFAULT_INSTALLERS)!!.toSet()

        fun setInstallerAllowed(context: Context, pkg: String, allowed: Boolean) {
            val set = allowedInstallers(context).toMutableSet()
            if (allowed) set += pkg else set -= pkg
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_ALLOWED, set).apply()
        }

        /** App che oggi hanno davvero il permesso di installare (esclusi i componenti di sistema). */
        fun currentInstallers(context: Context): List<String> =
            AppOpsReader.allowedPackages(context, OP_INSTALL)
                .filter { it != context.packageName && !Apps.isSystem(context, it) }
                .sortedBy { Apps.label(context, it).lowercase() }

        /** Toglie il permesso di installare app via Shizuku. Va chiamato fuori dal main thread. */
        fun revokeInstaller(pkg: String): Boolean =
            Blocker.isPackageName(pkg) && Blocker.exec("appops", "set", pkg, "REQUEST_INSTALL_PACKAGES", "default") == 0

        fun unsuspend(pkg: String, profileUser: Int? = null): Boolean =
            Blocker.isPackageName(pkg) && Blocker.exec("pm", "unsuspend", *userArgs(profileUser), pkg) == 0

        /** "--user N" per i comandi pm che riguardano un'app del profilo di lavoro. */
        fun userArgs(profileUser: Int?): Array<String> =
            if (profileUser == null) emptyArray() else arrayOf("--user", profileUser.toString())

        fun autoSuspend(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_SUSPEND, true)

        fun setAutoSuspend(context: Context, value: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_SUSPEND, value).apply()
        }
    }
}
