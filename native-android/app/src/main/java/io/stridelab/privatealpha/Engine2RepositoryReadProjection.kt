package io.stridelab.privatealpha

import java.time.Instant
import java.util.Locale

/**
 * Phase 42: read-only projection of the VERIFIED Engine1 repository state.
 * This adapter never saves a plan, adds a proposal receipt, or modifies
 * TrainingStore. Provenance assumes TrainingState originated from the trusted
 * account-scoped TrainingRepository loader, not an arbitrary caller.
 */
internal object Engine2RepositoryReadProjection {
    fun build(state:TrainingState,asOf:Instant):Engine2ImportResult {
        val owner=state.account ?: throw IllegalArgumentException("No active local account")
        require(owner.isNotBlank() && state.plans.all { it.owner==owner }) {
            "Mixed-account plan state cannot enter the coaching context."
        }
        val workouts=mutableListOf<Engine2IncomingWorkout>()
        val wellness=mutableListOf<Engine2IncomingWellness>()
        val skipped=mutableListOf<Engine2ImportIssue>()
        val cachedIds=state.activities.map { it.id }.toSet()

        fun mapType(raw:String):Engine2Discipline? {
            val type=raw.lowercase(Locale.ROOT).replace(Regex("[ _-]"),"")
            return when(type) {
                "run","running","trailrun","trailrunning","treadmill","virtualrun" ->
                    Engine2Discipline.RUNNING
                "strengthtraining","strength","weighttraining","weightlifting" ->
                    Engine2Discipline.STRENGTH
                "calisthenics" -> Engine2Discipline.CALISTHENICS
                "cycling","biking","rowing","conditioning" -> Engine2Discipline.CONDITIONING
                else -> null // Do not invent a discipline for unknown provider labels.
            }
        }
        for(activity in state.activities) {
            val kind=mapType(activity.type)
            if(kind==null) {
                skipped+=Engine2ImportIssue(activity.id,
                    "Unsupported source activity type retained only in the original cache")
                continue
            }
            val source=Engine2Source(owner,activity.source,activity.id,activity.startedAt)
            val metrics=buildList {
                activity.distanceM?.let {
                    add(Engine2RawMetric(Engine2Metric.DISTANCE_KM,it/1000.0))
                }
                activity.durationS?.let {
                    add(Engine2RawMetric(Engine2Metric.DURATION_SECONDS,it))
                }
                activity.averageHr?.let {
                    add(Engine2RawMetric(Engine2Metric.AVERAGE_HR_BPM,it))
                }
            }
            workouts+=Engine2IncomingWorkout("source:${activity.source}:${activity.id}",
                kind,activity.startedAt,Engine2RecordStatus.CONFIRMED,source,metrics)
        }

        for(plan in state.plans) {
            if(!Engine1Engines.canManage(plan)) {
                skipped+=Engine2ImportIssue(plan.id,
                    "Unknown historical engine/policy preserved as original snapshot, not coached")
                continue
            }
            for(session in plan.sessions) {
                val log=session.log ?: continue
                // A cached imported run linked to a plan is one physical event.
                // Retain its manual log in Engine1 history; do not count it twice here.
                if(session.linkedActivityId!=null && session.linkedActivityId in cachedIds)
                    continue
                val discipline=when(session.discipline) {
                    "run" -> Engine2Discipline.RUNNING
                    "str" -> Engine2Discipline.STRENGTH
                    "cal" -> Engine2Discipline.CALISTHENICS
                    "cond" -> Engine2Discipline.CONDITIONING
                    else -> {
                        skipped+=Engine2ImportIssue(session.id,
                            "Unsupported saved discipline kept in original plan")
                        continue
                    }
                }
                val source=Engine2Source(owner,"native-manual",
                    "${plan.id}:${session.id}:${session.logRevision}",log.at)
                val measurements=buildList {
                    log.minutes?.let { add(Engine2RawMetric(Engine2Metric.DURATION_SECONDS,it*60.0)) }
                    log.distanceKm?.let { add(Engine2RawMetric(Engine2Metric.DISTANCE_KM,it)) }
                    log.sets.forEach { set ->
                        set.reps?.let { add(Engine2RawMetric(Engine2Metric.REPS,it.toDouble(),
                            set.exerciseId,set.set)) }
                        set.kg?.let { add(Engine2RawMetric(Engine2Metric.LOAD_KG,it,
                            set.exerciseId,set.set)) }
                        set.seconds?.let { add(Engine2RawMetric(Engine2Metric.HOLD_SECONDS,
                            it.toDouble(),set.exerciseId,set.set)) }
                    }
                }
                workouts+=Engine2IncomingWorkout("native:${plan.id}:${session.id}",
                    discipline,log.at,if(session.status=="completed")
                        Engine2RecordStatus.CONFIRMED else Engine2RecordStatus.DRAFT,
                    source,measurements)
            }
        }

        for(row in state.wellness) {
            val metric=when(row.type) {
                "resting_hr" -> Engine2Metric.RESTING_HR_BPM
                "sleep_duration" -> Engine2Metric.SLEEP_SECONDS
                // No HRV RMSSD/SDNN method tag exists in the Engine2Metric.HRV_MS enum.
                // Refuse to conflate them; keep the source row unmodified.
                else -> {
                    skipped+=Engine2ImportIssue(row.id,
                        "Wellness method unavailable in Engine2 read projection")
                    continue
                }
            }
            val value=if(metric==Engine2Metric.SLEEP_SECONDS)row.value*3600.0 else row.value
            val source=Engine2Source(owner,row.source,row.id,row.recordedAt)
            wellness+=Engine2IncomingWellness("wellness:${row.source}:${row.id}",
                source,metric,value)
        }
        val selected=state.plans.firstOrNull { Engine1Engines.canManage(it) }
        val result=Engine2ContextAssembler.build(owner,asOf,workouts,wellness,
            equipment=selected?.request?.equipment ?: emptyMap(),
            goals=selected?.request?.disciplineGoals?.values?.toList() ?: emptyList())
        return result.copy(rejected=skipped+result.rejected)
    }
}
