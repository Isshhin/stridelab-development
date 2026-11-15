package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/**
 * M10c has two distinct destinations:
 * - production: the existing Engine15/Engine1 saved PlanSnapshot;
 * - research: a separate, NON-executable comparison under kind="exercise".
 *
 * A research draft cannot be cast, serialized or restored as PlanSnapshot.
 * Every research value is a caller-asserted illustration, not an athlete
 * prescription or a qualification for production promotion.
 */
internal enum class IndependentEngineRoute {
    EXISTING_COACH,
    RESEARCH_COMPARISON
}

internal object IndependentEngineRouteGate {
    const val POLICY_ID = "stridelab.engine15.m10c-route-gate-v1"

    fun permitsProductionGeneration(route: IndependentEngineRoute): Boolean =
        route == IndependentEngineRoute.EXISTING_COACH

    fun permitsResearchSave(route: IndependentEngineRoute): Boolean =
        route == IndependentEngineRoute.RESEARCH_COMPARISON

    /** Never allow a research route to impersonate the production generator. */
    fun requireProduction(route: IndependentEngineRoute) {
        if (!permitsProductionGeneration(route))
            throw PlanConstraint(
                "Research mode only saves non-executable comparisons. Select Existing coach to create a real training plan."
            )
    }
}

internal data class IndependentResearchDraftDay(
    val isoDay: Int,
    val kilometers: Double,
    val longRunCandidate: Boolean,
    val qualityOpportunityOnly: Boolean
) {
    init {
        require(isoDay in 1..7)
        require(kilometers.isFinite() && kilometers > 0.0)
        require(!(longRunCandidate && qualityOpportunityOnly))
    }
}

internal data class IndependentResearchDraftWeek(
    val week: Int,
    val weeklyKilometers: Double,
    val recoveryWeek: Boolean,
    val days: List<IndependentResearchDraftDay>
) {
    init {
        require(week in 1..52)
        require(weeklyKilometers.isFinite() && weeklyKilometers > 0.0)
        require(days.isNotEmpty() && days.map { it.isoDay }.distinct().size == days.size)
        require(days.map { it.isoDay } == days.map { it.isoDay }.sorted())
        require(abs(days.sumOf { it.kilometers } - weeklyKilometers) < 0.00001)
        require(!recoveryWeek || days.none { it.qualityOpportunityOnly })
        require(days.count { it.longRunCandidate } <= 1)
    }
}

internal data class IndependentResearchDraft(
    val id: String,
    val owner: String,
    val createdAt: Instant,
    val request: PlanRequest,
    val selectedRunDays: Set<Int>,
    val weeks: List<IndependentResearchDraftWeek>,
    val policyId: String = POLICY_ID,
    val sourceEvidenceVerified: Boolean = false,
    val executable: Boolean = false,
    val authorizedForPlanMutation: Boolean = false,
    val authorizedForProduction: Boolean = false
) {
    init {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        require(owner.isNotBlank() && owner.length <= 128)
        require(policyId == POLICY_ID)
        require(request.targetWeeklyKm != null && request.targetWeeklyKm > 0.0)
        require(selectedRunDays.size == request.runDays)
        require(selectedRunDays.all { it in request.days && it in 1..7 })
        require(request.start.dayOfWeek == java.time.DayOfWeek.MONDAY)
        require(weeks.size == request.weeks && weeks.isNotEmpty())
        require(weeks.map { it.week } == (1..request.weeks).toList())
        require(weeks.all { week ->
            week.days.map { it.isoDay }.toSet() == selectedRunDays &&
                week.days.count { it.longRunCandidate } == if (selectedRunDays.size >= 3) 1 else 0
        })
        require(!sourceEvidenceVerified && !executable &&
            !authorizedForPlanMutation && !authorizedForProduction)
    }

    val recordId: String get() = "engine15-research-" + id
    val totalKilometers: Double get() = weeks.sumOf { it.weeklyKilometers }

    fun encode(): JSONObject {
        val encoded = JSONObject()
            .put("schemaVersion", SCHEMA)
            .put("kind", KIND)
            .put("id", id)
            .put("user_id", owner)
            .put("policyId", policyId)
            .put("createdAt", createdAt.toString())
            .put("request", request.encode())
            .put("selectedRunDays", JSONArray(selectedRunDays.sorted()))
            .put("weeks", JSONArray(weeks.map { week ->
                JSONObject()
                    .put("week", week.week)
                    .put("weeklyKilometers", week.weeklyKilometers)
                    .put("recoveryWeek", week.recoveryWeek)
                    .put("days", JSONArray(week.days.map { day ->
                        JSONObject()
                            .put("isoDay", day.isoDay)
                            .put("kilometers", day.kilometers)
                            .put("longRunCandidate", day.longRunCandidate)
                            .put("qualityOpportunityOnly", day.qualityOpportunityOnly)
                    }))
            }))
        // A research record intentionally has no "sessions", "status",
        // "engineVersion" or plan ID that could be approved or played.
        return encoded
    }

    companion object {
        const val SCHEMA = 1
        const val POLICY_ID = "stridelab.engine15.m10c-read-only-research-draft-v1"
        const val KIND = "engine15-research-review"

        fun fromPreview(
            owner: String,
            request: PlanRequest,
            result: IndependentAndroidPreviewDecision.ReadOnly,
            selectedRunDays: Set<Int>,
            id: String = UUID.randomUUID().toString(),
            createdAt: Instant = Instant.now()
        ): IndependentResearchDraft {
            require(!result.sourceEvidenceVerified && !result.executable &&
                !result.authorizedForPlanMutation && !result.authorizedForProduction)
            require(result.startMonday == request.start)
            val weeks = result.mileage.mileage.weeks.zip(result.qualitySlots.weeks)
                .map { (mileage, spacing) ->
                    require(mileage.week == spacing.week &&
                        mileage.recoveryWeek == spacing.recoveryWeek)
                    val quality = spacing.opportunities.map { it.isoDay }.toSet()
                    require(quality.size == spacing.opportunities.size)
                    require(quality.all { day ->
                        mileage.dayAllocations.any {
                            it.isoDay == day && !it.longRunCandidate
                        }
                    })
                    IndependentResearchDraftWeek(
                        week = mileage.week,
                        weeklyKilometers = mileage.weeklyKilometers,
                        recoveryWeek = mileage.recoveryWeek,
                        days = mileage.dayAllocations.sortedBy { it.isoDay }.map { day ->
                            IndependentResearchDraftDay(
                                isoDay = day.isoDay,
                                kilometers = day.kilometers,
                                longRunCandidate = day.longRunCandidate,
                                qualityOpportunityOnly = day.isoDay in quality
                            )
                        }
                    )
                }
            return IndependentResearchDraft(
                id = id, owner = owner, createdAt = createdAt,
                request = request, selectedRunDays = selectedRunDays, weeks = weeks
            )
        }

        fun decode(raw: JSONObject, owner: String): IndependentResearchDraft {
            require(raw.getInt("schemaVersion") == SCHEMA)
            require(raw.getString("kind") == KIND)
            require(raw.getString("policyId") == POLICY_ID)
            require(raw.getString("user_id") == owner)
            require(!raw.optBoolean("sourceEvidenceVerified", false))
            require(!raw.optBoolean("executable", false))
            require(!raw.optBoolean("authorizedForPlanMutation", false))
            require(!raw.optBoolean("authorizedForProduction", false))
            require(!raw.has("sessions") && !raw.has("engineVersion") &&
                !raw.has("status"))
            val days = raw.getJSONArray("selectedRunDays")
            val decodedDays = (0 until days.length()).map { days.getInt(it) }
            require(decodedDays.distinct().size == decodedDays.size)
            val storedWeeks = raw.getJSONArray("weeks")
            require(storedWeeks.length() in 1..52)
            val decodedWeeks = (0 until storedWeeks.length()).map { index ->
                val w = storedWeeks.getJSONObject(index)
                val allocations = w.getJSONArray("days")
                require(allocations.length() in 2..7)
                IndependentResearchDraftWeek(
                    week = w.getInt("week"),
                    weeklyKilometers = w.getDouble("weeklyKilometers"),
                    recoveryWeek = w.getBoolean("recoveryWeek"),
                    days = (0 until allocations.length()).map { i ->
                        val d = allocations.getJSONObject(i)
                        IndependentResearchDraftDay(
                            isoDay = d.getInt("isoDay"),
                            kilometers = d.getDouble("kilometers"),
                            longRunCandidate = d.getBoolean("longRunCandidate"),
                            qualityOpportunityOnly = d.getBoolean("qualityOpportunityOnly")
                        )
                    }
                )
            }
            return IndependentResearchDraft(
                id = raw.getString("id"),
                owner = owner,
                createdAt = Instant.parse(raw.getString("createdAt")),
                request = PlanRequest.decode(raw.getJSONObject("request")),
                selectedRunDays = decodedDays.toSet(),
                weeks = decodedWeeks
            )
        }
    }
}
