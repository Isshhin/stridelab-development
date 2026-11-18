package io.stridelab.privatealpha

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate

/** Manually entered, source-labelled completed sessions. No generated coaching data. */
internal enum class ManualDiscipline(val label: String) {
    STRENGTH("Strength"), CALISTHENICS("Calisthenics"), CONDITIONING("Conditioning")
}

internal data class ManualExerciseEntry(
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val seconds: Int? = null,
    val weightKg: Double? = null
)
internal data class ManualDisciplineSession(
    val id: String,
    val owner: String,
    val discipline: ManualDiscipline,
    val title: String,
    val performedOn: LocalDate,
    val exercises: List<ManualExerciseEntry>,
    val notes: String = ""
)

internal object ManualDisciplineCodec {
    fun encode(record: ManualDisciplineSession): String {
        RecordPolicy.identifier(record.id)
        RecordPolicy.identifier(record.owner)
        require(record.title.isNotBlank() && record.title.length <= 100)
        require(record.notes.length <= 1000)
        require(record.performedOn.year in 2000..2100)
        require(record.exercises.size in 1..30)
        for (entry in record.exercises) {
            require(entry.name.isNotBlank() && entry.name.length <= 100)
            require(entry.sets in 1..30)
            require(entry.reps == null || entry.reps in 0..500)
            require(entry.seconds == null || entry.seconds in 1..7200)
            require(entry.weightKg == null || entry.weightKg.isFinite() &&
                entry.weightKg >= 0.0 && entry.weightKg <= 5000.0)
            require(entry.reps != null || entry.seconds != null) {
                "Each logged exercise needs recorded reps or duration."
            }
        }
        return JSONObject()
            .put("schema", 1)
            .put("id", record.id)
            .put("owner", record.owner)
            .put("discipline", record.discipline.name)
            .put("title", record.title)
            .put("performedOn", record.performedOn.toString())
            .put("source", "manual")
            .put("notes", record.notes)
            .put("exercises", JSONArray().apply {
                record.exercises.forEach { e ->
                    put(JSONObject().put("name", e.name).put("sets", e.sets)
                        .put("reps", e.reps).put("seconds", e.seconds).put("weightKg", e.weightKg))
                }
            }).toString()
    }

    fun decode(raw: String, owner: String): ManualDisciplineSession {
        require(raw.length <= 200_000)
        val obj = JSONObject(raw)
        require(obj.getInt("schema") == 1 && obj.getString("source") == "manual")
        require(obj.getString("owner") == owner) { "Manual log belongs to a different owner." }
        val rows = obj.getJSONArray("exercises")
        require(rows.length() in 1..30)
        val record = ManualDisciplineSession(
            id = obj.getString("id"),
            owner = owner,
            discipline = ManualDiscipline.valueOf(obj.getString("discipline")),
            title = obj.getString("title"),
            performedOn = LocalDate.parse(obj.getString("performedOn")),
            exercises = (0 until rows.length()).map { index ->
                val entry = rows.getJSONObject(index)
                ManualExerciseEntry(
                    name = entry.getString("name"),
                    sets = entry.getInt("sets"),
                    reps = if (entry.has("reps") && !entry.isNull("reps")) entry.getInt("reps") else null,
                    seconds = if (entry.has("seconds") && !entry.isNull("seconds")) entry.getInt("seconds") else null,
                    weightKg = if (entry.has("weightKg") && !entry.isNull("weightKg"))
                        entry.getDouble("weightKg") else null
                )
            },
            notes = obj.optString("notes", "")
        )
        encode(record) // Revalidate every decoded value and prevent loose parser defaults.
        return record
    }
}

/** Portable manual-logs backup. The SHA-256 detects accidental changes, not malicious edits. */
internal object ManualDisciplineBackup {
    private fun hash(data: String): String =
        MessageDigest.getInstance("SHA-256").digest(data.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun export(owner: String, records: List<ManualDisciplineSession>): String {
        RecordPolicy.identifier(owner)
        require(records.size <= 2000 && records.all { it.owner == owner })
        require(records.map { it.id }.distinct().size == records.size)
        val content = JSONObject()
            .put("schema", 1)
            .put("owner", owner)
            .put("records", JSONArray(records.map { JSONObject(ManualDisciplineCodec.encode(it)) }))
            .toString()
        require(content.length <= 8_000_000)
        return JSONObject().put("data", content).put("sha256", hash(content)).toString()
    }

    fun import(raw: String, owner: String): List<ManualDisciplineSession> {
        RecordPolicy.identifier(owner)
        require(raw.length <= 8_100_000)
        val outer = JSONObject(raw)
        val content = outer.getString("data")
        require(hash(content) == outer.getString("sha256")) { "Backup checksum did not match." }
        val inner = JSONObject(content)
        require(inner.getInt("schema") == 1 && inner.getString("owner") == owner) {
            "This backup belongs to a different owner or version."
        }
        val rows = inner.getJSONArray("records")
        require(rows.length() <= 2000)
        val records = (0 until rows.length()).map {
            ManualDisciplineCodec.decode(rows.getJSONObject(it).toString(), owner)
        }
        require(records.map { it.id }.distinct().size == records.size)
        return records
    }
}

/** Separate owner-scoped database. Never writes to TrainingStore or Garmin FIT records. */
internal class ManualDisciplineStore(
    context: Context,
    databaseName: String = "stridelab-standalone-sessions-v1.db"
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sessions(owner TEXT NOT NULL, record_id TEXT NOT NULL, performed_on TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(owner,record_id))")
        db.execSQL("CREATE INDEX session_owner_date ON sessions(owner,performed_on DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("A non-destructive migration is required; existing logs retained.")
    }

    suspend fun save(record: ManualDisciplineSession) = withContext(Dispatchers.IO) {
        val payload = ManualDisciplineCodec.encode(record)
        val values = ContentValues().apply {
            put("owner", record.owner)
            put("record_id", record.id)
            put("performed_on", record.performedOn.toString())
            put("payload", payload)
        }
        writableDatabase.insertOrThrow("sessions", null, values)
        Unit
    }

    suspend fun list(owner: String): List<ManualDisciplineSession> = withContext(Dispatchers.IO) {
        RecordPolicy.identifier(owner)
        val result = mutableListOf<ManualDisciplineSession>()
        readableDatabase.query("sessions", arrayOf("payload"), "owner=?", arrayOf(owner),
            null, null, "performed_on DESC,record_id DESC", "2000").use { cursor ->
            while (cursor.moveToNext()) result += ManualDisciplineCodec.decode(cursor.getString(0), owner)
        }
        result
    }

    suspend fun export(owner: String): String =
        ManualDisciplineBackup.export(owner, list(owner))

    suspend fun delete(owner: String, id: String): Boolean = withContext(Dispatchers.IO) {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        writableDatabase.delete("sessions", "owner=? AND record_id=?", arrayOf(owner,id)) > 0
    }

    /** Transactional, insert-only recovery: duplicates with altered data abort everything. */
    suspend fun restore(owner: String, raw: String): Int = withContext(Dispatchers.IO) {
        val records = ManualDisciplineBackup.import(raw, owner)
        val db = writableDatabase
        var additions = 0
        db.beginTransaction()
        try {
            for (row in records) {
                val incoming = ManualDisciplineCodec.encode(row)
                val old = db.query("sessions", arrayOf("payload"),
                    "owner=? AND record_id=?", arrayOf(owner,row.id),
                    null,null,null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (old != null) {
                    require(old == incoming) {
                        "A saved manual session has this ID but different contents. No records changed."
                    }
                    continue
                }
                val values = ContentValues().apply {
                    put("owner", owner); put("record_id", row.id)
                    put("performed_on", row.performedOn.toString()); put("payload", incoming)
                }
                db.insertOrThrow("sessions", null, values)
                additions++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        additions
    }
}
