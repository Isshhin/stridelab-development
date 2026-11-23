package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.round

/**
 * Pure, deterministic read-only diagnostic evaluator across 28-day volume tolerance,
 * quality execution, easy-day pacing discipline, and HR-gated aerobic efficiency.
 * Never mutates PlanSnapshot and never triggers automatic plan replanning.
 */
internal object Engine15AthleteDiagnosticsEngine {
    private val TARGETED_PACE_INTENTS = setOf(
        RunningStepIntent.TEMPO,
        RunningStepIntent.THRESHOLD,
        RunningStepIntent.VO2_INTERVAL,
        RunningStepIntent.RACE_PACE
    )

    private data class LinkedExecutionPair(
        val session: PlannedSession,
        val activity: ActivityRecord?,
        val analysis: WorkoutExecutionAnalysis
    )

    private data class EfObservation(
        val date: LocalDate,
        val ef: Double
    )

    fun evaluate(
        plan: PlanSnapshot,
        activities: List<ActivityRecord>,
        matchesByActivityId: Map<String, ActivityMatchRecord>,
        analysesBySessionId: Map<String, WorkoutExecutionAnalysis>,
        athleteHrZones: ExecutionHrZoneProfile? = null,
        asOfDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        evaluatedAtEpochMs: Long = 0L
    ): AthleteDiagnosticSnapshot {
        val primaryStart = asOfDate.minusDays(27)
        val acuteStart = asOfDate.minusDays(6)
        val exposureStart = asOfDate.minusDays(29)
        val efficiencyStart = asOfDate.minusDays(41)

        val runActivities = activities
            .filter { DashboardSummary.isRun(it) }
            .map { it to it.startedAt.atZone(zoneId).toLocalDate() }
            .filter { (_, date) -> !date.isAfter(asOfDate) }
        val activityById = runActivities.associate { (a, _) -> a.id to a }

        val primaryActivities = runActivities.filter { (_, d) -> d in primaryStart..asOfDate }
        val acuteActivities = runActivities.filter { (_, d) -> d in acuteStart..asOfDate }
        val exposureActivities = runActivities.filter { (_, d) -> d in exposureStart..asOfDate }

        val pendingMatchesInWindow = matchesByActivityId.values.filter { match ->
            match.matchStatus == ActivityMatchStatus.NEEDS_CONFIRMATION &&
                activityById[match.activityId]?.startedAt?.atZone(zoneId)?.toLocalDate() in primaryStart..asOfDate
        }
        val pendingCount = pendingMatchesInWindow.size

        val runSessions = plan.sessions.filter { it.discipline == "run" && it.status != "skipped" }
        val plannedInWindow = runSessions.filter { it.date in primaryStart..asOfDate }

        // Resolve linked activity -> session pairs strictly separated from UNMATCHED / NEEDS_CONFIRMATION.
        val linkedPairs = resolveLinkedPairs(
            plannedInWindow = plannedInWindow,
            matchesByActivityId = matchesByActivityId,
            analysesBySessionId = analysesBySessionId,
            activityById = activityById
        )

        val volumeDiag = evaluateVolumeTolerance(
            primaryStart = primaryStart,
            plannedInWindow = plannedInWindow,
            primaryActivities = primaryActivities,
            acuteActivities = acuteActivities,
            exposureActivities = exposureActivities,
            matchesByActivityId = matchesByActivityId,
            pendingMatches = pendingMatchesInWindow,
            pendingCount = pendingCount,
            zoneId = zoneId
        )

        val qualityDiag = evaluateQualityExecution(
            linkedPairs = linkedPairs,
            pendingCount = pendingCount
        )

        val easyDiag = evaluateEasyDiscipline(
            linkedPairs = linkedPairs
        )

        val aerobicDiag = evaluateAerobicEfficiency(
            athleteHrZones = athleteHrZones,
            efficiencyStart = efficiencyStart,
            asOfDate = asOfDate,
            runActivities = runActivities,
            allRunSessions = runSessions,
            matchesByActivityId = matchesByActivityId,
            analysesBySessionId = analysesBySessionId,
            pendingCount = pendingCount
        )

        val advisory = determineAdvisory(
            volume = volumeDiag,
            quality = qualityDiag,
            easy = easyDiag,
            aerobic = aerobicDiag
        )

        val overallConfidence = determineOverallConfidence(
            volume = volumeDiag,
            quality = qualityDiag,
            easy = easyDiag,
            pendingCount = pendingCount
        )

        val insights = buildRankedInsights(
            overallConfidence = overallConfidence,
            volume = volumeDiag,
            quality = qualityDiag,
            easy = easyDiag,
            aerobic = aerobicDiag,
            advisory = advisory,
            pendingCount = pendingCount
        )

        val allReasons = LinkedHashSet<String>().apply {
            addAll(volumeDiag.reasonCodes)
            addAll(qualityDiag.reasonCodes)
            addAll(easyDiag.reasonCodes)
            addAll(aerobicDiag.reasonCodes)
            if (pendingCount > 0) add("PENDING_MATCH_CONFIRMATIONS_PRESENT")
            add("ADVISORY_${advisory.name}")
        }.toList()

        return AthleteDiagnosticSnapshot(
            schemaVersion = ENGINE15_DIAGNOSTIC_SCHEMA,
            planId = plan.id,
            asOfDate = asOfDate,
            overallConfidence = overallConfidence,
            pendingConfirmationCount = pendingCount,
            volumeTolerance = volumeDiag,
            qualityExecution = qualityDiag,
            easyDiscipline = easyDiag,
            aerobicEfficiency = aerobicDiag,
            recalibrationAdvisory = advisory,
            insights = insights,
            reasonCodes = allReasons,
            evaluatedAtEpochMs = evaluatedAtEpochMs
        )
    }

    private fun resolveLinkedPairs(
        plannedInWindow: List<PlannedSession>,
        matchesByActivityId: Map<String, ActivityMatchRecord>,
        analysesBySessionId: Map<String, WorkoutExecutionAnalysis>,
        activityById: Map<String, ActivityRecord>
    ): List<LinkedExecutionPair> {
        val result = ArrayList<LinkedExecutionPair>()
        for (session in plannedInWindow) {
            val analysis = analysesBySessionId[session.id] ?: continue
            val linkedMatch = matchesByActivityId.values.firstOrNull { match ->
                match.plannedSessionId == session.id &&
                    (match.matchStatus == ActivityMatchStatus.AUTO_LINKED ||
                        match.matchStatus == ActivityMatchStatus.MANUALLY_LINKED)
            }
            val resolvedActivityId = linkedMatch?.activityId ?: session.linkedActivityId
            if (resolvedActivityId == null) continue
            // Ensure the analysis actually belongs to the currently linked activity.
            if (analysis.activityId != resolvedActivityId) continue
            result += LinkedExecutionPair(
                session = session,
                activity = activityById[resolvedActivityId],
                analysis = analysis
            )
        }
        return result
    }

    private fun evaluateVolumeTolerance(
        primaryStart: LocalDate,
        plannedInWindow: List<PlannedSession>,
        primaryActivities: List<Pair<ActivityRecord, LocalDate>>,
        acuteActivities: List<Pair<ActivityRecord, LocalDate>>,
        exposureActivities: List<Pair<ActivityRecord, LocalDate>>,
        matchesByActivityId: Map<String, ActivityMatchRecord>,
        pendingMatches: List<ActivityMatchRecord>,
        pendingCount: Int,
        zoneId: ZoneId
    ): VolumeToleranceDiagnostic {
        val reasons = LinkedHashSet<String>()
        val plannedKm = plannedInWindow.sumOf {
            it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0
        }
        val completedKm = primaryActivities.sumOf { (a, _) -> (a.distanceM ?: 0.0) / 1000.0 }
        val rollingWeeklyAvgKm = completedKm / 4.0
        val last7dKm = acuteActivities.sumOf { (a, _) -> (a.distanceM ?: 0.0) / 1000.0 }
        val longest30dKm = exposureActivities.maxOfOrNull { (a, _) -> (a.distanceM ?: 0.0) / 1000.0 } ?: 0.0

        val linkedSessionIds = matchesByActivityId.values
            .filter {
                it.matchStatus == ActivityMatchStatus.AUTO_LINKED ||
                    it.matchStatus == ActivityMatchStatus.MANUALLY_LINKED
            }
            .mapNotNull { it.plannedSessionId }
            .toHashSet()
        val activityDates = primaryActivities.map { it.second }.toHashSet()
        val manualWithoutFitCount = plannedInWindow.count {
            it.status == "completed" && it.linkedActivityId == null &&
                it.id !in linkedSessionIds && it.date !in activityDates
        }
        val completedSessions = primaryActivities.size + manualWithoutFitCount

        val volumeCompletionRatio = when {
            plannedKm > 0.0 -> completedKm / plannedKm
            plannedInWindow.isNotEmpty() -> completedSessions.toDouble() / plannedInWindow.size
            else -> 0.0
        }

        val pendingSessionIds = pendingMatches.flatMap { m ->
            listOfNotNull(m.plannedSessionId) + m.candidates.map { it.plannedSessionId }
        }.toHashSet()
        val pendingActivityDates = pendingMatches.mapNotNull { m ->
            primaryActivities.find { it.first.id == m.activityId }?.second
        }.toHashSet()

        var consecutiveMissed = 0
        for (session in plannedInWindow.sortedByDescending { it.date }) {
            val fulfilled = session.status == "completed" ||
                session.linkedActivityId != null ||
                session.id in linkedSessionIds ||
                session.id in pendingSessionIds ||
                session.date in pendingActivityDates
            if (fulfilled) break
            consecutiveMissed++
        }

        val activeWeeks = primaryActivities
            .map { ChronoUnit.DAYS.between(primaryStart, it.second).toInt().coerceAtLeast(0) / 7 }
            .distinct()
            .size
        val acuteToBaselineRatio = if (
            primaryActivities.size >= 4 &&
            activeWeeks >= 2 &&
            rollingWeeklyAvgKm >= 5.0
        ) {
            last7dKm / rollingWeeklyAvgKm
        } else null

        val sparseHistory = primaryActivities.size < 3 && plannedInWindow.size < 4
        val exposureStatus = when {
            sparseHistory -> {
                reasons += "SPARSE_VOLUME_WINDOW"
                ExposureStatus.INSUFFICIENT_DATA
            }
            (acuteToBaselineRatio != null && acuteToBaselineRatio > 1.30) ||
                (rollingWeeklyAvgKm > 0.0 && longest30dKm > rollingWeeklyAvgKm * 0.55 && longest30dKm >= 15.0) -> {
                reasons += "ACUTE_OR_SINGLE_RUN_OVERREACH_RISK"
                ExposureStatus.OVERREACH_RISK
            }
            (acuteToBaselineRatio != null && acuteToBaselineRatio > 1.15) ||
                (plannedKm > 0.0 && volumeCompletionRatio > 1.18) -> {
                reasons += "LOAD_STRETCHED_ABOVE_BASELINE"
                ExposureStatus.STRETCHED
            }
            else -> {
                reasons += "VOLUME_EXPOSURE_SAFE"
                ExposureStatus.SAFE
            }
        }

        if (consecutiveMissed >= 3) reasons += "CONSECUTIVE_MISSED_SESSIONS"
        else if (plannedInWindow.size >= 4 && volumeCompletionRatio < 0.65) reasons += "LOW_VOLUME_COMPLETION"

        var confidence = when {
            primaryActivities.size < 3 && (plannedInWindow.size < 4 || consecutiveMissed < 3) ->
                DiagnosticConfidence.INSUFFICIENT
            primaryActivities.size < 6 -> DiagnosticConfidence.LOW
            primaryActivities.size >= 12 && activeWeeks >= 3 -> DiagnosticConfidence.HIGH
            else -> DiagnosticConfidence.MODERATE
        }
        if (pendingCount >= 2 && confidence == DiagnosticConfidence.HIGH) {
            confidence = DiagnosticConfidence.MODERATE
        }

        return VolumeToleranceDiagnostic(
            windowDays = 28,
            plannedKm = round2(plannedKm),
            completedKm = round2(completedKm),
            volumeCompletionRatio = round2(volumeCompletionRatio),
            plannedSessions = plannedInWindow.size,
            completedSessions = completedSessions,
            consecutiveMissedSessions = consecutiveMissed,
            rolling28dWeeklyAvgKm = round2(rollingWeeklyAvgKm),
            last7dKm = round2(last7dKm),
            acuteToBaselineRatio = acuteToBaselineRatio?.let(::round2),
            longest30dKm = round2(longest30dKm),
            exposureStatus = exposureStatus,
            confidence = confidence,
            reasonCodes = reasons.toList()
        )
    }

    private fun evaluateQualityExecution(
        linkedPairs: List<LinkedExecutionPair>,
        pendingCount: Int
    ): QualityExecutionDiagnostic {
        val reasons = LinkedHashSet<String>()
        val linkedQuality = linkedPairs.filter {
            it.session.quality || it.analysis.qualityStimulusVerified != null
        }
        val evaluatedCount = linkedQuality.size
        val verifiedHitCount = linkedQuality.count {
            it.analysis.qualityStimulusVerified == true &&
                it.analysis.verdict == ExecutionVerdict.ON_TARGET
        }
        val subCount = linkedQuality.count { it.analysis.verdict == ExecutionVerdict.VOLUME_ONLY_MATCH }
        val tooSlowCount = linkedQuality.count { it.analysis.verdict == ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW }
        val tooFastCount = linkedQuality.count { it.analysis.verdict == ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST }
        val partialCount = linkedQuality.count { it.analysis.verdict == ExecutionVerdict.PARTIALLY_EXECUTED }

        val verifiedHitRatePct = if (evaluatedCount > 0) verifiedHitCount * 100.0 / evaluatedCount else null

        val nonSummary = linkedQuality.filter {
            it.analysis.evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY
        }

        // Compute work-step pace delta (negative = faster than target midpoint, positive = slower).
        val sessionDeltas = nonSummary.mapNotNull { pair ->
            val workSlices = pair.analysis.stepBreakdowns.filter {
                it.stepIntent in TARGETED_PACE_INTENTS &&
                    it.actualPaceSecPerKm != null &&
                    (it.targetPaceMinSecPerKm != null || it.targetPaceMaxSecPerKm != null)
            }
            if (workSlices.isEmpty()) return@mapNotNull null
            val sliceDeltas = workSlices.mapNotNull { step ->
                val actual = step.actualPaceSecPerKm ?: return@mapNotNull null
                val mid = when {
                    step.targetPaceMinSecPerKm != null && step.targetPaceMaxSecPerKm != null ->
                        (step.targetPaceMinSecPerKm + step.targetPaceMaxSecPerKm) / 2.0
                    step.targetPaceMaxSecPerKm != null -> step.targetPaceMaxSecPerKm.toDouble()
                    step.targetPaceMinSecPerKm != null -> step.targetPaceMinSecPerKm.toDouble()
                    else -> return@mapNotNull null
                }
                val weight = (step.actualSeconds ?: step.plannedSeconds ?: 60).toDouble().coerceAtLeast(1.0)
                (actual - mid) to weight
            }
            if (sliceDeltas.isEmpty()) return@mapNotNull null
            sliceDeltas.sumOf { it.first * it.second } / sliceDeltas.sumOf { it.second }
        }
        val avgDelta = if (sessionDeltas.isNotEmpty()) sessionDeltas.average() else null

        var confidence = when {
            evaluatedCount == 0 -> DiagnosticConfidence.INSUFFICIENT
            nonSummary.size < 3 -> {
                if (nonSummary.size < evaluatedCount) reasons += "SUMMARY_ONLY_EVIDENCE_CAPS_CONFIDENCE"
                DiagnosticConfidence.LOW
            }
            nonSummary.size == 3 -> DiagnosticConfidence.MODERATE
            else -> DiagnosticConfidence.HIGH
        }
        if (pendingCount >= 1 && confidence == DiagnosticConfidence.HIGH) {
            confidence = DiagnosticConfidence.MODERATE
            reasons += "PENDING_MATCHES_CAP_QUALITY_CONFIDENCE"
        }

        val paceTrend = when {
            sessionDeltas.size < 2 || avgDelta == null -> {
                reasons += "QUALITY_PACE_SAMPLE_INSUFFICIENT"
                LongitudinalPaceTrend.INSUFFICIENT_DATA
            }
            avgDelta <= -3.0 && tooSlowCount == 0 && subCount == 0 &&
                sessionDeltas.count { it <= -3.0 }.toDouble() / sessionDeltas.size >= 0.75 -> {
                reasons += "CONSISTENT_FASTER_THAN_TARGET_QUALITY"
                LongitudinalPaceTrend.FASTER_THAN_TARGET
            }
            tooSlowCount >= 2 || (avgDelta >= 6.0 && tooFastCount == 0) -> {
                reasons += "CONSISTENT_SLOWER_THAN_TARGET_QUALITY"
                LongitudinalPaceTrend.SLOWER_THAN_TARGET
            }
            avgDelta in -3.0..6.0 && (verifiedHitRatePct ?: 0.0) >= 65.0 -> {
                reasons += "QUALITY_EXECUTION_ON_TARGET"
                LongitudinalPaceTrend.ON_TARGET
            }
            else -> {
                reasons += "QUALITY_PACE_MIXED"
                LongitudinalPaceTrend.MIXED
            }
        }
        if (subCount > 0) reasons += "VOLUME_ONLY_QUALITY_SUBSTITUTIONS_PRESENT"

        return QualityExecutionDiagnostic(
            evaluatedQualityCount = evaluatedCount,
            verifiedHitCount = verifiedHitCount,
            verifiedHitRatePct = verifiedHitRatePct?.let(::round1),
            volumeOnlySubstitutionCount = subCount,
            tooSlowCount = tooSlowCount,
            tooFastCount = tooFastCount,
            partialCount = partialCount,
            paceTrend = paceTrend,
            avgWorkPaceDeltaSecPerKm = avgDelta?.let(::round1),
            confidence = confidence,
            reasonCodes = reasons.toList()
        )
    }

    private fun evaluateEasyDiscipline(
        linkedPairs: List<LinkedExecutionPair>
    ): EasyDisciplineDiagnostic {
        val reasons = LinkedHashSet<String>()
        val linkedEasy = linkedPairs.filter {
            !it.session.quality && it.analysis.qualityStimulusVerified == null
        }
        val count = linkedEasy.size
        val tooFastRuns = linkedEasy.filter {
            it.analysis.verdict == ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST
        }
        val tooFastCount = tooFastRuns.size
        val creepRate = if (count > 0) tooFastCount * 100.0 / count else null

        val overshoots = tooFastRuns.mapNotNull { pair ->
            val easyStep = pair.analysis.stepBreakdowns.firstOrNull {
                it.targetPaceMinSecPerKm != null && it.actualPaceSecPerKm != null
            } ?: return@mapNotNull null
            val minPace = easyStep.targetPaceMinSecPerKm!!.toDouble()
            val actualPace = easyStep.actualPaceSecPerKm!!
            (minPace - actualPace).coerceAtLeast(0.0)
        }
        val avgOvershoot = if (overshoots.isNotEmpty()) overshoots.average() else null

        val hiddenFatigueRisk = (count >= 3 && (creepRate ?: 0.0) >= 50.0) ||
            (count >= 2 && tooFastCount == count && (avgOvershoot ?: 0.0) >= 15.0)
        if (hiddenFatigueRisk) {
            reasons += "EASY_DAY_GRAY_ZONE_CREEP"
            reasons += "HIDDEN_FATIGUE_RISK"
        } else if (count >= 2) {
            reasons += "EASY_PACE_DISCIPLINE_GOOD"
        }

        val nonSummaryCount = linkedEasy.count {
            it.analysis.evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY
        }
        val confidence = when {
            count == 0 -> DiagnosticConfidence.INSUFFICIENT
            nonSummaryCount == 0 -> DiagnosticConfidence.LOW
            count <= 2 -> DiagnosticConfidence.LOW
            count <= 5 -> DiagnosticConfidence.MODERATE
            else -> DiagnosticConfidence.HIGH
        }

        return EasyDisciplineDiagnostic(
            evaluatedEasyCount = count,
            tooFastCount = tooFastCount,
            grayZoneCreepRatePct = creepRate?.let(::round1),
            avgOvershootSecPerKm = avgOvershoot?.let(::round1),
            hiddenFatigueRisk = hiddenFatigueRisk,
            confidence = confidence,
            reasonCodes = reasons.toList()
        )
    }

    private fun evaluateAerobicEfficiency(
        athleteHrZones: ExecutionHrZoneProfile?,
        efficiencyStart: LocalDate,
        asOfDate: LocalDate,
        runActivities: List<Pair<ActivityRecord, LocalDate>>,
        allRunSessions: List<PlannedSession>,
        matchesByActivityId: Map<String, ActivityMatchRecord>,
        analysesBySessionId: Map<String, WorkoutExecutionAnalysis>,
        pendingCount: Int
    ): AerobicEfficiencyDiagnostic {
        if (athleteHrZones == null) {
            return AerobicEfficiencyDiagnostic(
                hrDataAvailable = false,
                eligibleRunCount = 0,
                earlyWindowEf = null,
                recentWindowEf = null,
                efChangePct = null,
                medianDecouplingPct = null,
                trend = AerobicEfficiencyTrend.UNAVAILABLE,
                confidence = DiagnosticConfidence.INSUFFICIENT,
                reasonCodes = listOf("NO_VERIFIED_HR_ZONE_PROFILE")
            )
        }

        val sessionById = allRunSessions.associateBy { it.id }
        val eligible = runActivities.mapNotNull { (activity, date) ->
            if (date !in efficiencyStart..asOfDate) return@mapNotNull null
            val durS = (activity.movingTimeS ?: activity.durationS)?.takeIf { it >= 1800.0 } ?: return@mapNotNull null
            val distM = activity.distanceM?.takeIf { it >= 3000.0 } ?: return@mapNotNull null
            val avgHr = activity.averageHr?.takeIf { it.isFinite() && it in 70.0..220.0 } ?: return@mapNotNull null
            val linkedSessionId = matchesByActivityId[activity.id]
                ?.takeIf {
                    it.matchStatus == ActivityMatchStatus.AUTO_LINKED ||
                        it.matchStatus == ActivityMatchStatus.MANUALLY_LINKED
                }
                ?.plannedSessionId
            if (linkedSessionId != null && sessionById[linkedSessionId]?.quality == true) {
                return@mapNotNull null
            }
            val ef = (distM / durS) / avgHr
            if (!ef.isFinite() || ef <= 0.0) return@mapNotNull null
            EfObservation(date, ef)
        }.sortedBy { it.date }

        val spanDays = if (eligible.size >= 2) {
            ChronoUnit.DAYS.between(eligible.first().date, eligible.last().date)
        } else 0L

        if (eligible.size < 4 || spanDays < 14L) {
            return AerobicEfficiencyDiagnostic(
                hrDataAvailable = false,
                eligibleRunCount = eligible.size,
                earlyWindowEf = null,
                recentWindowEf = null,
                efChangePct = null,
                medianDecouplingPct = null,
                trend = AerobicEfficiencyTrend.UNAVAILABLE,
                confidence = DiagnosticConfidence.INSUFFICIENT,
                reasonCodes = listOf("INSUFFICIENT_ELIGIBLE_HR_RUNS")
            )
        }

        val half = eligible.size / 2
        val early = eligible.take(half)
        val recent = eligible.drop(half)
        val earlyEf = early.map { it.ef }.average()
        val recentEf = recent.map { it.ef }.average()
        val efChangePct = ((recentEf - earlyEf) / earlyEf) * 100.0

        val decouplingValues = analysesBySessionId.values
            .filter {
                val sessionDate = sessionById[it.plannedSessionId]?.date
                sessionDate != null && sessionDate in efficiencyStart..asOfDate && it.aerobicDecouplingPct != null
            }
            .mapNotNull { it.aerobicDecouplingPct }
            .sorted()
        val medianDecoupling = if (decouplingValues.isNotEmpty()) {
            val mid = decouplingValues.size / 2
            if (decouplingValues.size % 2 == 1) decouplingValues[mid]
            else (decouplingValues[mid - 1] + decouplingValues[mid]) / 2.0
        } else null

        val reasons = LinkedHashSet<String>()
        val trend = when {
            efChangePct <= -3.0 || ((medianDecoupling ?: 0.0) >= 8.0 && efChangePct < 0.0) -> {
                reasons += "AEROBIC_EFFICIENCY_DECLINING"
                AerobicEfficiencyTrend.FATIGUING
            }
            efChangePct >= 2.5 && (medianDecoupling == null || medianDecoupling < 6.5) -> {
                reasons += "AEROBIC_EFFICIENCY_IMPROVING"
                AerobicEfficiencyTrend.IMPROVING
            }
            else -> {
                reasons += "AEROBIC_EFFICIENCY_STABLE"
                AerobicEfficiencyTrend.STABLE
            }
        }

        var confidence = if (eligible.size >= 6) DiagnosticConfidence.HIGH else DiagnosticConfidence.MODERATE
        if (pendingCount >= 1 && confidence == DiagnosticConfidence.HIGH) {
            confidence = DiagnosticConfidence.MODERATE
        }

        return AerobicEfficiencyDiagnostic(
            hrDataAvailable = true,
            eligibleRunCount = eligible.size,
            earlyWindowEf = round4(earlyEf),
            recentWindowEf = round4(recentEf),
            efChangePct = round1(efChangePct),
            medianDecouplingPct = medianDecoupling?.let(::round1),
            trend = trend,
            confidence = confidence,
            reasonCodes = reasons.toList()
        )
    }

    private fun determineAdvisory(
        volume: VolumeToleranceDiagnostic,
        quality: QualityExecutionDiagnostic,
        easy: EasyDisciplineDiagnostic,
        aerobic: AerobicEfficiencyDiagnostic
    ): RecalibrationAdvisory {
        if (volume.consecutiveMissedSessions >= 3 ||
            (volume.plannedSessions >= 4 && volume.completedSessions > 0 && volume.volumeCompletionRatio < 0.65) ||
            volume.exposureStatus == ExposureStatus.OVERREACH_RISK
        ) {
            return RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED
        }
        if (volume.confidence == DiagnosticConfidence.INSUFFICIENT &&
            quality.confidence == DiagnosticConfidence.INSUFFICIENT
        ) {
            return RecalibrationAdvisory.INSUFFICIENT_DATA
        }
        if (easy.hiddenFatigueRisk) {
            return RecalibrationAdvisory.ADDRESS_EASY_PACING_FIRST
        }
        if (quality.confidence in setOf(DiagnosticConfidence.MODERATE, DiagnosticConfidence.HIGH)) {
            if (quality.paceTrend == LongitudinalPaceTrend.FASTER_THAN_TARGET &&
                (quality.verifiedHitRatePct ?: 0.0) >= 75.0 &&
                quality.tooSlowCount == 0 &&
                quality.volumeOnlySubstitutionCount == 0 &&
                aerobic.trend != AerobicEfficiencyTrend.FATIGUING
            ) {
                return RecalibrationAdvisory.SUGGEST_FASTER_CHECK
            }
            if (quality.paceTrend == LongitudinalPaceTrend.SLOWER_THAN_TARGET &&
                quality.tooSlowCount >= 2
            ) {
                return RecalibrationAdvisory.SUGGEST_SLOWER_CHECK
            }
            return RecalibrationAdvisory.HOLD
        }
        return if (volume.confidence in setOf(DiagnosticConfidence.INSUFFICIENT, DiagnosticConfidence.LOW) &&
            quality.confidence in setOf(DiagnosticConfidence.INSUFFICIENT, DiagnosticConfidence.LOW)
        ) {
            RecalibrationAdvisory.INSUFFICIENT_DATA
        } else {
            RecalibrationAdvisory.HOLD
        }
    }

    private fun determineOverallConfidence(
        volume: VolumeToleranceDiagnostic,
        quality: QualityExecutionDiagnostic,
        easy: EasyDisciplineDiagnostic,
        pendingCount: Int
    ): DiagnosticConfidence {
        if (volume.confidence == DiagnosticConfidence.INSUFFICIENT) {
            return DiagnosticConfidence.INSUFFICIENT
        }
        val executionMax = maxConfidence(quality.confidence, easy.confidence)
        val combinedOrdinal = (volume.confidence.ordinal + executionMax.ordinal) / 2
        var overall = DiagnosticConfidence.entries[combinedOrdinal.coerceIn(0, DiagnosticConfidence.HIGH.ordinal)]
        if (pendingCount >= 1 && overall == DiagnosticConfidence.HIGH) {
            overall = DiagnosticConfidence.MODERATE
        }
        if ("SUMMARY_ONLY_EVIDENCE_CAPS_CONFIDENCE" in quality.reasonCodes &&
            overall > DiagnosticConfidence.LOW
        ) {
            overall = DiagnosticConfidence.LOW
        }
        return overall
    }

    private fun buildRankedInsights(
        overallConfidence: DiagnosticConfidence,
        volume: VolumeToleranceDiagnostic,
        quality: QualityExecutionDiagnostic,
        easy: EasyDisciplineDiagnostic,
        aerobic: AerobicEfficiencyDiagnostic,
        advisory: RecalibrationAdvisory,
        pendingCount: Int
    ): List<DiagnosticInsight> {
        val list = ArrayList<DiagnosticInsight>(4)

        // 1. Safety / Volume Recovery / Exposure
        if (advisory == RecalibrationAdvisory.VOLUME_RECOVERY_NEEDED) {
            val detail = when {
                volume.consecutiveMissedSessions >= 3 ->
                    "${volume.consecutiveMissedSessions} consecutive planned runs were missed"
                volume.exposureStatus == ExposureStatus.OVERREACH_RISK ->
                    "acute 7-day load (${formatKm(volume.last7dKm)}) or longest run (${formatKm(volume.longest30dKm)}) exceeds safe baseline"
                else ->
                    "28-day volume is ${formatKm(volume.completedKm)} of ${formatKm(volume.plannedKm)} planned (${(volume.volumeCompletionRatio * 100).toInt()}%)"
            }
            list += DiagnosticInsight(
                code = "VOLUME_RECOVERY_NEEDED",
                severity = DiagnosticInsightSeverity.ACTIONABLE,
                confidence = volume.confidence,
                message = "Prioritize volume consistency over pace changes because $detail."
            )
        } else if (volume.confidence == DiagnosticConfidence.INSUFFICIENT) {
            list += DiagnosticInsight(
                code = "INSUFFICIENT_BASELINE_SAMPLES",
                severity = DiagnosticInsightSeverity.INFO,
                confidence = DiagnosticConfidence.INSUFFICIENT,
                message = "Only ${volume.completedSessions} runs recorded in 28 days (need at least 3 runs and 3 non-summary quality sessions before trend recalibration)."
            )
        }

        // 2. Easy-day gray-zone creep
        if (easy.hiddenFatigueRisk) {
            val rate = (easy.grayZoneCreepRatePct ?: 0.0).toInt()
            val overshoot = easy.avgOvershootSecPerKm?.let { " by ~${it.toInt()} s/km" }.orEmpty()
            list += DiagnosticInsight(
                code = "EASY_GRAY_ZONE_CREEP",
                severity = DiagnosticInsightSeverity.CAUTION,
                confidence = easy.confidence,
                message = "$rate% of linked easy days (${easy.tooFastCount}/${easy.evaluatedEasyCount}) drifted too fast$overshoot, creating hidden fatigue risk."
            )
        }

        // 3. Quality execution / substitutions / recalibration
        if (quality.volumeOnlySubstitutionCount > 0 && list.size < 4) {
            list += DiagnosticInsight(
                code = "VOLUME_ONLY_SUBSTITUTIONS",
                severity = DiagnosticInsightSeverity.CAUTION,
                confidence = quality.confidence,
                message = "${quality.volumeOnlySubstitutionCount} of ${quality.evaluatedQualityCount} quality workouts matched total distance only without hitting work-step pace."
            )
        }
        if (advisory == RecalibrationAdvisory.SUGGEST_FASTER_CHECK && list.size < 4) {
            val delta = kotlin.math.abs(quality.avgWorkPaceDeltaSecPerKm ?: 0.0)
            list += DiagnosticInsight(
                code = "SUGGEST_FASTER_PACE_CHECK",
                severity = DiagnosticInsightSeverity.ACTIONABLE,
                confidence = quality.confidence,
                message = String.format(
                    Locale.US,
                    "All %d verified quality sessions averaged %.0f s/km faster than target with controlled easy pacing; pace zones may be ready for a faster check.",
                    quality.verifiedHitCount,
                    delta
                )
            )
        } else if (advisory == RecalibrationAdvisory.SUGGEST_SLOWER_CHECK && list.size < 4) {
            val delta = quality.avgWorkPaceDeltaSecPerKm ?: 0.0
            list += DiagnosticInsight(
                code = "SUGGEST_SLOWER_PACE_CHECK",
                severity = DiagnosticInsightSeverity.ACTIONABLE,
                confidence = quality.confidence,
                message = String.format(
                    Locale.US,
                    "%d of %d quality sessions finished slower than target (avg +%.0f s/km), signaling a potential pace-zone ease check.",
                    quality.tooSlowCount,
                    quality.evaluatedQualityCount,
                    delta
                )
            )
        }

        // 4. Aerobic efficiency & pending confirmations
        if (aerobic.hrDataAvailable && aerobic.efChangePct != null && list.size < 4) {
            val decouplingText = aerobic.medianDecouplingPct?.let {
                String.format(Locale.US, " (median Pa:Hr decoupling %.1f%%)", it)
            }.orEmpty()
            list += DiagnosticInsight(
                code = "AEROBIC_EFFICIENCY_${aerobic.trend.name}",
                severity = if (aerobic.trend == AerobicEfficiencyTrend.FATIGUING)
                    DiagnosticInsightSeverity.CAUTION else DiagnosticInsightSeverity.INFO,
                confidence = aerobic.confidence,
                message = String.format(
                    Locale.US,
                    "Aerobic efficiency shifted %+.1f%% across %d steady HR runs%s.",
                    aerobic.efChangePct,
                    aerobic.eligibleRunCount,
                    decouplingText
                )
            )
        }
        if (pendingCount > 0 && list.size < 4) {
            list += DiagnosticInsight(
                code = "PENDING_CONFIRMATIONS_PRESENT",
                severity = DiagnosticInsightSeverity.INFO,
                confidence = overallConfidence,
                message = "$pendingCount unconfirmed run match(es) count toward total load (${formatKm(volume.completedKm)}) but are excluded from quality scoring until confirmed."
            )
        }

        return list.take(4)
    }

    private fun maxConfidence(a: DiagnosticConfidence, b: DiagnosticConfidence): DiagnosticConfidence =
        if (a.ordinal >= b.ordinal) a else b

    private fun formatKm(km: Double): String = String.format(Locale.US, "%.1f km", km)
    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
    private fun round4(v: Double): Double = round(v * 10000.0) / 10000.0
}
