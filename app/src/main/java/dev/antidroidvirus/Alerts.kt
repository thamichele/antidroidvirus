package dev.antidroidvirus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Avvisa per le app fuori whitelist: fino a [REPEATS] notifiche, a [INTERVAL_MS] di distanza. */
object Alerts {
    const val CHANNEL_ALERTS = "alerts"
    const val REPEATS = 3
    private const val INTERVAL_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private val active = HashSet<String>()

    fun key(pkg: String, op: String) = "$pkg|$op"

    fun alert(context: Context, pkg: String, op: String, time: Long) {
        val key = key(pkg, op)
        val app = context.applicationContext
        handler.post {
            if (!active.add(key)) return@post
            send(app, pkg, op, time, 1)
        }
    }

    private fun send(context: Context, pkg: String, op: String, time: Long, count: Int) {
        val key = key(pkg, op)
        if (key !in active) return
        if (Whitelist.isTrusted(context, pkg)) {
            stop(context, key)
            return
        }
        val label = Apps.label(context, pkg)
        val at = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(time))
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$label sta usando ${AppOpsReader.describe(op)}!")
            .setContentText("Avviso $count di $REPEATS · alle $at · app non in whitelist")
            .setCategory(Notification.CATEGORY_ALARM)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setDeleteIntent(action(context, ActionReceiver.ACTION_IGNORE, pkg, op))
            .addAction(
                Notification.Action.Builder(null, "Fidati", action(context, ActionReceiver.ACTION_TRUST, pkg, op)).build()
            )
            .addAction(
                Notification.Action.Builder(null, "Ignora", action(context, ActionReceiver.ACTION_IGNORE, pkg, op)).build()
            )
            .build()
        context.getSystemService(NotificationManager::class.java).notify(key.hashCode(), notification)

        if (count < REPEATS) {
            handler.postDelayed({ send(context, pkg, op, time, count + 1) }, key, INTERVAL_MS)
        } else {
            active.remove(key)
        }
    }

    /** Ferma le ripetizioni e chiude la notifica. Va chiamato sul main thread. */
    fun stop(context: Context, key: String) {
        active.remove(key)
        handler.removeCallbacksAndMessages(key)
        context.getSystemService(NotificationManager::class.java).cancel(key.hashCode())
    }

    fun stopAllFor(context: Context, pkg: String) {
        for (op in AppOpsReader.WATCHED_OPS) stop(context, key(pkg, op))
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, action: String, pkg: String, op: String): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(ActionReceiver.EXTRA_PKG, pkg)
            .putExtra(ActionReceiver.EXTRA_OP, op)
        return PendingIntent.getBroadcast(
            context, "$action|$pkg|$op".hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
