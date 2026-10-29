package dev.antidroidvirus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_KILL) {
            KillSwitch.engage(context)
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
        }
        val id = intent.getIntExtra(Alerts.EXTRA_NOTIFICATION, 0)
        if (id != 0) Alerts.dismiss(context, id) else if (perm != null) Alerts.dismiss(context, pkg, perm)
    }

    companion object {
        const val ACTION_TRUST = "dev.antidroidvirus.TRUST"
        const val ACTION_TRUST_ANOMALY = "dev.antidroidvirus.TRUST_ANOMALY"
        const val ACTION_TRUST_CHECKS = "dev.antidroidvirus.TRUST_CHECKS"
        const val ACTION_IGNORE = "dev.antidroidvirus.IGNORE"
        const val ACTION_KILL = "dev.antidroidvirus.KILL"
    }
}
