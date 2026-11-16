package io.stridelab.privatealpha

/**
 * Planned-distance presentation grid, with constraints expressed in 100 m units.
 *
 * Use half-kilometre targets when the *actual available session and weekly budget*
 * supports them. Never increase a source allocation beyond its upstream session
 * ceiling, silently move kilometres into quality work, or zero out a short run.
 *
 * The half-km preference is subordinate to the original dose limits; short and
 * tightly constrained runs retain their exact original tenths if needed.
 */
internal object Engine15DistanceRounding {
    fun allocate(
        allocatedTenths: IntArray,
        sessionLimitsTenths: IntArray,
        baselineSessionTenths: IntArray,
        weekBudgetTenths: Int,
        longIndex: Int = -1,
        fixedIndices: Set<Int> = emptySet()
    ): IntArray {
        require(weekBudgetTenths >= 0)
        require(allocatedTenths.size == sessionLimitsTenths.size &&
            allocatedTenths.size == baselineSessionTenths.size)
        require(longIndex == -1 || longIndex in allocatedTenths.indices)
        require(fixedIndices.all { it in allocatedTenths.indices })
        require(allocatedTenths.all { it > 0 })
        val cap=IntArray(allocatedTenths.size) { i ->
            minOf(sessionLimitsTenths[i],baselineSessionTenths[i])
        }
        val maximum=minOf(weekBudgetTenths,cap.sum())
        // Preserve the existing engine allocation if an upstream session is
        // already above a source-specific ceiling. Rounding is not a validator
        // and must never make previously valid generation crash.
        if(allocatedTenths.sum()>maximum ||
            allocatedTenths.indices.any { allocatedTenths[it]>cap[it] })
            return allocatedTenths.copyOf()

        // Prefer upward half-kilometre rounding. When the additional metres
        // do not fit the verified weekly/session ceiling, keep the original
        // precise value rather than silently reducing another workout.
        val rounded=allocatedTenths.copyOf()
        fun longShareOk():Boolean =
            longIndex<0 || rounded.size<3 ||
                rounded[longIndex]*5 <= rounded.sum()*2

        val candidates=allocatedTenths.indices
            .filter { it !in fixedIndices && allocatedTenths[it]>=5 &&
                allocatedTenths[it]%5!=0 }
            .sortedWith(compareBy<Int> { 5-allocatedTenths[it]%5 }.thenBy { it })
        for(i in candidates) {
            val next=((allocatedTenths[i]+4)/5)*5
            if(next>cap[i] || rounded.sum()+(next-rounded[i])>maximum)continue
            val before=rounded[i]
            rounded[i]=next
            if(!longShareOk()) rounded[i]=before
        }
        return rounded
    }

    fun roundUpHalfKm(km:Double):Double {
        require(km.isFinite() && km>=0.0)
        return kotlin.math.ceil(km*2.0-1e-9)/2.0
    }
}
