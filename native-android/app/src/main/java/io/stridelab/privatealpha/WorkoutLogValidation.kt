package io.stridelab.privatealpha

import java.time.LocalDate

/** Validate actuals without changing prescriptions or inferring performance from targets. */
internal object WorkoutLogValidation {
    fun validate(session:PlannedSession,log:WorkoutLog) {
        fun valid(n:Double?,zero:Boolean=false)=n==null || (n.isFinite() && if(zero) n>=0 else n>0)
        if(!valid(log.minutes) || !valid(log.distanceKm) || (log.effort!=null && log.effort !in 1..10) || log.notes.length>2000)throw PlanConstraint("Check actual duration, distance, effort (1-10), and notes. Blank metrics stay unrecorded.")
        if(log.performedOn!=null && (log.performedOn>LocalDate.now() || log.performedOn<LocalDate.of(1970,1,1)))throw PlanConstraint("Choose the date the workout actually happened, no later than today.")
        if(log.minutes!=null && log.minutes>1440 || log.distanceKm!=null && log.distanceKm>500)throw PlanConstraint("Check the units for actual duration or distance.")
        if(log.sets.map { it.exerciseId to it.set }.distinct().size!=log.sets.size)throw PlanConstraint("Each exercise set can be logged only once.")
        log.sets.forEach { row ->
            val prescription=session.effectiveExercises.find { it.id==row.exerciseId } ?: throw PlanConstraint("This exercise is not in the saved prescription.")
            if(row.set !in 1..prescription.sets || row.reps?.let { it !in 0..500 }==true || row.seconds?.let { it !in 0..3600 }==true || !valid(row.kg,true))throw PlanConstraint("Check the actual set number, repetitions, hold seconds and load.")
        }
    }

    fun hasPerformedSet(session:PlannedSession,log:WorkoutLog)=log.sets.any { row ->
        session.effectiveExercises.find { it.id==row.exerciseId }?.let { prescription ->
            if(prescription.seconds!=null)(row.seconds ?: 0)>0 else prescription.reps!=null && (row.reps ?: 0)>0
        }==true
    }
}
