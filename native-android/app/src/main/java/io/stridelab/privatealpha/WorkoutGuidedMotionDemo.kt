package io.stridelab.privatealpha

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

internal enum class GuidedMotionPattern {
    PUSH,
    PULL,
    SQUAT,
    HINGE,
    CORE_HOLD,
    JUMP_PLYO,
    RUN_STRIDE,
    MOBILITY_BREATH
}

internal object GuidedMotionClassifier {
    fun classify(exerciseName: String, category: WorkoutHubCategory): GuidedMotionPattern {
        val n = exerciseName.lowercase()
        return when {
            "breath" in n || "stretch" in n || "pigeon" in n || "child" in n ||
                "thoracic" in n || "cat-cow" in n || "ankle rock" in n || "hip switch" in n ||
                "windmill" in n || "circle" in n || "alphabet" in n || "legs up" in n ||
                "dislocate" in n || "floss" in n || "pry" in n -> GuidedMotionPattern.MOBILITY_BREATH
            "jump" in n || "hop" in n || "bound" in n || "skip" in n || "burpee" in n ||
                "thrust" in n || "jack" in n || "bounce" in n -> GuidedMotionPattern.JUMP_PLYO
            "run" in n || "stride" in n || "jog" in n || "sprint" in n || "walk" in n ||
                "shuffle" in n || "climber" in n || "shadow" in n -> GuidedMotionPattern.RUN_STRIDE
            "plank" in n || "hollow" in n || "l-sit" in n || "wall sit" in n || "hold" in n ||
                "dead bug" in n || "arch body" in n || "carry" in n || "stance" in n -> GuidedMotionPattern.CORE_HOLD
            "pull" in n || "row" in n || "hang" in n -> GuidedMotionPattern.PULL
            "push" in n || "press" in n || "dip" in n || "inchworm" in n -> GuidedMotionPattern.PUSH
            "deadlift" in n || "hinge" in n || "swing" in n || "bridge" in n || "clean" in n -> GuidedMotionPattern.HINGE
            "squat" in n || "lunge" in n || "step-up" in n || "calf raise" in n -> GuidedMotionPattern.SQUAT
            else -> when (category) {
                WorkoutHubCategory.RUNNING -> GuidedMotionPattern.RUN_STRIDE
                WorkoutHubCategory.STRENGTH -> GuidedMotionPattern.SQUAT
                WorkoutHubCategory.CALISTHENICS -> GuidedMotionPattern.PUSH
                WorkoutHubCategory.CONDITIONING -> GuidedMotionPattern.JUMP_PLYO
                WorkoutHubCategory.PLYOMETRICS -> GuidedMotionPattern.JUMP_PLYO
                WorkoutHubCategory.ISOMETRICS -> GuidedMotionPattern.CORE_HOLD
                WorkoutHubCategory.MOBILITY, WorkoutHubCategory.RECOVERY -> GuidedMotionPattern.MOBILITY_BREATH
            }
        }
    }
}

@Composable
internal fun WorkoutGuidedMotionCanvas(
    pattern: GuidedMotionPattern,
    accentColor: Color,
    paused: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val phase = if (paused || reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "guided-motion")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "guided-phase"
        )
        animated
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        val dim = accentColor.copy(alpha = 0.28f)
        val floorY = h * 0.82f

        drawLine(
            color = StrideLabSurfaceTokens.hairline,
            start = Offset(w * 0.14f, floorY),
            end = Offset(w * 0.86f, floorY),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        when (pattern) {
            GuidedMotionPattern.PUSH -> {
                val dip = phase * h * 0.22f
                val shoulder = Offset(w * 0.62f, h * 0.42f + dip)
                val hip = Offset(w * 0.40f, h * 0.56f + dip * 0.6f)
                val feet = Offset(w * 0.20f, floorY - 4f)
                val hands = Offset(w * 0.64f, floorY - 4f)
                drawLine(accentColor, feet, hip, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, hip, shoulder, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, shoulder, hands, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(accentColor, radius = 6.dp.toPx(), center = Offset(shoulder.x + 12.dp.toPx(), shoulder.y - 6.dp.toPx()))
            }
            GuidedMotionPattern.PULL -> {
                val lift = phase * h * 0.24f
                drawLine(dim, Offset(w * 0.24f, h * 0.20f), Offset(w * 0.76f, h * 0.20f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val headY = h * 0.46f - lift
                val hipY = h * 0.70f - lift
                drawCircle(accentColor, radius = 6.dp.toPx(), center = Offset(w * 0.50f, headY))
                drawLine(accentColor, Offset(w * 0.50f, headY + 8.dp.toPx()), Offset(w * 0.50f, hipY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, Offset(w * 0.38f, h * 0.20f), Offset(w * 0.46f, headY + 10.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, Offset(w * 0.62f, h * 0.20f), Offset(w * 0.54f, headY + 10.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            GuidedMotionPattern.SQUAT -> {
                val drop = phase * h * 0.20f
                val hip = Offset(w * 0.42f, h * 0.50f + drop)
                val knee = Offset(w * 0.58f, h * 0.64f + drop * 0.25f)
                val ankle = Offset(w * 0.50f, floorY - 4f)
                val shoulder = Offset(w * 0.48f, h * 0.28f + drop)
                drawLine(accentColor, ankle, knee, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, knee, hip, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, hip, shoulder, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(accentColor, radius = 6.dp.toPx(), center = Offset(shoulder.x, shoulder.y - 10.dp.toPx()))
            }
            GuidedMotionPattern.HINGE -> {
                val hinge = phase * w * 0.14f
                val ankle = Offset(w * 0.48f, floorY - 4f)
                val hip = Offset(w * 0.44f - hinge * 0.5f, h * 0.52f)
                val shoulder = Offset(w * 0.48f + hinge, h * 0.28f + phase * h * 0.16f)
                drawLine(accentColor, ankle, hip, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, hip, shoulder, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(accentColor, radius = 6.dp.toPx(), center = Offset(shoulder.x + 6.dp.toPx(), shoulder.y - 8.dp.toPx()))
            }
            GuidedMotionPattern.CORE_HOLD -> {
                val pulse = sin(phase * Math.PI).toFloat() * 3.dp.toPx()
                val feet = Offset(w * 0.22f, floorY - 4f)
                val hip = Offset(w * 0.48f, h * 0.64f - pulse)
                val shoulder = Offset(w * 0.72f, h * 0.58f - pulse)
                val elbow = Offset(w * 0.72f, floorY - 4f)
                drawLine(accentColor, feet, hip, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, hip, shoulder, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, shoulder, elbow, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(accentColor, radius = 5.5.dp.toPx(), center = Offset(shoulder.x + 12.dp.toPx(), shoulder.y - 2.dp.toPx()))
            }
            GuidedMotionPattern.JUMP_PLYO -> {
                val air = phase * h * 0.26f
                val footY = floorY - 6.dp.toPx() - air
                val hipY = footY - h * 0.22f
                val headY = hipY - h * 0.18f
                drawCircle(accentColor, radius = 6.dp.toPx(), center = Offset(w * 0.50f, headY))
                drawLine(accentColor, Offset(w * 0.50f, headY + 8.dp.toPx()), Offset(w * 0.50f, hipY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, Offset(w * 0.50f, hipY), Offset(w * 0.44f, footY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, Offset(w * 0.50f, hipY), Offset(w * 0.56f, footY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            GuidedMotionPattern.RUN_STRIDE -> {
                val swing = (phase - 0.5f) * w * 0.22f
                val hip = Offset(w * 0.50f, h * 0.52f)
                val shoulder = Offset(w * 0.54f, h * 0.30f)
                drawCircle(accentColor, radius = 6.dp.toPx(), center = Offset(shoulder.x + 2.dp.toPx(), shoulder.y - 10.dp.toPx()))
                drawLine(accentColor, hip, shoulder, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, hip, Offset(w * 0.50f + swing, floorY - 6.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(accentColor, hip, Offset(w * 0.50f - swing, floorY - 10.dp.toPx()), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            GuidedMotionPattern.MOBILITY_BREATH -> {
                val r1 = w * (0.12f + phase * 0.10f)
                val r2 = w * (0.20f + phase * 0.12f)
                val center = Offset(w * 0.50f, h * 0.48f)
                drawCircle(dim, radius = r2, center = center, style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(accentColor, radius = r1, center = center, style = stroke)
                val arc = Path().apply {
                    moveTo(w * 0.24f, h * 0.52f)
                    cubicTo(w * 0.40f, h * 0.30f - phase * 10.dp.toPx(), w * 0.60f, h * 0.30f - phase * 10.dp.toPx(), w * 0.76f, h * 0.52f)
                }
                drawPath(arc, color = accentColor.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
