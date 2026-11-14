package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * M3 research-only segment evidence. Current WorkoutLog contains overall
 * minutes/RPE, NOT completed threshold/tempo intervals. It cannot be used
 * as a substitute for this explicit segment-level record.
 *
 * Both origin and source-to-session matching are caller assertions until a
 * future importer verifies them against original recordings. None of the
 * records below constitutes independent device provenance verification.
 */
internal enum class IndependentQualityEvidenceOrigin {
    DEVICE_SEGMENTS_ASSERTED,
    SELF_REPORTED_SEGMENTS
}

internal data class IndependentQualitySegmentEvidence(
    val ownerId: String,
    val sourceRecordId: String,
    val sessionId: String,
    val performedOn: LocalDate,
    val family: IndependentQualityFamily,
    val origin: IndependentQualityEvidenceOrigin,
    val sourceSessionMatchAsserted: Boolean,
    val performedWorkSegmentsSeconds: List<Int>?
) {
    init {
        require(ownerId.isNotBlank() && ownerId.length <= 256)
        require(sourceRecordId.isNotBlank() && sourceRecordId.length <= 256)
        require(sessionId.isNotBlank() && sessionId.length <= 256)
        require(performedWorkSegmentsSeconds == null ||
            (performedWorkSegmentsSeconds.size <= 100 &&
                performedWorkSegmentsSeconds.all { it in 1..14400 } &&
                performedWorkSegmentsSeconds.sum() <= 14400))
    }

    /** Null is unknown; an explicitly empty segment list is zero work. */
    val observedWorkSeconds: Int? get() = performedWorkSegmentsSeconds?.sum()
}

internal enum class IndependentQualityExposureConfidence {
    UNAVAILABLE,
    SELF_REPORTED_ONLY,
    INSUFFICIENT_MATCHED_DEVICE_RECORDS,
    REPEATED_DEVICE_RECORDS_ASSERTED,
    CONFLICT_REVIEW
}

internal data class IndependentQualityExposureAssessment(
    val family: IndependentQualityFamily,
    /** Smaller of the two most recent asserted, segment-level device work doses. */
    val provisionalRepeatedWorkSeconds: Int?,
    val matchedDeviceSessionCount: Int,
    val selfReportedSessionCount: Int,
    val conflictingSessionCount: Int,
    val confidence: IndependentQualityExposureConfidence,
    val evidenceSessionIds: List<String>,
    val ruleIds: List<String>,
    val provenance: String = "CALLER_ASSERTED_DEVICE_SEGMENTS"
) {
    init {
        require(provisionalRepeatedWorkSeconds == null ||
            provisionalRepeatedWorkSeconds in 1..14400)
        require(matchedDeviceSessionCount >= 0 && selfReportedSessionCount >= 0 &&
            conflictingSessionCount >= 0)
        require(provenance == "CALLER_ASSERTED_DEVICE_SEGMENTS")
        require(evidenceSessionIds.distinct() == evidenceSessionIds)
        require(ruleIds.distinct() == ruleIds)
    }

    /** No source importer or cryptographic binding has been implemented yet. */
    val sourceEvidenceVerified: Boolean get() = false

    /** M2's confirmedRecentWorkSeconds must NOT be filled from this field yet. */
    val authorizedForTemplateSelection: Boolean get() = false
}

/**
 * Deterministic comparison only. Two different source IDs can represent one
 * imported workout; if they claim the same planned session we fail closed.
 * Identical repeated records are deduplicated; conflicting exact IDs or
 * session links require an explicit provenance review.
 *
 * We do not infer quality minutes from activity titles, training pace,
 * generic completed flags, prescribed steps or whole-session duration.
 */
internal object IndependentQualityExposure {
    const val POLICY_ID = "stridelab.engine15.m3-quality-exposure-v1"

    fun assess(
        evidence: List<IndependentQualitySegmentEvidence>,
        expectedOwnerId: String,
        family: IndependentQualityFamily,
        asOf: LocalDate,
        lookbackDays: Int = 28
    ): IndependentQualityExposureAssessment {
        require(expectedOwnerId.isNotBlank() && expectedOwnerId.length <= 256)
        require(lookbackDays in 1..84)
        require(evidence.all { it.ownerId == expectedOwnerId }) {
            "Mixed-owner quality history cannot be assessed"
        }

        val recent = evidence.filter { record ->
            ChronoUnit.DAYS.between(record.performedOn, asOf) in
                0L until lookbackDays.toLong()
        }.distinct()

        val sourceConflicts = recent.groupBy { it.sourceRecordId }
            .filterValues { it.size > 1 }.keys
        val sessionConflicts = recent.groupBy { it.sessionId }
            .filterValues { records -> records.size > 1 }.keys
        val conflicts = recent.filter { record ->
            record.sourceRecordId in sourceConflicts ||
                record.sessionId in sessionConflicts
        }
        val relevantConflictSessions = conflicts.filter { it.family == family }
            .map { it.sessionId }.distinct().sorted()

        val clean = recent.filterNot { record ->
            record.sourceRecordId in sourceConflicts ||
                record.sessionId in sessionConflicts
        }.filter { it.family == family }

        val assertedDevice = clean.filter {
            it.origin == IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED &&
                it.sourceSessionMatchAsserted &&
                (it.observedWorkSeconds ?: 0) > 0
        }.sortedWith(
            compareByDescending<IndependentQualitySegmentEvidence> { it.performedOn }
                .thenBy { it.sourceRecordId }
        )
        val manual = clean.count {
            it.origin == IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS
        }
        val comparable = assertedDevice.take(2)
        // Repeated comparable work matters more than a single peak effort.
        // This is an evidence descriptor, NOT an exercise-dose approval.
        val preliminaryWork = if (comparable.size == 2 && relevantConflictSessions.isEmpty())
            comparable.minOf { requireNotNull(it.observedWorkSeconds) }
        else null
        val confidence = when {
            relevantConflictSessions.isNotEmpty() ->
                IndependentQualityExposureConfidence.CONFLICT_REVIEW
            preliminaryWork != null ->
                IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED
            assertedDevice.isNotEmpty() ->
                IndependentQualityExposureConfidence.INSUFFICIENT_MATCHED_DEVICE_RECORDS
            manual > 0 -> IndependentQualityExposureConfidence.SELF_REPORTED_ONLY
            else -> IndependentQualityExposureConfidence.UNAVAILABLE
        }
        val rules = mutableListOf("SOURCE_VERIFICATION_NOT_IMPLEMENTED")
        if (relevantConflictSessions.isNotEmpty()) rules += "DUPLICATE_SOURCE_OR_SESSION_REVIEW"
        if (assertedDevice.isNotEmpty()) rules += "ASSERTED_MATCHED_DEVICE_SEGMENTS"
        if (manual > 0) rules += "MANUAL_SEGMENTS_SEPARATE"
        if (preliminaryWork != null) rules += "MINIMUM_OF_TWO_RECENT_SESSIONS"
        if (preliminaryWork == null) rules += "NO_SUPPORTED_REPEATED_WORK_DOSE"
        return IndependentQualityExposureAssessment(
            family = family,
            provisionalRepeatedWorkSeconds = preliminaryWork,
            matchedDeviceSessionCount = assertedDevice.size,
            selfReportedSessionCount = manual,
            conflictingSessionCount = relevantConflictSessions.size,
            confidence = confidence,
            evidenceSessionIds = if (preliminaryWork != null)
                comparable.map { it.sessionId } else emptyList(),
            ruleIds = rules
        )
    }
}
