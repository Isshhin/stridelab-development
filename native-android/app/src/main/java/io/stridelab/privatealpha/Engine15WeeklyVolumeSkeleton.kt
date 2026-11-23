package io.stridelab.privatealpha

import kotlin.math.floor

/**
 * M4 Stage A: one reusable owner for Engine 1.5's EXISTING weekly ceiling and
 * recovery intersection. This policy is intentionally identical to the
 * previous implementation; it is NOT a new progression recommendation.
 *
 * The independent M4 Stage B can substitute a separately reviewed policy,
 * but must not reinterpret missing history as no running or raise a ceiling
 * because a confidence label changed.
 */
internal object Engine15WeeklyVolumeSkeleton {
    const val POLICY_ID="engine15.m4.legacy-parity-20261117.1"
    private fun tenths(km:Double):Int=floor(km*10.0+1e-8).toInt()

    enum class WeekType { LOADING, RECOVERY }
    enum class BaselineAvailability {
        NEEDS_BASELINE, REQUIRES_REVIEW, TIME_ONLY_CONFIRMED_ZERO, DISTANCE_ENVELOPE
    }

    /**
     * Multi-factor weekly mileage progression grounded in recent sustainable baseline,
     * training frequency (runDays), experience, race distance horizon, scheduled 4th-week
     * recovery cutbacks, and 30-day single-session exposure context.
     */
    fun multiFactorCeiling(
        week:Int,
        totalWeeks:Int,
        recentKm:Double,
        targetKm:Double?=null,
        runDays:Int=4,
        experience:Int=1,
        raceGoal:String?=null,
        longestKm:Double?=null,
        returning:Boolean=false
    ):Double {
        require(week>=0 && totalWeeks>=1 && recentKm.isFinite() && recentKm>=0.0 &&
            (targetKm==null || targetKm.isFinite() && targetKm>=0.0))
        if(recentKm<=0.0) return 0.0
        val expRate=when(experience.coerceIn(0,2)) {
            0 -> 0.050
            1 -> 0.058
            else -> 0.065
        }
        val freqScale=when {
            runDays<=2 -> 0.75
            runDays==3 -> 0.85
            runDays==4 -> 1.00
            runDays==5 -> 1.08
            else -> 1.12
        }
        val weeklyRamp=(expRate*freqScale).coerceIn(0.035,0.080)
        val startKm=when {
            targetKm==null -> recentKm*0.90
            targetKm<=recentKm -> minOf(targetKm,recentKm*0.90)
            recentKm>=55.0 -> recentKm*0.96
            else -> recentKm*0.95
        }
        val goalPeakCapKm=when(raceGoal) {
            "Marathon" -> minOf(115.0,maxOf(recentKm*1.70,58.0+experience.coerceIn(0,2)*15.0))
            "Half marathon" -> minOf(95.0,maxOf(recentKm*1.55,42.0+experience.coerceIn(0,2)*12.0))
            "Improve 10K" -> minOf(85.0,maxOf(recentKm*1.40,34.0+experience.coerceIn(0,2)*10.0))
            "Improve 5K" -> minOf(80.0,maxOf(recentKm*1.38,26.0+experience.coerceIn(0,2)*10.0))
            else -> minOf(105.0,recentKm*(1.24+experience.coerceIn(0,2)*0.08))
        }
        val loadingWeeksElapsed=(0 until week).count { idx ->
            val suppressed=Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(raceGoal,totalWeeks,idx)
            !(idx%4==3 && !suppressed)
        }
        val raw=minOf(goalPeakCapKm,startKm*Math.pow(1.0+weeklyRamp,loadingWeeksElapsed.toDouble()))
        val longestCap=longestKm?.takeIf { it.isFinite() && it>0.0 }?.let { longest ->
            // Gentle guard if 30d single-run exposure is far below average per-run budget when building
            val exposureScale=if(returning) 0.75 else 1.15
            maxOf(recentKm,(longest*exposureScale)*runDays*1.15)
        }
        val exposureBounded=longestCap?.let { minOf(raw,it) } ?: raw
        val targetBounded=targetKm?.let { minOf(it,exposureBounded) } ?: exposureBounded
        val suppressedHere=Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(raceGoal,totalWeeks,week)
        return if(week%4==3 && !suppressedHere) targetBounded*0.80 else targetBounded
    }

    /**
     * EXACT extracted predecessor of Engine15.runningCeiling.
     * Only one implementation remains; Engine15 delegates to this function.
     */
    fun ceiling(week:Int,recentKm:Double,targetKm:Double?=null):Double {
        require(week>=0 && recentKm.isFinite() && recentKm>=0.0 &&
            (targetKm==null || targetKm.isFinite() && targetKm>=0.0))
        val block=week/4
        val part=week%4
        if(targetKm==null) {
            val factor=if(part==3) minOf(0.90,0.76+block*0.04)
                else minOf(1.10,0.90+part*0.05+block*0.045)
            return recentKm*factor
        }
        val peak=minOf(targetKm,recentKm*(1.0+block*0.05))
        return if(part==3) peak*0.80
            else minOf(targetKm,recentKm*(0.90+part*0.05+block*0.05))
    }

    data class WeekEnvelope(
        val week:Int,
        val type:WeekType,
        /** Policy ceiling, not a commanded workout or proof of tolerance. */
        val ceilingKm:Double?,
        val reasonCode:String
    )

    data class VolumeSkeleton(
        val policyId:String,
        val availability:BaselineAvailability,
        val baseline:Engine15AthleteCapacity,
        val requestedTargetKm:Double?,
        val weeks:List<WeekEnvelope>
    )

    /**
     * Engine-agnostic M4 contract: requires an M3 capacity profile, NOT a
     * PlanRequest or the legacy calendar. Does not prescribe days/sessions.
     * Only an explicit confirmed average may create numeric ceiling values.
     *
     * No profile-confidence multiplier is applied in Stage A.
     */
    fun outline(
        baseline:Engine15AthleteCapacity,
        targetKm:Double?,
        weekCount:Int
    ):VolumeSkeleton {
        require(weekCount in 1..52)
        require(targetKm==null || targetKm.isFinite() && targetKm in 0.0..120.0)
        val availability=when {
            baseline.reviewReasons.isNotEmpty() -> BaselineAvailability.REQUIRES_REVIEW
            baseline.typicalWeeklyKm==null -> BaselineAvailability.NEEDS_BASELINE
            baseline.typicalWeeklyKm==0.0 -> BaselineAvailability.TIME_ONLY_CONFIRMED_ZERO
            else -> BaselineAvailability.DISTANCE_ENVELOPE
        }
        val result=(0 until weekCount).map { week ->
            val value=baseline.typicalWeeklyKm?.takeIf {
                availability==BaselineAvailability.DISTANCE_ENVELOPE
            }?.let { ceiling(week,it,targetKm) }
            WeekEnvelope(
                week,if(week%4==3)WeekType.RECOVERY else WeekType.LOADING,
                value,when(availability) {
                    BaselineAvailability.REQUIRES_REVIEW -> "M3_CONTRADICTION"
                    BaselineAvailability.NEEDS_BASELINE -> "M3_MISSING_TYPICAL_AVERAGE"
                    BaselineAvailability.TIME_ONLY_CONFIRMED_ZERO -> "M3_CONFIRMED_ZERO_TIME_ONLY"
                    BaselineAvailability.DISTANCE_ENVELOPE -> "EXISTING_ENGINE15_CEILING"
                }
            )
        }
        return VolumeSkeleton(POLICY_ID,availability,baseline,targetKm,result)
    }

    /**
     * Bridge for the existing Engine 1.5 generator. It still receives the
     * upstream Engine 1.0 scheduled-volume boundary, so Stage A does NOT
     * eliminate that second upstream planner. Never add catch-up miles.
     */
    data class ExistingWeekDecision(
        val week:Int,
        val baselineKm:Double,
        val policyCeilingKm:Double,
        val beforeRecoveryTenths:Int,
        val recoveryLimitKm:Double?,
        val budgetTenths:Int
    ) {
        val budgetKm:Double get()=budgetTenths/10.0
        val beforeRecoveryKm:Double get()=beforeRecoveryTenths/10.0
    }

    fun existingWeekBudget(
        week:Int,
        reportedRecentKm:Double,
        requestedTargetKm:Double?,
        upstreamBaselineKm:Double,
        previousDeliveredKm:Double?
    ):ExistingWeekDecision {
        require(upstreamBaselineKm.isFinite() && upstreamBaselineKm>=0.0)
        require(previousDeliveredKm==null ||
            previousDeliveredKm.isFinite() && previousDeliveredKm>=0.0)
        val maxKm=ceiling(week,reportedRecentKm,requestedTargetKm)
        val initial=tenths(minOf(upstreamBaselineKm,maxKm))
        val recoveryCap=previousDeliveredKm?.takeIf { week%4==3 }?.times(0.8)
        val budget=recoveryCap?.let { minOf(initial,tenths(it)) } ?: initial
        return ExistingWeekDecision(week,upstreamBaselineKm,maxKm,
            initial,recoveryCap,budget)
    }
}
