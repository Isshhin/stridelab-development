package io.stridelab.privatealpha

/**
 * Read-only integration bridge for known Engine 1.0 plans.
 * Deliberately does NOT write back to PlanSnapshot or masquerade as durable approval.
 *
 * Engine 1.0 schema v1 has no persisted Engine2 applied-proposal receipt.
 * A transactional storage/revision adapter is required before enabling "Approve"
 * in the native UI; using copy(...) on a cached plan would be unsafe.
 */
internal object Engine2PlanBridge {
    fun preview(source: PlanSnapshot): Engine2PlanView {
        require(Engine1Engines.canManage(source)) {
            "Unknown source engine or policy; keep the original saved plan unchanged."
        }
        require(source.revision > 0)
        val status = when (source.status) {
            "approved" -> Engine2PlanStatus.APPROVED
            "paused" -> Engine2PlanStatus.PAUSED
            "draft" -> Engine2PlanStatus.DRAFT
            else -> throw IllegalArgumentException("Unsupported plan status: " + source.status)
        }
        val converted = source.sessions.map { session ->
            val discipline = when (session.discipline) {
                "run" -> Engine2Discipline.RUNNING
                "str" -> Engine2Discipline.STRENGTH
                "cal" -> Engine2Discipline.CALISTHENICS
                "cond" -> Engine2Discipline.CONDITIONING
                else -> throw IllegalArgumentException("Unsupported discipline: " + session.discipline)
            }
            val state = when (session.status) {
                "planned" -> Engine2SessionStatus.PLANNED
                "in_progress" -> Engine2SessionStatus.IN_PROGRESS
                "completed" -> Engine2SessionStatus.COMPLETED
                "skipped" -> Engine2SessionStatus.SKIPPED
                "missed" -> Engine2SessionStatus.MISSED
                else -> throw IllegalArgumentException("Unsupported workout state: " + session.status)
            }
            Engine2SessionTarget(
                session.id, session.date, discipline, session.effectiveMinutes, state
            )
        }
        return Engine2PlanView(
            source.owner, source.id, source.revision.toLong(), status, converted
        )
    }
}
