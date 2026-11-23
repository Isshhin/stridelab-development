package io.stridelab.privatealpha

import androidx.compose.animation.core.Spring
import kotlin.math.abs
import kotlin.math.round

/**
 * Shared, interaction-only liquid motion for the Today week picker and
 * floating navigation dock. Motion comes from a spring + tiny elastic
 * deformation, NOT continuous simulation, particles, blur, or shader work.
 */
internal object TracksideLiquidMotion {
    const val DAMPING = 0.76f
    val STIFFNESS = Spring.StiffnessMedium

    /** Remaining distance, normalized to one destination cell (0..1). */
    fun pressure(current: Float, target: Float, cell: Float): Float =
        if (!current.isFinite() || !target.isFinite() || cell <= 0f) 0f
        else (abs(target - current) / cell).coerceIn(0f, 1f)

    /** Temporary deformation while the selected element is in transit. */
    fun stretch(pressure: Float, amplitude: Float): Float =
        1f + pressure.coerceIn(0f, 1f) * amplitude

    fun squash(pressure: Float, amplitude: Float): Float =
        1f - pressure.coerceIn(0f, 1f) * amplitude

    /**
     * A notch is relaxed at the center of each weekday and becomes slightly
     * wider/deeper between days, like the wake behind a moving droplet.
     */
    fun notchWake(normalizedPosition: Float): Float {
        if (!normalizedPosition.isFinite()) return 0f
        val cellPosition = normalizedPosition * 7f - .5f
        return (abs(cellPosition - round(cellPosition)) * 2f).coerceIn(0f, 1f)
    }
}
