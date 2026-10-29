package dev.antidroidvirus

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.util.Locale

/**
 * Pagina "Controllo": per ogni app installata dall'utente somma gli indizi (manifest, provenienza,
 * accessi speciali attivi, comportamento registrato, traffico in background) in un punteggio 0-100.
 */
object RiskCheck {
    private const val TAG = "RiskCheck"
    private const val PREFS = "risk"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MB = 1024L * 1024

    @Volatile
    var lastReports: List<RiskReport> = emptyList()
        private set

    @Volatile
    var lastRun: Long = 0L
        private set

    fun hasUsageAccess(context: Context): Boolean {
        val aom = context.getSystemService(AppOpsManager::class.java)
        return aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** Analizza tutte le app e avvisa per quelle che superano la soglia per la prima volta (o con indizi nuovi). */
    fun runAndNotify(context: Context) {
        val reports = run(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (report in reports) {
            if (report.level != RiskLevel.HIGH || Whitelist.ignoresChecks(context, report.pkg)) continue
            val signature = report.findings.map { it.text.substringBefore(':') }.sorted().joinToString("|").hashCode()
            if (prefs.getInt("notified|${report.pkg}", 0) == signature) continue
            editor.putInt("notified|${report.pkg}", signature)
            Alerts.check(context, report.pkg, report)
        }
        editor.apply()
    }

    @Synchronized
    fun run(context: Context): List<RiskReport> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val accessibility = enabledAccessibility(context)
        val admins = context.getSystemService(DevicePolicyManager::class.java).activeAdmins
            ?.map { it.packageName }?.toSet() ?: emptySet()
        val notificationListeners = notificationListeners()
        val overlay = AppOpsReader.allowedPackages(context, "android:system_alert_window")
        val installers = AppOpsReader.allowedPackages(context, "android:request_install_packages")
        val usageAccess = AppOpsReader.allowedPackages(context, "android:get_usage_stats")
        val backgroundUpload = if (hasUsageAccess(context)) backgroundTxBytes(context, now - DAY_MS, now) else emptyMap()
        val events = EventStore.get(context).since(now - 7 * DAY_MS).groupBy { it.pkg }

        val reports = ArrayList<RiskReport>()
        for (app in pm.getInstalledApplications(0)) {
            val pkg = app.packageName
            if (pkg == context.packageName || Apps.isSystem(context, pkg)) continue
            val info = try {
                pm.getPackageInfo(pkg, AppAnalyzer.FLAGS)
            } catch (e: Exception) {
                continue
            }
            val findings = ArrayList<Finding>()

            if (pkg in accessibility) findings += Finding("Accessibilità attiva: vede e controlla lo schermo", 40)
            if (pkg in admins) findings += Finding("È amministratore del dispositivo", 40)
            if (pkg in notificationListeners) findings += Finding("Legge le notifiche di tutte le app", 25)
            if (pkg in installers) findings += Finding("Ha il permesso di installare app", 15)
            if (pkg in overlay) findings += Finding("Può disegnare sopra le altre app", 10)
            if (pkg in usageAccess) findings += Finding("Vede quali app usi", 10)

            // Le voci statiche già coperte da un accesso speciale attivo non si contano due volte.
            findings += AppAnalyzer.analyze(info, AppAnalyzer.hasLauncher(context, pkg)).filterNot { static ->
                (static.text.startsWith("Può chiedere l'accessibilità") && pkg in accessibility) ||
                    (static.text.startsWith("Può diventare amministratore") && pkg in admins) ||
                    (static.text.startsWith("Può leggere le notifiche") && pkg in notificationListeners) ||
                    (static.text.startsWith("Può installare") && pkg in installers) ||
                    (static.text.startsWith("Può disegnare") && pkg in overlay)
            }

            findings += provenance(context, pkg, info.firstInstallTime, now)

            val digest = AppAnalyzer.signerDigest(info)
            val known = prefs.getString("signer|$pkg", null)
            if (digest != null) {
                if (known != null && known != digest) {
                    findings += Finding("La firma è cambiata: potrebbe essere stata sostituita da una copia", 50)
                } else if (known == null) {
                    editor.putString("signer|$pkg", digest)
                }
            }

            val appEvents = events[pkg].orEmpty()
            val spying = appEvents.count {
                Perm.fromOp(it.op)?.sensitive == true && it.has(UsageEvent.FLAG_SCREEN_OFF)
            }
            if (spying > 0) findings += Finding("Fotocamera o microfono a schermo spento: $spying volte in 7 giorni", 20)
            val night = appEvents.count { it.has(UsageEvent.FLAG_NIGHT) && it.has(UsageEvent.FLAG_BACKGROUND) }
            if (night >= 3) findings += Finding("Usa permessi in background di notte: $night volte in 7 giorni", 5)
            if (appEvents.any { it.has(UsageEvent.FLAG_FREQUENT) }) {
                findings += Finding("Ha usato permessi molto più spesso del solito", 10)
            }

            val uploaded = backgroundUpload[info.applicationInfo?.uid ?: -1] ?: 0L
            val recentSpying = appEvents.any {
                it.time >= now - DAY_MS && Perm.fromOp(it.op)?.sensitive == true && it.has(UsageEvent.FLAG_SCREEN_OFF)
            }
            if (recentSpying && uploaded >= 5 * MB) {
                findings += Finding("Ha inviato ${mb(uploaded)} in background dopo aver usato fotocamera o microfono", 25)
            } else if (uploaded >= 100 * MB) {
                findings += Finding("Ha inviato ${mb(uploaded)} in background nelle ultime 24 ore", 10)
            }

            if (findings.isNotEmpty()) reports += RiskReport(pkg, findings.sortedByDescending { it.weight })
        }
        editor.apply()
        val sorted = reports.sortedByDescending { it.score }
        lastReports = sorted
        lastRun = now
        return sorted
    }

    /** Da dove arriva l'app: store, PC o un'app qualsiasi (browser, messaggi, file manager). */
    private fun provenance(context: Context, pkg: String, installed: Long, now: Long): List<Finding> {
        val findings = ArrayList<Finding>()
        val installer = try {
            context.packageManager.getInstallSourceInfo(pkg).installingPackageName
        } catch (e: Exception) {
            null
        }
        when {
            installer == null -> findings += Finding("Installata da PC o da una fonte sconosciuta", 5)
            installer !in AppAnalyzer.TRUSTED_INSTALLERS ->
                findings += Finding("Installata fuori dagli store, da ${Apps.label(context, installer)}", 15)
        }
        val days = ((now - installed) / DAY_MS).toInt()
        if (days < 7) findings += Finding(if (days == 0) "Installata oggi" else "Installata da $days giorni", 5)
        return findings
    }

    private fun enabledAccessibility(context: Context): Set<String> = try {
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .toSet()
    } catch (e: Exception) {
        emptySet()
    }

    /** L'elenco dei lettori di notifiche si legge in modo affidabile solo da shell, quindi serve Shizuku. */
    private fun notificationListeners(): Set<String> {
        if (!Blocker.shizukuGranted()) return emptySet()
        val raw = Blocker.output("settings", "get", "secure", "enabled_notification_listeners") ?: return emptySet()
        return raw.trim().split(':').mapNotNull { it.substringBefore('/').takeIf { p -> p.isNotBlank() && p != "null" } }.toSet()
    }

    /** Byte inviati per UID con l'app non in primo piano, su Wi-Fi e rete mobile. */
    private fun backgroundTxBytes(context: Context, start: Long, end: Long): Map<Int, Long> {
        val nsm = context.getSystemService(NetworkStatsManager::class.java)
        val totals = HashMap<Int, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                nsm.querySummary(type, null, start, end).use { stats ->
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) {
                        stats.getNextBucket(bucket)
                        if (bucket.state == NetworkStats.Bucket.STATE_FOREGROUND) continue
                        totals[bucket.uid] = (totals[bucket.uid] ?: 0L) + bucket.txBytes
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Statistiche di rete non disponibili per il tipo $type", e)
            }
        }
        return totals
    }

    private fun mb(bytes: Long) = String.format(Locale.ITALIAN, "%.0f MB", bytes / MB.toDouble())

    /** Dimentica la firma registrata, es. dopo che l'utente ha confermato una reinstallazione voluta. */
    fun forgetSigner(context: Context, pkg: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("signer|$pkg").apply()
    }
}
