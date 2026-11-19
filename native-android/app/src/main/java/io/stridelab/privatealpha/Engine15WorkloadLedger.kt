package io.stridelab.privatealpha

import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

/**
 * R0 diagnostic only. Records executed stage boundaries and the policy
 * ceilings considered there, without changing any prescription.
 *
 * A stage's input/output difference is not additive across independent
 * ceilings. Q2 timed support steps yield estimated, not exact, distances.
 * No record is automatically persisted or uploaded.
 */
internal data class Engine15WorkloadEvent(
    val week:Int,
    val stage:String,
    val ruleId:String,
    val inputKm:Double?,
    val limitKm:Double?,
    val outputKm:Double?,
    val detail:String=""
)

internal class Engine15WorkloadLedger {
    private val events=mutableListOf<Engine15WorkloadEvent>()
    val entries:List<Engine15WorkloadEvent> get()=events.toList()

    fun record(
        week:Int,
        stage:String,
        ruleId:String,
        inputKm:Double?,
        limitKm:Double?,
        outputKm:Double?,
        detail:String=""
    ) {
        require(week>=0 && stage.isNotBlank() && ruleId.isNotBlank())
        require(listOfNotNull(inputKm,limitKm,outputKm).all { it.isFinite() && it>=0.0 })
        events+=Engine15WorkloadEvent(week,stage,ruleId,inputKm,limitKm,outputKm,detail)
    }

    fun recordSessions(
        week:Int,
        stage:String,
        ruleId:String,
        sessions:List<PlannedSession>,
        priorKm:Double?,
        detail:String=""
    )=record(week,stage,ruleId,priorKm,null,runningKm(sessions),detail)

    /** Unknown exact and estimated distances stay unknown, never coerced to 0 km. */
    fun runningKm(sessions:List<PlannedSession>):Double? {
        val runs=sessions.filter { it.discipline=="run" }
        if(runs.isEmpty())return 0.0
        val distances=runs.map { it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm }
        return if(distances.any { it==null || !it.isFinite() })null
            else distances.filterNotNull().sum()
    }

    data class WeekSummary(
        val week:Int,
        val baselineKm:Double?,
        val ceilingKm:Double?,
        val assignedKm:Double?,
        val deliveredKm:Double?,
        val targetKm:Double?,
        val gapToTargetKm:Double?,
        val unusedCeilingKm:Double?,
        val estimateInFinal:Boolean,
        val note:String
    )

    fun summaries(request:PlanRequest,plan:PlanSnapshot):List<WeekSummary> =
        (0 until request.weeks).map { week ->
            val eventsForWeek=events.filter { it.week==week }
            val sessions=plan.sessions.filter { it.discipline=="run" &&
                ChronoUnit.DAYS.between(request.start,it.date)/7L==week.toLong() }
            val baseline=eventsForWeek.firstOrNull { it.ruleId=="ENGINE1_BASELINE_OUTPUT" }?.outputKm
            val ceiling=eventsForWeek.lastOrNull { it.ruleId=="ENGINE15_RECOVERY_CAP" ||
                it.ruleId=="ENGINE15_WEEKLY_CEILING" }?.outputKm
            val assigned=eventsForWeek.lastOrNull { it.ruleId=="DISTANCE_ROUNDING" }?.outputKm
            val delivered=runningKm(sessions)
            val gap=if(delivered!=null && request.targetWeeklyKm!=null)
                max(0.0,request.targetWeeklyKm-delivered) else null
            val unallocated=if(ceiling!=null && delivered!=null)
                max(0.0,ceiling-delivered) else null
            WeekSummary(
                week,baseline,ceiling,assigned,delivered,request.targetWeeklyKm,
                gap,unallocated,sessions.any { it.runningPrescription?.mixedSegments!=null },
                if(delivered==null) "UNKNOWN_TIME_ONLY"
                else if(sessions.any { it.runningPrescription?.mixedSegments!=null }) "PLANNING_PACE_ESTIMATE"
                else "EXACT_DISTANCE_TARGETS"
            )
        }

    private fun csv(value:String):String="\""+value.replace("\"","\"\"")+"\""
    private fun number(value:Double?):String=value?.let {
        String.format(Locale.US,"%.3f",it)
    } ?: ""

    fun eventCsv(profile:String):String=buildString {
        appendLine("profile,week,stage,rule_id,input_km,limit_km,output_km,delta_km,detail")
        events.forEach { e ->
            val delta=if(e.inputKm!=null && e.outputKm!=null)e.outputKm-e.inputKm else null
            appendLine(listOf(csv(profile),(e.week+1).toString(),csv(e.stage),
                csv(e.ruleId),number(e.inputKm),number(e.limitKm),
                number(e.outputKm),number(delta),csv(e.detail)).joinToString(","))
        }
    }

    fun summaryCsv(profile:String,request:PlanRequest,plan:PlanSnapshot):String=buildString {
        appendLine("profile,week,recent_km,requested_target_km,baseline_km,ceiling_km,allocated_km,delivered_km,gap_to_target_km,unused_ceiling_km,output_basis")
        summaries(request,plan).forEach { s ->
            appendLine(listOf(csv(profile),(s.week+1).toString(),number(request.weeklyKm),
                number(s.targetKm),number(s.baselineKm),number(s.ceilingKm),
                number(s.assignedKm),number(s.deliveredKm),number(s.gapToTargetKm),
                number(s.unusedCeilingKm),csv(s.note)).joinToString(","))
        }
    }
}
