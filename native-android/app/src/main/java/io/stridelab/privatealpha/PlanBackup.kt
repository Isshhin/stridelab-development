package io.stridelab.privatealpha

import org.json.JSONObject

/** Validate the whole file before beginning a non-destructive restore transaction. */
internal object PlanBackup {
    fun read(raw:String,owner:String):List<PlanSnapshot> {
        try {
            RecordPolicy.payload(raw)
            val j=JSONObject(raw)
            require(j.getInt("schemaVersion")==1)
            val rows=j.getJSONArray("plans")
            require(rows.length() in 1..50)
            val plans=rows.objects().map { row ->
                val plan=PlanSnapshot.decode(row,owner)
                RecordPolicy.identifier(plan.id)
                require(plan.revision>0 && plan.status in setOf("draft","approved","paused","archived"))
                require(plan.request.weeks in 1..16 && plan.sessions.isNotEmpty())
                require(plan.feedback.size<=1000)
                plan.sessions.forEach { s ->
                    RecordPolicy.identifier(s.id)
                    require(s.date>=plan.request.start && s.date<plan.request.start.plusWeeks(plan.request.weeks.toLong()))
                    require(s.minutes in 0..1440 && s.discipline in setOf("run","str","cal","cond") && s.status in setOf("planned","in_progress","completed","missed","skipped"))
                    require(s.exercises.size<=30 && s.exercises.all { it.sets in 1..20 && (it.reps==null||it.reps in 0..500) && (it.seconds==null||it.seconds in 0..3600) })
                    s.log?.let { log -> require(log.notes.length<=2000 && (log.effort==null||log.effort in 1..10) && (log.minutes==null||log.minutes.isFinite()&&log.minutes>0&&log.minutes<=1440) && (log.distanceKm==null||log.distanceKm.isFinite()&&log.distanceKm>0&&log.distanceKm<=500)) }
                }
                plan
            }
            require(plans.map { it.id }.distinct().size==plans.size)
            require(plans.flatMap { it.sessions }.map { it.id }.distinct().size==plans.sumOf { it.sessions.size })
            return plans
        } catch(_:Exception) { throw PlanConstraint("This backup is invalid, too large, unsupported, or belongs to a different account. No saved records were changed.") }
    }
    fun additions(incoming:List<PlanSnapshot>,existing:List<PlanSnapshot>):List<PlanSnapshot> {
        val additions=incoming.filter { row -> existing.none { it.id==row.id } }
        val links=(existing+additions).flatMap { it.sessions }.mapNotNull { it.linkedActivityId }
        if(links.distinct().size!=links.size)throw PlanConstraint("The backup would link one source run to multiple sessions. No records were changed.")
        val active=existing.filter { it.status=="approved" }+additions.filter { it.status=="approved" }
        for(i in active.indices) for(k in i+1 until active.size) {
            if(active[i].sessions.any { a -> a.status in setOf("planned","in_progress") && active[k].sessions.any { b -> b.status in setOf("planned","in_progress") && Engine1.conflict(a,b) } })throw PlanConstraint("The backup's approved calendar conflicts with a saved approved plan. Pause the saved plan before restoring; no records were changed.")
        }
        return additions
    }
}
