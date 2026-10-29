package dev.antidroidvirus

import android.content.Context
import android.database.ContentObserver
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sorveglia la cartella Download: ogni file nuovo, appena finito di scaricare, passa da [FileScanner].
 * Serve l'accesso a tutti i file (Impostazioni › Accesso speciale), concesso dall'utente una volta.
 * Due inneschi perché nessuno basta da solo: MediaStore vede i download dei browser, FileObserver
 * le copie fatte dai file manager.
 */
class DownloadWatcher(private val context: Context, private val handler: Handler) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private var fileObserver: FileObserver? = null
    private val scanNow = Runnable { scan() }

    private val mediaObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule()
    }

    fun start() {
        if (!Environment.isExternalStorageManager()) return
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, mediaObserver,
        )
        fileObserver = object : FileObserver(root, CLOSE_WRITE or MOVED_TO or CREATE) {
            override fun onEvent(event: Int, path: String?) = schedule()
        }.also { it.startWatching() }
        if (!prefs.getBoolean(KEY_BASELINE, false)) {
            // Alla prima attivazione i file già presenti non generano avvisi.
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).putBoolean(KEY_BASELINE, true).apply()
        }
        schedule()
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(mediaObserver)
        fileObserver?.stopWatching()
        fileObserver = null
        handler.removeCallbacks(scanNow)
    }

    private fun schedule() {
        handler.removeCallbacks(scanNow)
        handler.postDelayed(scanNow, SETTLE_MS)
    }

    private fun scan() {
        if (!Environment.isExternalStorageManager()) return
        val since = prefs.getLong(KEY_LAST, System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val candidates = root.walkTopDown().maxDepth(3)
            .filter { it.isFile && it.lastModified() > since - SETTLE_MS && it.length() > 0 }
            .filterNot { isPartial(it.name) }
            .toList()
        // Un file modificato negli ultimi istanti potrebbe essere ancora in scrittura: si riprova dopo.
        if (candidates.any { now - it.lastModified() < SETTLE_MS }) {
            handler.postDelayed(scanNow, SETTLE_MS)
            return
        }
        val seen = prefs.getStringSet(KEY_SEEN, emptySet())!!.toMutableSet()
        for (file in candidates) {
            val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
            if (key in seen) continue
            seen += key
            try {
                val report = FileScanner.scan(context, file)
                FileLog.add(context, report)
                if (report.verdict != FileVerdict.OK) Alerts.file(context, report)
            } catch (e: Exception) {
                Log.e(TAG, "Analisi di ${file.name} fallita", e)
            }
        }
        while (seen.size > 300) seen.remove(seen.first())
        prefs.edit().putStringSet(KEY_SEEN, seen).putLong(KEY_LAST, now).apply()
    }

    private fun isPartial(name: String): Boolean {
        val lower = name.lowercase()
        return name.startsWith(".") || lower.endsWith(".crdownload") || lower.endsWith(".part") ||
            lower.endsWith(".tmp") || lower.endsWith(".download")
    }

    companion object {
        private const val TAG = "DownloadWatcher"
        private const val PREFS = "downloads"
        private const val KEY_LAST = "last_scan"
        private const val KEY_SEEN = "seen"
        private const val KEY_BASELINE = "baseline"
        private const val SETTLE_MS = 4_000L

        /** Il file sta nella cartella Download: l'unica in cui "Elimina" può cancellare. */
        fun isInDownloads(path: String): Boolean {
            val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath
            return File(path).canonicalPath.startsWith("$root/")
        }
    }
}

/** Ultimi file controllati, per la pagina Controllo. */
object FileLog {
    private const val PREFS = "file_log"
    private const val KEY = "reports"
    private const val MAX = 100

    @Synchronized
    fun add(context: Context, report: FileReport) {
        val list = all(context).filterNot { it.path == report.path }.toMutableList()
        list.add(0, report)
        save(context, list.take(MAX))
    }

    @Synchronized
    fun remove(context: Context, path: String) {
        save(context, all(context).filterNot { it.path == path })
    }

    @Synchronized
    fun all(context: Context): List<FileReport> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val findings = o.getJSONArray("findings")
                FileReport(
                    o.getString("path"), o.getString("name"), o.getLong("size"), o.getLong("time"),
                    o.getString("sha256"), o.getString("type"),
                    (0 until findings.length()).map { j ->
                        val f = findings.getJSONObject(j)
                        Finding(f.getString("text"), f.getInt("weight"))
                    },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<FileReport>) {
        val array = JSONArray()
        for (r in list) {
            val findings = JSONArray()
            for (f in r.findings) findings.put(JSONObject().put("text", f.text).put("weight", f.weight))
            array.put(
                JSONObject().put("path", r.path).put("name", r.name).put("size", r.size).put("time", r.time)
                    .put("sha256", r.sha256).put("type", r.type).put("findings", findings)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
