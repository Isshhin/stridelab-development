package io.stridelab.privatealpha

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Local, user-requested archive of source-recorded activity summaries, their full
 * saved recordings, and original FIT bytes that this installation actually cached.
 *
 * This archive does NOT contain account credentials, plans, manual discipline logs,
 * or uncached Garmin FIT downloads. It is unencrypted and contains precise location.
 */
internal enum class LocalActivityArchiveScope {
    LOCAL_GARMIN, CLOUD_ACCOUNT
}

internal data class LocalActivityArchiveCounts(
    val summaries: Int, val details: Int, val originals: Int,
    val newlyRestored: Int = 0
)

internal class LocalActivityArchive(
    context: Context,
    databaseName: String = TrainingStore.DATABASE
) {
    private val store = TrainingStore(context.applicationContext, databaseName)
    private val originals = FitImportCache(context.applicationContext)

    private data class Item(
        val path: String, val kind: String, val id: String,
        val size: Int, val digest: String
    ) {
        fun json() = JSONObject().put("path", path).put("kind", kind)
            .put("id", id).put("bytes", size).put("sha256", digest)
    }

    companion object {
        private const val MAX_ITEMS = 30_000
        private const val MAX_ARCHIVE_BYTES = 600L * 1024 * 1024
        private const val MAX_RAW_BYTES = 800L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
        private const val MAX_SUMMARY_BYTES = 2 * 1024 * 1024
        private const val MAX_DETAIL_BYTES = 20 * 1024 * 1024
        private val DIGEST_PATTERN = Regex("[0-9a-f]{64}")

        private fun sha(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }

        private fun InputStream.readLimited(max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                require(out.size().toLong() + read <= max.toLong()) {
                    "Archive entry exceeds its size limit; no records changed."
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        private fun validateScope(owner: String, scope: LocalActivityArchiveScope) {
            RecordPolicy.identifier(owner)
            if (scope == LocalActivityArchiveScope.LOCAL_GARMIN)
                require(owner.startsWith("local-garmin-")) {
                    "The archive does not belong to a local Garmin source."
                }
            else require(!owner.startsWith("local-garmin-")) {
                "A Garmin-only owner cannot be used for a signed-in account archive."
            }
        }

        private fun validatedPayload(
            source: String, target: String, kind: String, id: String,
            raw: String, scope: LocalActivityArchiveScope
        ): String {
            require(kind == "activity" || kind == "activity_detail")
            val checked = when (kind) {
                "activity" -> {
                    val activity = ActivityRecord.parse(raw, source)
                    require(activity.id == id)
                    if (scope == LocalActivityArchiveScope.LOCAL_GARMIN)
                        require(activity.source == "garmin") {
                            "Unexpected non-Garmin record in local Garmin archive."
                        }
                    raw
                }
                else -> {
                    ActivityDetail.parse(JSONObject(raw), source, id)
                    raw
                }
            }
            if (source == target) return checked
            // Only local Garmin backup data may be rehomed onto a new installation.
            require(scope == LocalActivityArchiveScope.LOCAL_GARMIN &&
                source.startsWith("local-garmin-") &&
                target.startsWith("local-garmin-"))
            val translated = JSONObject(checked).put("user_id", target).toString()
            if (kind == "activity") {
                val parsed = ActivityRecord.parse(translated, target)
                require(parsed.id == id && parsed.source == "garmin")
            } else ActivityDetail.parse(JSONObject(translated), target, id)
            return translated
        }
    }

    fun close() { store.close() }

    /**
     * Exports every summary/detail for one exact owner. A malformed or oversized
     * saved record ABORTS the entire export; it must not produce a partial backup.
     * Caller should export to an app-private temporary file before invoking SAF.
     */
    fun export(owner: String, scope: LocalActivityArchiveScope, output: OutputStream):
        LocalActivityArchiveCounts {
        validateScope(owner, scope)
        val list = mutableListOf<Item>()
        val ids = linkedMapOf("activity" to mutableListOf<String>(),
            "activity_detail" to mutableListOf<String>())
        for ((kind, result) in ids) {
            store.readableDatabase.query(
                "records", arrayOf("record_id"), "account=? AND kind=?",
                arrayOf(owner, kind), null, null, "record_id"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    require(result.size < MAX_ITEMS) {
                        "Too many saved activities for one archive. No partial export is valid."
                    }
                    result += cursor.getString(0)
                }
            }
        }
        val fitHashes = originals.archivedHashes(owner)
        require(ids.values.sumOf { it.size } + fitHashes.size <= MAX_ITEMS) {
            "Archive contains too many entries."
        }
        var uncompressed = 0L
        ZipOutputStream(output).use { zip ->
            fun append(path: String, kind: String, id: String, bytes: ByteArray) {
                uncompressed += bytes.size
                require(uncompressed <= MAX_RAW_BYTES) {
                    "Archive exceeds the supported size; no complete export was generated."
                }
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
                list += Item(path, kind, id, bytes.size, sha(bytes))
            }
            for ((kind, records) in ids) {
                for (id in records) {
                    RecordPolicy.identifier(id)
                    val raw = if (kind == "activity_detail")
                        store.getActivityDetailPayloadChunked(owner, id)
                    else store.get(owner, kind, id)?.payload
                    requireNotNull(raw) { "Saved activity changed during export." }
                    validatedPayload(owner, owner, kind, id, raw, scope)
                    val bytes = raw.toByteArray(StandardCharsets.UTF_8)
                    require(bytes.size <= if (kind == "activity") MAX_SUMMARY_BYTES else MAX_DETAIL_BYTES)
                    append("records/$kind/${list.size}.json", kind, id, bytes)
                }
            }
            for (hash in fitHashes) {
                require(DIGEST_PATTERN.matches(hash))
                val bytes = originals.originalBytes(owner, hash)
                append("fits/${list.size}.fit", "fit", hash, bytes)
            }
            val manifest = JSONObject()
                .put("schema", 1)
                .put("sourceOwner", owner)
                .put("scope", scope.name)
                .put("entries", JSONArray(list.map { it.json() }))
                .toString().toByteArray(StandardCharsets.UTF_8)
            require(manifest.size <= MAX_MANIFEST_BYTES)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
        }
        return LocalActivityArchiveCounts(
            ids.getValue("activity").size,
            ids.getValue("activity_detail").size,
            fitHashes.size
        )
    }

    /**
     * Fully validate a staged, app-private ZIP before any database modification.
     * Existing records are never replaced; conflicting IDs roll the transaction
     * back. FIT files are atomically installed only after payload validation.
     * If a process is killed during restore, an orphaned valid FIT cache may
     * remain, but no previously saved activity payload is overwritten.
     */
    fun restore(
        archive: File, targetOwner: String, scope: LocalActivityArchiveScope
    ): LocalActivityArchiveCounts {
        validateScope(targetOwner, scope)
        require(archive.isFile && archive.length() in 1..MAX_ARCHIVE_BYTES) {
            "Archive file is missing or too large."
        }
        ZipFile(archive).use { zip ->
            val zipEntries = linkedSetOf<String>()
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                require(!entry.isDirectory && zipEntries.add(entry.name)) {
                    "Archive contains duplicate entries or directories."
                }
                require(zipEntries.size <= MAX_ITEMS + 1)
            }
            val manifestEntry = requireNotNull(zip.getEntry("manifest.json")) {
                "Archive manifest is missing."
            }
            val manifest = JSONObject(String(zip.getInputStream(manifestEntry).use {
                it.readLimited(MAX_MANIFEST_BYTES)
            }, StandardCharsets.UTF_8))
            require(manifest.getInt("schema") == 1) { "Unsupported activity archive version." }
            val sourceOwner = manifest.getString("sourceOwner")
            validateScope(sourceOwner, scope)
            require(manifest.getString("scope") == scope.name)
            if (scope == LocalActivityArchiveScope.CLOUD_ACCOUNT) {
                require(sourceOwner == targetOwner) {
                    "Sign in to the same StrideLab account before restoring cloud-owned activities."
                }
            }
            val rows = manifest.getJSONArray("entries")
            require(rows.length() <= MAX_ITEMS)
            require(zipEntries.size == rows.length() + 1)
            val items = ArrayList<Item>(rows.length())
            val seen = HashSet<String>()
            var total = 0L
            var summaries = 0
            var details = 0
            var fits = 0
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val kind = row.getString("kind")
                require(kind in setOf("activity", "activity_detail", "fit"))
                val path = row.getString("path")
                val expected = if (kind == "fit") "fits/$index.fit" else "records/$kind/$index.json"
                require(path == expected && path in zipEntries)
                val id = row.getString("id")
                if (kind == "fit") require(DIGEST_PATTERN.matches(id))
                else RecordPolicy.identifier(id)
                require(seen.add("$kind:$id")) {
                    "Archive contains repeated activity or FIT identifiers."
                }
                val size = row.getInt("bytes")
                val max = when (kind) {
                    "activity" -> MAX_SUMMARY_BYTES
                    "activity_detail" -> MAX_DETAIL_BYTES
                    else -> FitContainerReader.MAX_BYTES
                }
                require(size in 1..max)
                total += size
                require(total <= MAX_RAW_BYTES)
                val digest = row.getString("sha256")
                require(DIGEST_PATTERN.matches(digest))
                val entry = requireNotNull(zip.getEntry(path))
                require(entry.size < 0 || entry.size == size.toLong())
                val bytes = zip.getInputStream(entry).use { it.readLimited(max) }
                require(bytes.size == size && sha(bytes) == digest) {
                    "Archive entry checksum or length is invalid."
                }
                if (kind == "fit") {
                    require(sha(bytes) == id) { "Archived FIT does not match source identity." }
                    val fit = FitActivityDecoder.decode(bytes, targetOwner)
                    require(fit.identity.sha256 == id)
                    fits++
                } else {
                    val raw = String(bytes, StandardCharsets.UTF_8)
                    validatedPayload(sourceOwner, targetOwner, kind, id, raw, scope)
                    if (kind == "activity") summaries++ else details++
                }
                items += Item(path, kind, id, size, digest)
            }
            require(zipEntries == items.mapTo(linkedSetOf("manifest.json")) { it.path }) {
                "Unexpected archive entries must not be ignored."
            }

            val db = store.writableDatabase
            var added = 0
            db.beginTransaction()
            try {
                // Validate conflicts before modifying any activity row.
                for (item in items.filter { it.kind != "fit" }) {
                    val raw = String(zip.getInputStream(zip.getEntry(item.path)).use {
                        it.readLimited(if (item.kind == "activity") MAX_SUMMARY_BYTES else MAX_DETAIL_BYTES)
                    }, StandardCharsets.UTF_8)
                    val translated = validatedPayload(
                        sourceOwner, targetOwner, item.kind, item.id, raw, scope
                    )
                    val existing = if (item.kind == "activity_detail")
                        store.getActivityDetailPayloadChunked(targetOwner, item.id)
                    else store.get(targetOwner, item.kind, item.id)?.payload
                    if (existing != null) require(existing == translated) {
                        "An existing activity has the same ID with different data. No saved activity was overwritten."
                    }
                }
                for (item in items) {
                    if (item.kind == "fit") {
                        val bytes = zip.getInputStream(zip.getEntry(item.path)).use {
                            it.readLimited(FitContainerReader.MAX_BYTES)
                        }
                        originals.saveSelected(bytes, targetOwner)
                        continue
                    }
                    val existing = if (item.kind == "activity_detail")
                        store.getActivityDetailPayloadChunked(targetOwner, item.id)
                    else store.get(targetOwner, item.kind, item.id)?.payload
                    if (existing != null) continue
                    val raw = String(zip.getInputStream(zip.getEntry(item.path)).use {
                        it.readLimited(if (item.kind == "activity") MAX_SUMMARY_BYTES else MAX_DETAIL_BYTES)
                    }, StandardCharsets.UTF_8)
                    val translated = validatedPayload(
                        sourceOwner, targetOwner, item.kind, item.id, raw, scope
                    )
                    val values = ContentValues().apply {
                        put("account", targetOwner)
                        put("kind", item.kind)
                        put("record_id", item.id)
                        put("payload", translated)
                        putNull("server_version")
                        put("synced_at", 0L)
                    }
                    db.insertOrThrow("records", null, values)
                    added++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return LocalActivityArchiveCounts(summaries, details, fits, added)
        }
    }
}
