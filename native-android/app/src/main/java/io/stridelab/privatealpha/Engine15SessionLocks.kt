package io.stridelab.privatealpha

import java.time.LocalDate

/** Exact persisted athlete placement constraints, independent of Compose. */
internal object Engine15SessionLocks {
    fun validateRequest(request:PlanRequest) {
        if(request.policyChoice!="engine15" && request.lockedSessionDays.isNotEmpty())
            throw PlanConstraint("Only an Engine 1.5 request supports workout locks.")
        if(request.lockedSessionDays.size>400)
            throw PlanConstraint("Too many workout locks were requested.")
        for((id,raw) in request.lockedSessionDays) {
            if(id.isBlank() || id.length>200)
                throw PlanConstraint("A locked workout has an invalid identifier.")
            val date=try { LocalDate.parse(raw) }
                catch (_:Exception) { throw PlanConstraint("A locked workout has an invalid date.") }
            if(date<request.start || date>=request.start.plusWeeks(request.weeks.toLong()))
                throw PlanConstraint("A locked workout is outside the selected plan.")
            if(date.dayOfWeek.value !in request.days)
                throw PlanConstraint("A locked workout falls on an unavailable day. Unlock or change availability.")
        }
    }

    fun restrictDates(
        request:PlanRequest, session:PlannedSession, eligible:List<LocalDate>
    ):List<LocalDate> {
        val raw=request.lockedSessionDays[session.id] ?: return eligible
        val wanted=LocalDate.parse(raw)
        return eligible.filter { it==wanted }
    }

    fun validateOutput(request:PlanRequest,sessions:List<PlannedSession>) {
        if(request.lockedSessionDays.isEmpty())return
        val byId=sessions.associateBy { it.id }
        for((id,raw) in request.lockedSessionDays) {
            val saved=byId[id] ?: throw PlanConstraint(
                "A locked workout no longer exists in the generated plan. Review its lock.")
            if(saved.date.toString()!=raw)
                throw PlanConstraint("Workout ${saved.title} cannot retain its locked day. Review constraints before regenerating.")
        }
    }

    fun validateSaved(plan:PlanSnapshot) {
        if(plan.engineVersion!=ENGINE15_VERSION)return
        validateRequest(plan.request)
        validateOutput(plan.request,plan.sessions)
    }
}
