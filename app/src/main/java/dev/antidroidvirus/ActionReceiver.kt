package dev.antidroidvirus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.widget.Toast
import java.io.File

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_KILL) {
            KillSwitch.engage(context)
            return
        }
        val id = intent.getIntExtra(Alerts.EXTRA_NOTIFICATION, 0)
        val path = intent.getStringExtra(Alerts.EXTRA_PATH)
        if (path != null) {
            if (intent.action == ACTION_DELETE_FILE && DownloadWatcher.isInDownloads(path)) {
                val deleted = File(path).delete()
                if (deleted) {
                    FileLog.remove(context, path)
                    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
                Toast.makeText(context, if (deleted) "File eliminato" else "Non sono riuscito a eliminarlo", Toast.LENGTH_SHORT).show()
            }
            if (id != 0) Alerts.dismiss(context, id)
            return
        }

        val pkg = intent.getStringExtra(Alerts.EXTRA_PKG) ?: return
        val perm = intent.getStringExtra(Alerts.EXTRA_PERM)?.let { Perm.valueOf(it) }
        when (intent.action) {
            ACTION_TRUST -> if (perm != null) Whitelist.setTrusted(context, pkg, perm.name, true)
            ACTION_TRUST_ANOMALY -> Whitelist.setTrusted(context, pkg, Whitelist.ANOMALY, true)
            ACTION_TRUST_CHECKS -> {
                Whitelist.setTrusted(context, pkg, Whitelist.CHECKS, true)
                RiskCheck.forgetSigner(context, pkg)
            }
            ACTION_ALLOW_INSTALLER -> InstallGuard.setInstallerAllowed(context, pkg, true)
            ACTION_UNSUSPEND, ACTION_REVOKE_INSTALLER, ACTION_UNINSTALL_PROFILE -> {
                // I comandi Shizuku non vanno eseguiti sul main thread.
                val pending = goAsync()
                val app = context.applicationContext
                val user = intent.getIntExtra(Alerts.EXTRA_USER, -1).takeIf { it >= 0 }
                Thread {
                    val label = Apps.label(app, pkg)
                    val ok = when (intent.action) {
                        ACTION_UNSUSPEND -> InstallGuard.unsuspend(pkg, user)
                        ACTION_UNINSTALL_PROFILE -> user != null && Profiles.uninstall(pkg, user)
                        else -> InstallGuard.revokeInstaller(pkg)
                    }
                    val message = when {
                        !ok -> "Shizuku non disponibile: operazione non riuscita"
                        intent.action == ACTION_UNSUSPEND -> "$label riattivata"
                        intent.action == ACTION_UNINSTALL_PROFILE -> "$label disinstallata dal profilo di lavoro"
                        else -> "$label non può più installare app"
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                    }
                    pending.finish()
                }.start()
            }
        }
        if (id != 0) Alerts.dismiss(context, id) else if (perm != null) Alerts.dismiss(context, pkg, perm)
    }

    companion object {
        const val ACTION_TRUST = "dev.antidroidvirus.TRUST"
        const val ACTION_TRUST_ANOMALY = "dev.antidroidvirus.TRUST_ANOMALY"
        const val ACTION_TRUST_CHECKS = "dev.antidroidvirus.TRUST_CHECKS"
        const val ACTION_IGNORE = "dev.antidroidvirus.IGNORE"
        const val ACTION_KILL = "dev.antidroidvirus.KILL"
        const val ACTION_DELETE_FILE = "dev.antidroidvirus.DELETE_FILE"
        const val ACTION_IGNORE_FILE = "dev.antidroidvirus.IGNORE_FILE"
        const val ACTION_UNSUSPEND = "dev.antidroidvirus.UNSUSPEND"
        const val ACTION_REVOKE_INSTALLER = "dev.antidroidvirus.REVOKE_INSTALLER"
        const val ACTION_ALLOW_INSTALLER = "dev.antidroidvirus.ALLOW_INSTALLER"
        const val ACTION_UNINSTALL_PROFILE = "dev.antidroidvirus.UNINSTALL_PROFILE"
    }
}
