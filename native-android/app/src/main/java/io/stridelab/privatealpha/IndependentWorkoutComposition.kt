package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject

internal sealed interface IndependentWorkoutElement {
    data class Step(val value: WorkoutStep) : IndependentWorkoutElement
    data class Intervals(val value: IndependentIntervalGroup) : IndependentWorkoutElement
}

/** A research-only ordered workout. It does not replace persisted PlannedSession steps. */
internal data class IndependentWorkoutComposition(val elements: List<IndependentWorkoutElement>) {
    init { require(elements.isNotEmpty() && elements.size <= 400) }

    fun total(unit: WorkoutUnit): Double? = sum(unit, null)
    fun dose(unit: WorkoutUnit, purpose: WorkoutPurpose): Double? = sum(unit, purpose)

    private fun sum(unit: WorkoutUnit, purpose: WorkoutPurpose?): Double? {
        var total = 0.0
        fun add(step: WorkoutStep, count: Int): Boolean {
            if (purpose != null && step.purpose != purpose) return true
            if (step.unit != unit) return false
            total += step.amount * count
            require(total.isFinite())
            return true
        }
        for (element in elements) when (element) {
            is IndependentWorkoutElement.Step -> if (!add(element.value, element.value.repetitions)) return null
            is IndependentWorkoutElement.Intervals -> {
                val group = element.value
                if (!add(group.work, group.repetitions) || !add(group.recovery, group.repetitions - 1)) return null
            }
        }
        return total
    }

    fun encode(): JSONObject = JSONObject().put("type", "structured_workout").put("version", 1)
        .put("elements", JSONArray(elements.map { element -> when (element) {
            is IndependentWorkoutElement.Step -> JSONObject().put("kind", "step").put("value", element.value.encode())
            is IndependentWorkoutElement.Intervals -> JSONObject().put("kind", "intervals").put("value", element.value.encode())
        } }))

    companion object {
        fun decode(json: JSONObject): IndependentWorkoutComposition {
            require(json.getString("type") == "structured_workout" && json.get("version") == 1)
            val elements = json.getJSONArray("elements")
            require(elements.length() in 1..400)
            return IndependentWorkoutComposition((0 until elements.length()).map { index ->
                val element = elements.getJSONObject(index)
                when (element.getString("kind")) {
                    "step" -> IndependentWorkoutElement.Step(WorkoutStep.decode(element.getJSONObject("value")))
                    "intervals" -> IndependentWorkoutElement.Intervals(IndependentIntervalGroup.decode(element.getJSONObject("value")))
                    else -> throw IllegalArgumentException("Unsupported workout element")
                }
            })
        }
    }
}
