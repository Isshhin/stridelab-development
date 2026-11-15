package io.stridelab.privatealpha

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.floor

internal const val ENGINE15_VERSION = "1.5.0"
internal const val ENGINE15_FOUNDATION_POLICY = "stridelab.engine15.foundation-20261103.1"
internal const val ENGINE15_POLICY = "stridelab.engine15.bounded-running-20261103.1"
internal const val ENGINE15_MULTI_POLICY = "stridelab.engine15.multi-session-20261103.1"
internal const val ENGINE15_INTERFERENCE_POLICY = "stridelab.engine15.interference-calendar-20261103.1"

/** Deterministic, explicitly selected candidate. Does not infer fitness or recovery. */
internal object Engine15 : PlanGenerationEngine {
    override val version = ENGINE15_VERSION
    override val policyId = ENGINE15_POLICY

    internal fun validate(request: PlanRequest) {
        if (request.recentQualitySessionsPerWeek != null && request.recentQualitySessionsPerWeek !in 0..2)
            throw PlanConstraint("Recent quality frequency must be unknown, zero, one or two sessions per week.")
        if (request.runDays > 0 && request.runningRecoveryHold)
            throw PlanConstraint("Running is on hold for recovery review. Resolve the pain, illness or return concern before requesting a running prescription.")
        if (request.policyChoice != "engine15")
            throw PlanConstraint("Engine 1.5 must be selected explicitly.")
        if (request.targetWeeklyKm != null &&
            (!request.targetWeeklyKm.isFinite() || request.targetWeeklyKm !in 0.0..120.0))
            throw PlanConstraint("Target weekly mileage must be between 0 and 120 km.")
        if (request.runDays > 0 && request.targetWeeklyKm == 0.0)
            throw PlanConstraint("For no running, switch running off. Otherwise enter a positive weekly target.")
        if (request.disciplineGoals.any { (d, goal) -> goal !in Engine1Engines.goals[d].orEmpty() })
            throw PlanConstraint("Choose supported goals for each discipline.")
        Engine15SessionLocks.validateRequest(request)
        if (requiresMulti(request))
            MultiSessionEngine1.validate(request.copy(policyChoice = "multi-session"))
        else Engine1.validate(request)
    }

    private fun requiresMulti(p:PlanRequest): Boolean {
        val assigned = p.weeklySlots.values.flatten()
        return p.allowDoubleDays || p.runDays + p.strengthDays + p.calDays + p.conditioningDays > p.days.size ||
            assigned.distinct().size != assigned.size
    }

    /**
     * Planned-mileage ceiling from athlete-supplied weekly mileage.
     * Three staged loading weeks followed by a lighter fourth week.
     * No catch-up mileage is added after deload weeks.
     */
    internal fun runningCeiling(week: Int, recentKm: Double, targetKm: Double? = null): Double {
        require(week >= 0 && recentKm.isFinite() && recentKm >= 0.0 &&
            (targetKm == null || targetKm.isFinite() && targetKm >= 0.0))
        val block = week / 4
        val part = week % 4
        if(targetKm==null) {
            val factor=if(part==3) minOf(0.90,0.76+block*0.04)
                else minOf(1.10,0.90+part*0.05+block*0.045)
            return recentKm*factor
        }
        val peak = minOf(targetKm, recentKm * (1.0 + block * 0.05))
        return if (part == 3) peak * 0.80
            else minOf(targetKm, recentKm * (0.90 + part * 0.05 + block * 0.05))
    }

    private fun weekIndex(request: PlanRequest, session: PlannedSession) =
        (ChronoUnit.DAYS.between(request.start, session.date) / 7L).toInt()

    private fun roundDownKm(km: Double) = floor(km * 10.0 + 1e-9) / 10.0

    override fun generate(
        request: PlanRequest, owner: String, id: String, now: Instant
    ): PlanSnapshot = generateProgram(request,owner,id,now,null)

    private fun generateProgram(request:PlanRequest,owner:String,id:String,now:Instant,review:Engine15Feedback.DraftReview?,pattern:Engine15RunHistory?=null,includeAlternatives:Boolean=true):PlanSnapshot {
        validate(request)
        if(request.runDays>0 && review?.illnessThrough!=null && request.start<=review.illnessThrough && request.start.plusWeeks(request.weeks.toLong())>review.asOf)
            throw PlanConstraint("Recent reported pain or illness needs recovery review before a new immediate running plan. Choose a later start or review your current records; no catch-up training is prescribed.")
        // Keep the original generator, calendar, multi-discipline recipes and
        // saved-plan contract unchanged; only refine proposed RUNNING sessions.
        val multi = requiresMulti(request)
        val interference = multi && request.runDays > 0 && request.strengthDays >= 2
        val baseline = when {
            interference -> Engine15InterferenceScheduling.generateBase(request, owner, id, now)
            multi -> MultiSessionEngine1.generate(request.copy(policyChoice = "multi-session"), owner, id, now)
            request.disciplineGoals.isNotEmpty() -> CoordinatedEngine1.generate(
                request.copy(policyChoice = "coordinated"), owner, id, now)
            else -> Engine1.generate(request.copy(policyChoice = "baseline"), owner, id, now)
        }
        val adapted = Engine15RunningProgram.compose(request,baseline.sessions,review,pattern)
        Engine15SessionLocks.validateOutput(request, adapted)
        check(adapted.map { it.id to it.date } == baseline.sessions.map { it.id to it.date })
        val originalById=baseline.sessions.associateBy { it.id }
        check(adapted.filter { it.discipline!="run" }.all { it==originalById.getValue(it.id) })
        for ((week,sessions) in adapted.groupBy { weekIndex(request,it) }) {
            val original=baseline.sessions.filter { weekIndex(request,it)==week && it.discipline=="run" }
            check(sessions.filter { it.discipline=="run" }.sumOf { it.distanceKm ?: 0.0 } <= original.sumOf { it.distanceKm ?: 0.0 }+1e-8)
        }
        val options=if(includeAlternatives && request.targetWeeklyKm!=null && adapted.filter { it.discipline=="run" }.all { it.distanceKm!=null }) {
            val peak=adapted.filter { it.discipline=="run" }.groupBy { weekIndex(request,it) }.values.maxOfOrNull { rows -> rows.sumOf { it.distanceKm ?: 0.0 } } ?: 0.0
            if(request.runDays>0 && peak+0.01<request.targetWeeklyKm) {
                val longer=((request.weeks+1)..16).firstOrNull { weeks ->
                    val candidateRequest=request.copy(weeks=weeks)
                    val candidate=try { generateProgram(candidateRequest,owner,id,now,review,pattern,includeAlternatives=false) } catch(_:PlanConstraint) { return@firstOrNull false }
                    candidate.sessions.filter { it.discipline=="run" }.groupBy { weekIndex(candidateRequest,it) }.values.any { rows -> rows.sumOf { it.distanceKm ?: 0.0 }+0.01>=request.targetWeeklyKm }
                }
                Engine15MileageOptions(roundDownKm(peak),request.targetWeeklyKm,longer)
            } else null
        } else null
        return baseline.copy(
            engineVersion = version,
            policyId = when {
                interference -> ENGINE15_INTERFERENCE_POLICY
                multi -> ENGINE15_MULTI_POLICY
                else -> policyId
            },
            request = request,
            mileageOptions = options,
            sessions = adapted,
            explanations = listOf(
                "Engine 1.5 policy (${if (interference) ENGINE15_INTERFERENCE_POLICY else if (multi) ENGINE15_MULTI_POLICY else policyId}): the scheduling baseline " +
                    "still supplies scheduled days, disciplines and equipment prescriptions.",
                "Running volume has staged loading ceilings and lighter fourth weeks " +
                    "based on manually supplied recent mileage. The week is allocated by session purpose; missed work is never made up.",
                "With at least three weekly runs, long-run distance is capped to 40% " +
                    "of final planned weekly distance. Missing mileage remains time-based.",
                "Quality running is limited by recent mileage, experience, return " +
                    "status, lighter weeks, race taper and available time. Effort " +
                    "descriptions are not measured thresholds, zones or target paces.",
                "Target weekly mileage is a capped goal; progression remains bounded by the reported recent mileage and includes a fourth-week recovery reduction.",
                if(multi) "Multi-session days use the existing conservative easy-run + calisthenics pairing and daily budget. Incompatible schedules are rejected." else "Single-session Engine 1.0 calendar rules remain unchanged.",
                "Review every workout before approval; no existing saved plan, " +
                    "completed activity or athlete record has been changed."
            ) + (options?.explanations(request.weeks) ?: emptyList()) + (pattern?.let { listOf("OBSERVED_RUN_PATTERN: Repeated logged easy/long exposures inform session allocation. Weekly coverage is unknown; logged totals do not replace reported weekly mileage. Sources: ${it.evidenceIds.joinToString()}.") } ?: emptyList()) + coachingReview(request, adapted) + (if(review!=null && request.start<=review.recoveryThrough)
                listOf("RECENT_RESPONSE_REVIEW: Recent explicit difficulty limits upcoming running to shorter easy roles through ${review.recoveryThrough}. Evidence: ${review.evidenceIds.joinToString()}. No fitness or recovery score is inferred.") else emptyList()) + baseline.explanations.filterNot {
                it.startsWith("Engine 1.0 uses ") ||
                    it.startsWith("Every fourth week is lighter.")
            }
        )
    }

    fun generateWithHistory(request:PlanRequest,owner:String,history:List<PlanSnapshot>,
        id:String=java.util.UUID.randomUUID().toString(),now:Instant=Instant.now()):PlanSnapshot =
        generateProgram(request,owner,id,now,Engine15Feedback.reviewForNewDraft(owner,history,now),Engine15RunHistory.assess(owner,history,now))

    private fun coachingReview(request: PlanRequest, sessions: List<PlannedSession>): List<String> {
        if (request.runDays == 0) return emptyList()
        val notes = mutableListOf(
            "PROVISIONAL_SCHEDULE: Future weeks are projections, not demonstrated tolerance. Review actual completion and response before advancing.",
            "SESSION_EXPOSURE_BOUND: Every run is bounded by reported or repeated observed session exposure; removed distance is not redistributed. This provisional limit is not an injury-risk estimate."
        )
        if (request.qualityCount > (request.recentQualitySessionsPerWeek ?: 1))
            notes += "QUALITY_FREQUENCY_REVIEW: Requested quality exceeds the reported recent weekly pattern. Unknown history supports at most one introductory controlled session, not an inferred twice-weekly tolerance."
        val runs = sessions.filter { it.discipline == "run" }
        val peak = runs.groupBy { weekIndex(request,it) }.values.maxOfOrNull { week -> week.sumOf { it.distanceKm ?: 0.0 } } ?: 0.0
        request.targetWeeklyKm?.let { target ->
            if (runs.any { it.distanceKm == null }) notes += "TARGET_FEASIBILITY_UNKNOWN: Time-only sessions cannot establish progress toward a kilometre target. Enter a recent running baseline before a mileage build."
            else if (peak + 0.01 < target) notes += "TARGET_NOT_REACHED: Requested $target km/week; this constrained schedule peaks at ${roundDownKm(peak)} km. Review recent session exposure, running days and actual completed weeks before choosing a new build or timeline."
        }
        if (request.returning) notes += "RETURN_REASSESSMENT: This reduced return schedule does not establish regained capacity. Review completed easy weeks and symptoms before regenerating with a current baseline; do not automatically remove the return restriction."
        if (request.goal in setOf("Improve 5K","Improve 10K","Half marathon","Marathon"))
            notes += "RACE_READINESS_UNASSESSED: The race label selects workout style only. Event readiness and a feasible preparation timeline have not been established; this is provisional conditioning, not confirmed race preparation."
        return notes
    }
}
