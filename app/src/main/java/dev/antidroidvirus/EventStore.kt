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
)

class EventStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "events.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, " +
                "pkg TEXT NOT NULL, op TEXT NOT NULL, trusted INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX events_time ON events(time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(event: UsageEvent) {
        val values = ContentValues().apply {
            put("time", event.time)
            put("pkg", event.pkg)
            put("op", event.op)
            put("trusted", if (event.trusted) 1 else 0)
        }
        writableDatabase.insert("events", null, values)
    }

    fun recent(limit: Int = 300): List<UsageEvent> {
        val events = ArrayList<UsageEvent>()
        readableDatabase.rawQuery(
            "SELECT time, pkg, op, trusted FROM events ORDER BY time DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                events += UsageEvent(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3) == 1)
            }
        }
        return events
    }

    fun pruneOlderThan(time: Long) {
        writableDatabase.delete("events", "time < ?", arrayOf(time.toString()))
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
