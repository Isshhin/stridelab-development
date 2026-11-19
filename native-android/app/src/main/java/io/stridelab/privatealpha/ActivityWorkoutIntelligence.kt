package io.stridelab.privatealpha

import org.json.JSONObject
import java.time.Duration
import kotlin.math.abs

internal enum class ActivityWorkoutKind(val label: String) {
    EASY_RUN("Easy Run"),
    RECOVERY_RUN("Recovery Run"),
    LONG_RUN("Long Run"),
    QUALITY_RUN("Quality Run"),
    MIXED_SESSION("Mixed Session"),
    UNKNOWN("Unknown")
}

internal enum class ActivityQualitySubtype(val label: String) {
    TEMPO("Tempo"),
    THRESHOLD("Threshold"),
    INTERVALS("Intervals"),
    FARTLEK("Fartlek"),
    PROGRESSION("Progression"),
    HILLS("Hills"),
    STRIDES("Strides");

    companion object {
        fun fromFamily(value: String?): ActivityQualitySubtype? = entries.firstOrNull {
            it.label.equals(value, ignoreCase = true)
        }
    }
}

internal enum class ActivityWorkoutConfidence { HIGH, MODERATE, INSUFFICIENT }

internal data class ActivityWorkoutEvidence(
    val kind: ActivityWorkoutKind,
    val qualitySubtype: ActivityQualitySubtype? = null,
    val evidence: List<String>,
    val confidence: ActivityWorkoutConfidence = ActivityWorkoutConfidence.INSUFFICIENT
)
internal data class ActivityWorkoutAdherence(
    val status: String,
    val evidence: List<String>,
    val observedWorkLaps: Int,
    val observedRecoveryLaps: Int
)

internal data class ActivityWorkoutAssessment(
    val detected: ActivityWorkoutEvidence,
    val intended: ActivityWorkoutEvidence? = null,
    val intentWarning: String? = null,
    val linkedSession: PlannedSession? = null,
    val adherence: ActivityWorkoutAdherence? = null
)


/**
 * Report source-observed execution separately from linked plan intention.
 * An unexecuted FIT WorkoutStep list or a session title never proves performance.
 */
internal object ActivityWorkoutIntelligence {
    fun assess(
        activity: ActivityRecord,
        recorded: ActivityDetail?,
        plans: List<PlanSnapshot>,
        matchedSourceIds: Set<String>,
        allowedPlanOwners: Set<String>,
        history: List<ActivityRecord> = emptyList(),
        zoneDistribution: HeartRateZoneDistribution? = null
    ): ActivityWorkoutAssessment {
        val detected = detect(activity, recorded, history, zoneDistribution)
        if (!DashboardSummary.isRun(activity) || matchedSourceIds.isEmpty())
            return ActivityWorkoutAssessment(detected)

        val links = completedRunLinks(plans, allowedPlanOwners)
            .filter { it.second.linkedActivityId in matchedSourceIds }.take(2)
        if (links.size > 1) {
            return ActivityWorkoutAssessment(detected, intentWarning =
                "More than one completed plan session links to this activity; planned intent is ambiguous.")
        }
        val session = links.singleOrNull()?.second ?: return ActivityWorkoutAssessment(detected)
        val prescription = session.runningPrescription
        val kind = when (prescription?.role) {
            "RECOVERY" -> ActivityWorkoutKind.RECOVERY_RUN
            "EASY", "ENDURANCE" -> ActivityWorkoutKind.EASY_RUN
            "LONG" -> ActivityWorkoutKind.LONG_RUN
            "QUALITY" -> ActivityWorkoutKind.QUALITY_RUN
            else -> when (session.kind.lowercase()) {
                "recovery" -> ActivityWorkoutKind.RECOVERY_RUN
                "easy" -> ActivityWorkoutKind.EASY_RUN
                "long" -> ActivityWorkoutKind.LONG_RUN
                "quality" -> ActivityWorkoutKind.QUALITY_RUN
                "mixed" -> ActivityWorkoutKind.MIXED_SESSION
                else -> ActivityWorkoutKind.UNKNOWN
            }
        }
        if (kind == ActivityWorkoutKind.UNKNOWN) return ActivityWorkoutAssessment(
            detected, intentWarning = "The linked plan does not contain a supported running workout label."
        )
        val subtype = if (kind == ActivityWorkoutKind.QUALITY_RUN)
            ActivityQualitySubtype.fromFamily(prescription?.family) else null
        return ActivityWorkoutAssessment(
            detected,
            intended = ActivityWorkoutEvidence(
                kind, subtype,
                listOf("The linked plan records this as intended " +
                    (subtype?.label ?: kind.label) +
                    ". This does not confirm which steps were performed.")
            ),
            linkedSession = session,
            adherence = adherence(activity, recorded, session)
        )
    }

    private fun unknown(reason: String) = ActivityWorkoutEvidence(
        ActivityWorkoutKind.UNKNOWN, evidence = listOf(reason))

    fun detect(
        activity: ActivityRecord,
        recorded: ActivityDetail?,
        history: List<ActivityRecord> = emptyList(),
        zones: HeartRateZoneDistribution? = null
    ): ActivityWorkoutEvidence {
        if (!DashboardSummary.isRun(activity))
            return unknown("Workout classification is supported only for running activities.")
        if (recorded == null) return unknown("Saved workout-step detail is unavailable for this activity.")

        // WORKOUT_STEP describes a proposed structure. Only lap references
        // associated with the recorded activity establish execution.
        val byStep = recorded.workoutSteps.associateBy { it.index }
        val executed = recorded.laps.mapNotNull { lap ->
            val step = lap.workoutStepIndex?.let(byStep::get)
            if (step == null || lap.seconds == null || lap.seconds < 10.0) null
            else lap to step
        }
        val work = executed.filter { (_, step) -> step.role == "WORK" }
        val recovery = executed.filter { (_, step) -> step.role == "RECOVERY" }
        if (work.isNotEmpty()) {
            val names = work.mapNotNull { (_, step) -> step.name?.lowercase() }
            val hinted = ActivityQualitySubtype.entries.filter { family ->
                names.any { name ->
                    when (family) {
                        ActivityQualitySubtype.INTERVALS -> "interval" in name || "repeat" in name
                        ActivityQualitySubtype.THRESHOLD -> "threshold" in name || "lthr" in name
                        ActivityQualitySubtype.TEMPO -> "tempo" in name
                        ActivityQualitySubtype.FARTLEK -> "fartlek" in name
                        ActivityQualitySubtype.PROGRESSION -> "progression" in name
                        ActivityQualitySubtype.HILLS -> "hill" in name
                        ActivityQualitySubtype.STRIDES -> "stride" in name
                    }
                }
            }
            val workSeconds = work.sumOf { it.first.seconds ?: 0.0 }
            if (hinted.size == 1 && workSeconds >= 120.0) {
                return ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN, hinted.single(),
                    listOf(
                        "Recorded FIT laps reference " + work.size +
                            " executed work step(s) for a named " + hinted.single().label + " workout.",
                        "Verified work-lap duration: " +
                            ActivityRecord.duration(workSeconds) +
                            "; recorded recovery laps: " + recovery.size + "."
                    ), ActivityWorkoutConfidence.HIGH)
            }
            if (work.size >= 3 && recovery.size >= 2 && workSeconds >= 180.0) {
                return ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                    ActivityQualitySubtype.INTERVALS, listOf(
                        "FIT lap indices match " + work.size +
                            " recorded active steps and " + recovery.size + " recovery steps.",
                        "The executed structure is repeated work and recovery; no specific pace target is inferred."
                    ), ActivityWorkoutConfidence.HIGH)
            }
            if (hinted.size > 1)
                return unknown("Multiple workout families appear in executed FIT steps. The performed type is ambiguous.")
            return unknown("FIT work steps were recorded, but their purpose cannot be distinguished safely.")
        }

        // A workout definition on its own is NOT evidence of execution.
        // When the FIT has labelled laps with no work, recovery-only sessions
        // need an explicit executed Recovery Run label.
        if (executed.isNotEmpty() && executed.all { (_, step) ->
                step.role == "RECOVERY" || step.role == "WARM_UP" ||
                    step.role == "COOL_DOWN"
            } && executed.any { (_, step) ->
                step.name?.contains("recovery run", ignoreCase = true) == true
            }) {
            return ActivityWorkoutEvidence(ActivityWorkoutKind.RECOVERY_RUN,
                evidence = listOf("Recorded FIT laps reference a named Recovery Run step."),
                confidence = ActivityWorkoutConfidence.HIGH)
        }

        // Unlabelled auto-laps: require repeatable measured fast/slow cycles,
        // not merely variable pace, stopping, GPS pauses or activity title.
        val laps = recorded.laps.filter {
            it.seconds != null && it.seconds in 30.0..1200.0 &&
                it.pace != null && it.pace in 120.0..900.0 &&
                it.distance != null && it.distance > 70.0
        }
        if (laps.size >= 6 && recorded.pauseIntervals.isEmpty()) {
            val alternating = laps.take(6).chunked(2)
            if (alternating.size == 3 && alternating.all { pair ->
                    pair.size == 2 && pair[0].pace!! * 1.18 <= pair[1].pace!!
                }) {
                return ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                    ActivityQualitySubtype.INTERVALS, listOf(
                        "At least three recorded fast/slow lap pairs show distinct effort and recovery pacing.",
                        "FIT WORK/RECOVERY labels are unavailable; classification is based on repeated measured lap patterns."
                    ), ActivityWorkoutConfidence.MODERATE)
            }
        }

        // Sustained progression uses consecutive source laps, not title hints.
        val kmLaps = recorded.laps.filter {
            it.distance != null && it.distance in 800.0..1200.0 &&
                it.pace != null && it.pace in 120.0..900.0
        }
        if (kmLaps.size >= 5 && recorded.pauseIntervals.isEmpty()) {
            val paces = kmLaps.map { it.pace!! }
            val faster = paces.zipWithNext().count { (a, b) -> b < a * .995 }
            if (faster >= paces.size - 2 && paces.last() < paces.first() * .92) {
                return ActivityWorkoutEvidence(ActivityWorkoutKind.QUALITY_RUN,
                    ActivityQualitySubtype.PROGRESSION, listOf(
                        "Recorded kilometre splits show sustained faster pacing from early to late laps.",
                        "This is a measured progression pattern, not proof of a prescribed intensity target."
                    ), ActivityWorkoutConfidence.MODERATE)
            }
        }

        val duration = activity.movingTimeS ?: activity.durationS
        val lowerZoneSeconds = zones?.buckets?.take(2)?.sumOf { it.seconds } ?: 0.0
        val zoneSeconds = zones?.classifiedSeconds ?: 0.0
        val aerobic = duration != null && duration > 0 &&
            zoneSeconds >= duration * .55 && lowerZoneSeconds >= zoneSeconds * .85
        val recent = history.filter {
            it.id != activity.id && it.source == activity.source &&
                DashboardSummary.isRun(it) && it.startedAt < activity.startedAt &&
                Duration.between(it.startedAt, activity.startedAt).toDays() <= 90 &&
                it.distanceM != null && it.distanceM > 1000.0
        }.take(20)
        if (aerobic && recent.size >= 6 && activity.distanceM != null &&
            duration != null && duration >= 75 * 60) {
            val distances = recent.mapNotNull { it.distanceM }.sorted()
            val median = distances[distances.size / 2]
            if (activity.distanceM >= median * 1.5) {
                return ActivityWorkoutEvidence(ActivityWorkoutKind.LONG_RUN,
                    evidence = listOf(
                        "A sustained aerobic recording was at least 50% longer than median recent distance.",
                        "Comparison uses " + recent.size +
                            " earlier same-source running records; conditions and history completeness may differ."
                    ), confidence = ActivityWorkoutConfidence.MODERATE)
            }
        }
        if (aerobic && duration != null && duration >= 20 * 60) {
            return ActivityWorkoutEvidence(ActivityWorkoutKind.EASY_RUN,
                evidence = listOf(
                    "At least 85% of classified HR recording time was in the selected first two zones.",
                    "Measured time covered at least 55% of moving duration. Easy describes observed intensity, not a Garmin workout label."
                ), confidence = ActivityWorkoutConfidence.MODERATE)
        }

        return unknown(
            if (recorded.workoutSteps.isNotEmpty())
                "FIT workout definitions exist but no reliable executed WORK/RECOVERY labels or sufficient intensity evidence were found."
            else "Recorded laps contain no WORK/RECOVERY step labels, so workout purpose cannot be verified."
        )
    }

    private fun adherence(
        activity: ActivityRecord,
        recorded: ActivityDetail?,
        session: PlannedSession
    ): ActivityWorkoutAdherence {
        val prescription = session.runningPrescription
        val plannedPurposes = prescription?.mixedSegments?.map { it.purpose } ?:
            prescription?.segments?.map { it.purpose }.orEmpty()
        val expectedWork = plannedPurposes.count { it == "WORK" }
        val expectedRecovery = plannedPurposes.count { it == "RECOVERY" }

        val byStep = recorded?.workoutSteps.orEmpty().associateBy { it.index }
        val executed = recorded?.laps.orEmpty().mapNotNull { lap ->
            val step = lap.workoutStepIndex?.let { byStep[it] }
            if (step == null || lap.seconds == null || lap.seconds <= 0.0) null
            else lap to step
        }
        val actualWork = executed.count { it.second.role == "WORK" }
        val actualRecovery = executed.count { it.second.role == "RECOVERY" }
        val summary = mutableListOf(
            "Planned " + (session.distanceKm?.let {
                String.format(java.util.Locale.ROOT, "%.2f km", it)
            } ?: "distance unavailable") +
                "; recorded " + activity.distanceLabel + ".",
            "Planned " + session.effectiveMinutes + " min; recorded elapsed " +
                activity.durationLabel + ". Elapsed and moving time are different."
        )
        if (prescription == null || plannedPurposes.isEmpty() || executed.isEmpty()) {
            return ActivityWorkoutAdherence("SUMMARY_ONLY", summary +
                "No matched source-labelled work/recovery laps; planned intensity and steps cannot be verified.",
                actualWork, actualRecovery)
        }
        fun describeMeasured(purpose: String, plannedMeters: Int?, plannedSeconds: Int?) {
            val matched = executed.filter { it.second.role == purpose }
            if (matched.isEmpty()) return
            val elapsed = matched.mapNotNull { it.first.seconds }.sum()
            val distances = matched.map { it.first.distance }
            val meters = if (distances.all { it != null }) distances.filterNotNull().sum()
                else null
            summary += purpose.lowercase().replaceFirstChar { it.uppercase() } +
                " recorded: " + matched.size + " lap(s), " +
                ActivityRecord.duration(elapsed) +
                (meters?.let {
                    ", " + String.format(java.util.Locale.ROOT, "%.0f m", it)
                } ?: ", distance unavailable") + "."
            if (plannedMeters != null && meters != null) {
                val difference = meters - plannedMeters
                summary += purpose.lowercase().replaceFirstChar { it.uppercase() } +
                    " distance difference: " +
                    String.format(java.util.Locale.ROOT, "%+.0f m", difference) +
                    " compared with " + plannedMeters + " planned m."
            }
            if (plannedSeconds != null) {
                summary += purpose.lowercase().replaceFirstChar { it.uppercase() } +
                    " duration difference: " +
                    String.format(java.util.Locale.ROOT, "%+.0f s",
                        elapsed - plannedSeconds) +
                    " compared with " + plannedSeconds + " planned s."
            }
        }
        fun plannedDistance(purpose: String): Int? =
            if (prescription.mixedSegments != null) {
                val rows = prescription.mixedSegments.filter { it.purpose == purpose }
                if (rows.isEmpty() || rows.any { it.meters == null }) null
                else rows.sumOf { it.meters!! }
            } else {
                val rows = prescription.segments.filter { it.purpose == purpose }
                rows.takeIf { it.isNotEmpty() }?.sumOf { it.meters }
            }
        fun plannedTime(purpose: String): Int? {
            val rows = prescription.mixedSegments?.filter { it.purpose == purpose }
                ?: return null
            return if (rows.isNotEmpty() && rows.all { it.seconds != null })
                rows.sumOf { it.seconds!! } else null
        }
        describeMeasured("WORK", plannedDistance("WORK"), plannedTime("WORK"))
        describeMeasured("RECOVERY", plannedDistance("RECOVERY"), plannedTime("RECOVERY"))
        val countsMatch = expectedWork == actualWork && expectedRecovery == actualRecovery
        summary += "Planned " + expectedWork + " work / " + expectedRecovery +
            " recovery step(s); observed FIT laps " + actualWork +
            " work / " + actualRecovery + " recovery step(s)."
        summary += "Matching recorded amounts do not prove prescribed pace, HR targets, or workout quality."
        return ActivityWorkoutAdherence(
            if (countsMatch) "STEP_COUNTS_MATCH" else "STEP_COUNTS_DIFFER",
            summary, actualWork, actualRecovery
        )
    }

    internal fun completedRunLinks(
        plans: List<PlanSnapshot>,
        allowedOwners: Set<String>
    ): List<Pair<PlanSnapshot, PlannedSession>> = plans.asSequence()
        .filter { it.owner in allowedOwners && it.status in setOf("approved", "paused") &&
            Engine1Engines.supports(it.engineVersion, it.policyId) }
        .flatMap { plan -> plan.sessions.asSequence()
            .filter { it.discipline == "run" && it.status == "completed" &&
                it.linkedActivityId != null }
            .map { plan to it }
        }.toList()
}

/** A user-entered activity label, stored separately from immutable imported activity data. */
internal data class UserActivityWorkoutLabel(
    val kind: ActivityWorkoutKind,
    val qualitySubtype: ActivityQualitySubtype? = null
) {
    init {
        require(kind != ActivityWorkoutKind.UNKNOWN)
        require(qualitySubtype == null || kind == ActivityWorkoutKind.QUALITY_RUN)
    }

    val label: String get() = qualitySubtype?.let { "${kind.label} · ${it.label}" } ?: kind.label
}

internal object UserActivityWorkoutLabelCodec {
    private const val VERSION = 1

    fun encode(label: UserActivityWorkoutLabel): String = JSONObject()
        .put("version", VERSION)
        .put("kind", label.kind.name)
        .put("qualitySubtype", label.qualitySubtype?.name)
        .put("provenance", "USER_REPORTED")
        .toString()

    fun decode(raw: String): UserActivityWorkoutLabel? = runCatching {
        val json = JSONObject(raw)
        require(json.getInt("version") == VERSION)
        require(json.getString("provenance") == "USER_REPORTED")
        val kind = ActivityWorkoutKind.valueOf(json.getString("kind"))
        val subtype = if (json.isNull("qualitySubtype")) null
            else ActivityQualitySubtype.valueOf(json.getString("qualitySubtype"))
        UserActivityWorkoutLabel(kind, subtype)
    }.getOrNull()
}

internal fun activityCorrectionOwner(activityId: String, state: TrainingState): String? =
    if (activityId in state.localGarminIds) state.localGarminOwner
    else state.account ?: state.localPlanOwner
