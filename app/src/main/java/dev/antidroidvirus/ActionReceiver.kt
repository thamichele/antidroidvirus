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
        val perm = intent.getStringExtra(Alerts.EXTRA_PERM)?.let { Perm.valueOf(it) } ?: return
        if (intent.action == ACTION_TRUST) Whitelist.setTrusted(context, pkg, perm.name, true)
        Alerts.dismiss(context, pkg, perm)
    }

    companion object {
        const val ACTION_TRUST = "dev.antidroidvirus.TRUST"
        const val ACTION_IGNORE = "dev.antidroidvirus.IGNORE"
        const val ACTION_KILL = "dev.antidroidvirus.KILL"
    }
}
