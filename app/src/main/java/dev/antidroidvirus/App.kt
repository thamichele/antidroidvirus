package dev.antidroidvirus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // AppOpsManager.getPackagesForOps e le classi PackageOps/OpEntry sono API nascoste.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Alerts.CHANNEL_ALERTS, "Allarmi permessi", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(Alerts.CHANNEL_CHECKS, "App da controllare", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(MonitorService.CHANNEL_SERVICE, "Monitoraggio attivo", NotificationManager.IMPORTANCE_MIN)
        )
    }
}
