package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal enum class Engine15DetectionSource {
    USER_OVERRIDE,
    LINKED_PLAN_INTENT,
    LAP_STRUCTURE_HEURISTIC,
    INSUFFICIENT_DATA
}

internal enum class Engine15DetectionConfidence {
    USER_ASSERTED,
    INTENT_LINKED_UNVERIFIED,
    HEURISTIC_CANDIDATE,
    AMBIGUOUS_CONFLICT,
    INSUFFICIENT_SIGNAL
}

internal data class Engine15DetectedWorkout(
    val activityId: String,
    val mergedSourceIds: List<String>,
    val performedOn: LocalDate,
    val kind: ActivityWorkoutKind,
    val qualitySubtype: ActivityQualitySubtype? = null,
    val source: Engine15DetectionSource,
    val confidence: Engine15DetectionConfidence,
    val underlyingAutomaticKind: ActivityWorkoutKind,
    val underlyingAutomaticSubtype: ActivityQualitySubtype? = null,
    val linkedSessionId: String? = null,
    val candidateWorkSegmentsSeconds: List<Int> = emptyList(),
    val warningCode: String? = null,
    val evidenceNotes: List<String>,
    val sourceEvidenceVerified: Boolean = false
) {
    init {
        require(activityId.isNotBlank())
        require(mergedSourceIds.isNotEmpty() && mergedSourceIds.distinct() == mergedSourceIds)
        require(evidenceNotes.isNotEmpty())
        require(!sourceEvidenceVerified)
    }

    val isUserOverridden: Boolean get() = source == Engine15DetectionSource.USER_OVERRIDE
}

/**
 * Stage 5 (Batch 31): Deterministic automatic workout detection, deduplication,
 * plan-intent linkage, and reversible user-override resolution.
 *
 * - Deduplicates multi-source activity copies via ActivityDisplayConsolidation.
 * - Preserves explicit uncertainty: unlabelled laps never claim verified physiological
 *   zones or performed workout truth; heuristic detections stay HEURISTIC_CANDIDATE.
 * - User overrides take precedence cleanly while retaining the underlying automatic
 *   classification so removing an override restores automatic detection.
 * - Never promotes unconfirmed heuristic candidates or ambiguous conflicts into
 *   IndependentQualitySegmentEvidence.
 */
internal object Engine15WorkoutDetection {
    const val POLICY_ID = "stridelab.engine15.workout-detection-v1"

    fun detectAll(
        owner: String,
        activities: List<ActivityRecord>,
        allowedActivityIds: Set<String>,
        detailsByActivityId: Map<String, ActivityDetail>,
        plans: List<PlanSnapshot>,
        userLabels: Map<String, UserActivityWorkoutLabel>,
        asOf: Instant
    ): List<Engine15DetectedWorkout> {
        require(owner.isNotBlank())
        val groups = ActivityDisplayConsolidation.groups(activities)
            .filter { group ->
                group.primary.id in allowedActivityIds &&
                    DashboardSummary.isRun(group.primary) &&
                    !group.primary.startedAt.isAfter(asOf)
            }
            .sortedWith(compareBy<DisplayActivity> { it.primary.startedAt }.thenBy { it.primary.id })

        return groups.map { group ->
            detectGroup(owner, group, detailsByActivityId, plans, userLabels)
        }
    }

    private fun detectGroup(
        owner: String,
        group: DisplayActivity,
        detailsByActivityId: Map<String, ActivityDetail>,
        plans: List<PlanSnapshot>,
        userLabels: Map<String, UserActivityWorkoutLabel>
    ): Engine15DetectedWorkout {
        val primary = group.primary
        val mergedIds = (listOf(primary) + group.additionalSources).map { it.id }.distinct().sorted()
        val detail = mergedIds.firstNotNullOfOrNull { detailsByActivityId[it] }
        val performedOn = primary.startedAt.atZone(ZoneOffset.UTC).toLocalDate()
        val intelligence = ActivityWorkoutIntelligence.assess(
            activity = primary,
            recorded = detail,
            plans = plans,
            matchedSourceIds = mergedIds.toSet(),
            allowedPlanOwners = setOf(owner)
        )

        val autoResult = classifyAutomatic(primary, detail, intelligence)
        val override = mergedIds.firstNotNullOfOrNull { userLabels[it] }
        if (override != null) {
            return Engine15DetectedWorkout(
                activityId = primary.id,
                mergedSourceIds = mergedIds,
                performedOn = performedOn,
                kind = override.kind,
                qualitySubtype = override.qualitySubtype,
                source = Engine15DetectionSource.USER_OVERRIDE,
                confidence = Engine15DetectionConfidence.USER_ASSERTED,
                underlyingAutomaticKind = autoResult.kind,
                underlyingAutomaticSubtype = autoResult.subtype,
                linkedSessionId = intelligence.linkedSession?.id,
                candidateWorkSegmentsSeconds = autoResult.workSegmentsSeconds,
                warningCode = autoResult.warningCode,
                evidenceNotes = listOf(
                    "User override applied (${override.kind.label}${override.qualitySubtype?.let { " · ${it.label}" } ?: ""}); reversible to automatic classification (${autoResult.kind.label})."
                ) + autoResult.notes
            )
        }

        return Engine15DetectedWorkout(
            activityId = primary.id,
            mergedSourceIds = mergedIds,
            performedOn = performedOn,
            kind = autoResult.kind,
            qualitySubtype = autoResult.subtype,
            source = autoResult.source,
            confidence = autoResult.confidence,
            underlyingAutomaticKind = autoResult.kind,
            underlyingAutomaticSubtype = autoResult.subtype,
            linkedSessionId = intelligence.linkedSession?.id,
            candidateWorkSegmentsSeconds = autoResult.workSegmentsSeconds,
            warningCode = autoResult.warningCode,
            evidenceNotes = autoResult.notes
        )
    }

    private data class AutomaticClassification(
        val kind: ActivityWorkoutKind,
        val subtype: ActivityQualitySubtype?,
        val source: Engine15DetectionSource,
        val confidence: Engine15DetectionConfidence,
        val workSegmentsSeconds: List<Int>,
        val warningCode: String?,
        val notes: List<String>
    )

    private fun classifyAutomatic(
        activity: ActivityRecord,
        detail: ActivityDetail?,
        intelligence: ActivityWorkoutAssessment
    ): AutomaticClassification {
        if (intelligence.intentWarning != null) {
            return AutomaticClassification(
                kind = ActivityWorkoutKind.UNKNOWN,
                subtype = null,
                source = Engine15DetectionSource.INSUFFICIENT_DATA,
                confidence = Engine15DetectionConfidence.AMBIGUOUS_CONFLICT,
                workSegmentsSeconds = emptyList(),
                warningCode = "AMBIGUOUS_PLAN_LINKAGE",
                notes = listOf(intelligence.intentWarning)
            )
        }
        val intended = intelligence.intended
        if (intended != null) {
            val plannedWorkSeconds = intelligence.linkedSession?.runningPrescription?.mixedSegments
                ?.filter { it.purpose == "WORK" }
                ?.mapNotNull { it.seconds?.takeIf { s -> s in 1..14400 } }
                .orEmpty()
            return AutomaticClassification(
                kind = intended.kind,
                subtype = intended.qualitySubtype,
                source = Engine15DetectionSource.LINKED_PLAN_INTENT,
                confidence = Engine15DetectionConfidence.INTENT_LINKED_UNVERIFIED,
                workSegmentsSeconds = plannedWorkSeconds,
                warningCode = null,
                notes = listOf(
                    "Linked to planned session '${intelligence.linkedSession?.title ?: "workout"}' (${intended.kind.label}); lap-level physiological verification remains unavailable."
                )
            )
        }

        val validLaps = detail?.laps.orEmpty().filter { lap ->
            lap.seconds != null && lap.seconds.isFinite() && lap.seconds > 0.0 &&
                lap.pace != null && lap.pace.isFinite() && lap.pace > 0.0
        }
        if (validLaps.size >= 3) {
            val paces = validLaps.map { it.pace!! }.sorted()
            val medianPace = paces[paces.size / 2]
            val fastIndices = validLaps.indices.filter { i ->
                val lap = validLaps[i]
                lap.pace!! <= medianPace * 0.90 && lap.seconds!! in 60.0..600.0
            }
            val separatedRepeats = fastIndices.size >= 2 && fastIndices.zipWithNext().all { (a, b) ->
                b > a + 1 && (a + 1 until b).any { mid ->
                    validLaps[mid].pace!! >= validLaps[a].pace!! * 1.12
                }
            }
            if (separatedRepeats) {
                val workDurations = fastIndices.map { kotlin.math.round(validLaps[it].seconds!!).toInt() }
                val avgWork = workDurations.average()
                val subtype = if (avgWork <= 210.0) ActivityQualitySubtype.INTERVALS else ActivityQualitySubtype.THRESHOLD
                return AutomaticClassification(
                    kind = ActivityWorkoutKind.QUALITY_RUN,
                    subtype = subtype,
                    source = Engine15DetectionSource.LAP_STRUCTURE_HEURISTIC,
                    confidence = Engine15DetectionConfidence.HEURISTIC_CANDIDATE,
                    workSegmentsSeconds = workDurations,
                    warningCode = null,
                    notes = listOf(
                        "Heuristic lap-pace candidate (${workDurations.size} faster laps separated by slower recoveries); requires user confirmation before informing training progression."
                    )
                )
            }
            val interior = validLaps.drop(1).dropLast(1)
            val boundaryPace = (validLaps.first().pace!! + validLaps.last().pace!!) / 2.0
            val sustainedFaster = interior.filter { it.pace!! <= boundaryPace * 0.92 }
            val sustainedSeconds = sustainedFaster.sumOf { it.seconds!! }
            if (sustainedFaster.isNotEmpty() && sustainedSeconds >= 600.0) {
                return AutomaticClassification(
                    kind = ActivityWorkoutKind.QUALITY_RUN,
                    subtype = ActivityQualitySubtype.TEMPO,
                    source = Engine15DetectionSource.LAP_STRUCTURE_HEURISTIC,
                    confidence = Engine15DetectionConfidence.HEURISTIC_CANDIDATE,
                    workSegmentsSeconds = listOf(kotlin.math.round(sustainedSeconds).toInt()),
                    warningCode = null,
                    notes = listOf(
                        "Heuristic sustained faster middle block (${kotlin.math.round(sustainedSeconds / 60.0).toInt()} min) relative to warm-up/cooldown laps; unverified candidate."
                    )
                )
            }
        }

        val distanceM = activity.distanceM?.takeIf { it.isFinite() && it > 0.0 }
        val durationS = activity.durationS?.takeIf { it.isFinite() && it > 0.0 }
        if ((distanceM != null && distanceM >= 14_000.0) || (durationS != null && durationS >= 5_400.0)) {
            return AutomaticClassification(
                kind = ActivityWorkoutKind.LONG_RUN,
                subtype = null,
                source = Engine15DetectionSource.LAP_STRUCTURE_HEURISTIC,
                confidence = Engine15DetectionConfidence.HEURISTIC_CANDIDATE,
                workSegmentsSeconds = emptyList(),
                warningCode = null,
                notes = listOf("Heuristic long-run candidate from recorded distance/duration without interval structure.")
            )
        }

        return AutomaticClassification(
            kind = ActivityWorkoutKind.UNKNOWN,
            subtype = null,
            source = Engine15DetectionSource.INSUFFICIENT_DATA,
            confidence = Engine15DetectionConfidence.INSUFFICIENT_SIGNAL,
            workSegmentsSeconds = emptyList(),
            warningCode = null,
            notes = listOf("No linked plan intent or clear lap-structure signal; classification stays Unknown.")
        )
    }

    /**
     * Converts ONLY confirmed user-overridden or unambiguously plan-linked quality
     * detections with bounded work segments into IndependentQualitySegmentEvidence.
     * Unconfirmed heuristic candidates and ambiguous conflicts are excluded.
     */
    fun toQualitySegmentEvidence(
        owner: String,
        detections: List<Engine15DetectedWorkout>
    ): List<IndependentQualitySegmentEvidence> {
        require(owner.isNotBlank())
        return detections.mapNotNull { detection ->
            if (detection.kind != ActivityWorkoutKind.QUALITY_RUN) return@mapNotNull null
            if (detection.confidence !in setOf(
                    Engine15DetectionConfidence.USER_ASSERTED,
                    Engine15DetectionConfidence.INTENT_LINKED_UNVERIFIED
                )) return@mapNotNull null
            val family = when (detection.qualitySubtype) {
                ActivityQualitySubtype.TEMPO -> IndependentQualityFamily.TEMPO
                ActivityQualitySubtype.THRESHOLD -> IndependentQualityFamily.THRESHOLD
                ActivityQualitySubtype.INTERVALS -> IndependentQualityFamily.INTERVALS
                ActivityQualitySubtype.HILLS -> IndependentQualityFamily.HILLS
                ActivityQualitySubtype.FARTLEK -> IndependentQualityFamily.FARTLEK
                ActivityQualitySubtype.PROGRESSION -> IndependentQualityFamily.PROGRESSION
                else -> return@mapNotNull null
            }
            val segments = detection.candidateWorkSegmentsSeconds
                .takeIf { it.isNotEmpty() && it.all { s -> s in 1..14400 } && it.sum() <= 14400 }
                ?: return@mapNotNull null
            val origin = if (detection.isUserOverridden) {
                IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS
            } else {
                IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED
            }
            IndependentQualitySegmentEvidence(
                ownerId = owner,
                sourceRecordId = detection.activityId,
                sessionId = detection.linkedSessionId ?: "activity:${detection.activityId}",
                performedOn = detection.performedOn,
                family = family,
                origin = origin,
                sourceSessionMatchAsserted = detection.linkedSessionId != null,
                performedWorkSegmentsSeconds = segments
            )
        }
    }
}
