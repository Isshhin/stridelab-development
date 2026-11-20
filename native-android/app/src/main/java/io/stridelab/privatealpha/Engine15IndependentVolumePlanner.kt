package io.stridelab.privatealpha

import kotlin.math.floor
import kotlin.math.max

/**
 * M4 Stage B — a shadow-only, independent weekly volume candidate.
 *
 * No Engine 1.0, calendar generator, session composer, persistence, or approval
 * path uses this planner. It does not create workouts or certify that a budget
 * is schedulable. A separate Gate B decision is required before live use.
 *
 * The existing Engine 1.5 weekly ceiling remains an upper bound. The former
 * Q2 final-distance estimate is deliberately NOT recycled as the next week's
 * planned-volume baseline. M5 must reconcile timed steps before approval.
 */
internal object Engine15IndependentVolumePlanner {
    const val POLICY_ID="engine15.m4b.independent-shadow-20261117.1"

    enum class Status {
        DISTANCE_CANDIDATE,
        NEEDS_BASELINE,
        NEEDS_LEGACY_CONFIRMATION,
        NEEDS_REVIEW,
        TIME_ONLY,
        RECOVERY_REVIEW
    }
    enum class TargetStatus {
        NOT_EVALUATED, TARGET_REACHED_IN_BUDGET, TARGET_NOT_REACHED_IN_BUDGET
    }
    enum class WeekKind { LOADING, RECOVERY }

    data class Input(
        val weeks:Int,
        val runningDays:Int,
        val targetWeeklyKm:Double?,
        val returning:Boolean=false,
        val recoveryHold:Boolean=false,
        val userDirected:Boolean=false,
        val raceGoal:String?=null
    ) {
        init {
            require(weeks in 1..52 && runningDays in 1..7)
            require(targetWeeklyKm==null ||
                targetWeeklyKm.isFinite() && targetWeeklyKm in 0.0..120.0)
        }
    }

    data class Week(
        val index:Int,
        val kind:WeekKind,
        val existingPolicyCeilingKm:Double?,
        /** Optimistic workload *upper bound*, not a session recipe. */
        val provisionalSessionCapacityKm:Double?,
        val returnFromBreakCeilingKm:Double?,
        val recoveryCeilingKm:Double?,
        val proposedKm:Double?,
        val constraintCodes:List<String>
    )

    data class Result(
        val policyId:String,
        val status:Status,
        val targetStatus:TargetStatus,
        val athleteBaseline:Engine15AthleteCapacity,
        val input:Input,
        val weeks:List<Week>,
        val peakProposedKm:Double?,
        val shortfallToRequestedTargetKm:Double?,
        /** Reminder to check actual completed weeks, NOT automatic advancement. */
        val reviewAfterCompletedWeek:Int?,
        val reasons:List<String>
    )

    private fun floorTenth(km:Double):Double=floor(km*10.0+1e-8)/10.0

    /**
     * Retained session exposure concept: LONG max = reported longest + 1 km,
     * every other day receives at most 80% of that bound. Because sessions
     * and harder-adjacent recovery roles do not exist yet, this is strictly
     * an upper bound; M5 must find a valid lower/equal distribution.
     */
    private fun provisionalSessionCapacityKm(longestKm:Double?,days:Int):Double? =
        longestKm?.let { longest ->
            val exposure=longest+1.0
            exposure+(days-1)*exposure*0.8
        }

    /**
     * The older return-from-break starting bound, extracted as a constraint
     * rather than reusing the Engine 1.0 planner or applying a second plan.
     * Deload is applied exactly once downstream.
     */
    private fun returnCeilingKm(week:Int,baselineKm:Double,returning:Boolean):Double? =
        if(returning) baselineKm*0.6*minOf(1.2,1.0+week*0.05) else null

    fun propose(profile:Engine15AthleteCapacity,input:Input):Result {
        val status=when {
            input.recoveryHold -> Status.RECOVERY_REVIEW
            profile.reviewReasons.isNotEmpty() ||
                profile.status==AthleteBaselineStatus.NEEDS_REVIEW -> Status.NEEDS_REVIEW
            profile.typicalWeeklyKm==null -> Status.NEEDS_BASELINE
            profile.typicalWeeklyKm==0.0 -> Status.TIME_ONLY
            profile.confidence==AthleteBaselineConfidence.LEGACY_UNVERIFIED ->
                Status.NEEDS_LEGACY_CONFIRMATION
            else -> Status.DISTANCE_CANDIDATE
        }
        val numeric=status==Status.DISTANCE_CANDIDATE
        val base=profile.typicalWeeklyKm.takeIf { numeric }
        val capacity=if(numeric) provisionalSessionCapacityKm(
            profile.reportedLongestRunKm,input.runningDays
        ) else null
        var priorKm:Double?=null
        var peakBuildKm:Double?=null
        val marathonTaper = input.raceGoal == "Marathon" && input.weeks >= 12
        val halfOr10kTaper = input.raceGoal in setOf("Half marathon", "Improve 10K") && input.weeks >= 10
        val weeks=(0 until input.weeks).map { week ->
            val weeksFromEnd = input.weeks - 1 - week
            val taperMultiplier = when {
                marathonTaper && weeksFromEnd == 2 -> 0.85
                marathonTaper && weeksFromEnd == 1 -> 0.70
                marathonTaper && weeksFromEnd == 0 -> 0.55
                halfOr10kTaper && weeksFromEnd == 1 -> 0.80
                halfOr10kTaper && weeksFromEnd == 0 -> 0.60
                else -> null
            }
            val kind=if(week%4==3 || (taperMultiplier != null && weeksFromEnd == 0)) WeekKind.RECOVERY else WeekKind.LOADING
            val existingCeiling=base?.let { baseline ->
                Engine15WeeklyVolumeSkeleton.ceiling(week,baseline,input.targetWeeklyKm)
            }
            val returnCeiling=base?.let { baseline ->
                returnCeilingKm(week,baseline,input.returning)
            }
            val recoveryCeiling=if(kind==WeekKind.RECOVERY && taperMultiplier == null)
                priorKm?.times(0.8) else null
            val taperCeiling=taperMultiplier?.let { mult ->
                (peakBuildKm ?: priorKm ?: base)?.times(mult)
            }

            val requested=if(input.userDirected) input.targetWeeklyKm ?: existingCeiling else null
            val components=if(input.userDirected) listOfNotNull(recoveryCeiling, taperCeiling)
                else listOfNotNull(existingCeiling,capacity,returnCeiling,recoveryCeiling,taperCeiling)
            val rawBound=components.minOrNull()
            val directedBudget=if(input.userDirected && (recoveryCeiling!=null || taperCeiling!=null))
                listOfNotNull(requested, recoveryCeiling, taperCeiling).minOrNull() else requested
            val proposed=(directedBudget ?: rawBound)?.let(::floorTenth)
            val flags=mutableListOf<String>()
            if(!numeric)flags+="NO_NUMERIC_PROPOSAL"
            if(rawBound!=null && proposed!=null) {
                // An exact raw minimum actually binds. Do not mislabel nearby
                // candidate limits as executed cuts just because of 100 m flooring.
                fun binds(value:Double?)=value!=null && kotlin.math.abs(value-rawBound)<=1e-8
                if(binds(existingCeiling))flags+="EXISTING_ENGINE15_CEILING"
                if(binds(capacity))flags+="PROVISIONAL_SESSION_EXPOSURE_CAP"
                if(binds(returnCeiling))flags+="RETURN_FROM_BREAK_CAP"
                if(binds(recoveryCeiling))flags+="RECOVERY_FROM_PLANNED_WEEK_CAP"
                if(taperCeiling!=null && binds(taperCeiling))flags+="RACE_TAPER_VOLUME_REDUCTION"
                if(rawBound-proposed>1e-8)flags+="ROUNDING_DOWN_TO_100M"
                if(capacity==null)flags+="SESSION_EXPOSURE_UNCONFIRMED"
            }
            if(taperMultiplier == null && proposed != null && kind == WeekKind.LOADING) {
                peakBuildKm = maxOf(peakBuildKm ?: 0.0, proposed)
            }
            if(input.userDirected && proposed!=null) {
                flags+="USER_REQUESTED_NOT_CAPACITY_VALIDATED"
                if(existingCeiling!=null && proposed>existingCeiling+1e-8)
                    flags+="REQUESTED_TARGET_ABOVE_COACH_RECOMMENDATION"
                if(capacity!=null && proposed>capacity+1e-8)
                    flags+="PROVISIONAL_SESSION_EXPOSURE_WARNING"
                if(returnCeiling!=null && proposed>returnCeiling+1e-8)
                    flags+="RETURN_FROM_BREAK_RECOMMENDATION_EXCEEDED"
            }
            if(proposed!=null) priorKm=proposed
            Week(
                index=week,kind=kind,existingPolicyCeilingKm=existingCeiling,
                provisionalSessionCapacityKm=capacity,
                returnFromBreakCeilingKm=returnCeiling,
                recoveryCeilingKm=recoveryCeiling,
                proposedKm=proposed,constraintCodes=flags.toList()
            )
        }
        val peak=weeks.mapNotNull { it.proposedKm }.maxOrNull()
        val gap=if(peak!=null && input.targetWeeklyKm!=null)
            max(0.0,input.targetWeeklyKm-peak) else null
        val targetStatus=when {
            gap==null -> TargetStatus.NOT_EVALUATED
            gap<=1e-8 -> TargetStatus.TARGET_REACHED_IN_BUDGET
            else -> TargetStatus.TARGET_NOT_REACHED_IN_BUDGET
        }
        val reviewWeek=if(numeric && (input.returning ||
            profile.confidence!=AthleteBaselineConfidence.COMPLETE_FOUR_WEEK_HISTORY))
            minOf(2,input.weeks) else null
        val reasons=buildList {
            when(status) {
                Status.RECOVERY_REVIEW -> add("EXPLICIT_RECOVERY_HOLD")
                Status.NEEDS_REVIEW -> add("M3_BASELINE_CONTRADICTION")
                Status.NEEDS_BASELINE -> add("MISSING_TYPICAL_RUNNING_MILEAGE")
                Status.NEEDS_LEGACY_CONFIRMATION -> add("LEGACY_SCALAR_NOT_CONFIRMED_AS_AVERAGE")
                Status.TIME_ONLY -> add("CONFIRMED_ZERO_REQUIRES_TIME_ONLY_FOUNDATION")
                Status.DISTANCE_CANDIDATE -> add("SHADOW_VOLUME_ONLY_NOT_WORKOUTS")
            }
            if(numeric && capacity==null)add("SESSION_EXPOSURE_MUST_BE_REVIEWED_BY_M5")
            if(reviewWeek!=null)add("REASSESS_AFTER_COMPLETED_WEEK_$reviewWeek")
            if(targetStatus==TargetStatus.TARGET_NOT_REACHED_IN_BUDGET)
                add("TARGET_NOT_DELIVERED_BY_PLANNING_BUDGET")
        }
        return Result(
            POLICY_ID,status,targetStatus,profile,input,weeks,peak,gap,reviewWeek,reasons
        )
    }
}
