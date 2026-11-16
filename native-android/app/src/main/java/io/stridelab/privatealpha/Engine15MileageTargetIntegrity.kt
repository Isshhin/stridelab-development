package io.stridelab.privatealpha

import java.time.temporal.ChronoUnit
import kotlin.math.floor

/**
 * Never equate an athlete's target with the mileage actually prescribed.
 * Only explicit acceptance may relabel a substantial shortfall as a
 * foundation plan, not as a fulfilled Train Your Way target.
 */
internal data class Engine15MileageTargetReview(
    val requestedKm:Double,
    val reportedRecentKm:Double,
    val estimatedPeakKm:Double?,
    val requiresFoundationDecision:Boolean
) {
    fun explanation():String {
        val peak=estimatedPeakKm?.let { "%.1f".format(java.util.Locale.US,it) }
            ?: "unknown (time-only prescriptions)"
        return "Requested ${"%.1f".format(java.util.Locale.US,requestedKm)} km/week; " +
            "reported recent ${"%.1f".format(java.util.Locale.US,reportedRecentKm)} km/week; " +
            "planned peak $peak. The target is not a delivered weekly prescription."
    }
}

internal object Engine15MileageTargetIntegrity {
    /** Mixed-unit totals are planning estimates, never exact mileage targets. */
    fun review(plan:PlanSnapshot):Engine15MileageTargetReview? {
        if(plan.engineVersion!=ENGINE15_VERSION || plan.request.runDays==0)return null
        val target=plan.request.targetWeeklyKm?.takeIf { it.isFinite() && it>0.0 }
            ?: return null
        val runs=plan.sessions.filter { it.discipline=="run" }
        if(runs.isEmpty())return null
        val weekly=runs.groupBy {
            ChronoUnit.DAYS.between(plan.request.start,it.date)/7L
        }.values.map { week ->
            val distances=week.map { s ->
                s.distanceKm ?: s.runningPrescription?.estimatedDistanceKm
            }
            if(distances.any { it==null || !it.isFinite() || it<0.0 })null
            else distances.filterNotNull().sum()
        }
        val peak=if(weekly.any { it==null })null else weekly.filterNotNull().maxOrNull()
        // Apply the new approval gate to Train Your Way and target-bearing
        // zero-baseline plans. Preserve unrelated legacy/race plan behavior.
        val guarded=plan.request.trainYourWay || plan.request.weeklyKm==0.0
        val materialShortfall=peak==null || peak+0.01<target*0.75
        return Engine15MileageTargetReview(
            target,plan.request.weeklyKm,peak,guarded && materialShortfall
        )
    }

    fun requireReviewedBeforeApproval(plan:PlanSnapshot) {
        val assessment=review(plan) ?: return
        if(assessment.requiresFoundationDecision && !plan.request.foundationAcknowledged)
            throw PlanConstraint(
                "This draft does not demonstrate your requested mileage. " +
                    assessment.explanation() +
                    " Return to plan review: enter your actual recent mileage " +
                    "or explicitly choose a foundation plan. It cannot be approved as a ${assessment.requestedKm} km/week plan."
            )
    }

    /** A foundation is explicitly distinct from achieving the requested target. */
    fun displayName(request:PlanRequest):String? = when {
        request.foundationAcknowledged -> {
            val future=request.targetWeeklyKm
            if(future!=null) "Foundation · toward ${floor(future*10.0+1e-8)/10.0} km/week"
            else "Foundation plan"
        }
        request.trainYourWay -> "Train Your Way"
        else -> null
    }
}
