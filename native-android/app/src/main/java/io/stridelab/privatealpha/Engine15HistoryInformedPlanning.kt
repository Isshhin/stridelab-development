package io.stridelab.privatealpha

import java.time.Instant

internal data class Engine15HistoryAdvisory(
    val code: String,
    val message: String,
    val sourceActivityIds: List<String> = emptyList()
) {
    init {
        require(code.isNotBlank() && message.isNotBlank())
        require(sourceActivityIds.distinct() == sourceActivityIds)
    }
}

internal data class Engine15HistoryInformedPlanResult(
    val policyId: String,
    val effortBaseline: Engine15AthleteEffortBaseline,
    val activityEvidence: ActivityAthleteEvidenceProfile,
    val qualityEvidence: List<IndependentQualitySegmentEvidence>,
    val decision: Engine15IndependentPlanDecision,
    val advisories: List<Engine15HistoryAdvisory>,
    val ruleIds: List<String>,
    val sourceEvidenceVerified: Boolean = false
) {
    init {
        require(policyId == Engine15HistoryInformedPlanner.POLICY_ID)
        require(ruleIds.distinct() == ruleIds && ruleIds.isNotEmpty())
        require(!sourceEvidenceVerified)
    }
}

/**
 * Stage 7 (Batch 33): Source-labeled history-informed initial planning facade.
 *
 * Bridges Stages 5 & 6 (deduplicated workout detection, effort/exposure baselines,
 * and owner-scoped activity profiles) into Engine15IndependentPlanGenerator for
 * initial static plan generation (Engine 1.5 only; never Engine 2.x adaptation).
 *
 * - Missing device history never penalizes the athlete or overrides manual inputs.
 * - Unconfirmed heuristic workouts are never silently promoted into progression.
 * - Confirmed/linked repeated quality sessions inform bounded Stage 3 progression.
 */
internal object Engine15HistoryInformedPlanner {
    const val POLICY_ID = "stridelab.engine15.history-informed-initial-plan-v1"

    fun preview(
        request: PlanRequest,
        owner: String,
        activities: List<ActivityRecord>,
        allowedActivityIds: Set<String>,
        detailsByActivityId: Map<String, ActivityDetail>,
        existingPlans: List<PlanSnapshot>,
        userLabels: Map<String, UserActivityWorkoutLabel>,
        id: String = java.util.UUID.randomUUID().toString(),
        now: Instant = Instant.now()
    ): Engine15HistoryInformedPlanResult {
        require(owner.isNotBlank())
        val detections = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = activities,
            allowedActivityIds = allowedActivityIds,
            detailsByActivityId = detailsByActivityId,
            plans = existingPlans,
            userLabels = userLabels,
            asOf = now
        )
        val qualityEvidence = Engine15WorkoutDetection.toQualitySegmentEvidence(owner, detections)
        val effortBaseline = Engine15AthleteEffortBaselineBuilder.build(
            owner = owner,
            activities = activities,
            allowedActivityIds = allowedActivityIds,
            detailsByActivityId = detailsByActivityId,
            plans = existingPlans,
            userLabels = userLabels,
            asOf = now
        )
        val activityProfile = ActivityAthleteEvidence.profile(
            owner = owner,
            activities = activities,
            allowedActivityIds = allowedActivityIds,
            userLabels = userLabels,
            asOf = now
        )
        val decision = Engine15IndependentPlanGenerator.preview(
            request = request,
            owner = owner,
            id = id,
            now = now,
            existingPlans = existingPlans,
            activityEvidence = activityProfile,
            qualityEvidence = qualityEvidence
        )

        val advisories = buildList {
            if (effortBaseline.historyCoverage == Engine15HistoryCoverage.NO_RECORDED_RUNS) {
                add(Engine15HistoryAdvisory(
                    code = "MISSING_HISTORY_PRESERVES_MANUAL_INPUT",
                    message = "No recent recorded runs were found in the local cache; initial planning honors your declared running inputs without assuming poor fitness or inactivity."
                ))
            } else {
                val recordedMean = effortBaseline.recordedMeanWeeklyKm28d
                if (recordedMean != null && request.weeklyKm > 0.0 && request.weeklyKm > recordedMean * 1.5) {
                    add(Engine15HistoryAdvisory(
                        code = "RECORDED_VOLUME_BELOW_DECLARED",
                        message = "Cached 28-day mean volume (${"%.1f".format(java.util.Locale.US, recordedMean)} km/week) is lower than declared weekly mileage (${request.weeklyKm} km/week). Because cache coverage is unverified, your declared baseline was not overridden.",
                        sourceActivityIds = activityProfile.evidenceActivityIds
                    ))
                }
                val longestRecorded = effortBaseline.longestRunKm28d
                if (longestRecorded != null && request.longestKm > 0.0 && longestRecorded >= request.longestKm * 0.9) {
                    add(Engine15HistoryAdvisory(
                        code = "RECORDED_LONGEST_RUN_SUPPORTS_BASELINE",
                        message = "Recorded 28-day longest run (${"%.1f".format(java.util.Locale.US, longestRecorded)} km) aligns with your declared longest run (${request.longestKm} km).",
                        sourceActivityIds = activityProfile.evidenceActivityIds
                    ))
                }
            }
            if (request.qualityCount > 0) {
                val selectedFamily = when (decision) {
                    is Engine15IndependentPlanDecision.Deferred -> decision.qualitySelection?.selectedFamily
                    is Engine15IndependentPlanDecision.Candidate -> {
                        val fam = decision.plan.sessions.firstOrNull { it.quality }?.runningPrescription?.family
                        IndependentQualityFamily.entries.firstOrNull { it.name.equals(fam, ignoreCase = true) }
                    }
                }
                if (selectedFamily != null) {
                    val famExposure = effortBaseline.familyExposures.getValue(selectedFamily)
                    if (famExposure.confidence == IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED &&
                        famExposure.repeatedWorkSeconds != null) {
                        add(Engine15HistoryAdvisory(
                            code = "CONFIRMED_QUALITY_EXPOSURE_ENABLES_BOUNDED_PROGRESSION",
                            message = "Repeated ${selectedFamily.name.lowercase()} exposure (${famExposure.repeatedWorkSeconds / 60} min work across ${famExposure.confirmedSessionCount} sessions) anchors bounded multi-week progression.",
                            sourceActivityIds = famExposure.evidenceSessionIds
                        ))
                    } else {
                        val unconfirmedNote = if (famExposure.unconfirmedHeuristicCandidateCount > 0)
                            " (${famExposure.unconfirmedHeuristicCandidateCount} unconfirmed lap candidate(s) await review.)" else ""
                        add(Engine15HistoryAdvisory(
                            code = "NO_CONFIRMED_QUALITY_EXPOSURE_HOLDS_INTRO",
                            message = "No repeated confirmed ${selectedFamily.name.lowercase()} exposure in the last 28 days; quality dose holds at the introductory level.$unconfirmedNote"
                        ))
                    }
                }
            }
        }

        return Engine15HistoryInformedPlanResult(
            policyId = POLICY_ID,
            effortBaseline = effortBaseline,
            activityEvidence = activityProfile,
            qualityEvidence = qualityEvidence,
            decision = decision,
            advisories = advisories,
            ruleIds = listOf(
                "INITIAL_PLANNING_ONLY_NO_ENGINE2_ADAPTATION",
                "MISSING_HISTORY_NEVER_PENALIZES_ATHLETE",
                "MANUAL_INPUTS_NOT_SILENTLY_OVERRIDDEN",
                "CONFIRMED_EXPOSURE_ONLY_FOR_PROGRESSION"
            )
        )
    }
}
