package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject

/** Isolated research contract; repetitions are consecutive, not interleaved intervals. */
internal data class IndependentWorkout(val steps: List<WorkoutStep>) {
    init { require(steps.isNotEmpty() && steps.size <= 400) }

    fun encode(): JSONObject = JSONObject().put("version", 1).put("steps", JSONArray(steps.map { it.encode() }))

    /** A total is unknown when even one step uses the other unit. */
    fun total(unit: WorkoutUnit): Double? {
        if (steps.any { it.unit != unit }) return null
        return steps.sumOf { it.amount * it.repetitions }.also { require(it.isFinite()) }
    }

    companion object {
        fun decode(json: JSONObject): IndependentWorkout {
            require(json.get("version") == 1) { "Unsupported workout version" }
            val steps = json.getJSONArray("steps")
            require(steps.length() in 1..400)
            return IndependentWorkout((0 until steps.length()).map { WorkoutStep.decode(steps.getJSONObject(it)) })
        }
    }
}

internal enum class WorkoutPurpose { WARM_UP, WORK, RECOVERY, COOL_DOWN }
internal enum class WorkoutUnit { SECONDS, METERS }
internal enum class WorkoutTarget { EASY, STEADY, THRESHOLD, TEMPO, INTERVALS, HILLS, FARTLEK, PROGRESSION, STRIDES, UNSPECIFIED }

internal data class WorkoutStep(
    val purpose: WorkoutPurpose,
    val unit: WorkoutUnit,
    val amount: Double,
    val repetitions: Int,
    val target: WorkoutTarget
) {
    init {
        require(amount.isFinite() && amount > 0 && repetitions in 1..100)
        require((amount * repetitions).isFinite())
    }

    fun encode(): JSONObject = JSONObject().put("purpose", purpose.name).put("unit", unit.name)
        .put("amount", amount).put("repetitions", repetitions).put("target", target.name)

    companion object {
        fun decode(json: JSONObject): WorkoutStep {
            val amount = json.get("amount")
            val repetitions = json.get("repetitions")
            require(amount is Number && repetitions is Int)
            return WorkoutStep(WorkoutPurpose.valueOf(json.getString("purpose")),
                WorkoutUnit.valueOf(json.getString("unit")), amount.toDouble(), repetitions,
                WorkoutTarget.valueOf(json.getString("target")))
        }
    }
}
