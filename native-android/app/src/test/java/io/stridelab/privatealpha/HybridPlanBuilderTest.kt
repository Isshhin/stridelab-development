package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class HybridPlanBuilderTest {
    private val start = LocalDate.of(2026, 11, 23)
    private val owner = "hybrid-owner"
    private val now = Instant.parse("2026-11-23T00:00:00Z")
    private val library by lazy { ExerciseLibraryV2.load { File("src/main/assets", it).inputStream() } }
    private var sequence = 0
    private fun generate(request: PlanRequest, peers: List<PlanSnapshot>) =
        Engine15IndependentPlanGenerator.generateWithHistory(request, owner, peers,
            id = "hybrid-track-${++sequence}", now = now, exerciseLibrary = library)

    private fun request() = PlanRequest(
        goal = "General fitness", start = start, weeks = 2, days = (1..7).toSet(),
        runDays = 1, longDay = 7, qualityCount = 0, qualityDays = emptySet(),
        weeklyKm = 8.0, targetWeeklyKm = 8.0, longestKm = 10.0, experience = 1, timeBudgetEnabled = false,
        strengthDays = 1, calDays = 1, conditioningDays = 1,
        disciplineGoals = mapOf("str" to "Upper Body & Posture", "cal" to "Core / Anti-Rotation",
            "cond" to "Steady aerobic cross-conditioning")
    )

    @Test fun sixTracksGenerateSeparateIdentityAndRoundTripThroughExistingBackup() {
        val tracks = HybridPlanBuilder.split(request(), setOf("plyo", "iso"))
        val preview = HybridPlanBuilder.build("six-tracks", owner, tracks, emptyList(), ::generate)
        assertEquals(setOf("run", "str", "plyo", "iso", "cal", "cond"), preview.drafts.map { it.first }.toSet())
        assertTrue(preview.drafts.all { it.second.status == "draft" && it.second.sessions.size == 2 })
        val approved = HybridPlanBuilder.prepareActivation(preview, owner, emptyList())
        assertEquals(6, approved.map { it.id }.distinct().size)
        assertTrue(approved.all { it.owner == owner && it.revision == 2 && it.status == "approved" })
        assertTrue(approved.flatMap { it.sessions }.groupBy { it.date }.values.all { it.size == 1 })
        assertTrue(approved.single { it.request.runDays > 0 }.sessions.all { it.date.dayOfWeek.value == 7 })
        val backup = PlanBackup.encode(approved, emptyList(), owner)
        assertEquals(approved, PlanBackup.additions(PlanBackup.read(backup, owner), emptyList()))
    }

    @Test fun automaticWeekdaysPreferBalancedGapsInsteadOfMondayTuesday() {
        val initial = request().copy(runDays = 0, strengthDays = 2, calDays = 0, conditioningDays = 2)
        val preview = HybridPlanBuilder.build("balanced", owner, HybridPlanBuilder.split(initial, emptySet()),
            emptyList(), ::generate)
        preview.drafts.forEach { (_, plan) ->
            val days = plan.sessions.take(2).map { it.date.dayOfWeek.value }.sorted()
            assertTrue(days[1] - days[0] in 3..4)
        }
    }

    @Test fun compatibleFixedPairKeepsSelectedTrackPrescriptions() {
        val initial = request().copy(runDays = 0, calDays = 0,
            weeklySlots = mapOf("str" to listOf(3), "cond" to listOf(3)))
        val tracks = HybridPlanBuilder.split(initial, emptySet())
        val preview = HybridPlanBuilder.build("accepted-pair", owner, tracks, emptyList(), ::generate)
        assertTrue(preview.notes.any { it.contains("first, then") })
        assertTrue(preview.drafts.flatMap { it.second.sessions }.all { it.date.dayOfWeek.value == 3 })
        assertEquals(2, HybridPlanBuilder.prepareActivation(preview, owner, emptyList()).size)
    }

    @Test fun dailyLimitStillChecksTheCombinedDurationOfCompatibleFixedWorkouts() {
        val initial = request().copy(runDays = 0, calDays = 0,
            weeklySlots = mapOf("str" to listOf(3), "cond" to listOf(3)))
        val requests = HybridPlanBuilder.split(initial, emptySet())
        val preview = HybridPlanBuilder.build("no-cap", owner, requests, emptyList(), ::generate)
        val peakDay = preview.drafts.flatMap { it.second.sessions }.groupBy { it.date }.values
            .maxBy { sessions -> sessions.sumOf { it.effectiveMinutes } }
        val combined = peakDay.sumOf { it.effectiveMinutes }
        val cap = maxOf(15, peakDay.maxOf { it.effectiveMinutes })
        assertTrue(cap < combined)
        val limited = requests.map { it.copy(request = it.request.copy(
            timeBudgetEnabled = true, dailyMinutes = cap)) }
        val rejection = assertThrows(PlanConstraint::class.java) {
            HybridPlanBuilder.build("limited", owner, limited, emptyList(), ::generate)
        }
        assertTrue(rejection.message.orEmpty().contains("$cap-minute daily limit"))
        assertNull(hybridDailyLimit(""))
        assertEquals(combined, hybridDailyLimit(combined.toString()))
        assertThrows(PlanConstraint::class.java) { hybridDailyLimit("14") }
    }

    @Test fun preparationRejectsAnyStaleOrInvalidTrackBeforeWriting() {
        val initial = request().copy(runDays = 0, calDays = 0)
        val preview = HybridPlanBuilder.build("atomic", owner, HybridPlanBuilder.split(initial, emptySet()),
            emptyList(), ::generate)
        val last = preview.drafts.last()
        val invalid = preview.copy(drafts = preview.drafts.dropLast(1) + (last.first to last.second.copy(owner = "other")))
        assertThrows(PlanConstraint::class.java) { HybridPlanBuilder.prepareActivation(invalid, owner, emptyList()) }
        val changed = preview.drafts.first().second.copy(status = "approved")
        assertThrows(PlanConstraint::class.java) { HybridPlanBuilder.prepareActivation(preview, owner, listOf(changed)) }
    }

    @Test fun failedSameFamilyReconciliationHasAnExplicitReviewPath() {
        val text = coachBuilderError("QUALITY_RECONCILIATION_UNSAFE_OR_INFEASIBLE: unsafe dose")
        assertFalse(text.contains("QUALITY_RECONCILIATION"))
        assertTrue(text.contains("smallest safe dose") && text.contains("fewer hard sessions"))
        assertTrue(text.contains("choose that goal explicitly"))
    }
}
