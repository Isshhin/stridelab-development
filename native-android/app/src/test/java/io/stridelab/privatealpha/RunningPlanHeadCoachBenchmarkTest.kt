package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

class RunningPlanHeadCoachBenchmarkTest {

    private val fixedNow: Instant = Instant.parse("2026-04-01T08:00:00Z")
    private val fixedStart: LocalDate = LocalDate.of(2026, 4, 6)
    private val hrFloors = listOf(115, 138, 154, 168, 178)

    private data class ProfileSpec(
        val name: String,
        val request: PlanRequest,
        val isHybrid: Boolean = false
    )

    private fun buildProfiles(): List<ProfileSpec> = listOf(
        ProfileSpec(
            name = "1_Beginner_5K",
            request = PlanRequest(
                goal = "Improve 5K",
                experience = 0,
                start = fixedStart,
                weeks = 8,
                minutes = 60,
                days = setOf(1, 3, 5, 7),
                runDays = 4,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(3),
                weeklyKm = 20.0,
                longestKm = 7.0,
                targetWeeklyKm = 26.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 390.0
            )
        ),
        ProfileSpec(
            name = "2_Intermediate_10K",
            request = PlanRequest(
                goal = "Improve 10K",
                experience = 1,
                start = fixedStart,
                weeks = 10,
                minutes = 120,
                days = setOf(1, 2, 3, 5, 7),
                runDays = 5,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(3),
                weeklyKm = 45.0,
                longestKm = 14.0,
                targetWeeklyKm = 52.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 320.0
            )
        ),
        ProfileSpec(
            name = "3_Intermediate_HM",
            request = PlanRequest(
                goal = "Half marathon",
                experience = 1,
                start = fixedStart,
                weeks = 12,
                minutes = 150,
                days = setOf(1, 2, 4, 5, 7),
                runDays = 5,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 2,
                qualityDays = setOf(2, 4),
                weeklyKm = 55.0,
                longestKm = 16.0,
                targetWeeklyKm = 65.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 315.0
            )
        ),
        ProfileSpec(
            name = "4_Advanced_Marathon",
            request = PlanRequest(
                goal = "Marathon",
                experience = 2,
                start = fixedStart,
                weeks = 14,
                minutes = 210,
                days = setOf(1, 2, 3, 4, 5, 7),
                runDays = 6,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 2,
                qualityDays = setOf(2, 5),
                weeklyKm = 80.0,
                longestKm = 24.0,
                targetWeeklyKm = 92.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 290.0
            )
        ),
        ProfileSpec(
            name = "5_DualQuality_10K",
            request = PlanRequest(
                goal = "Improve 10K",
                experience = 2,
                start = fixedStart,
                weeks = 10,
                minutes = 135,
                days = setOf(1, 2, 3, 4, 5, 7),
                runDays = 6,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 2,
                qualityDays = setOf(2, 5),
                weeklyKm = 65.0,
                longestKm = 18.0,
                targetWeeklyKm = 74.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 285.0
            )
        ),
        ProfileSpec(
            name = "6_TimeCapped_45m",
            request = PlanRequest(
                goal = "Improve 10K",
                experience = 1,
                start = fixedStart,
                weeks = 8,
                minutes = 45,
                days = setOf(1, 2, 3, 5, 7),
                runDays = 5,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(3),
                weeklyKm = 22.0,
                longestKm = 6.0,
                targetWeeklyKm = 24.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 330.0,
                timeBudgetEnabled = true
            )
        ),
        ProfileSpec(
            name = "7_HR_Based_HM",
            request = PlanRequest(
                goal = "Half marathon",
                experience = 1,
                start = fixedStart,
                weeks = 12,
                minutes = 150,
                days = setOf(1, 2, 4, 5, 7),
                runDays = 5,
                strengthDays = 0,
                longDay = 7,
                qualityCount = 2,
                qualityDays = setOf(2, 4),
                weeklyKm = 50.0,
                longestKm = 15.0,
                targetWeeklyKm = 60.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 320.0,
                runningTargetMode = "HR",
                hrZoneFloors = hrFloors,
                hrMaxBpm = 192
            )
        ),
        ProfileSpec(
            name = "8_HR_Based_Hybrid",
            request = PlanRequest(
                goal = "Improve 10K",
                experience = 1,
                start = fixedStart,
                weeks = 8,
                minutes = 120,
                days = setOf(1, 2, 3, 5, 7),
                runDays = 3,
                strengthDays = 2,
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(3),
                weeklyKm = 24.0,
                longestKm = 10.0,
                targetWeeklyKm = 27.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                planningPace = 325.0,
                runningTargetMode = "HR",
                hrZoneFloors = hrFloors,
                hrMaxBpm = 192
            ),
            isHybrid = true
        )
    )

    private fun generatePlan(spec: ProfileSpec): PlanSnapshot {
        return if (spec.isHybrid) {
            Engine18HybridProgramBuilder.build(
                request = spec.request,
                owner = "coach-bench",
                id = "bench-${spec.name}",
                now = fixedNow,
                existingPlans = emptyList()
            )
        } else {
            Engine15IndependentPlanGenerator.generateWithHistory(
                request = spec.request,
                owner = "coach-bench",
                plans = emptyList(),
                id = "bench-${spec.name}",
                now = fixedNow
            )
        }
    }

    private fun stepStats(session: PlannedSession, paceSecPerKm: Double): QualityMetrics {
        val rx = session.runningPrescription
        val mixed = rx?.mixedSegments.orEmpty()
        var wuSec = 0.0
        var cdSec = 0.0
        var workRecSec = 0.0
        var totalSec = 0.0
        if (mixed.isNotEmpty()) {
            mixed.forEach { step ->
                val sec = step.seconds?.toDouble()
                    ?: (((step.meters ?: step.distanceEquivalentMeters ?: 0) / 1000.0) * paceSecPerKm)
                totalSec += sec
                when (step.purpose) {
                    "WARM_UP" -> wuSec += sec
                    "COOL_DOWN" -> cdSec += sec
                    "WORK", "RECOVERY" -> workRecSec += sec
                }
            }
        } else {
            rx?.segments.orEmpty().forEach { seg ->
                val s = (seg.meters / 1000.0) * paceSecPerKm
                totalSec += s
                when (seg.purpose) {
                    "WARM_UP" -> wuSec += s
                    "COOL_DOWN" -> cdSec += s
                    "WORK", "RECOVERY" -> workRecSec += s
                }
            }
        }
        return QualityMetrics(
            wuMin = wuSec / 60.0,
            cdMin = cdSec / 60.0,
            workRecRatio = if (totalSec > 0.0) workRecSec / totalSec else 0.0,
            totalMin = totalSec / 60.0
        )
    }

    private data class QualityMetrics(
        val wuMin: Double,
        val cdMin: Double,
        val workRecRatio: Double,
        val totalMin: Double
    )

    @Test
    fun benchmarkAllProfilesAgainstHeadCoachRubric() {
        val profiles = buildProfiles()
        val failures = mutableListOf<String>()
        println("=== HEAD COACH BENCHMARK REPORT ===")
        for (spec in profiles) {
            val plan = generatePlan(spec)
            if (spec.isHybrid) {
                Engine18HybridProgramBuilder.validateSaved(plan)
            } else {
                Engine15IndependentPlanGenerator.validateSaved(plan)
            }

            val runSessions = plan.sessions.filter { it.discipline == "run" }
            val qualitySessions = runSessions.filter { it.quality }
            val pace = spec.request.planningPace
            val qMetrics = qualitySessions.map { it to stepStats(it, pace) }

            val maxWu = qMetrics.maxOfOrNull { it.second.wuMin } ?: 0.0
            val maxCd = qMetrics.maxOfOrNull { it.second.cdMin } ?: 0.0
            val maxCombined = qMetrics.maxOfOrNull { it.second.wuMin + it.second.cdMin } ?: 0.0
            val nonNoviceFocusRatios = if (spec.request.experience >= 1) {
                qMetrics.filter { (session, _) ->
                    val fam = session.runningPrescription?.family.orEmpty().uppercase(Locale.ROOT)
                    fam in setOf("TEMPO", "THRESHOLD", "VO2", "FARTLEK", "MARATHON_PACE")
                }.map { it.first to it.second.workRecRatio }
            } else {
                emptyList()
            }
            val minFocusRatio = nonNoviceFocusRatios.minOfOrNull { it.second } ?: 1.0

            val totalRunKm = runSessions.sumOf { it.distanceKm ?: 0.0 }
            val easyRunKm = runSessions.filter { !it.quality }.sumOf { it.distanceKm ?: 0.0 }
            val easyPct = if (totalRunKm > 0.0) (easyRunKm / totalRunKm) * 100.0 else 0.0

            val sessionsByWeek = plan.sessions.groupBy {
                (ChronoUnit.DAYS.between(spec.request.start, it.date).toInt() / 7).coerceAtLeast(0)
            }.toSortedMap()
            val duplicate2QWeeks = if (spec.request.qualityCount >= 2) {
                sessionsByWeek.values.count { wkSessions ->
                    val fams = wkSessions.filter { it.discipline == "run" && it.quality }
                        .mapNotNull { it.runningPrescription?.family?.uppercase(Locale.ROOT) }
                    fams.size >= 2 && fams.distinct().size < fams.size
                }
            } else 0

            val weekRunKms = sessionsByWeek.values.map { wkSessions ->
                wkSessions.filter { it.discipline == "run" }.sumOf { it.distanceKm ?: 0.0 }
            }
            val peakKm = weekRunKms.maxOrNull() ?: 0.0
            val taperKm = weekRunKms.lastOrNull() ?: 0.0

            println(
                String.format(
                    Locale.ROOT,
                    "%-22s | Q=%2d | maxWU=%.1fm maxCD=%.1fm maxWU+CD=%.1fm | minFocus=%.0f%% | easy=%.0f%% | dup2Q=%d | peak=%.1fkm taper=%.1fkm",
                    spec.name,
                    qualitySessions.size,
                    maxWu,
                    maxCd,
                    maxCombined,
                    minFocusRatio * 100.0,
                    easyPct,
                    duplicate2QWeeks,
                    peakKm,
                    taperKm
                )
            )

            // Rubric 1: WU/CD budget
            if (maxWu > 15.5) failures.add("${spec.name}: maxWu=$maxWu > 15.5m")
            if (maxCd > 10.5) failures.add("${spec.name}: maxCd=$maxCd > 10.5m")
            if (maxCombined > 25.5) failures.add("${spec.name}: maxWU+CD=$maxCombined > 25.5m")

            // Rubric 2: Quality focus ratio on intermediate/advanced
            nonNoviceFocusRatios.forEach { (sess, ratio) ->
                if (ratio < 0.45) {
                    val m = stepStats(sess, pace)
                    val msg = "${spec.name} (${sess.title}, fam=${sess.runningPrescription?.family}, km=${sess.distanceKm}): " +
                        "wu=${"%.1f".format(Locale.ROOT, m.wuMin)}m cd=${"%.1f".format(Locale.ROOT, m.cdMin)}m " +
                        "tot=${"%.1f".format(Locale.ROOT, m.totalMin)}m work+rec=${(ratio * 100).roundToInt()}% < 45%"
                    println("   -> FAIL FOCUS: $msg")
                    failures.add(msg)
                }
            }

            // Rubric 3: 2Q variety
            if (duplicate2QWeeks > 0) {
                failures.add("${spec.name}: duplicate2QWeeks=$duplicate2QWeeks")
            }

            // Rubric 4: Macrocycle taper & easy polarity
            if (sessionsByWeek.size >= 4 && taperKm >= peakKm) {
                failures.add("${spec.name}: taperKm=$taperKm >= peakKm=$peakKm")
            }
            if (easyPct < 68.0) {
                failures.add("${spec.name}: easyPct=$easyPct < 68%")
            }

            // Rubric 5: HR coherence
            if (spec.request.runningTargetMode == "HR") {
                runSessions.forEach { sess ->
                    val rx = sess.runningPrescription
                    if (rx == null) {
                        failures.add("${spec.name}: ${sess.title} missing runningPrescription")
                    } else {
                        rx.segments.forEach { seg ->
                            if (seg.targetHrMinBpm == null || seg.targetHrMaxBpm == null) {
                                failures.add("${spec.name}: ${sess.title} segment ${seg.purpose} missing HR bounds")
                            }
                        }
                        rx.mixedSegments.orEmpty().forEach { step ->
                            if (step.targetHrMinBpm == null || step.targetHrMaxBpm == null) {
                                failures.add("${spec.name}: ${sess.title} step ${step.purpose} missing HR bounds")
                            }
                        }
                    }
                }
            }
        }
        assertTrue("Head Coach Rubric failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun hrTargetModeEndToEndVerification() {
        // 1. HR_ZONES_REQUIRED deferral when HR mode is requested without hrZoneFloors
        val missingHrReq = PlanRequest(
            goal = "Half marathon",
            experience = 1,
            start = fixedStart,
            weeks = 8,
            minutes = 120,
            days = setOf(1, 3, 5, 7),
            runDays = 4,
            strengthDays = 0,
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(3),
            weeklyKm = 40.0,
            longestKm = 12.0,
            targetWeeklyKm = 48.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            planningPace = 320.0,
            runningTargetMode = "HR"
        )
        val decision = Engine15IndependentPlanGenerator.previewAutoReconciled(
            request = missingHrReq,
            owner = "coach-bench",
            id = "hr-deferral-test",
            now = fixedNow
        )
        assertTrue(decision is Engine15IndependentPlanDecision.Deferred)
        assertEquals("HR_ZONES_REQUIRED", (decision as Engine15IndependentPlanDecision.Deferred).reasonCode)

        // 2. HeartRateZoneAnalysis.toExecutionZoneProfile supports LTHR_PERCENT, HRR_PERCENT, MAX_BPM
        val lthrProfile = HeartRateZoneAnalysis.toExecutionZoneProfile(
            AthleteHeartRateZoneSettings(HeartRateZoneMethod.LTHR_PERCENT, lactateThresholdHr = 172)
        )
        assertNotNull(lthrProfile)
        assertTrue(lthrProfile!!.z2FloorBpm < lthrProfile.z4FloorBpm)

        val hrrProfile = HeartRateZoneAnalysis.toExecutionZoneProfile(
            AthleteHeartRateZoneSettings(HeartRateZoneMethod.HRR_PERCENT, maximumHr = 190, restingHr = 52)
        )
        assertNotNull(hrrProfile)

        val maxProfile = HeartRateZoneAnalysis.toExecutionZoneProfile(
            AthleteHeartRateZoneSettings(HeartRateZoneMethod.MAX_BPM, maximumHr = 190)
        )
        assertNotNull(maxProfile)

        // 3. HR plan generation + Difficulty adjuster (-2 -> RECOVERY HR) + FIT HR export + UI BPM cards
        val hrReq = missingHrReq.copy(hrZoneFloors = hrFloors, hrMaxBpm = 192)
        val hrPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            request = hrReq,
            owner = "coach-bench",
            plans = emptyList(),
            id = "hr-e2e-test",
            now = fixedNow
        )
        val qualitySession = hrPlan.sessions.first { it.discipline == "run" && it.quality }
        val downgraded = Engine15WorkoutDifficultyAdjuster.adjust(qualitySession, hrReq, -2)
        assertTrue(downgraded.runningPrescription!!.segments.all { it.targetHrMinBpm != null && it.targetHrMaxBpm != null })
        assertTrue(downgraded.runningPrescription!!.segments.all { it.targetHrMaxBpm!! <= hrFloors[1] + 5 })

        val fitBytes = GarminFitWorkoutEncoder.encode(
            title = qualitySession.title,
            prescription = qualitySession.runningPrescription,
            createdAt = fixedNow,
            verifiedHrZones = hrReq.resolvedHrZoneProfile()
        )
        assertTrue(fitBytes.isNotEmpty())

        val stepItems = buildWorkoutStepItems(
            session = qualitySession,
            segments = qualitySession.runningPrescription?.segments,
            mixedSegments = qualitySession.runningPrescription?.mixedSegments,
            textFallback = qualitySession.steps,
            planningPaceSecPerKm = hrReq.planningPace
        )
        val hasBpmCard = stepItems.any { item ->
            when (item) {
                is WorkoutStepItem.SingleStep -> item.step.effortLine?.contains("bpm", ignoreCase = true) == true
                is WorkoutStepItem.RepeatGroup -> item.children.any { it.effortLine?.contains("bpm", ignoreCase = true) == true }
            }
        }
        assertTrue("Expected BPM effort line in TracksideRunningStepCards", hasBpmCard)

        // 4. Adaptation engine suppresses PACE_RECALIBRATION_UP/DOWN when runningTargetMode == "HR"
        val asOf = fixedStart.plusWeeks(2)
        val diag = AthleteDiagnosticSnapshot(
            planId = hrPlan.id,
            asOfDate = asOf,
            overallConfidence = DiagnosticConfidence.HIGH,
            pendingConfirmationCount = 0,
            volumeTolerance = VolumeToleranceDiagnostic(
                windowDays = 28,
                plannedKm = 160.0,
                completedKm = 160.0,
                volumeCompletionRatio = 1.0,
                plannedSessions = 16,
                completedSessions = 16,
                consecutiveMissedSessions = 0,
                rolling28dWeeklyAvgKm = 40.0,
                last7dKm = 40.0,
                acuteToBaselineRatio = 1.0,
                longest30dKm = 14.0,
                exposureStatus = ExposureStatus.SAFE,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            qualityExecution = QualityExecutionDiagnostic(
                evaluatedQualityCount = 4,
                verifiedHitCount = 4,
                verifiedHitRatePct = 100.0,
                volumeOnlySubstitutionCount = 0,
                tooSlowCount = 0,
                tooFastCount = 0,
                partialCount = 0,
                paceTrend = LongitudinalPaceTrend.FASTER_THAN_TARGET,
                avgWorkPaceDeltaSecPerKm = -8.0,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            easyDiscipline = EasyDisciplineDiagnostic(
                evaluatedEasyCount = 12,
                tooFastCount = 0,
                grayZoneCreepRatePct = 0.0,
                avgOvershootSecPerKm = null,
                hiddenFatigueRisk = false,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            aerobicEfficiency = AerobicEfficiencyDiagnostic(
                hrDataAvailable = true,
                eligibleRunCount = 8,
                earlyWindowEf = 0.0210,
                recentWindowEf = 0.0221,
                efChangePct = 5.2,
                medianDecouplingPct = 2.0,
                trend = AerobicEfficiencyTrend.IMPROVING,
                confidence = DiagnosticConfidence.HIGH,
                reasonCodes = emptyList()
            ),
            recalibrationAdvisory = RecalibrationAdvisory.SUGGEST_FASTER_CHECK,
            insights = emptyList(),
            reasonCodes = emptyList()
        )
        val proposal = Engine15PlanAdaptationEngine.evaluateProposal(
            plan = hrPlan,
            diagnostic = diag,
            activities = emptyList(),
            matchesByActivityId = emptyMap(),
            asOfDate = asOf,
            zoneId = ZoneOffset.UTC
        )
        if (proposal != null) {
            assertFalse(proposal.type == AdaptationType.PACE_RECALIBRATION_UP || proposal.type == AdaptationType.PACE_RECALIBRATION_DOWN)
        }
    }
}
