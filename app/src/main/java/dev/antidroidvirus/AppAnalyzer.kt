package dev.antidroidvirus

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class Finding(val text: String, val weight: Int)

data class RiskReport(
    val pkg: String,
    val findings: List<Finding>,
    /** Utente del profilo di lavoro (Shelter) se l'app è lì, null se è nel profilo principale. */
    val profileUser: Int? = null,
    val frozen: Boolean = false,
    /** Permessi sorvegliati concessi a questa copia (titoli di [Perm]); null se non si può sapere. */
    val granted: List<String>? = null,
) {
    /** Chiave unica: la stessa app può comparire sia nel profilo principale sia in quello di lavoro. */
    val key: String get() = if (profileUser == null) pkg else "$pkg@$profileUser"
    val score: Int get() = findings.sumOf { it.weight }.coerceAtMost(100)
    val level: RiskLevel get() = when {
        score >= RiskLevel.HIGH.threshold -> RiskLevel.HIGH
        score >= RiskLevel.MEDIUM.threshold -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
}

enum class RiskLevel(val threshold: Int, val title: String) {
    LOW(0, "Bassa"),
    MEDIUM(25, "Attenzione"),
    HIGH(50, "Da controllare"),
}

/**
 * Analisi statica di un pacchetto: cosa dichiara nel manifest e come è firmato. Funziona sia sulle app
 * installate sia sugli APK non ancora installati (PackageManager.getPackageArchiveInfo).
 */
object AppAnalyzer {
    const val FLAGS = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or PackageManager.GET_SIGNING_CERTIFICATES

    private const val BIND_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"
    private const val BIND_DEVICE_ADMIN = "android.permission.BIND_DEVICE_ADMIN"
    private const val BIND_NOTIFICATIONS = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    private const val BIND_VPN = "android.permission.BIND_VPN_SERVICE"

    /** Store da cui un'installazione non è considerata sideload. */
    val TRUSTED_INSTALLERS = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "org.fdroid.fdroid",
        "org.fdroid.basic",
        "com.looker.droidify",
        "com.machiav3lli.fdroid",
        "dev.imranr.obtainium",
    )

    fun analyze(info: PackageInfo, hasLauncher: Boolean?): List<Finding> {
        val findings = ArrayList<Finding>()
        val requested = info.requestedPermissions?.toSet() ?: emptySet()
        fun has(vararg perms: String) = perms.any { "android.permission.$it" in requested }

        if (info.services?.any { it.permission == BIND_ACCESSIBILITY } == true) {
            findings += Finding("Può chiedere l'accessibilità: vedere e toccare tutto lo schermo", 10)
        }
        if (info.receivers?.any { it.permission == BIND_DEVICE_ADMIN } == true) {
            findings += Finding("Può diventare amministratore del dispositivo", 10)
        }
        if (info.services?.any { it.permission == BIND_NOTIFICATIONS } == true) {
            findings += Finding("Può leggere le notifiche di tutte le app", 5)
        }
        if (info.services?.any { it.permission == BIND_VPN } == true) {
            findings += Finding("Può fare da VPN e vedere il traffico", 5)
        }
        if (has("SEND_SMS")) findings += Finding("Può inviare SMS", 10)
        if (has("RECEIVE_SMS", "READ_SMS")) findings += Finding("Può leggere gli SMS, anche i codici di accesso", 10)
        if (has("REQUEST_INSTALL_PACKAGES")) findings += Finding("Può installare altre app", 10)
        if (has("SYSTEM_ALERT_WINDOW")) findings += Finding("Può disegnare sopra le altre app", 5)
        if (has("MANAGE_EXTERNAL_STORAGE")) findings += Finding("Può leggere tutti i file", 5)
        if (has("CAMERA") && has("RECORD_AUDIO") && has("ACCESS_FINE_LOCATION") && has("READ_CONTACTS")) {
            findings += Finding("Chiede insieme fotocamera, microfono, posizione e contatti", 10)
        }
        if (hasLauncher == false) {
            val dangerous = has("RECORD_AUDIO", "CAMERA", "READ_SMS", "RECEIVE_SMS", "ACCESS_FINE_LOCATION", "READ_CONTACTS")
            if (dangerous) findings += Finding("Non ha un'icona nel launcher ma chiede permessi sensibili", 20)
            if (dangerous && has("RECEIVE_BOOT_COMPLETED")) findings += Finding("Parte da sola all'accensione senza icona", 10)
        }
        val target = info.applicationInfo?.targetSdkVersion ?: 0
        if (target in 1..28) findings += Finding("Fatta per Android $target o precedenti: aggira alcune protezioni", 10)
        if ((info.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            findings += Finding("È una build di debug", 5)
        }
        if (signerSubject(info)?.contains("CN=Android Debug") == true) {
            findings += Finding("Firmata con una chiave di debug pubblica", 5)
        }
        return findings
    }

    fun hasLauncher(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    /** SHA-256 del primo certificato di firma, in esadecimale. */
    fun signerDigest(info: PackageInfo): String? {
        val cert = firstSigner(info) ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
    }

    private fun signerSubject(info: PackageInfo): String? = try {
        val bytes = firstSigner(info) ?: return null
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        cert.subjectX500Principal.name
    } catch (e: Exception) {
        null
    }

    private fun firstSigner(info: PackageInfo): ByteArray? {
        val signing = info.signingInfo ?: return null
        val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return signers?.firstOrNull()?.toByteArray()
    }
}
