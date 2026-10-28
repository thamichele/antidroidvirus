package dev.antidroidvirus

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

/**
 * Spegnimento immediato: ferma il servizio, cancella le notifiche, disattiva l'avvio al boot
 * e termina il processo. Al prossimo avvio l'app parte spenta finché non si riattiva il monitoraggio.
 */
object KillSwitch {
    fun engage(context: Context) {
        val app = context.applicationContext
        MonitorService.stop(app)
        setBootReceiverEnabled(app, false)
        app.getSystemService(NotificationManager::class.java).cancelAll()
        Process.killProcess(Process.myPid())
    }

    fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
