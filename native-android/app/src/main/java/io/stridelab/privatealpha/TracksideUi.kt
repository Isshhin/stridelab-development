package io.stridelab.privatealpha

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * StrideLab app-shell palette, based on the current native Android interface:
 * AMOLED black, neutral charcoal surfaces, lime primary actions.
 *
 * Workout disciplines retain their own informational accents. Presentation
 * tokens must never affect training, persistence, or recorded metrics.
 */
internal object TracksidePalette {
    val backgroundTop = Color(0xFF000000)
    val backgroundBottom = Color(0xFF000000)
    val surface = Color(0xFF121212)
    val surfaceRaised = Color(0xFF1A1D21)
    val surfaceLow = Color(0xFF000000)
    val surfaceVariant = Color(0xFF181A1D)
    val text = Color(0xFFF5F7FA)
    val textMuted = Color(0xFFA0A8B3)
    val lime = Color(0xFFD8FB70)
    // Compatibility alias for callers not yet renamed; its rendered color is lime.
    val red = lime
    val outline = Color(0xFF1F242B)
    val running = lime
    val strength = Color(0xFFFF8A77)
    val calisthenics = Color(0xFFC8A8FF)
    val recovery = Color(0xFF9CD6C5)
}

/** Presentation-only discipline accents; never interpreted as training intensity. */
internal fun tracksideDisciplineColor(discipline: String): Color = when (discipline.lowercase()) {
    "run", "running" -> TracksidePalette.running
    "str", "strength" -> TracksidePalette.strength
    "cal", "calisthenics", "body", "bodyweight" -> TracksidePalette.calisthenics
    "mob", "mobility", "recovery" -> TracksidePalette.recovery
    else -> disciplineColor(discipline)
}
