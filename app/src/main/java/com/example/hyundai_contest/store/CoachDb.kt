package com.example.hyundai_contest.store

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class TripRow(
    val id: Long, val startMs: Long, val endMs: Long?,
    val startLat: Double?, val startLng: Double?, val path: List<Pair<Double, Double>>,
)

data class StoredRecord(val seq: Long, val tripId: Long?, val record: Record)

/** On-device store. Lives in the app's private data dir, which Android separates per driver profile. */
class CoachDb(context: Context) : SQLiteOpenHelper(context, "coach.db", null, 1) {
    private val _version = MutableStateFlow(0L)

    /** Bumped on every write the screens care about, so they re-query. */
    val version: StateFlow<Long> = _version

    private var batching = false

    private fun bump() {
        if (!batching) _version.value += 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE trip(id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL, end_ms INTEGER, " +
                "start_lat REAL, start_lng REAL, path TEXT NOT NULL DEFAULT '')",
        )
        db.execSQL(
            "CREATE TABLE record(seq INTEGER PRIMARY KEY AUTOINCREMENT, trip_id INTEGER, type TEXT NOT NULL, " +
                "at_ms INTEGER NOT NULL, lat REAL, lng REAL, detail TEXT NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Trips left open by an app restart or a drive that never reached the exit check end at their last record. */
    fun closeDanglingTrips() {
        writableDatabase.execSQL(
            "UPDATE trip SET end_ms = COALESCE((SELECT MAX(at_ms) FROM record WHERE record.trip_id = trip.id), start_ms) " +
                "WHERE end_ms IS NULL",
        )
        bump()
    }

    fun startTrip(atMs: Long, lat: Double?, lng: Double?): Long {
        val id = writableDatabase.insertOrThrow("trip", null, ContentValues().apply {
            put("start_ms", atMs); put("start_lat", lat); put("start_lng", lng)
        })
        bump()
        return id
    }

    fun endTrip(id: Long, atMs: Long) {
        writableDatabase.update("trip", ContentValues().apply { put("end_ms", atMs) }, "id=?", arrayOf(id.toString()))
        bump()
    }

    /** Not bumped: live paths are shown from the recorder, the DB copy is for later views. */
    fun appendPath(tripId: Long, lat: Double, lng: Double) {
        writableDatabase.execSQL(
            "UPDATE trip SET path = CASE WHEN path = '' THEN ? ELSE path || ';' || ? END WHERE id = ?",
            arrayOf("$lat,$lng", "$lat,$lng", tripId),
        )
    }

    fun setPath(tripId: Long, path: List<Pair<Double, Double>>) {
        writableDatabase.update("trip", ContentValues().apply {
            put("path", path.joinToString(";") { "${it.first},${it.second}" })
        }, "id=?", arrayOf(tripId.toString()))
    }

    fun append(tripId: Long?, r: Record) {
        writableDatabase.insertOrThrow("record", null, ContentValues().apply {
            put("trip_id", tripId); put("type", r.type.name); put("at_ms", r.atMs)
            put("lat", r.lat); put("lng", r.lng); put("detail", JSONObject(r.detail).toString())
        })
        bump()
    }

    /** Runs many writes as one transaction (the demo history is thousands of rows). */
    fun <T> batch(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        batching = true
        try {
            return block().also { db.setTransactionSuccessful() }
        } finally {
            batching = false
            db.endTransaction()
            bump()
        }
    }

    fun records(): List<StoredRecord> =
        readableDatabase.rawQuery("SELECT seq, trip_id, type, at_ms, lat, lng, detail FROM record ORDER BY at_ms, seq", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toStored()) } }

    private fun Cursor.toStored(): StoredRecord {
        val json = JSONObject(getString(6))
        val detail = json.keys().asSequence().associateWith { json.getString(it) }
        val r = Record(RecordType.valueOf(getString(2)), getLong(3), doubleOrNull(4), doubleOrNull(5), detail)
        return StoredRecord(getLong(0), if (isNull(1)) null else getLong(1), r)
    }

    private fun Cursor.doubleOrNull(i: Int) = if (isNull(i)) null else getDouble(i)

    fun trips(): List<TripRow> =
        readableDatabase.rawQuery("SELECT id, start_ms, end_ms, start_lat, start_lng, path FROM trip ORDER BY start_ms", null)
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val path = c.getString(5).split(';').filter { it.isNotBlank() }.map {
                            val (a, b) = it.split(',')
                            a.toDouble() to b.toDouble()
                        }
                        add(TripRow(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.doubleOrNull(3), c.doubleOrNull(4), path))
                    }
                }
            }

    fun clearAll() {
        listOf("trip", "record").forEach { writableDatabase.delete(it, null, null) }
        bump()
    }
}
