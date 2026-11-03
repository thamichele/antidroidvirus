package dev.antidroidvirus

import android.content.Context
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Produttore di un gruppo di app: chi le ha firmate. Il nome viene dal certificato (es. "Samsung", "Google"),
 * ma la fiducia si salva per chiave di firma: un'app qualsiasi può scrivere "Google" nel suo certificato,
 * non può firmare con la chiave di Google. Un produttore può avere più chiavi: le vedi tutte insieme.
 */
data class Vendor(
    val name: String,
    /** SHA-256 delle chiavi di firma del gruppo. */
    val keys: Set<String>,
    val apps: List<VendorApp>,
) {
    val systemCount: Int get() = apps.count { it.system }
}

data class VendorApp(val pkg: String, val label: String, val system: Boolean)

object Vendors {
    /** Prefisso delle voci di whitelist per chiave: "#sha256|AMBITO". */
    const val PREFIX = "#"

    fun entryKey(digest: String) = "$PREFIX$digest"

    /** SHA-256 della chiave di firma di un'app, anche se è solo nel profilo di lavoro. */
    fun signerOf(context: Context, pkg: String): String? {
        val info = Profiles.packageInfo(context, pkg, Profiles.myUserId(), PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return null
        return AppAnalyzer.signerDigest(info)
    }

    /** Raggruppa le app per produttore. Va chiamato fuori dal main thread: legge ogni pacchetto. */
    fun group(context: Context, apps: List<VendorApp>): List<Vendor> {
        val byKey = LinkedHashMap<String, MutableList<VendorApp>>()
        val names = HashMap<String, String>()
        for (app in apps) {
            val info = Profiles.packageInfo(context, app.pkg, Profiles.myUserId(), PackageManager.GET_SIGNING_CERTIFICATES)
                ?: continue
            val cert = AppAnalyzer.firstSigner(info) ?: continue
            val digest = MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
            byKey.getOrPut(digest) { ArrayList() } += app
            if (digest !in names) names[digest] = nameOf(cert) ?: fromPackages(app.pkg)
        }
        // Chiavi diverse con lo stesso nome (Google ne usa parecchie) diventano un solo produttore. Ma il nome nel
        // certificato lo sceglie chi firma: se un nome ha chiavi con app di sistema, quelle senza restano a parte,
        // così un'app che si spaccia per "Google" non entra nel gruppo delle app preinstallate.
        val withSystem = names.filterKeys { key -> byKey[key].orEmpty().any { it.system } }.values.toSet()
        return byKey.entries
            .groupBy { (key, list) ->
                val name = names[key] ?: "Sconosciuto"
                if (name in withSystem && list.none { it.system }) "$name · altre firme" else name
            }
            .map { (name, groups) ->
                Vendor(name, groups.map { it.key }.toSet(), groups.flatMap { it.value }.sortedBy { it.label.lowercase() })
            }
            .sortedWith(compareByDescending<Vendor> { it.apps.size }.thenBy { it.name.lowercase() })
    }

    /** Nome leggibile dal certificato: organizzazione, o in mancanza il nome comune. */
    private fun nameOf(bytes: ByteArray): String? = try {
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        val parts = attributes(cert.subjectX500Principal.name)
        val org = parts["O"]
        val cn = parts["CN"]
        normalize(org?.takeIf { it.isNotBlank() && !it.equals("Android", true) } ?: cn)
    } catch (e: Exception) {
        null
    }

    /** "CN=Android,O=Google Inc.,C=US" → mappa; le virgole precedute da "\\" fanno parte del valore. */
    private fun attributes(dn: String): Map<String, String> {
        val result = HashMap<String, String>()
        val current = StringBuilder()
        var escaped = false
        fun flush() {
            val part = current.toString()
            val eq = part.indexOf('=')
            if (eq > 0) result.putIfAbsent(part.substring(0, eq).trim().uppercase(), part.substring(eq + 1).trim())
            current.clear()
        }
        for (c in dn) {
            when {
                escaped -> { current.append(c); escaped = false }
                c == '\\' -> escaped = true
                c == ',' -> flush()
                else -> current.append(c)
            }
        }
        flush()
        return result
    }

    private fun normalize(raw: String?): String? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Unknown", true) } ?: return null
        return when {
            name.contains("Samsung", true) -> "Samsung"
            name.contains("Google", true) -> "Google"
            name.equals("Android", true) || name.contains("Android Debug", true) -> "Android (chiave di piattaforma)"
            else -> name.removeSuffix(".").removeSuffix(" Inc").removeSuffix(" Inc.").removeSuffix(" LLC")
        }
    }

    /** Ultima spiaggia: il prefisso del pacchetto (es. "com.whatsapp" → "whatsapp"). */
    private fun fromPackages(pkg: String): String =
        pkg.split('.').getOrNull(1)?.replaceFirstChar { it.uppercase() } ?: pkg
}
