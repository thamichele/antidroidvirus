package dev.antidroidvirus

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

data class FileReport(
    val path: String,
    val name: String,
    val size: Long,
    val time: Long,
    val sha256: String,
    val type: String,
    val findings: List<Finding>,
) {
    val score: Int get() = findings.sumOf { it.weight }.coerceAtMost(100)
    val verdict: FileVerdict get() = when {
        score >= FileVerdict.DANGEROUS.threshold -> FileVerdict.DANGEROUS
        score >= FileVerdict.SUSPICIOUS.threshold -> FileVerdict.SUSPICIOUS
        else -> FileVerdict.OK
    }
}

enum class FileVerdict(val threshold: Int, val title: String) {
    OK(0, "Nessun indizio"),
    SUSPICIOUS(20, "Sospetto"),
    DANGEROUS(50, "Pericoloso"),
}

/**
 * Controllo base dei file scaricati, senza firme: riconosce il tipo reale dai primi byte,
 * lo confronta con l'estensione e cerca dentro i formati più usati le parti che servono agli attacchi
 * (JavaScript nei PDF, macro nei documenti Office, APK ed eseguibili negli archivi, trucchi sul nome).
 */
object FileScanner {
    private const val MAX_TEXT_SCAN = 32L * 1024 * 1024

    private val EXECUTABLE_EXT = setOf(
        "apk", "xapk", "apks", "apkm", "dex", "exe", "scr", "com", "pif", "msi", "bat", "cmd", "js", "jse", "vbs",
        "vbe", "wsf", "ps1", "hta", "jar", "lnk", "sh", "elf", "so", "dll", "reg", "iso", "img",
    )
    private val DOCUMENT_EXT = setOf(
        "pdf", "doc", "docx", "docm", "xls", "xlsx", "xlsm", "ppt", "pptx", "pptm", "odt", "ods", "odp", "rtf",
        "txt", "csv", "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "mp3", "mp4", "m4a", "wav", "mov", "avi",
        "mkv", "html", "htm", "svg", "zip", "rar", "7z",
    )
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
    private val MEDIA_EXT = setOf("mp3", "mp4", "m4a", "wav", "ogg", "opus", "mov", "avi", "mkv", "webm", "3gp")
    private val OOXML_EXT = setOf("docx", "docm", "xlsx", "xlsm", "pptx", "pptm", "dotm", "xltm")
    private val ODF_EXT = setOf("odt", "ods", "odp")
    private val ZIP_EXT = setOf("zip", "jar")
    private val APK_EXT = setOf("apk")
    private val BUNDLE_EXT = setOf("xapk", "apks", "apkm")
    private val OLE_EXT = setOf("doc", "xls", "ppt", "msi", "dot", "xlt")
    private val HTML_EXT = setOf("html", "htm", "xhtml", "shtml", "mht", "mhtml")

    fun scan(context: Context, file: File): FileReport {
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val head = readHead(file, 64)
        val type = detect(head, file)
        val findings = ArrayList<Finding>()

        findings += nameTricks(name, ext)
        findings += mismatch(ext, type)

        try {
            when (type) {
                "apk" -> findings += apk(context, file)
                "zip" -> findings += zip(context, file, ext)
                "pdf" -> findings += pdf(file)
                "ole" -> findings += ole(file)
                "rtf" -> findings += rtf(file)
                "html", "svg" -> findings += html(file, type, ext)
                "windows" -> findings += Finding("È un programma per Windows", 40)
                "elf" -> findings += Finding("È un programma eseguibile Linux/Android", 30)
                "script" -> findings += Finding("È uno script eseguibile", 20)
                "lnk" -> findings += Finding("È un collegamento Windows, usato per lanciare comandi", 40)
                "iso" -> findings += Finding("È un'immagine disco, spesso usata per nascondere eseguibili", 20)
                "rar", "7z" -> findings += Finding("Archivio ${type.uppercase(Locale.ROOT)}: il contenuto non si può controllare", 5)
                "csv" -> findings += csv(file)
            }
        } catch (e: Exception) {
            findings += Finding("Il file è danneggiato o in un formato non standard", 10)
        }
        if (ext == "hta") findings += Finding("È un'applicazione HTML per Windows", 50)

        return FileReport(
            file.absolutePath, name, file.length(), System.currentTimeMillis(), sha256(file), type,
            findings.sortedByDescending { it.weight },
        )
    }

    /** Tipo reale dai primi byte; per i file di testo guarda l'inizio del contenuto. */
    fun detect(head: ByteArray, file: File): String {
        fun starts(vararg bytes: Int) = head.size >= bytes.size && bytes.indices.all { head[it] == bytes[it].toByte() }
        return when {
            starts(0x50, 0x4B, 0x03, 0x04) || starts(0x50, 0x4B, 0x05, 0x06) ->
                if (isApk(file)) "apk" else "zip"
            starts(0x25, 0x50, 0x44, 0x46) -> "pdf"
            starts(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> "ole"
            starts(0x7B, 0x5C, 0x72, 0x74, 0x66) -> "rtf"
            starts(0x4D, 0x5A) -> "windows"
            starts(0x7F, 0x45, 0x4C, 0x46) -> "elf"
            starts(0x64, 0x65, 0x78, 0x0A) -> "dex"
            starts(0x4C, 0x00, 0x00, 0x00, 0x01, 0x14, 0x02, 0x00) -> "lnk"
            starts(0x52, 0x61, 0x72, 0x21) -> "rar"
            starts(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> "7z"
            starts(0x1F, 0x8B) -> "gzip"
            starts(0xFF, 0xD8, 0xFF) -> "jpg"
            starts(0x89, 0x50, 0x4E, 0x47) -> "png"
            starts(0x47, 0x49, 0x46, 0x38) -> "gif"
            starts(0x42, 0x4D) -> "bmp"
            starts(0x52, 0x49, 0x46, 0x46) -> "riff"
            starts(0x49, 0x44, 0x33) || starts(0xFF, 0xFB) || starts(0xFF, 0xF3) -> "mp3"
            starts(0x4F, 0x67, 0x67, 0x53) -> "ogg"
            starts(0x1A, 0x45, 0xDF, 0xA3) -> "mkv"
            head.size >= 12 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> "mp4"
            starts(0x23, 0x21) -> "script"
            isIso(file) -> "iso"
            else -> textType(head)
        }
    }

    private fun textType(head: ByteArray): String {
        val text = String(head, Charsets.ISO_8859_1).trimStart('\uFEFF', '\u00EF', '\u00BB', '\u00BF', ' ', '\n', '\r', '\t')
            .lowercase(Locale.ROOT)
        return when {
            text.startsWith("<svg") || (text.startsWith("<?xml") && text.contains("<svg")) -> "svg"
            text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head") ||
                text.startsWith("<script") -> "html"
            text.startsWith("<") -> "xml"
            head.isNotEmpty() && head.all { it >= 0x09 || it < 0 } -> "csv"
            else -> "unknown"
        }
    }

    private fun nameTricks(name: String, ext: String): List<Finding> {
        val findings = ArrayList<Finding>()
        if (name.any { it == '\u202E' || it == '\u202D' || it == '\u200F' }) {
            findings += Finding("Il nome contiene caratteri che invertono il testo per nascondere l'estensione vera", 60)
        }
        val parts = name.lowercase(Locale.ROOT).split('.')
        if (parts.size >= 3 && ext in EXECUTABLE_EXT && parts[parts.size - 2] in DOCUMENT_EXT) {
            findings += Finding("Doppia estensione: sembra .${parts[parts.size - 2]} ma è .$ext", 40)
        }
        if (Regex(" {5,}\\.[A-Za-z0-9]+$").containsMatchIn(name)) {
            findings += Finding("Molti spazi prima dell'estensione per nasconderla", 20)
        }
        return findings
    }

    /** Il tipo reale non corrisponde all'estensione: un ".pdf" che è un APK è il caso classico. */
    private fun mismatch(ext: String, type: String): List<Finding> {
        if (ext.isEmpty()) return emptyList()
        val dangerousType = type in setOf("apk", "windows", "elf", "dex", "lnk", "script")
        val expected: Set<String>? = when {
            ext == "pdf" -> setOf("pdf")
            ext in IMAGE_EXT -> setOf("jpg", "png", "gif", "bmp", "riff", "mp4", "unknown")
            ext in MEDIA_EXT -> setOf("mp3", "mp4", "riff", "ogg", "mkv", "unknown")
            ext in OOXML_EXT || ext in ODF_EXT -> setOf("zip")
            ext in OLE_EXT -> setOf("ole")
            ext == "rtf" -> setOf("rtf")
            ext in ZIP_EXT -> setOf("zip")
            ext in APK_EXT -> setOf("apk")
            ext == "txt" || ext == "csv" -> setOf("csv", "unknown", "xml", "html")
            else -> null
        }
        return when {
            expected == null || type in expected -> emptyList()
            ext in APK_EXT && type == "zip" -> emptyList()
            dangerousType -> listOf(Finding("Estensione falsa: si chiama .$ext ma è ${describeType(type)}", 60))
            type == "unknown" -> emptyList()
            else -> listOf(Finding("L'estensione .$ext non corrisponde al contenuto (${describeType(type)})", 20))
        }
    }

    private fun describeType(type: String) = when (type) {
        "apk" -> "un'app Android"
        "windows" -> "un programma Windows"
        "elf" -> "un eseguibile"
        "dex" -> "codice Android"
        "lnk" -> "un collegamento Windows"
        "script" -> "uno script"
        "zip" -> "un archivio ZIP"
        "pdf" -> "un PDF"
        "ole" -> "un documento Office vecchio formato"
        "html" -> "una pagina web"
        else -> "un file $type"
    }

    private fun apk(context: Context, file: File): List<Finding> {
        val findings = ArrayList<Finding>()
        findings += Finding("È un'app Android da installare", 10)
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, AppAnalyzer.FLAGS)
            ?: return findings + Finding("Android non riesce a leggere l'app: potrebbe essere manomessa", 30)
        findings += AppAnalyzer.analyze(info, null)
        findings += impersonation(context, info.packageName, AppAnalyzer.signerDigest(info))
        return findings
    }

    /** Stesso nome pacchetto di un'app installata ma firma diversa: è una copia contraffatta. */
    fun impersonation(context: Context, pkg: String, digest: String?): List<Finding> {
        if (digest == null) return emptyList()
        val installed = try {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        val installedDigest = AppAnalyzer.signerDigest(installed) ?: return emptyList()
        return if (installedDigest != digest) {
            listOf(Finding("Si presenta come ${Apps.label(context, pkg)} ma è firmata da un altro autore", 60))
        } else {
            emptyList()
        }
    }

    private fun zip(context: Context, file: File, ext: String): List<Finding> {
        val findings = ArrayList<Finding>()
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val names = entries.map { it.name.lowercase(Locale.ROOT) }
            val total = entries.sumOf { maxOf(it.size, 0L) }
            if (total > 2L * 1024 * 1024 * 1024 || (file.length() > 0 && total / file.length() > 200)) {
                findings += Finding("Si espande in modo anomalo (archivio bomba)", 20)
            }
            if (names.any { it.endsWith(".apk") }) {
                findings += Finding(
                    if (ext in BUNDLE_EXT) "È un pacchetto di app Android da installare" else "Contiene un'app Android",
                    if (ext in BUNDLE_EXT) 10 else 30,
                )
            }
            val executables = names.filter { n ->
                val e = n.substringAfterLast('.', "")
                e in EXECUTABLE_EXT && e != "apk" && e != "so" && !n.startsWith("lib/")
            }
            if (executables.isNotEmpty()) {
                findings += Finding("Contiene file eseguibili: ${executables.take(3).joinToString { it.substringAfterLast('/') }}", 30)
            }
            val doubled = names.map { it.substringAfterLast('/') }.filter { n ->
                val parts = n.split('.')
                parts.size >= 3 && parts.last() in EXECUTABLE_EXT && parts[parts.size - 2] in DOCUMENT_EXT
            }
            if (doubled.isNotEmpty()) findings += Finding("Contiene file con doppia estensione: ${doubled.first()}", 40)
            if (names.any { it.contains("vbaproject.bin") }) findings += Finding("Documento con macro", 40)
            if (names.any { it.contains("/embeddings/oleobject") || it.contains("activex") }) {
                findings += Finding("Documento con oggetti incorporati o ActiveX", 20)
            }
            if (ext in ODF_EXT && names.any { it.startsWith("basic/") || it.startsWith("scripts/") }) {
                findings += Finding("Documento con macro", 40)
            }
            val rels = entries.filter { it.name.endsWith(".rels") }
            for (entry in rels.take(30)) {
                val text = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                if (text.contains("TargetMode=\"External\"") && text.contains("attachedTemplate")) {
                    findings += Finding("Scarica un modello remoto all'apertura (tecnica usata per le macro)", 40)
                    break
                }
            }
        }
        return findings
    }

    private fun pdf(file: File): List<Finding> {
        val text = readText(file)
        val findings = ArrayList<Finding>()
        val js = Regex("/JavaScript|/JS[\\s(<\\[/]").containsMatchIn(text)
        if (js) findings += Finding("Contiene JavaScript", 40)
        if (text.contains("/Launch")) findings += Finding("Può lanciare programmi esterni", 50)
        if (text.contains("/EmbeddedFile")) findings += Finding("Contiene file allegati nascosti", 25)
        if (text.contains("/OpenAction") && js) findings += Finding("Esegue codice appena aperto", 10)
        if (text.contains("/RichMedia")) findings += Finding("Contiene contenuti Flash o multimediali attivi", 20)
        if (text.contains("/XFA")) findings += Finding("Usa moduli XFA, spesso sfruttati dagli attacchi", 10)
        if (text.contains("/SubmitForm")) findings += Finding("Ha un modulo che invia dati a un sito", 10)
        // Nomi offuscati con codici esadecimali (/J#61vaScript) servono solo a sfuggire ai controlli.
        if (Regex("/[A-Za-z]*#[0-9A-Fa-f]{2}[A-Za-z#0-9]*").findAll(text).any { decodeName(it.value).let { n -> n.contains("JavaScript") || n.contains("Launch") || n == "/JS" } }) {
            findings += Finding("Nasconde comandi pericolosi con nomi offuscati", 40)
        }
        return findings
    }

    private fun decodeName(name: String): String =
        Regex("#([0-9A-Fa-f]{2})").replace(name) { it.groupValues[1].toInt(16).toChar().toString() }

    private fun ole(file: File): List<Finding> {
        val text = readText(file)
        val findings = ArrayList<Finding>()
        if (text.contains("_VBA_PROJECT") || text.contains("VBA") && text.contains("Attribut")) {
            findings += Finding("Documento con macro", 40)
        }
        if (Regex("AutoOpen|Document_Open|Workbook_Open|Auto_Open").containsMatchIn(text)) {
            findings += Finding("Le macro partono da sole all'apertura", 20)
        }
        if (text.contains("Equation.3") || text.contains("Equation Native")) {
            findings += Finding("Contiene l'Editor di equazioni, sfruttato da attacchi noti", 40)
        }
        return findings
    }

    private fun rtf(file: File): List<Finding> {
        val text = readText(file)
        val findings = ArrayList<Finding>()
        if (text.contains("\\objdata")) findings += Finding("Contiene oggetti incorporati", 30)
        if (text.contains("\\objupdate")) findings += Finding("Gli oggetti si attivano da soli all'apertura", 20)
        if (text.contains("Equation", ignoreCase = true)) findings += Finding("Richiama l'Editor di equazioni", 30)
        return findings
    }

    private fun html(file: File, type: String, ext: String): List<Finding> {
        val text = readText(file).lowercase(Locale.ROOT)
        val findings = ArrayList<Finding>()
        val script = text.contains("<script") || Regex("\\son[a-z]+\\s*=").containsMatchIn(text)
        if (type == "svg" && script) findings += Finding("Immagine SVG con codice al suo interno", 40)
        if (type == "html" && ext !in HTML_EXT) findings += Finding("È una pagina web travestita da altro file", 30)
        if (Regex("type\\s*=\\s*[\"']?password").containsMatchIn(text)) {
            findings += Finding("Chiede una password in una pagina salvata sul telefono (phishing)", 40)
        }
        if (Regex("atob\\(|unescape\\(|eval\\(|fromcharcode").containsMatchIn(text)) {
            findings += Finding("Contiene codice offuscato", 20)
        }
        if (text.contains("data:application/") || text.contains("msSaveOrOpenBlob".lowercase(Locale.ROOT))) {
            findings += Finding("Crea e scarica altri file da sé (HTML smuggling)", 40)
        }
        if (Regex("http-equiv\\s*=\\s*[\"']?refresh|window\\.location|location\\.href\\s*=").containsMatchIn(text)) {
            findings += Finding("Ti porta automaticamente su un altro sito", 10)
        }
        return findings
    }

    /** CSV con formule che lanciano comandi quando lo apri in un foglio di calcolo. */
    private fun csv(file: File): List<Finding> {
        if (file.length() > 8 * 1024 * 1024) return emptyList()
        val text = readText(file)
        val formula = Regex("(^|[,;\\t\"])[=+\\-@][^,;\\n]*(cmd\\||powershell|mshta|DDE\\(|WEBSERVICE\\(|HYPERLINK\\()", RegexOption.IGNORE_CASE)
        return if (formula.containsMatchIn(text)) listOf(Finding("Contiene formule che eseguono comandi o aprono link", 40)) else emptyList()
    }

    private fun isApk(file: File): Boolean = try {
        ZipFile(file).use { it.getEntry("AndroidManifest.xml") != null && it.getEntry("classes.dex") != null }
    } catch (e: Exception) {
        false
    }

    private fun isIso(file: File): Boolean = try {
        if (file.length() < 0x8006) false else RandomAccessFile(file, "r").use { raf ->
            raf.seek(0x8001)
            val b = ByteArray(5)
            raf.readFully(b)
            String(b, Charsets.ISO_8859_1) == "CD001"
        }
    } catch (e: Exception) {
        false
    }

    private fun readHead(file: File, size: Int): ByteArray = try {
        file.inputStream().use { input ->
            val buffer = ByteArray(size)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    } catch (e: Exception) {
        ByteArray(0)
    }

    private fun readText(file: File): String = file.inputStream().use { input ->
        val bytes = ByteArray(minOf(file.length(), MAX_TEXT_SCAN).toInt())
        var off = 0
        while (off < bytes.size) {
            val n = input.read(bytes, off, bytes.size - off)
            if (n <= 0) break
            off += n
        }
        String(bytes, 0, off, Charsets.ISO_8859_1)
    }

    fun sha256(file: File): String = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }
}
