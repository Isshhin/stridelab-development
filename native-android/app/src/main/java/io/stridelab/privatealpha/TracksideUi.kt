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
 * Trackside 3.0 foundation, derived from the approved native Figma review.
 *
 * Use a single palette for the app shell; workout domains retain their own accent colors.
 * This file contains presentation values only and must not change persisted plan data.
 */
internal object TracksidePalette {
    val backgroundTop = Color(0xFF0B141B)
    val backgroundBottom = Color(0xFF04080C)
    val surface = Color(0xFF101B22)
    val surfaceRaised = Color(0xFF25323D)
    val surfaceLow = Color(0xFF081219)
    val surfaceVariant = Color(0xFF1D303D)
    val text = Color(0xFFF4F8FA)
    val textMuted = Color(0xFFB7C5CF)
    val red = Color(0xFFFF344E)
    val outline = Color(0xFF677987)
    val running = red
    val strength = Color(0xFF5799F5)
    val calisthenics = Color(0xFFAE66F0)
    val recovery = Color(0xFF75C3B8)
}

/** Figma 551:6, 602:3, 732:3: presentation colors only, never training intensity. */
internal fun tracksideDisciplineColor(discipline: String): Color = when (discipline.lowercase()) {
    "run", "running" -> TracksidePalette.running
    "str", "strength" -> TracksidePalette.strength
    "cal", "calisthenics" -> TracksidePalette.calisthenics
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
