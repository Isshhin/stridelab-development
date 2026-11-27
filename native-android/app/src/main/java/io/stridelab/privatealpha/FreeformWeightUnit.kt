package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.round

internal enum class FreeformWeightUnit(val shortLabel: String, val unitSuffix: String) {
    KG("KG", "kg"),
    LB("LB", "lb");

    fun toggle(): FreeformWeightUnit = if (this == KG) LB else KG
}

/**
 * Pure display-unit converter for Freeform Logger weights.
 * Canonical persisted storage and training load engines remain strictly in kilograms (kg).
 */
internal object FreeformWeightConverter {
    const val LB_PER_KG = 2.20462262185

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG

    fun lbToKg(lb: Double): Double = lb / LB_PER_KG

    fun formatDisplayValue(value: Double): String {
        if (!value.isFinite()) return ""
        val rounded = round(value * 10.0) / 10.0
        val clean = if (rounded == -0.0) 0.0 else rounded
        return freeformNumber(clean)
    }

    fun kgStringToDisplay(kgRaw: String, unit: FreeformWeightUnit): String {
        if (unit == FreeformWeightUnit.KG) return kgRaw
        if (kgRaw.isBlank()) return ""
        val parsed = kgRaw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return kgRaw
        return formatDisplayValue(kgToLb(parsed))
    }

    fun displayStringToCanonicalKg(displayRaw: String, unit: FreeformWeightUnit): String {
        if (unit == FreeformWeightUnit.KG) return displayRaw
        if (displayRaw.isBlank()) return ""
        val parsed = displayRaw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return displayRaw
        val kg = lbToKg(parsed)
        return freeformNumber(if (kg == -0.0) 0.0 else kg)
    }

    fun formatVolume(volumeKg: Double, unit: FreeformWeightUnit): String {
        val converted = if (unit == FreeformWeightUnit.KG) volumeKg else kgToLb(volumeKg)
        return "${formatDisplayValue(converted)} ${unit.unitSuffix}"
    }

    fun formatMeasureLabel(
        measure: FreeformMeasure,
        discipline: ManualDiscipline,
        unit: FreeformWeightUnit
    ): String {
        if (unit == FreeformWeightUnit.KG) return measure.label(discipline)
        val parsedKg = measure.kg.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        return listOfNotNull(
            measure.quantity.trim().toIntOrNull()?.let {
                "$it ${if (discipline == ManualDiscipline.PLYOMETRICS) "contacts" else "reps"}"
            },
            measure.seconds.trim().toIntOrNull()?.let { "$it s" },
            parsedKg?.let { "${formatDisplayValue(kgToLb(it))} lb" }
        ).joinToString(" · ")
    }

    fun formatWorkoutSummary(workout: FreeformWorkout, unit: FreeformWeightUnit): String {
        if (unit == FreeformWeightUnit.KG || workout.discipline == ManualDiscipline.RUNNING) {
            return workout.summary()
        }
        val performed = workout.exercises.flatMap { exercise ->
            exercise.sets.filter { it.completed && it.validActual(workout.discipline, exercise.format) }
        }
        val quantity = performed.sumOf { it.actual.quantity.trim().toIntOrNull() ?: 0 }
        val seconds = performed.sumOf { it.actual.seconds.trim().toIntOrNull() ?: 0 }
        val volumeKg = performed.sumOf {
            val q = it.actual.quantity.trim().toIntOrNull() ?: 0
            val kg = it.actual.kg.trim().replace(',', '.').toDoubleOrNull()?.takeIf { d -> d.isFinite() } ?: 0.0
            q * kg
        }
        val roundUnit = if (workout.discipline == ManualDiscipline.CONDITIONING) "round" else "set"
        return buildList {
            add("${performed.size} $roundUnit${if (performed.size == 1) "" else "s"}")
            if (quantity > 0) add("$quantity ${if (workout.discipline == ManualDiscipline.PLYOMETRICS) "contacts" else "reps"}")
            if (seconds > 0) {
                add(
                    if (workout.discipline == ManualDiscipline.CONDITIONING) {
                        "${String.format(Locale.ROOT, "%.2f", seconds / 60.0).trimEnd('0').trimEnd('.')} min work"
                    } else {
                        "$seconds s"
                    }
                )
            }
            if (volumeKg > 0) {
                add("${formatVolume(volumeKg, FreeformWeightUnit.LB)} volume")
            }
        }.joinToString(" · ")
    }
}
