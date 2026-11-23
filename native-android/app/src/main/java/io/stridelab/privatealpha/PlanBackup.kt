package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject

internal data class PlanBackupContents(
    val plans: List<PlanSnapshot>,
    val researchReviews: List<IndependentResearchDraft>
)

/** Validate every record before beginning a non-destructive restore transaction. */
internal object PlanBackup {
    private const val LEGACY_SCHEMA = 1
    private const val CURRENT_SCHEMA = 2
    private const val MAX_PLANS = 50
    private const val MAX_RESEARCH_REVIEWS = 20

    fun encode(
        plans: List<PlanSnapshot>,
        researchReviews: List<IndependentResearchDraft>,
        owner: String
    ): String {
        require(plans.size <= MAX_PLANS && researchReviews.size <= MAX_RESEARCH_REVIEWS)
        require(plans.isNotEmpty() || researchReviews.isNotEmpty())
        RecordPolicy.identifier(owner)
        require(plans.all { it.owner == owner })
        require(researchReviews.all { it.owner == owner })
        val raw = JSONObject()
            .put("schemaVersion", CURRENT_SCHEMA)
            .put("plans", JSONArray(plans.map { it.encode() }))
            .put("researchReviews", JSONArray(researchReviews.map { it.encode() }))
            .toString(2)
        RecordPolicy.payload(raw)
        readBundle(raw,owner)
        return raw
    }

    /** Legacy callers can continue reading plan-only files; restore uses readBundle. */
    fun read(raw: String, owner: String): List<PlanSnapshot> {
        val bundle=readBundle(raw,owner)
        if(bundle.researchReviews.isNotEmpty())
            throw PlanConstraint("This backup also contains research reviews; use the version-aware restore path. No records were changed.")
        return bundle.plans
    }

    fun readBundle(raw: String, owner: String): PlanBackupContents {
        try {
            RecordPolicy.payload(raw)
            RecordPolicy.identifier(owner)
            val json = JSONObject(raw)
            val schema = json.getInt("schemaVersion")
            require(schema == LEGACY_SCHEMA || schema == CURRENT_SCHEMA)
            val plansJson = json.getJSONArray("plans")
            require(plansJson.length() <= MAX_PLANS)
            val plans = plansJson.objects().map { row ->
                val plan = PlanSnapshot.decode(row, owner)
                RecordPolicy.identifier(plan.id)
                require(plan.revision > 0 && plan.status in setOf("draft","approved","paused","archived"))
                require(plan.request.weeks in 1..16 && plan.sessions.isNotEmpty())
                require(plan.feedback.size <= 1000)
                plan.sessions.forEach { s ->
                    RecordPolicy.identifier(s.id)
                    require(s.date >= plan.request.start && s.date < plan.request.start.plusWeeks(plan.request.weeks.toLong()))
                    val reviewOnlyUnassigned = plan.engineVersion == ENGINE15_VERSION &&
                        Engine1Engines.canManage(plan) &&
                        plan.status in setOf("draft","paused") && s.status == "unassigned" &&
                        s.log == null && s.linkedActivityId == null
                    require(s.minutes in 0..1440 && s.discipline in setOf("run","str","cal","cond") &&
                        (s.status in setOf("planned","in_progress","completed","missed","skipped") || reviewOnlyUnassigned))
                    require(s.swaps.size <= s.exercises.size && s.swaps.map { it.originalId }.distinct().size == s.swaps.size && s.swaps.all { swap -> s.exercises.any { it.id == swap.originalId } })
                    require(s.exercises.size <= 30 && s.effectiveExercises.map { it.id }.distinct().size == s.effectiveExercises.size && (s.exercises + s.effectiveExercises).all { it.sets in 1..20 && (it.reps == null || it.reps in 0..500) && (it.seconds == null || it.seconds in 0..3600) })
                    s.log?.let { log ->
                        require(log.notes.length <= 2000 && (log.effort == null || log.effort in 1..10) && (log.minutes == null || log.minutes.isFinite() && log.minutes > 0 && log.minutes <= 1440) && (log.distanceKm == null || log.distanceKm.isFinite() && log.distanceKm > 0 && log.distanceKm <= 500))
                        if (Engine1Engines.canManage(plan)) WorkoutLogValidation.validate(s, log)
                    }
                }
                plan
            }
            require(plans.map { it.id }.distinct().size == plans.size)
            require(plans.flatMap { it.sessions }.map { it.id }.distinct().size == plans.sumOf { it.sessions.size })
            plans.forEach(PlanScheduleValidation::validatePlan)

            val reviews = when (schema) {
                LEGACY_SCHEMA -> {
                    require(!json.has("researchReviews") || json.getJSONArray("researchReviews").length() == 0)
                    emptyList()
                }
                else -> {
                    val rows = json.getJSONArray("researchReviews")
                    require(rows.length() <= MAX_RESEARCH_REVIEWS)
                    rows.objects().map { IndependentResearchDraft.decode(it, owner) }
                        .also { require(it.map { review -> review.id }.distinct().size == it.size) }
                }
            }
            require(plans.isNotEmpty() || reviews.isNotEmpty())
            return PlanBackupContents(plans, reviews)
        } catch (_: Exception) {
            throw PlanConstraint("This backup is invalid, too large, unsupported, or belongs to a different account. No saved records were changed.")
        }
    }

    fun additions(incoming: List<PlanSnapshot>, existing: List<PlanSnapshot>): List<PlanSnapshot> {
        val additions = incoming.filter { row -> existing.none { it.id == row.id } }
        val links = (existing + additions).flatMap { it.sessions }.mapNotNull { it.linkedActivityId }
        if (links.distinct().size != links.size) throw PlanConstraint("The backup would link one source run to multiple sessions. No records were changed.")
        PlanScheduleValidation.validateRestoration(existing + additions)
        return additions
    }

    fun researchAdditions(
        incoming: List<IndependentResearchDraft>,
        existing: List<IndependentResearchDraft>
    ): List<IndependentResearchDraft> {
        val byId = existing.associateBy { it.id }
        incoming.forEach { row ->
            val old = byId[row.id]
            if (old != null && old != row)
                throw PlanConstraint("A saved research comparison uses the same ID with different contents. No records were changed.")
        }
        return incoming.filter { it.id !in byId }
    }
}
