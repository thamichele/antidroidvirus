package dev.antidroidvirus

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Esporta il registro degli usi dei permessi in CSV (separatore ";" e BOM, così Excel e LibreOffice
 * in italiano lo aprono già in colonne). Il file va dove scegli tu, dalla finestra di sistema.
 */
object HistoryExport {
    const val MIME = "text/csv"

    fun fileName(): String = "antidroidvirus-storico-" + SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN).format(Date()) + ".csv"

    /** Va chiamato fuori dal main thread. Ritorna il numero di righe scritte, o -1 se la scrittura fallisce. */
    fun write(context: Context, uri: Uri): Int {
        val events = EventStore.get(context).all()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ITALIAN)
        val time = SimpleDateFormat("HH:mm:ss", Locale.ITALIAN)
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { out ->
                out.write("﻿")
                out.write("data;ora;app;pacchetto;profilo;permesso;operazione;esito;contesto\n")
                for (e in events) {
                    val at = Date(e.time)
                    val row = listOf(
                        date.format(at), time.format(at), Apps.label(context, e.pkg), e.pkg,
                        if (e.work) "lavoro" else "principale",
                        Perm.fromOp(e.op)?.title ?: "", e.op,
                        if (e.trusted) "fidata" else "avviso",
                        Monitor.describeFlags(e.flags),
                    )
                    out.write(row.joinToString(";") { csv(it) })
                    out.write("\n")
                }
            } ?: return -1
            events.size
        } catch (e: Exception) {
            -1
        }
    }

    private fun csv(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
