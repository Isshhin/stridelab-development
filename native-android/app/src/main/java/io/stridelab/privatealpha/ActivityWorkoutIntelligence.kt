package io.stridelab.privatealpha

import org.json.JSONObject

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

internal data class ActivityWorkoutEvidence(
    val kind: ActivityWorkoutKind,
    val qualitySubtype: ActivityQualitySubtype? = null,
    val evidence: List<String>
)

internal data class ActivityWorkoutAssessment(
    val detected: ActivityWorkoutEvidence,
    val intended: ActivityWorkoutEvidence? = null,
    val intentWarning: String? = null,
    val linkedSession: PlannedSession? = null
)

/** Current FIT/Garmin detail has unlabelled laps, so it can establish intent but not performed steps. */
internal object ActivityWorkoutIntelligence {
    fun assess(
        activity: ActivityRecord,
        recorded: ActivityDetail?,
        plans: List<PlanSnapshot>,
        matchedSourceIds: Set<String>,
        allowedPlanOwners: Set<String>
    ): ActivityWorkoutAssessment {
        val isRun = DashboardSummary.isRun(activity)
        val detected = ActivityWorkoutEvidence(
            ActivityWorkoutKind.UNKNOWN,
            evidence = listOf(
                when {
                    !isRun -> "Workout classification is supported only for running activities."
                    recorded == null -> "Saved workout-step detail is unavailable for this activity."
                    recorded.laps.isEmpty() -> "Recorded samples do not identify performed workout steps."
                    else -> "Recorded laps contain no WORK/RECOVERY step labels, so workout purpose cannot be verified."
                }
            )
        )
        if (!isRun || matchedSourceIds.isEmpty()) return ActivityWorkoutAssessment(detected)

        val links = completedRunLinks(plans, allowedPlanOwners)
            .filter { it.second.linkedActivityId in matchedSourceIds }
            .take(2)
        if (links.size > 1) {
            return ActivityWorkoutAssessment(detected, intentWarning =
                "More than one completed plan session links to this activity; planned intent is ambiguous.")
        }
        val session = links.singleOrNull()?.second ?: return ActivityWorkoutAssessment(detected)
        val prescription = session.runningPrescription
        val kind = when (prescription?.role) {
            "RECOVERY" -> ActivityWorkoutKind.RECOVERY_RUN
            "EASY" -> ActivityWorkoutKind.EASY_RUN
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
                listOf("The linked plan records this as intended ${subtype?.label ?: kind.label}. This does not confirm which steps were performed.")
            ),
            linkedSession = session
        )
    }

    internal fun completedRunLinks(
        plans: List<PlanSnapshot>,
        allowedOwners: Set<String>
    ): List<Pair<PlanSnapshot, PlannedSession>> = plans.asSequence()
        .filter { it.owner in allowedOwners && it.status in setOf("approved", "paused") &&
            Engine1Engines.supports(it.engineVersion, it.policyId) }
        .flatMap { plan -> plan.sessions.asSequence()
            .filter { it.discipline == "run" && it.status == "completed" && it.linkedActivityId != null }
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
