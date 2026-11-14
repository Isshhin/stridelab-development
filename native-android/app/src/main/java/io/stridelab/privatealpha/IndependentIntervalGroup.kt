package io.stridelab.privatealpha

import org.json.JSONObject

/** Repeats work with recovery only between repetitions; isolated from saved plans. */
internal data class IndependentIntervalGroup(val repetitions: Int, val work: WorkoutStep, val recovery: WorkoutStep) {
    init {
        require(repetitions in 2..100 && work.purpose == WorkoutPurpose.WORK &&
            recovery.purpose == WorkoutPurpose.RECOVERY && work.repetitions == 1 && recovery.repetitions == 1)
        require((work.amount * repetitions).isFinite() && (recovery.amount * (repetitions - 1)).isFinite())
    }

    fun total(unit: WorkoutUnit): Double? {
        if (work.unit != unit || recovery.unit != unit) return null
        return (work.amount * repetitions + recovery.amount * (repetitions - 1)).also { require(it.isFinite()) }
    }

    fun encode() = JSONObject().put("type", "interval_group").put("version", 1)
        .put("repetitions", repetitions).put("work", work.encode()).put("recovery", recovery.encode())

    companion object {
        fun decode(json: JSONObject): IndependentIntervalGroup {
            require(json.getString("type") == "interval_group" && json.get("version") == 1)
            val repetitions = json.get("repetitions")
            require(repetitions is Int)
            return IndependentIntervalGroup(repetitions, WorkoutStep.decode(json.getJSONObject("work")),
                WorkoutStep.decode(json.getJSONObject("recovery")))
        }
    }
}
