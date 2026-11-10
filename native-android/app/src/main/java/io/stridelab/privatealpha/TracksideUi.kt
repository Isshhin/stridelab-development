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
    val backgroundTop = Color(0xFF080B0A)
    val backgroundBottom = Color(0xFF000000)
    val surface = Color(0xFF111315)
    val surfaceRaised = Color(0xFF202324)
    val surfaceLow = Color(0xFF080B0A)
    val surfaceVariant = Color(0xFF181B1A)
    val text = Color(0xFFF7F7F5)
    val textMuted = Color(0xFF9FA4AA)
    val lime = Color(0xFFD8FB70)
    // Compatibility alias for callers not yet renamed; its rendered color is lime.
    val red = lime
    val outline = Color(0xFF303438)
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

/** Consistent compact navigation glyphs. Accessible labels live on the tab itself. */
@Composable
internal fun TracksideActivityIcon(modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val p = listOf(
            Offset(size.width * 0.07f, size.height * 0.52f),
            Offset(size.width * 0.29f, size.height * 0.52f),
            Offset(size.width * 0.42f, size.height * 0.20f),
            Offset(size.width * 0.59f, size.height * 0.80f),
            Offset(size.width * 0.70f, size.height * 0.49f),
            Offset(size.width * 0.93f, size.height * 0.49f)
        )
        p.zipWithNext().forEach { (start, end) ->
            drawLine(tint, start, end, strokeWidth = 1.9.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun TracksideLibraryIcon(modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val book = Path().apply {
            moveTo(size.width * 0.16f, size.height * 0.12f)
            lineTo(size.width * 0.84f, size.height * 0.12f)
            lineTo(size.width * 0.84f, size.height * 0.88f)
            lineTo(size.width * 0.16f, size.height * 0.88f)
            quadraticTo(size.width * 0.06f, size.height * 0.76f, size.width * 0.16f, size.height * 0.68f)
            close()
            moveTo(size.width * 0.16f, size.height * 0.68f)
            lineTo(size.width * 0.84f, size.height * 0.68f)
        }
        drawPath(book, tint, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun TracksideMoreIcon(modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        listOf(0.2f, 0.5f, 0.8f).forEach { x ->
            drawCircle(tint, radius = 1.9.dp.toPx(), center = Offset(size.width * x, size.height / 2))
        }
    }
}
