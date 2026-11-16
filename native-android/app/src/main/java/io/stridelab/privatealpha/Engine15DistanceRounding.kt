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
        require(allocatedTenths.sum() <= maximum &&
            allocatedTenths.indices.all { allocatedTenths[it] <= cap[it] })

        val rounded=IntArray(allocatedTenths.size) { i ->
            val original=allocatedTenths[i]
            if(i in fixedIndices || original<5)original else (original/5)*5
        }
        fun longShareOk():Boolean =
            longIndex<0 || rounded.size<3 ||
                rounded[longIndex]*5 <= rounded.sum()*2

        // Lowering other runs onto the half-km grid must not accidentally
        // increase the long run's share above 40%.
        if(!longShareOk()) {
            val original=allocatedTenths.copyOf()
            if(allocatedTenths[longIndex]*5 <= allocatedTenths.sum()*2) {
                // Keep the original bounded allocation if the requested grid
                // cannot represent it safely. Never weaken long-run safety.
                return original
            }
        }
        val candidates=allocatedTenths.indices
            .filter { it !in fixedIndices && allocatedTenths[it]>=5 && allocatedTenths[it]%5!=0 }
            .sortedWith(compareByDescending<Int> { allocatedTenths[it]%5 }
                .thenBy { it })
        for(i in candidates) {
            val next=rounded[i]+5
            if(next>cap[i] || rounded.sum()+5>maximum)continue
            rounded[i]=next
            if(!longShareOk()) rounded[i]-=5
        }
        return rounded
    }

    fun roundUpHalfKm(km:Double):Double {
        require(km.isFinite() && km>=0.0)
        return kotlin.math.ceil(km*2.0-1e-9)/2.0
    }
}
