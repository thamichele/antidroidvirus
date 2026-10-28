package dev.antidroidvirus

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/** Revoca permessi ad altre app eseguendo comandi come utente shell tramite Shizuku. */
object Blocker {
    const val SHIZUKU_REQUEST_CODE = 42
    private const val TAG = "Blocker"
    private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun shizukuGranted(): Boolean =
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    /** Va chiamato fuori dal main thread. Ritorna true se almeno una revoca è andata a buon fine. */
    fun revoke(pkg: String, perm: Perm): Boolean {
        // Il nome pacchetto finisce in un comando eseguito come shell: solo caratteri validi.
        if (!PACKAGE_NAME.matches(pkg)) return false
        var revoked = false
        // pm revoke fallisce per i permessi che l'app non ha mai chiesto: si ignora.
        for (permission in perm.permissions) {
            if (exec("pm", "revoke", pkg, permission) == 0) revoked = true
        }
        for (op in perm.appOps) {
            if (exec("appops", "set", pkg, op, "ignore") == 0) revoked = true
        }
        return revoked
    }

    private fun exec(vararg command: String): Int = try {
        // Shizuku.newProcess è privato dalla API 13 ma resta il modo più semplice di eseguire un comando shell.
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
        val process = newProcess.invoke(null, arrayOf(*command), null, null) as Process
        process.waitFor()
    } catch (e: Exception) {
        Log.e(TAG, "Comando fallito: ${command.joinToString(" ")}", e)
        -1
    }
}
