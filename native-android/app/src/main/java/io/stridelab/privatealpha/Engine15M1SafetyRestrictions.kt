package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections
import java.util.TreeSet

/**
 * Classification of athlete-declared or coach/medical safety restrictions (M1-D).
 * Kept strictly orthogonal to PlanRequest targets and workout prescriptions.
 */
internal enum class SafetyRestrictionKind {
    INJURY_OR_PAIN,
    RETURN_TO_RUNNING,
    MAX_WEEKLY_VOLUME_CAP,
    MAX_INTENSITY_CAP,
    MEDICAL_OR_REST_OVERRIDE
}

/**
 * Operational enforcement tier for safety restrictions.
 * HARD_BLOCK forbids new load for covered disciplines; CAP_LIMITED imposes
 * numerical/intensity ceilings; ADVISORY_CAUTION surfaces non-blocking warnings.
 */
internal enum class SafetyRestrictionSeverity {
    HARD_BLOCK,
    CAP_LIMITED,
    ADVISORY_CAUTION
}

/**
 * Explicit origin tracking separating user inputs, medical clearances, and backups.
 */
internal enum class SafetyRestrictionProvenance {
    USER_DECLARED,
    MEDICAL_CLEARANCE_REQUIRED,
    COACH_GUIDED_RETURN,
    IMPORTED_BACKUP
}

/**
 * Ordered intensity ceilings (ordinal order is strictest -> mildest).
 */
internal enum class SafetyIntensityCap(val strictnessRank: Int) {
    REST_ONLY(0),
    EASY_AEROBIC_ONLY(1),
    NO_HIGH_INTENSITY_Z3(2)
}

/**
 * Immutable, versioned M1-D persistent athlete safety restriction record.
 *
 * - An empty [disciplines] set means the restriction applies across all disciplines.
 * - Clearing preserves audit metadata ([clearedAtUtc], [clearedReason]) rather than deleting
 *   history silently.
 * - [contentHash] deterministically hashes all canonical safety attributes.
 */
internal class AthleteSafetyRestriction(
    val restrictionId: String,
    val accountId: String,
    val kind: SafetyRestrictionKind,
    disciplines: Set<AthleteDiscipline> = emptySet(),
    val severity: SafetyRestrictionSeverity,
    val provenance: SafetyRestrictionProvenance = SafetyRestrictionProvenance.USER_DECLARED,
    val maxWeeklyVolumeMeters: Meters? = null,
    val maxSessionIntensityCap: SafetyIntensityCap? = null,
    val note: String = "",
    val createdAtUtc: Instant,
    val updatedAtUtc: Instant = createdAtUtc,
    val expiresAtUtc: Instant? = null,
    val clearedAtUtc: Instant? = null,
    val clearedReason: String? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val policyVersion: String = Engine15M1PolicyRegistry.POLICY_VERSION
) {
    val disciplines: Set<AthleteDiscipline>
    val contentHash: String

    init {
        RecordPolicy.identifier(restrictionId)
        RecordPolicy.identifier(accountId)
        require(schemaVersion > 0) { "schemaVersion must be positive, was $schemaVersion" }
        RecordPolicy.identifier(policyVersion)
        require(!updatedAtUtc.isBefore(createdAtUtc)) {
            "updatedAtUtc ($updatedAtUtc) cannot precede createdAtUtc ($createdAtUtc)"
        }
        if (expiresAtUtc != null) {
            require(!expiresAtUtc.isBefore(createdAtUtc)) {
                "expiresAtUtc ($expiresAtUtc) cannot precede createdAtUtc ($createdAtUtc)"
            }
        }
        if (clearedAtUtc != null) {
            require(!clearedAtUtc.isBefore(createdAtUtc)) {
                "clearedAtUtc ($clearedAtUtc) cannot precede createdAtUtc ($createdAtUtc)"
            }
        }
        require(note.length <= MAX_NOTE_LENGTH && note.none { Character.isISOControl(it) }) {
            "note must be <= $MAX_NOTE_LENGTH printable characters"
        }
        if (clearedReason != null) {
            require(
                clearedReason.isNotBlank() &&
                    clearedReason.length <= MAX_CLEAR_REASON_LENGTH &&
                    clearedReason.none { Character.isISOControl(it) }
            ) {
                "clearedReason must be 1..$MAX_CLEAR_REASON_LENGTH printable characters"
            }
        }
        if (kind == SafetyRestrictionKind.MAX_WEEKLY_VOLUME_CAP) {
            require(maxWeeklyVolumeMeters != null) {
                "MAX_WEEKLY_VOLUME_CAP requires non-null maxWeeklyVolumeMeters"
            }
        }
        if (kind == SafetyRestrictionKind.MAX_INTENSITY_CAP) {
            require(maxSessionIntensityCap != null) {
                "MAX_INTENSITY_CAP requires non-null maxSessionIntensityCap"
            }
        }

        val sortedDisciplines = TreeSet<AthleteDiscipline>()
        sortedDisciplines.addAll(disciplines)
        this.disciplines = Collections.unmodifiableSet(sortedDisciplines)
        this.contentHash = computeSha256(canonicalPayload())
    }

    val isCleared: Boolean get() = clearedAtUtc != null

    fun appliesTo(discipline: AthleteDiscipline): Boolean =
        disciplines.isEmpty() || discipline in disciplines

    fun isActiveAt(referenceUtc: Instant): Boolean {
        if (referenceUtc.isBefore(createdAtUtc)) return false
        if (clearedAtUtc != null && !referenceUtc.isBefore(clearedAtUtc)) return false
        if (expiresAtUtc != null && !referenceUtc.isBefore(expiresAtUtc)) return false
        return true
    }

    fun clear(atUtc: Instant, reason: String? = null): AthleteSafetyRestriction {
        val cleanReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        val effectiveUpdate = if (atUtc.isAfter(updatedAtUtc)) atUtc else updatedAtUtc
        return AthleteSafetyRestriction(
            restrictionId = restrictionId,
            accountId = accountId,
            kind = kind,
            disciplines = disciplines,
            severity = severity,
            provenance = provenance,
            maxWeeklyVolumeMeters = maxWeeklyVolumeMeters,
            maxSessionIntensityCap = maxSessionIntensityCap,
            note = note,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = effectiveUpdate,
            expiresAtUtc = expiresAtUtc,
            clearedAtUtc = atUtc,
            clearedReason = cleanReason,
            schemaVersion = schemaVersion,
            policyVersion = policyVersion
        )
    }

    internal fun canonicalPayload(): String = buildString {
        append("schema=").append(schemaVersion)
        append("|policy=").append(AthleteStateSnapshot.canonicalSegment(policyVersion))
        append("|id=").append(AthleteStateSnapshot.canonicalSegment(restrictionId))
        append("|account=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|kind=").append(kind.name)
        append("|discs=").append(AthleteStateSnapshot.canonicalList(disciplines.map { it.code }))
        append("|sev=").append(severity.name)
        append("|prov=").append(provenance.name)
        append("|maxVol=").append(maxWeeklyVolumeMeters?.canonicalToken() ?: "NONE")
        append("|intCap=").append(maxSessionIntensityCap?.name ?: "NONE")
        append("|note=").append(AthleteStateSnapshot.canonicalSegment(note))
        append("|created=").append(createdAtUtc.toString())
        append("|updated=").append(updatedAtUtc.toString())
        append("|expires=").append(expiresAtUtc?.toString() ?: "NONE")
        append("|cleared=").append(clearedAtUtc?.toString() ?: "NONE")
        append("|clearReason=").append(clearedReason?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
    }

    override fun equals(other: Any?): Boolean =
        other is AthleteSafetyRestriction &&
            restrictionId == other.restrictionId &&
            accountId == other.accountId &&
            kind == other.kind &&
            disciplines == other.disciplines &&
            severity == other.severity &&
            provenance == other.provenance &&
            maxWeeklyVolumeMeters == other.maxWeeklyVolumeMeters &&
            maxSessionIntensityCap == other.maxSessionIntensityCap &&
            note == other.note &&
            createdAtUtc == other.createdAtUtc &&
            updatedAtUtc == other.updatedAtUtc &&
            expiresAtUtc == other.expiresAtUtc &&
            clearedAtUtc == other.clearedAtUtc &&
            clearedReason == other.clearedReason &&
            schemaVersion == other.schemaVersion &&
            policyVersion == other.policyVersion

    override fun hashCode(): Int = contentHash.hashCode()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_NOTE_LENGTH = 500
        const val MAX_CLEAR_REASON_LENGTH = 200

        private fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

/**
 * Owner-scoped collection of athlete safety restrictions providing deterministic
 * active filtering, strictest cap resolution, and AthleteStateSnapshot ID projection.
 */
internal class AthleteSafetyRestrictionSet(
    val accountId: String,
    restrictions: List<AthleteSafetyRestriction> = emptyList()
) {
    val restrictions: List<AthleteSafetyRestriction>

    init {
        RecordPolicy.identifier(accountId)
        val seen = HashSet<String>(restrictions.size)
        val sorted = restrictions.sortedWith(
            compareBy<AthleteSafetyRestriction> { it.createdAtUtc }.thenBy { it.restrictionId }
        )
        for (item in sorted) {
            require(item.accountId == accountId) {
                "INV-03 violation: restriction ${item.restrictionId} owner ${item.accountId} != $accountId"
            }
            require(seen.add(item.restrictionId)) {
                "Duplicate safety restrictionId: ${item.restrictionId}"
            }
        }
        this.restrictions = Collections.unmodifiableList(sorted)
    }

    fun activeAt(referenceUtc: Instant): List<AthleteSafetyRestriction> =
        restrictions.filter { it.isActiveAt(referenceUtc) }

    fun activeFor(discipline: AthleteDiscipline, referenceUtc: Instant): List<AthleteSafetyRestriction> =
        restrictions.filter { it.isActiveAt(referenceUtc) && it.appliesTo(discipline) }

    fun activeRestrictionIdsAt(referenceUtc: Instant): Set<String> =
        activeAt(referenceUtc).mapTo(LinkedHashSet()) { it.restrictionId }

    fun hasHardBlock(discipline: AthleteDiscipline, referenceUtc: Instant): Boolean =
        activeFor(discipline, referenceUtc).any {
            it.severity == SafetyRestrictionSeverity.HARD_BLOCK ||
                it.kind == SafetyRestrictionKind.MEDICAL_OR_REST_OVERRIDE
        }

    fun effectiveMaxWeeklyVolumeCap(discipline: AthleteDiscipline, referenceUtc: Instant): Meters? =
        activeFor(discipline, referenceUtc)
            .mapNotNull { it.maxWeeklyVolumeMeters }
            .minOrNull()

    fun effectiveIntensityCap(discipline: AthleteDiscipline, referenceUtc: Instant): SafetyIntensityCap? =
        activeFor(discipline, referenceUtc)
            .mapNotNull { it.maxSessionIntensityCap }
            .minByOrNull { it.strictnessRank }

    companion object {
        fun empty(accountId: String): AthleteSafetyRestrictionSet =
            AthleteSafetyRestrictionSet(accountId, emptyList())
    }
}
