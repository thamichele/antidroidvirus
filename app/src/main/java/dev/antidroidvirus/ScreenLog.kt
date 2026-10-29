package dev.antidroidvirus

import android.content.Context
import java.util.Calendar

/**
 * Ultimi cambi di stato dello schermo, per sapere a posteriori se un accesso è avvenuto a schermo spento:
 * a schermo spento le letture sono rade, quindi tra un accesso e la sua lettura lo schermo può essersi riacceso.
 */
object ScreenLog {
    private const val PREFS = "screen_log"
    private const val KEY = "transitions"
    private const val MAX = 64

    /** Coppie (orario, acceso) in ordine di tempo. */
    private var transitions: MutableList<Pair<Long, Boolean>>? = null

    @Synchronized
    fun record(context: Context, on: Boolean, time: Long = System.currentTimeMillis()) {
        val list = load(context)
        list += time to on
        while (list.size > MAX) list.removeAt(0)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" })
            .apply()
    }

    /** true se all'orario indicato lo schermo era spento; senza dati si considera acceso. */
    @Synchronized
    fun wasOff(context: Context, time: Long): Boolean {
        val last = load(context).lastOrNull { it.first <= time } ?: return false
        return !last.second
    }

    private fun load(context: Context): MutableList<Pair<Long, Boolean>> {
        transitions?.let { return it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        val list = raw.split(',').mapNotNull { item ->
            val parts = item.split(':')
            if (parts.size != 2) null else parts[0].toLongOrNull()?.let { it to (parts[1] == "1") }
        }.toMutableList()
        transitions = list
        return list
    }

    /** Fascia notturna: dall'1 alle 6. */
    fun isNight(time: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)
        return hour in NIGHT_START until NIGHT_END
    }

    const val NIGHT_START = 1
    const val NIGHT_END = 6
}
