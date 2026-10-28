package dev.antidroidvirus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val op = intent.getStringExtra(EXTRA_OP) ?: return
        when (intent.action) {
            ACTION_TRUST -> {
                Whitelist.setTrusted(context, pkg, true)
                Alerts.stopAllFor(context, pkg)
            }
            ACTION_IGNORE -> Alerts.stop(context, Alerts.key(pkg, op))
        }
    }

    companion object {
        const val ACTION_TRUST = "dev.antidroidvirus.TRUST"
        const val ACTION_IGNORE = "dev.antidroidvirus.IGNORE"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_OP = "op"
    }
}
