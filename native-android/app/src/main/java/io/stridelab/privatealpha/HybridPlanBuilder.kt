package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class HybridTrackRequest(val track: String, val request: PlanRequest)

internal data class HybridPlanPreview(
    val id: String,
    val drafts: List<Pair<String, PlanSnapshot>>,
    val peers: Map<String, Int>,
    val notes: List<String>
)

/** Keeps the saved request and plan contracts by generating one valid snapshot per track. */
internal object HybridPlanBuilder {
    private val order = listOf("run", "plyo", "str", "iso", "cal", "cond")

    fun split(base: PlanRequest, extraResistance: Set<String>): List<HybridTrackRequest> {
        val primaryStrength = strengthTrack(base)
        return order.mapNotNull { track ->
            val storage = if (track in setOf("plyo", "iso")) "str" else track
            val extra = track in extraResistance && (track != primaryStrength || base.strengthDays == 0)
            val count = when {
                extra -> 1
                storage == "str" && track != primaryStrength -> 0
                else -> base.disciplineSessionCount(storage)
            }
            if (count == 0) return@mapNotNull null
            val goal = when (track) {
                "iso" -> "Isometrics"
                "plyo" -> "Plyometrics"
                "run" -> base.disciplineGoals["run"] ?: if (base.goal == "General fitness") base.goal
                    else base.disciplineGoal("run")
                else -> base.disciplineGoal(storage)
            }
            HybridTrackRequest(track, base.copy(
                goal = goal,
                runDays = if (track == "run") count else 0,
                qualityCount = if (track == "run") base.qualityCount else 0,
                qualityDays = if (track == "run") base.qualityDays else emptySet(),
                strengthDays = if (storage == "str") count else 0,
                calDays = if (track == "cal") count else 0,
                conditioningDays = if (track == "cond") count else 0,
                disciplineGoals = mapOf(storage to goal),
                weeklySlots = if (extra) emptyMap() else base.weeklySlots.filterKeys { it == storage },
                lockedSessionDays = if (extra) emptyMap() else base.lockedSessionDays.filterKeys { id ->
                    id.substringAfterLast('-').startsWith(storage)
                },
                userDefinedQualityWorkouts = if (track == "run") base.userDefinedQualityWorkouts else emptyMap(),
                policyChoice = "engine15"
            ))
        }
    }

    fun build(
        id: String,
        owner: String,
        requests: List<HybridTrackRequest>,
        existing: List<PlanSnapshot>,
        allowPairs: Boolean,
        generate: (PlanRequest, List<PlanSnapshot>) -> PlanSnapshot
    ): HybridPlanPreview {
        if (requests.size !in 2..6 || requests.map { it.track }.distinct().size != requests.size ||
            requests.any { it.track !in order }
        ) throw PlanConstraint("Choose two to six different training tracks.")
        val active = existing.filter { it.owner == owner && it.status == "approved" }
        val picked = mutableListOf<Pair<String, PlanSnapshot>>()
        val notes = mutableListOf<String>()
        var reason: String? = null
        var attempts = 0
        val tracks = requests.sortedBy { order.indexOf(it.track) }
        val options = tracks.associate { (track, request) ->
            val storage = if (track in setOf("plyo", "iso")) "str" else track
            val variants = if (track == "run" || request.weeklySlots.containsKey(storage) ||
                request.lockedSessionDays.isNotEmpty()
            ) listOf(request) else {
                patterns(request.days, request.disciplineSessionCount(storage)).map { pattern ->
                    request.copy(weeklySlots = mapOf(storage to pattern))
                }
            }
            val results = variants.mapNotNull { variant ->
                try {
                    generate(variant, if (track == "run") active else emptyList())
                } catch (failure: PlanConstraint) {
                    reason = coachBuilderError(failure.message.orEmpty())
                    null
                }
            }
            if (results.isEmpty()) throw PlanConstraint(
                "${trainingTrackLabel(track)}: ${reason ?: "No valid weekday assignment. Change fixed days or add availability."}"
            )
            track to results
        }

        fun search(index: Int): Boolean {
            if (index == tracks.size) return true
            val track = tracks[index].track
            val peers = active + picked.map { it.second.copy(status = "approved") }
            val candidates = options.getValue(track).sortedBy { draft ->
                val occupied = scheduled(peers).groupingBy { it.date }.eachCount()
                draft.sessions.sumOf { occupied[it.date] ?: 0 }
            }
            for (draft in candidates) {
                if (++attempts > 6_000) return false
                val review = RunnerSupportSchedule.review(draft, peers)
                val candidate = review?.candidate ?: draft
                if (review?.blocked == true) {
                    reason = "${trainingTrackLabel(track)} needs a different day to preserve key-run recovery."
                    continue
                }
                val fitted = fit(candidate, peers, allowPairs)
                if (fitted.first == null) {
                    reason = fitted.second
                    continue
                }
                val priorNotes = notes.size
                val next = requireNotNull(fitted.first)
                val pairing = scheduled(peers).groupBy { it.date }
                for (session in next.sessions) {
                    pairing[session.date]?.singleOrNull()?.let { peer ->
                        val first = if (peer.discipline == "run" || harder(peer) || !harder(session)) peer else session
                        val second = if (first == peer) session else peer
                        notes += "${session.date} · ${first.title} first, then ${second.title}."
                    }
                }
                review?.changes?.forEach { change -> notes += "${change.after.date} · ${change.reason}" }
                if (next != candidate) notes += "${trainingTrackLabel(track)} uses fewer sets on paired days to fit the time limit."
                picked += track to next
                if (search(index + 1)) return true
                picked.removeAt(picked.lastIndex)
                while (notes.size > priorNotes) notes.removeAt(notes.lastIndex)
            }
            return false
        }

        if (!search(0)) {
            val detail = if (attempts > 6_000) "The automatic search found no clean schedule within its limit."
                else reason ?: "Your available days cannot fit all selected workouts and their recovery."
            throw PlanConstraint("$detail Open another training day, move a fixed support session, or reduce support frequency. No track was activated.")
        }
        picked.forEach { (_, draft) -> PlanScheduleValidation.validatePlan(draft.copy(status = "approved")) }
        return HybridPlanPreview(id, picked.toList(), active.associate { it.id to it.revision }, notes.distinct())
    }

    fun prepareActivation(preview: HybridPlanPreview, owner: String, existing: List<PlanSnapshot>): List<PlanSnapshot> {
        if (preview.drafts.size !in 2..6 || preview.drafts.map { it.first }.distinct().size != preview.drafts.size ||
            preview.drafts.map { it.second.id }.distinct().size != preview.drafts.size
        ) throw PlanConstraint("The Blueprint no longer contains a complete set of training tracks.")
        val active = existing.filter { it.owner == owner && it.status == "approved" }
        if (preview.peers != active.associate { it.id to it.revision })
            throw PlanConstraint("Your active calendar changed. Generate a new Blueprint before activating.")
        val approved = mutableListOf<PlanSnapshot>()
        for ((_, draft) in preview.drafts) {
            if (draft.owner != owner || existing.any { it.id == draft.id })
                throw PlanConstraint("This Blueprint is no longer available for this profile.")
            approved += PlanOperations.approve(draft, active + approved).copy(revision = draft.revision + 1)
        }
        // Also prove that the same set can be exported and reread without a format change.
        PlanBackup.encode(approved, emptyList(), owner)
        PlanScheduleValidation.validateRestoration(active + approved)
        return approved
    }

    private fun patterns(days: Set<Int>, count: Int): List<List<Int>> {
        val eligible = days.sorted()
        return (1 until (1 shl eligible.size)).filter { Integer.bitCount(it) == count }.map { mask ->
            eligible.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
        }.sortedWith(compareBy<List<Int>> { pattern ->
            val gaps = pattern.zipWithNext { a, b -> b - a } + (pattern.first() + 7 - pattern.last())
            gaps.sumOf { (it * count - 7) * (it * count - 7) }
        }.thenBy { pattern -> kotlin.math.abs(pattern.average() - 4.0) })
    }

    private fun scheduled(plans: List<PlanSnapshot>) = plans.flatMap { plan ->
        plan.sessions.filter { it.status in setOf("planned", "in_progress") }
    }

    private fun harder(session: PlannedSession) = session.quality ||
        session.kind == "long" || RunnerSupportSchedule.tier(session) in setOf(
            RunnerSupportSchedule.Tier.HEAVY_LOWER, RunnerSupportSchedule.Tier.PLYOMETRICS
        )

    private fun fit(draft: PlanSnapshot, peers: List<PlanSnapshot>, allowPairs: Boolean): Pair<PlanSnapshot?, String?> {
        val nearby = scheduled(peers)
        val byDate = nearby.groupBy { it.date }
        val keys = RunnerSupportSchedule.keyRuns(RunnerSupportSchedule.runningPlans(draft.owner, peers))
        val rows = mutableListOf<PlannedSession>()
        for (original in draft.sessions) {
            val date = original.date
            val same = byDate[date].orEmpty()
            if (same.size >= 2) return null to "$date already has two workouts."
            val peer = same.singleOrNull()
            if (peer != null) {
                if (trainingTrack(peer) == trainingTrack(original))
                    return null to "$date already has a ${trainingTrackLabel(trainingTrack(original))} workout."
                if (!allowPairs) return null to "$date needs two sessions. Allow supported pairs or choose a different day."
                val running = listOf(peer, original).singleOrNull { it.discipline == "run" }
                val support = listOf(peer, original).firstOrNull { it.discipline != "run" }
                val reviewedPair = running != null && support != null && original.discipline != "run" &&
                    RunnerSupportSchedule.supports(draft) &&
                    RunnerSupportSchedule.placement(support, date, keys)?.afterQuality == true
                if (harder(peer) && harder(original) && !reviewedPair ||
                    running?.kind == "long" && support?.lower == true
                ) return null to "$date pairs two demanding workouts without enough recovery."
            }
            if (nearby.any { other ->
                    kotlin.math.abs(ChronoUnit.DAYS.between(other.date, date)) == 1L &&
                        harder(other) && harder(original)
                }) return null to "$date is adjacent to another demanding workout."
            val limits = (peers.filter { plan -> plan.sessions.any { it.date == date } } + draft)
                .filter { it.request.timeBudgetEnabled }
            val cap = limits.minOfOrNull { it.request.dailyMinutes }
            val used = same.sumOf { it.effectiveMinutes }
            val row = if (cap != null && used + original.minutes > cap &&
                RunnerSupportSchedule.supports(draft) && original.runnerSupportDose == null
            ) RunnerSupportDose.LIGHT.apply(original, draft.request) else original
            if (cap != null && used + row.minutes > cap)
                return null to "$date exceeds your $cap-minute daily limit even with a smaller support dose."
            rows += row
        }
        return draft.copy(sessions = rows) to null
    }
}

internal fun coachBuilderError(message: String): String = when {
    message.startsWith("QUALITY_RECONCILIATION_UNSAFE_OR_INFEASIBLE") ->
        "The requested quality session still needs more room for warm-up, work and cool-down at the smallest safe dose. Try more time, a different quality day or fewer hard sessions. To switch to aerobic running or strides, choose that goal explicitly and review a new plan."
    message.startsWith("ENGINE15_COMBINED_SCHEDULE_CONFLICT") ->
        "These workouts need more room. Open an available day, move a fixed support session, or allow a compatible pair. Keep your long run and recovery days protected."
    else -> message
}
