package io.stridelab.privatealpha

import java.time.LocalDate

/**
 * Review-only availability changes. An unassigned session retains its last planned
 * date as provenance but is not an active calendar placement until moved explicitly.
 */
internal object Engine15Availability {
    data class Impact(
        val affectedSessionIds:List<String>,
        val newlyAvailableDays:Set<Int>,
        val newlyUnavailableDays:Set<Int>
    )

    fun preview(plan:PlanSnapshot,days:Set<Int>):Impact {
        if(plan.engineVersion!=ENGINE15_VERSION || !Engine1Engines.canManage(plan))
            throw PlanConstraint("Availability editing requires a supported Engine 1.5 plan.")
        if(plan.status !in setOf("approved","paused","draft"))
            throw PlanConstraint("Archived plans cannot change training availability.")
        if(days.isEmpty() || days.any { it !in 1..7 })
            throw PlanConstraint("Choose at least one available weekday.")
        val blocked=plan.request.lockedSessionDays.filterValues { raw ->
            LocalDate.parse(raw).dayOfWeek.value !in days
        }
        if(blocked.isNotEmpty())
            throw PlanConstraint("A locked workout falls on a newly unavailable day. Unlock or review the lock first.")
        val active=plan.sessions.filter { it.date.dayOfWeek.value !in days }
        if(active.any { it.status=="in_progress" })
            throw PlanConstraint("An in-progress workout occupies an unavailable day. Finish or review it first.")
        val displaced=active.filter { it.status=="planned" }.map { it.id }
        return Impact(displaced,days-plan.request.days,plan.request.days-days)
    }

    /** Validate approved schedules and restored snapshots against their current availability. */
    fun requireReady(plan:PlanSnapshot) {
        if(plan.engineVersion!=ENGINE15_VERSION)return
        if(plan.sessions.any { it.status=="unassigned" })
            throw PlanConstraint("Place or review every unassigned workout before approving this plan.")
        if(plan.sessions.any { it.status in setOf("planned","in_progress") &&
                it.date.dayOfWeek.value !in plan.request.days })
            throw PlanConstraint("A planned workout falls outside approved availability.")
        if(plan.request.runDays>0 && plan.request.longDay !in plan.request.days)
            throw PlanConstraint("Choose a new available long-run day before approving.")
        if(plan.request.qualityCount>0 &&
            (plan.request.qualityDays.size<plan.request.qualityCount ||
                plan.request.qualityDays.any {
                    it !in plan.request.days || it==plan.request.longDay
                }))
            throw PlanConstraint("Review preferred quality days before approving.")
    }
}
