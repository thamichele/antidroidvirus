package dev.antidroidvirus

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import rikka.shizuku.Shizuku

/**
 * Activity invisibile aperta dal pulsante "Blocca". Con Shizuku revoca il permesso;
 * senza, apre la pagina di sistema dei permessi dell'app per farlo a mano.
 */
class BlockActivity : Activity() {
    private lateinit var pkg: String
    private lateinit var perm: Perm
    private var user = 0

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
        if (requestCode != Blocker.SHIZUKU_REQUEST_CODE) return@OnRequestPermissionResultListener
        if (result == PackageManager.PERMISSION_GRANTED) revoke() else openSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Hardening.protect(this)
        val pkgExtra = intent.getStringExtra(Alerts.EXTRA_PKG)
        val permExtra = intent.getStringExtra(Alerts.EXTRA_PERM)
        if (pkgExtra == null || permExtra == null) {
            finish()
            return
        }
        pkg = pkgExtra
        perm = Perm.valueOf(permExtra)
        user = intent.getIntExtra(Alerts.EXTRA_USER, Profiles.myUserId())
        Alerts.dismiss(this, pkg, perm, user)

        when {
            Blocker.shizukuGranted() -> revoke()
            Blocker.shizukuRunning() -> {
                Shizuku.addRequestPermissionResultListener(permissionListener)
                Shizuku.requestPermission(Blocker.SHIZUKU_REQUEST_CODE)
            }
            else -> openSettings()
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun revoke() {
        val label = Alerts.labelOf(this, pkg, user)
        Thread {
            val ok = Blocker.revoke(pkg, perm, user)
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "${perm.title} revocata a $label", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    openSettings()
                }
            }
        }.start()
    }

    private fun openSettings() {
        Toast.makeText(
            this,
            "Shizuku non disponibile: togli ${perm.label} a ${Alerts.labelOf(this, pkg, user)} da qui",
            Toast.LENGTH_LONG,
        ).show()
        // La copia nel profilo di lavoro ha la sua pagina di sistema, raggiungibile da LauncherApps.
        if (user == Profiles.myUserId() || !Profiles.openSettings(this, pkg, user)) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }
}
