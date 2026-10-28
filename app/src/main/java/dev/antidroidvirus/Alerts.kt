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
    private const val COOLDOWN_MS = 10 * 60_000L

    private val lastAlert = HashMap<String, Long>()

    fun notificationId(pkg: String, perm: Perm) = "$pkg|${perm.name}".hashCode()

    fun alert(context: Context, pkg: String, perm: Perm, time: Long) {
        val key = "$pkg|${perm.name}"
        val now = System.currentTimeMillis()
        synchronized(lastAlert) {
            if (now - (lastAlert[key] ?: 0L) < COOLDOWN_MS) return
            lastAlert[key] = now
        }

        val label = Apps.label(context, pkg)
        val at = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(time))
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$label sta usando ${perm.label}!")
            .setContentText("Alle $at · app non in whitelist per ${perm.title.lowercase(Locale.ITALIAN)}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(action(context, "Fidati", broadcast(context, ActionReceiver.ACTION_TRUST, pkg, perm)))
            .addAction(action(context, "Blocca", block(context, pkg, perm)))
            .addAction(action(context, "Ignora", broadcast(context, ActionReceiver.ACTION_IGNORE, pkg, perm)))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notificationId(pkg, perm), notification)
    }

    fun dismiss(context: Context, pkg: String, perm: Perm) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(pkg, perm))
    }

    fun dismissAll(context: Context, pkg: String) {
        for (perm in Perm.entries) dismiss(context, pkg, perm)
    }

    private fun action(context: Context, title: String, intent: PendingIntent) =
        Notification.Action.Builder(null, title, intent).build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun broadcast(context: Context, action: String, pkg: String, perm: Perm): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_PKG, pkg)
            .putExtra(EXTRA_PERM, perm.name)
        return PendingIntent.getBroadcast(
            context, "$action|$pkg|${perm.name}".hashCode(), intent,
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
}
