package dev.antidroidvirus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Una notifica per app e permesso fuori whitelist. Per non subire raffiche (un'app che legge
 * la posizione ogni minuto) la stessa coppia non riavvisa prima di [COOLDOWN_MS]; gli usi
 * restano comunque nel registro.
 */
object Alerts {
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_CHECKS = "checks"
    private const val COOLDOWN_MS = 10 * 60_000L

    private val lastAlert = HashMap<String, Long>()

    fun notificationId(pkg: String, perm: Perm) = "$pkg|${perm.name}".hashCode()

    fun anomalyId(pkg: String, perm: Perm) = "$pkg|${perm.name}|anomaly".hashCode()

    fun checkId(pkg: String) = "$pkg|check".hashCode()

    fun alert(context: Context, pkg: String, perm: Perm, time: Long, flags: Int = 0) {
        if (!cooledDown("$pkg|${perm.name}")) return
        val label = Apps.label(context, pkg)
        val at = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(time))
        val where = Monitor.describeFlags(flags).let { if (it.isEmpty()) "" else " $it" }
        val id = notificationId(pkg, perm)
        val notification = base(context, CHANNEL_ALERTS, "Uso di un permesso rilevato")
            .setContentTitle("$label sta usando ${perm.label}!")
            .setContentText("Alle $at$where · app non in whitelist per ${perm.title.lowercase(Locale.ITALIAN)}")
            .addAction(action("Fidati", broadcast(context, ActionReceiver.ACTION_TRUST, pkg, perm, id)))
            .addAction(action("Blocca", block(context, pkg, perm)))
            .addAction(action("Ignora", broadcast(context, ActionReceiver.ACTION_IGNORE, pkg, perm, id)))
            .build()
        notify(context, id, notification)
    }

    /** Un'app fidata fa qualcosa fuori dal suo solito: "Fidati" silenzia solo questi avvisi per quell'app. */
    fun anomaly(context: Context, pkg: String, perm: Perm, time: Long, flags: Int) {
        if (!cooledDown("$pkg|${perm.name}|anomaly")) return
        val label = Apps.label(context, pkg)
        val at = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(time))
        val id = anomalyId(pkg, perm)
        val notification = base(context, CHANNEL_ALERTS, "Comportamento insolito rilevato")
            .setContentTitle("Comportamento insolito di $label")
            .setContentText("Ha usato ${perm.label} alle $at, ${Monitor.describeFlags(flags)}")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "Ha usato ${perm.label} alle $at, ${Monitor.describeFlags(flags)}. " +
                        "L'app è in whitelist, ma di solito non lo fa."
                )
            )
            .addAction(action("Fidati", broadcast(context, ActionReceiver.ACTION_TRUST_ANOMALY, pkg, perm, id)))
            .addAction(action("Blocca", block(context, pkg, perm)))
            .addAction(action("Ignora", broadcast(context, ActionReceiver.ACTION_IGNORE, pkg, perm, id)))
            .build()
        notify(context, id, notification)
    }

    /** Un'app ha superato la soglia della pagina Controllo. */
    fun check(context: Context, pkg: String, report: RiskReport) {
        val label = Apps.label(context, pkg)
        val id = checkId(pkg)
        val reasons = report.findings.joinToString("\n") { "• ${it.text}" }
        val notification = base(context, CHANNEL_CHECKS, "App da controllare")
            .setContentTitle("$label è da controllare")
            .setContentText(report.findings.first().text)
            .setStyle(Notification.BigTextStyle().bigText(reasons))
            .addAction(action("Va bene così", broadcast(context, ActionReceiver.ACTION_TRUST_CHECKS, pkg, null, id)))
            .addAction(action("Ignora", broadcast(context, ActionReceiver.ACTION_IGNORE, pkg, null, id)))
            .build()
        notify(context, id, notification)
    }

    fun dismiss(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    fun dismiss(context: Context, pkg: String, perm: Perm) {
        dismiss(context, notificationId(pkg, perm))
        dismiss(context, anomalyId(pkg, perm))
    }

    fun dismissAll(context: Context, pkg: String) {
        for (perm in Perm.entries) dismiss(context, pkg, perm)
    }

    private fun cooledDown(key: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lastAlert) {
            if (now - (lastAlert[key] ?: 0L) < COOLDOWN_MS) return false
            lastAlert[key] = now
        }
        return true
    }

    fun base(context: Context, channel: String, publicTitle: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setCategory(Notification.CATEGORY_ALARM)
            // Sulla schermata di blocco non si mostra quale app né quale permesso.
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setContentTitle(publicTitle)
                    .build()
            )
            .setAutoCancel(true)
            .setContentIntent(openApp(context))

    fun notify(context: Context, id: Int, notification: Notification) {
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun action(title: String, intent: PendingIntent) =
        Notification.Action.Builder(null, title, intent).build()

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    fun broadcast(context: Context, action: String, pkg: String, perm: Perm?, notificationId: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_PERM, perm?.name)
            .putExtra(EXTRA_NOTIFICATION, notificationId)
        return PendingIntent.getBroadcast(
            context, "$action|$notificationId".hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    // "Blocca" apre un'activity: da Android 12 una notifica non può avviare activity passando da un receiver.
    private fun block(context: Context, pkg: String, perm: Perm): PendingIntent {
        val intent = Intent(context, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_PERM, perm.name)
        return PendingIntent.getActivity(
            context, "block|$pkg|${perm.name}".hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    const val EXTRA_PKG = "pkg"
    const val EXTRA_PERM = "perm"
    const val EXTRA_NOTIFICATION = "notification"
}
