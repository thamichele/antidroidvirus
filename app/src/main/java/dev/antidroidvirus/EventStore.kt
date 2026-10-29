package dev.antidroidvirus

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class UsageEvent(
    val time: Long,
    val pkg: String,
    val op: String,
    val trusted: Boolean,
    /** Combinazione di [FLAG_BACKGROUND], [FLAG_SCREEN_OFF], [FLAG_NIGHT], [FLAG_FREQUENT]. */
    val flags: Int = 0,
) {
    fun has(flag: Int) = flags and flag != 0

    companion object {
        const val FLAG_BACKGROUND = 1
        const val FLAG_SCREEN_OFF = 2
        const val FLAG_NIGHT = 4
        const val FLAG_FREQUENT = 8
    }
}

class EventStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "events.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, " +
                "pkg TEXT NOT NULL, op TEXT NOT NULL, trusted INTEGER NOT NULL, flags INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX events_time ON events(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE events ADD COLUMN flags INTEGER NOT NULL DEFAULT 0")
    }

    fun add(event: UsageEvent) {
        val values = ContentValues().apply {
            put("time", event.time)
            put("pkg", event.pkg)
            put("op", event.op)
            put("trusted", if (event.trusted) 1 else 0)
            put("flags", event.flags)
        }
        writableDatabase.insert("events", null, values)
    }

    fun recent(limit: Int = 300): List<UsageEvent> =
        query("SELECT time, pkg, op, trusted, flags FROM events ORDER BY time DESC LIMIT ?", limit.toString())

    fun since(time: Long): List<UsageEvent> =
        query("SELECT time, pkg, op, trusted, flags FROM events WHERE time >= ? ORDER BY time DESC", time.toString())

    /** Usi registrati di un'app per un gruppo di op in un intervallo. */
    fun count(pkg: String, ops: List<String>, from: Long, to: Long): Int {
        val marks = ops.joinToString(",") { "?" }
        val args = arrayOf(pkg, from.toString(), to.toString()) + ops
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM events WHERE pkg = ? AND time >= ? AND time < ? AND op IN ($marks)", args,
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun pruneOlderThan(time: Long) {
        writableDatabase.delete("events", "time < ?", arrayOf(time.toString()))
    }

    private fun query(sql: String, vararg args: String): List<UsageEvent> {
        val events = ArrayList<UsageEvent>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                events += UsageEvent(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1, c.getInt(4))
            }
        }
        return events
    }

    companion object {
        @Volatile
        private var instance: EventStore? = null

        fun get(context: Context): EventStore =
            instance ?: synchronized(this) {
                instance ?: EventStore(context.applicationContext).also { instance = it }
            }
    }
}
