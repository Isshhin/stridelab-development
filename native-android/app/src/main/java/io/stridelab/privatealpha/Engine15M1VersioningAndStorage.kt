package io.stridelab.privatealpha

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.TreeMap

/**
 * M1-E & M1-F Record Classification, Versioning, Codecs, and Local-Only Storage Safeguards.
 */
internal enum class M1RecordType {
    SAFETY_RESTRICTION,
    ASSESSMENT_SNAPSHOT
}

internal enum class M1CompatibilityStatus {
    EXACT_MATCH,
    OLDER_POLICY_COMPATIBLE,
    FUTURE_SCHEMA_UNSUPPORTED
}

internal sealed interface M1DecodeOutcome<out T> {
    data class Decoded<out T>(
        val value: T,
        val compatibility: M1CompatibilityStatus,
        val payloadSha256: String?
    ) : M1DecodeOutcome<T>

    data class UnsupportedFutureSchema(
        val schemaVersion: Int,
        val policyVersion: String?,
        val reason: String
    ) : M1DecodeOutcome<Nothing>

    data class CorruptPayload(
        val reason: String
    ) : M1DecodeOutcome<Nothing>
}

/**
 * Versioned derived summary of an [AthleteStateSnapshot] cached strictly as a performance
 * aid (M1-E/M1-F). Derived assessments are NEVER authoritative over raw evidence and may
 * always be rebuilt when stale, missing, or corrupt — without affecting safety restrictions.
 */
internal data class M1AssessmentDisciplineEntry(
    val discipline: AthleteDiscipline,
    val coverage: HistoryCoverageStatus,
    val weeklyVolumeCanonical: String,
    val longestSessionCanonical: String,
    val evidenceCount: Int
)

internal class M1AssessmentRecord(
    val accountId: String,
    val snapshotId: String,
    val contentHash: String,
    val evaluatedAtUtc: Instant,
    val schemaVersion: Int = AthleteStateSnapshot.CURRENT_SCHEMA_VERSION,
    val policyVersion: String = Engine15M1PolicyRegistry.POLICY_VERSION,
    val activeRestrictionIds: Set<String> = emptySet(),
    val evidenceIds: Set<String> = emptySet(),
    val disciplineEntries: Map<AthleteDiscipline, M1AssessmentDisciplineEntry> = emptyMap(),
    val derivedNote: String = "DERIVED_CACHE_NON_AUTHORITATIVE"
) {
    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(snapshotId)
        RecordPolicy.identifier(contentHash)
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        RecordPolicy.identifier(policyVersion)
    }

    companion object {
        fun fromSnapshot(snapshot: AthleteStateSnapshot): M1AssessmentRecord {
            val entries = TreeMap<AthleteDiscipline, M1AssessmentDisciplineEntry>()
            for ((disc, summary) in snapshot.disciplines) {
                entries[disc] = M1AssessmentDisciplineEntry(
                    discipline = disc,
                    coverage = summary.coverage,
                    weeklyVolumeCanonical = summary.weeklyVolumeBelief.canonicalRepresentation(),
                    longestSessionCanonical = summary.longestSessionBelief.canonicalRepresentation(),
                    evidenceCount = summary.evidenceIds.size
                )
            }
            return M1AssessmentRecord(
                accountId = snapshot.accountId,
                snapshotId = snapshot.snapshotId,
                contentHash = snapshot.contentHash,
                evaluatedAtUtc = snapshot.evaluatedAtUtc,
                schemaVersion = snapshot.schemaVersion,
                policyVersion = snapshot.policyVersion,
                activeRestrictionIds = snapshot.activeRestrictionIds,
                evidenceIds = snapshot.evidenceIds,
                disciplineEntries = entries
            )
        }
    }
}

internal object M1SafetyRestrictionCodec {
    fun encode(
        restriction: AthleteSafetyRestriction,
        savedAtUtc: Instant = restriction.updatedAtUtc
    ): JSONObject {
        val core = JSONObject()
            .put("recordType", M1RecordType.SAFETY_RESTRICTION.name)
            .put("schemaVersion", restriction.schemaVersion)
            .put("policyVersion", restriction.policyVersion)
            .put("savedAtUtc", savedAtUtc.toString())
            .put("restrictionId", restriction.restrictionId)
            .put("accountId", restriction.accountId)
            .put("kind", restriction.kind.name)
            .put("disciplines", JSONArray(restriction.disciplines.map { it.code }))
            .put("severity", restriction.severity.name)
            .put("provenance", restriction.provenance.name)
            .put("maxWeeklyVolumeMeters", restriction.maxWeeklyVolumeMeters?.value ?: JSONObject.NULL)
            .put("maxSessionIntensityCap", restriction.maxSessionIntensityCap?.name ?: JSONObject.NULL)
            .put("note", restriction.note)
            .put("createdAtUtc", restriction.createdAtUtc.toString())
            .put("updatedAtUtc", restriction.updatedAtUtc.toString())
            .put("expiresAtUtc", restriction.expiresAtUtc?.toString() ?: JSONObject.NULL)
            .put("clearedAtUtc", restriction.clearedAtUtc?.toString() ?: JSONObject.NULL)
            .put("clearedReason", restriction.clearedReason ?: JSONObject.NULL)
            .put("contentHash", restriction.contentHash)
        return core.put("payloadSha256", sha256(restriction.canonicalPayload()))
    }

    fun decode(rawJson: String, expectedAccountId: String? = null): M1DecodeOutcome<AthleteSafetyRestriction> {
        val obj = runCatching { JSONObject(rawJson) }.getOrElse {
            return M1DecodeOutcome.CorruptPayload("MALFORMED_JSON")
        }
        val schemaVersion = obj.optInt("schemaVersion", -1)
        if (schemaVersion <= 0) {
            return M1DecodeOutcome.CorruptPayload("MISSING_OR_INVALID_SCHEMA_VERSION")
        }
        val policyVersion = obj.optString("policyVersion", "").trim()
        if (policyVersion.isEmpty()) {
            return M1DecodeOutcome.CorruptPayload("MISSING_POLICY_VERSION")
        }
        if (schemaVersion > AthleteSafetyRestriction.CURRENT_SCHEMA_VERSION) {
            return M1DecodeOutcome.UnsupportedFutureSchema(
                schemaVersion = schemaVersion,
                policyVersion = policyVersion,
                reason = "UNSUPPORTED_FUTURE_SCHEMA_V$schemaVersion"
            )
        }
        val recordType = obj.optString("recordType", "")
        if (recordType != M1RecordType.SAFETY_RESTRICTION.name) {
            return M1DecodeOutcome.CorruptPayload("UNEXPECTED_RECORD_TYPE")
        }

        return runCatching {
            val accountId = obj.getString("accountId")
            if (expectedAccountId != null) {
                require(accountId == expectedAccountId) { "ACCOUNT_MISMATCH" }
            }
            val discsArray = obj.optJSONArray("disciplines") ?: JSONArray()
            val discs = LinkedHashSet<AthleteDiscipline>()
            for (i in 0 until discsArray.length()) {
                discs.add(AthleteDiscipline.fromCode(discsArray.getString(i)))
            }
            val maxVol = if (obj.isNull("maxWeeklyVolumeMeters")) null
            else Meters.of(obj.getDouble("maxWeeklyVolumeMeters"))
            val intensityCap = if (obj.isNull("maxSessionIntensityCap")) null
            else SafetyIntensityCap.valueOf(obj.getString("maxSessionIntensityCap"))
            val expiresAt = if (obj.isNull("expiresAtUtc")) null
            else Instant.parse(obj.getString("expiresAtUtc"))
            val clearedAt = if (obj.isNull("clearedAtUtc")) null
            else Instant.parse(obj.getString("clearedAtUtc"))
            val clearedReason = if (obj.isNull("clearedReason")) null
            else obj.getString("clearedReason")

            val restored = AthleteSafetyRestriction(
                restrictionId = obj.getString("restrictionId"),
                accountId = accountId,
                kind = SafetyRestrictionKind.valueOf(obj.getString("kind")),
                disciplines = discs,
                severity = SafetyRestrictionSeverity.valueOf(obj.getString("severity")),
                provenance = obj.optString("provenance", SafetyRestrictionProvenance.USER_DECLARED.name)
                    .let { SafetyRestrictionProvenance.valueOf(it) },
                maxWeeklyVolumeMeters = maxVol,
                maxSessionIntensityCap = intensityCap,
                note = obj.optString("note", ""),
                createdAtUtc = Instant.parse(obj.getString("createdAtUtc")),
                updatedAtUtc = Instant.parse(obj.optString("updatedAtUtc")),
                expiresAtUtc = expiresAt,
                clearedAtUtc = clearedAt,
                clearedReason = clearedReason,
                schemaVersion = schemaVersion,
                policyVersion = policyVersion
            )

            val storedHash = obj.optString("contentHash", "")
            if (storedHash.isNotEmpty() && storedHash != restored.contentHash) {
                return M1DecodeOutcome.CorruptPayload("CONTENT_HASH_MISMATCH")
            }

            val compat = if (policyVersion == Engine15M1PolicyRegistry.POLICY_VERSION) {
                M1CompatibilityStatus.EXACT_MATCH
            } else {
                M1CompatibilityStatus.OLDER_POLICY_COMPATIBLE
            }
            M1DecodeOutcome.Decoded(
                value = restored,
                compatibility = compat,
                payloadSha256 = obj.optString("payloadSha256", "").takeIf { it.isNotEmpty() }
            )
        }.getOrElse { err ->
            M1DecodeOutcome.CorruptPayload(err.message ?: "INVALID_RESTRICTION_PAYLOAD")
        }
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

internal object M1AssessmentRecordCodec {
    fun encode(
        record: M1AssessmentRecord,
        savedAtUtc: Instant = record.evaluatedAtUtc
    ): JSONObject {
        val discsObj = JSONObject()
        for ((disc, entry) in record.disciplineEntries) {
            discsObj.put(
                disc.code,
                JSONObject()
                    .put("coverage", entry.coverage.name)
                    .put("weeklyVolumeCanonical", entry.weeklyVolumeCanonical)
                    .put("longestSessionCanonical", entry.longestSessionCanonical)
                    .put("evidenceCount", entry.evidenceCount)
            )
        }
        val payloadObj = JSONObject()
            .put("recordType", M1RecordType.ASSESSMENT_SNAPSHOT.name)
            .put("schemaVersion", record.schemaVersion)
            .put("policyVersion", record.policyVersion)
            .put("savedAtUtc", savedAtUtc.toString())
            .put("accountId", record.accountId)
            .put("snapshotId", record.snapshotId)
            .put("contentHash", record.contentHash)
            .put("evaluatedAtUtc", record.evaluatedAtUtc.toString())
            .put("activeRestrictionIds", JSONArray(record.activeRestrictionIds.sorted()))
            .put("evidenceIds", JSONArray(record.evidenceIds.sorted()))
            .put("disciplines", discsObj)
            .put("derivedNote", record.derivedNote)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${record.accountId}|${record.snapshotId}|${record.contentHash}".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return payloadObj.put("payloadSha256", digest)
    }

    fun decode(rawJson: String, expectedAccountId: String? = null): M1DecodeOutcome<M1AssessmentRecord> {
        val obj = runCatching { JSONObject(rawJson) }.getOrElse {
            return M1DecodeOutcome.CorruptPayload("MALFORMED_JSON")
        }
        val schemaVersion = obj.optInt("schemaVersion", -1)
        if (schemaVersion <= 0) {
            return M1DecodeOutcome.CorruptPayload("MISSING_OR_INVALID_SCHEMA_VERSION")
        }
        val policyVersion = obj.optString("policyVersion", "").trim()
        if (policyVersion.isEmpty()) {
            return M1DecodeOutcome.CorruptPayload("MISSING_POLICY_VERSION")
        }
        if (schemaVersion > AthleteStateSnapshot.CURRENT_SCHEMA_VERSION) {
            return M1DecodeOutcome.UnsupportedFutureSchema(
                schemaVersion = schemaVersion,
                policyVersion = policyVersion,
                reason = "UNSUPPORTED_FUTURE_SCHEMA_V$schemaVersion"
            )
        }
        if (obj.optString("recordType", "") != M1RecordType.ASSESSMENT_SNAPSHOT.name) {
            return M1DecodeOutcome.CorruptPayload("UNEXPECTED_RECORD_TYPE")
        }
        return runCatching {
            val accountId = obj.getString("accountId")
            if (expectedAccountId != null) {
                require(accountId == expectedAccountId) { "ACCOUNT_MISMATCH" }
            }
            val restrictionArr = obj.optJSONArray("activeRestrictionIds") ?: JSONArray()
            val restrictionIds = LinkedHashSet<String>()
            for (i in 0 until restrictionArr.length()) restrictionIds += restrictionArr.getString(i)
            val evArr = obj.optJSONArray("evidenceIds") ?: JSONArray()
            val evIds = LinkedHashSet<String>()
            for (i in 0 until evArr.length()) evIds += evArr.getString(i)
            val discsObj = obj.optJSONObject("disciplines") ?: JSONObject()
            val entries = TreeMap<AthleteDiscipline, M1AssessmentDisciplineEntry>()
            val keys = discsObj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val disc = AthleteDiscipline.fromCode(code)
                val row = discsObj.getJSONObject(code)
                entries[disc] = M1AssessmentDisciplineEntry(
                    discipline = disc,
                    coverage = HistoryCoverageStatus.valueOf(row.getString("coverage")),
                    weeklyVolumeCanonical = row.getString("weeklyVolumeCanonical"),
                    longestSessionCanonical = row.getString("longestSessionCanonical"),
                    evidenceCount = row.optInt("evidenceCount", 0)
                )
            }
            val record = M1AssessmentRecord(
                accountId = accountId,
                snapshotId = obj.getString("snapshotId"),
                contentHash = obj.getString("contentHash"),
                evaluatedAtUtc = Instant.parse(obj.getString("evaluatedAtUtc")),
                schemaVersion = schemaVersion,
                policyVersion = policyVersion,
                activeRestrictionIds = restrictionIds,
                evidenceIds = evIds,
                disciplineEntries = entries,
                derivedNote = obj.optString("derivedNote", "DERIVED_CACHE_NON_AUTHORITATIVE")
            )
            val compat = if (policyVersion == Engine15M1PolicyRegistry.POLICY_VERSION) {
                M1CompatibilityStatus.EXACT_MATCH
            } else {
                M1CompatibilityStatus.OLDER_POLICY_COMPATIBLE
            }
            M1DecodeOutcome.Decoded(
                value = record,
                compatibility = compat,
                payloadSha256 = obj.optString("payloadSha256", "").takeIf { it.isNotEmpty() }
            )
        }.getOrElse { err ->
            M1DecodeOutcome.CorruptPayload(err.message ?: "INVALID_ASSESSMENT_PAYLOAD")
        }
    }
}

internal data class M1StoredRow(
    val recordId: String,
    val payload: String,
    val savedAtMs: Long
)

/**
 * Abstraction for owner-scoped local-only M1 storage (`kind = "workout"`, never queued to `outbox`).
 */
internal interface M1LocalRecordBackend {
    fun putLocal(owner: String, recordId: String, payload: String, nowMs: Long)
    fun getLocal(owner: String, recordId: String): M1StoredRow?
    fun listByPrefix(owner: String, prefix: String): List<M1StoredRow>
    fun deleteLocal(owner: String, recordId: String)
}

/**
 * Production SQLite backend backed by [TrainingStore].
 *
 * Guarantees:
 * - Writes strictly with `kind = "workout"`, `server_version = null`, `outbox` untouched.
 * - Uses existing `applyRemotePage` / `records` table with zero schema migrations.
 * - Never catches `SQLiteDatabaseCorruptException`.
 */
internal class TrainingStoreM1Backend(
    private val store: TrainingStore
) : M1LocalRecordBackend {
    override fun putLocal(owner: String, recordId: String, payload: String, nowMs: Long) {
        RecordPolicy.record(owner, LOCAL_KIND, recordId, payload)
        store.applyRemotePage(
            owner,
            LOCAL_KIND,
            listOf(TrainingStore.RemoteRecord(recordId, payload, null)),
            null,
            nowMs
        )
    }

    override fun getLocal(owner: String, recordId: String): M1StoredRow? {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(recordId)
        val row = store.get(owner, LOCAL_KIND, recordId) ?: return null
        return M1StoredRow(row.id, row.payload, row.syncedAt)
    }

    override fun listByPrefix(owner: String, prefix: String): List<M1StoredRow> {
        RecordPolicy.identifier(owner)
        val out = ArrayList<M1StoredRow>()
        val db: SQLiteDatabase = store.readableDatabase
        db.query(
            "records",
            arrayOf("record_id", "payload", "synced_at"),
            "account=? AND kind=? AND record_id LIKE ?",
            arrayOf(owner, LOCAL_KIND, "$prefix%"),
            null,
            null,
            "record_id"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(M1StoredRow(c.getString(0), c.getString(1), c.getLong(2)))
            }
        }
        return out
    }

    override fun deleteLocal(owner: String, recordId: String) {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(recordId)
        store.deleteRecord(owner, LOCAL_KIND, recordId)
    }

    companion object {
        const val LOCAL_KIND = "workout"
    }
}

/**
 * Pure-JVM backend for deterministic unit verification of M1-D..F persistence,
 * outbox isolation, and offline/guest ownership.
 */
internal class InMemoryM1RecordBackend : M1LocalRecordBackend {
    private data class Key(val owner: String, val recordId: String)

    private val records = LinkedHashMap<Key, M1StoredRow>()
    var outboxWriteCount: Int = 0
        private set
    val recordedKinds: MutableList<String> = mutableListOf()

    override fun putLocal(owner: String, recordId: String, payload: String, nowMs: Long) {
        RecordPolicy.record(owner, TrainingStoreM1Backend.LOCAL_KIND, recordId, payload)
        recordedKinds += TrainingStoreM1Backend.LOCAL_KIND
        records[Key(owner, recordId)] = M1StoredRow(recordId, payload, nowMs)
    }

    override fun getLocal(owner: String, recordId: String): M1StoredRow? {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(recordId)
        return records[Key(owner, recordId)]
    }

    override fun listByPrefix(owner: String, prefix: String): List<M1StoredRow> {
        RecordPolicy.identifier(owner)
        return records.entries
            .filter { (k, _) -> k.owner == owner && k.recordId.startsWith(prefix) }
            .sortedBy { (k, _) -> k.recordId }
            .map { it.value }
    }

    override fun deleteLocal(owner: String, recordId: String) {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(recordId)
        records.remove(Key(owner, recordId))
    }

    fun injectRawForTest(owner: String, recordId: String, rawPayload: String, nowMs: Long = 0L) {
        records[Key(owner, recordId)] = M1StoredRow(recordId, rawPayload, nowMs)
    }

    fun rawRowCount(owner: String, prefix: String): Int =
        listByPrefix(owner, prefix).size
}

internal data class M1RestrictionLoadResult(
    val restrictions: AthleteSafetyRestrictionSet,
    val corruptRecordIds: List<String>,
    val unsupportedSchemaRecordIds: List<String>,
    val olderPolicyRecordIds: List<String>
) {
    /**
     * Fail-closed safety indicator: if persisted user restriction rows are unreadable or from a
     * newer schema, callers know an unparsed safety constraint exists without silently dropping it.
     */
    val failClosedSafetyActive: Boolean
        get() = corruptRecordIds.isNotEmpty() || unsupportedSchemaRecordIds.isNotEmpty()
}

internal sealed interface M1AssessmentLoadResult {
    data class Valid(
        val record: M1AssessmentRecord,
        val compatibility: M1CompatibilityStatus
    ) : M1AssessmentLoadResult

    data class NeedsRebuild(
        val reason: String,
        val corruptDetected: Boolean
    ) : M1AssessmentLoadResult
}

/**
 * Durable local-only M1 repository facade (M1-D..F).
 *
 * Safeguards:
 * - Works identically when signed out (`account == null`) via [LocalPlanOwnership.activeOwner].
 * - Never writes to `outbox`, never mutates `PlanSnapshot`, and never touches `.FIT` archives.
 * - Never deletes corrupt or unsupported future-schema restriction rows on decode failure.
 * - Allows corrupt derived assessment cache rows to rebuild cleanly without affecting restrictions.
 */
internal class Engine15M1LocalStore(
    private val backend: M1LocalRecordBackend
) {
    fun resolveOwner(account: String?, localPlanOwner: String): String =
        LocalPlanOwnership.activeOwner(account, localPlanOwner)

    fun saveRestriction(owner: String, restriction: AthleteSafetyRestriction, nowMs: Long) {
        require(restriction.accountId == owner) {
            "INV-03 violation: restriction.accountId (${restriction.accountId}) != owner ($owner)"
        }
        val key = restrictionRecordId(restriction.restrictionId)
        val json = M1SafetyRestrictionCodec.encode(restriction, Instant.ofEpochMilli(nowMs)).toString()
        backend.putLocal(owner, key, json, nowMs)
    }

    fun clearRestriction(
        owner: String,
        restrictionId: String,
        clearedAtUtc: Instant,
        reason: String?,
        nowMs: Long
    ): AthleteSafetyRestriction? {
        val loaded = loadRestrictions(owner)
        val existing = loaded.restrictions.restrictions.firstOrNull { it.restrictionId == restrictionId }
            ?: return null
        val cleared = existing.clear(clearedAtUtc, reason)
        saveRestriction(owner, cleared, nowMs)
        return cleared
    }

    fun loadRestrictions(owner: String): M1RestrictionLoadResult {
        RecordPolicy.identifier(owner)
        val rows = backend.listByPrefix(owner, RESTRICTION_PREFIX)
        val valid = mutableListOf<AthleteSafetyRestriction>()
        val corrupt = mutableListOf<String>()
        val unsupported = mutableListOf<String>()
        val olderPolicy = mutableListOf<String>()

        for (row in rows) {
            when (val decoded = M1SafetyRestrictionCodec.decode(row.payload, expectedAccountId = owner)) {
                is M1DecodeOutcome.Decoded -> {
                    valid += decoded.value
                    if (decoded.compatibility == M1CompatibilityStatus.OLDER_POLICY_COMPATIBLE) {
                        olderPolicy += row.recordId
                    }
                }
                is M1DecodeOutcome.UnsupportedFutureSchema -> {
                    // Retain untouched on disk so forward upgrades preserve explicit restrictions.
                    unsupported += row.recordId
                }
                is M1DecodeOutcome.CorruptPayload -> {
                    // Never silently delete explicit user safety restriction rows.
                    corrupt += row.recordId
                }
            }
        }
        return M1RestrictionLoadResult(
            restrictions = AthleteSafetyRestrictionSet(owner, valid),
            corruptRecordIds = corrupt,
            unsupportedSchemaRecordIds = unsupported,
            olderPolicyRecordIds = olderPolicy
        )
    }

    fun saveDerivedAssessment(owner: String, assessment: M1AssessmentRecord, nowMs: Long) {
        require(assessment.accountId == owner) {
            "INV-03 violation: assessment.accountId (${assessment.accountId}) != owner ($owner)"
        }
        val key = assessmentRecordId(owner)
        val json = M1AssessmentRecordCodec.encode(assessment, Instant.ofEpochMilli(nowMs)).toString()
        backend.putLocal(owner, key, json, nowMs)
    }

    fun loadDerivedAssessment(owner: String): M1AssessmentLoadResult {
        RecordPolicy.identifier(owner)
        val row = backend.getLocal(owner, assessmentRecordId(owner))
            ?: return M1AssessmentLoadResult.NeedsRebuild("NOT_CACHED", corruptDetected = false)
        return when (val decoded = M1AssessmentRecordCodec.decode(row.payload, expectedAccountId = owner)) {
            is M1DecodeOutcome.Decoded -> M1AssessmentLoadResult.Valid(decoded.value, decoded.compatibility)
            is M1DecodeOutcome.UnsupportedFutureSchema -> M1AssessmentLoadResult.NeedsRebuild(
                decoded.reason,
                corruptDetected = false
            )
            is M1DecodeOutcome.CorruptPayload -> M1AssessmentLoadResult.NeedsRebuild(
                decoded.reason,
                corruptDetected = true
            )
        }
    }

    fun invalidateDerivedAssessment(owner: String) {
        RecordPolicy.identifier(owner)
        backend.deleteLocal(owner, assessmentRecordId(owner))
    }

    companion object {
        const val RESTRICTION_PREFIX = "m1-restriction-"
        const val ASSESSMENT_PREFIX = "m1-assessment-"

        internal fun restrictionRecordId(restrictionId: String): String {
            RecordPolicy.identifier(restrictionId)
            return RESTRICTION_PREFIX + sha256(restrictionId)
        }

        internal fun assessmentRecordId(owner: String): String {
            RecordPolicy.identifier(owner)
            return ASSESSMENT_PREFIX + sha256(owner)
        }

        private fun sha256(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}
