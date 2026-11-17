package io.stridelab.privatealpha

/** Converts Plan Builder steps into the existing versioned workout contract. */
internal object Engine15UserWorkoutAuthoring {
    private val families=setOf("Threshold","Tempo","Intervals","Hills","Fartlek","Progression","Strides")

    fun build(family:String,steps:List<RunningStepV2>):RunningPrescription {
        if(family !in families) throw PlanConstraint("Choose a supported workout family.")
        if(steps.size !in 3..50 || steps.firstOrNull()?.purpose!="WARM_UP" ||
            steps.lastOrNull()?.purpose!="COOL_DOWN" || steps.count { it.purpose=="WORK" }<1 ||
            steps.drop(1).dropLast(1).any { it.purpose in setOf("WARM_UP","COOL_DOWN") })
            throw PlanConstraint("A structured workout needs warm-up, at least one work step, and cooldown in order.")
        val work=steps.indices.filter { steps[it].purpose=="WORK" }
        for((index,step) in steps.withIndex()) {
            if(step.purpose=="RECOVERY" && (work.none { it<index } || work.none { it>index }))
                throw PlanConstraint("Recovery steps belong between work repetitions.")
            if(step.purpose=="TRANSITION" && (work.none { it<index } || work.none { it>index }))
                throw PlanConstraint("A transition belongs between work blocks.")
        }
        if(steps.none { it.seconds!=null }) {
            return RunningPrescription("QUALITY",family,steps.map {
                RunningSegment(it.purpose,checkNotNull(it.meters),it.effort)
            })
        }
        if(steps.any { it.seconds!=null && it.distanceEquivalentMeters==null })
            throw PlanConstraint("Enter a distance equivalent for each timed step so the workout can reconcile to weekly mileage.")
        val total=steps.sumOf { it.meters ?: it.distanceEquivalentMeters!! }
        if(total !in 1..120000)
            throw PlanConstraint("The complete workout distance must be at most 120 km.")
        return RunningPrescription("QUALITY",family,emptyList(),steps,
            reconciledDistanceMeters=total)
    }
}
