package io.stridelab.privatealpha

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ExerciseCatalogCache {
    @Volatile
    private var cached: List<ExerciseLibraryRecordV2>? = null

    fun peek(): List<ExerciseLibraryRecordV2>? = cached

    fun load(context: Context): List<ExerciseLibraryRecordV2> {
        cached?.let { return it }
        val loaded = runCatching {
            val base = ExerciseLibraryV2.load { context.assets.open(it) }.records
            FreeformExerciseCatalog.augment(base)
        }.getOrElse { FreeformExerciseCatalog.builtinRecords() }
        cached = loaded
        return loaded
    }
}

@Composable
internal fun rememberAugmentedExerciseCatalog(): List<ExerciseLibraryRecordV2> {
    val context = LocalContext.current.applicationContext
    val catalog by produceState(
        initialValue = ExerciseCatalogCache.peek() ?: FreeformExerciseCatalog.builtinRecords(),
        context
    ) {
        value = withContext(Dispatchers.IO) {
            ExerciseCatalogCache.load(context)
        }
    }
    return catalog
}

/**
 * Pure-Compose side-by-side Front & Back vector silhouettes using unified #D8FB70 luminance scale.
 * Side-by-side Front + Back are always visible simultaneously (no Flip toggle).
 */
@Composable
internal fun AnatomicalMuscleMap(
    focus: MuscleFocusMap,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 176.dp
) {
    val summaryDescription = buildString {
        append("Anatomical muscle map, front and back.")
        if (focus.primaryMuscles.isNotEmpty()) {
            append(" Primary: ${focus.primaryMuscles.joinToString(", ")}.")
        }
        if (focus.secondaryMuscles.isNotEmpty()) {
            append(" Secondary: ${focus.secondaryMuscles.joinToString(", ")}.")
        }
    }

    Row(
        modifier = modifier
            .testTag("muscle-map-canvas")
            .semantics { contentDescription = summaryDescription },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyFigureCanvas(
            side = BodySide.FRONT,
            intensities = focus.regionIntensities,
            figureHeight = figureHeight,
            modifier = Modifier.testTag("muscle-map-front")
        )
        BodyFigureCanvas(
            side = BodySide.BACK,
            intensities = focus.regionIntensities,
            figureHeight = figureHeight,
            modifier = Modifier.testTag("muscle-map-back")
        )
    }
}

@Composable
private fun BodyFigureCanvas(
    side: BodySide,
    intensities: Map<AnatomicalMuscleRegion, Float>,
    figureHeight: Dp,
    modifier: Modifier = Modifier
) {
    val figureWidth = figureHeight * (100f / 220f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(figureWidth)
                .height(figureHeight)
        ) {
            val scaleX = size.width / 100f
            val scaleY = size.height / 220f
            val scale = minOf(scaleX, scaleY)
            val offsetX = (size.width - 100f * scale) / 2f
            val offsetY = (size.height - 220f * scale) / 2f

            fun px(nx: Float) = offsetX + nx * scale
            fun py(ny: Float) = offsetY + ny * scale

            // Base silhouette (head, hands, knees/calves backdrop)
            drawHeadAndSilhouetteBase(side, ::px, ::py, scale)

            // All anatomical muscle regions on this BodySide
            val regions = if (side == BodySide.FRONT) FRONT_REGIONS else BACK_REGIONS
            for (region in regions) {
                val intensity = intensities[region]?.coerceIn(0f, 1f) ?: 0f
                val fillColor = if (intensity > 0f) {
                    val alpha = (0.28f + intensity * 0.67f).coerceIn(0.28f, 0.95f)
                    StrideLabSurfaceTokens.lime.copy(alpha = alpha)
                } else {
                    StrideLabSurfaceTokens.surfaceSubtle
                }
                val strokeColor = if (intensity >= 0.7f) {
                    StrideLabSurfaceTokens.lime.copy(alpha = 0.92f)
                } else {
                    StrideLabSurfaceTokens.hairline
                }
                for (polygon in regionPolygons(region)) {
                    val path = buildNormalizedPath(polygon, ::px, ::py)
                    drawPath(path = path, color = fillColor)
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = (0.85f * scale).coerceAtLeast(1f), join = StrokeJoin.Round)
                    )
                }
            }
        }
        Text(
            text = side.label,
            color = StrideLabSurfaceTokens.inkMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
    }
}

@Composable
internal fun MuscleFocusCardContent(
    focus: MuscleFocusMap,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wideEnoughForSideLegend = maxWidth >= 340.dp
        if (wideEnoughForSideLegend) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnatomicalMuscleMap(focus = focus, figureHeight = 168.dp)
                MuscleLegendColumn(focus = focus, modifier = Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnatomicalMuscleMap(focus = focus, figureHeight = 156.dp)
                MuscleLegendColumn(focus = focus, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MuscleLegendColumn(
    focus: MuscleFocusMap,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (focus.primaryMuscles.isNotEmpty()) {
            LegendItem(
                label = "PRIMARY",
                muscles = focus.primaryMuscles,
                swatchColor = StrideLabSurfaceTokens.lime.copy(alpha = 0.94f),
                borderColor = StrideLabSurfaceTokens.lime,
                textColor = StrideLabSurfaceTokens.ink,
                tag = "muscle-map-primary-legend"
            )
        }
        if (focus.secondaryMuscles.isNotEmpty()) {
            LegendItem(
                label = "SECONDARY",
                muscles = focus.secondaryMuscles,
                swatchColor = StrideLabSurfaceTokens.lime.copy(alpha = 0.34f),
                borderColor = StrideLabSurfaceTokens.lime.copy(alpha = 0.6f),
                textColor = StrideLabSurfaceTokens.inkSecondary,
                tag = "muscle-map-secondary-legend"
            )
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    muscles: List<String>,
    swatchColor: Color,
    borderColor: Color,
    textColor: Color,
    tag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(swatchColor)
                    .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            )
            Text(
                text = label,
                color = StrideLabSurfaceTokens.inkMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        Text(
            text = muscles.joinToString(" · "),
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SessionMuscleFocusSection(
    focus: MuscleFocusMap,
    provenance: String,
    modifier: Modifier = Modifier
) {
    if (focus.isEmpty) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("session-muscle-focus"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StrideLabHairline()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "MUSCLE FOCUS",
                color = StrideLabSurfaceTokens.inkMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
            Text(
                text = provenance,
                color = StrideLabSurfaceTokens.inkSecondary,
                fontSize = 12.sp,
                style = TabularTextStyle
            )
        }
        MuscleFocusCardContent(focus = focus)
        StrideLabHairline()
    }
}

// ---------------------------------------------------------------------------
// Normalized 100x220 Athletic Silhouette Geometry (Pure Vector Compose Paths)
// ---------------------------------------------------------------------------

private fun DrawScope.drawHeadAndSilhouetteBase(
    side: BodySide,
    px: (Float) -> Float,
    py: (Float) -> Float,
    scale: Float
) {
    val fill = StrideLabSurfaceTokens.surfaceSubtle
    val stroke = Stroke(width = (1.0f * scale).coerceAtLeast(1.1f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val hairline = StrideLabSurfaceTokens.hairline

    // Head oval
    val headPath = Path().apply {
        addOval(Rect(px(42f), py(6f), px(58f), py(24f)))
    }
    drawPath(headPath, color = fill)
    drawPath(headPath, color = hairline, style = stroke)

    // Neutral structural silhouette joints (hands, knees, center spine/linea alba subtle cues)
    val hands = listOf(
        listOf(10f to 108f, 15f to 108f, 14f to 118f, 8f to 117f),
        listOf(85f to 108f, 90f to 108f, 92f to 117f, 86f to 118f)
    )
    hands.forEach { poly ->
        val p = buildNormalizedPath(poly, px, py)
        drawPath(p, color = fill)
        drawPath(p, color = hairline, style = stroke)
    }

    if (side == BodySide.FRONT) {
        // Knees & posterior-side calves visibility cue on front leg contour
        val knees = listOf(
            listOf(34f to 156f, 44f to 156f, 43f to 165f, 35f to 165f),
            listOf(56f to 156f, 66f to 156f, 65f to 165f, 57f to 165f)
        )
        knees.forEach { poly ->
            val p = buildNormalizedPath(poly, px, py)
            drawPath(p, color = fill)
            drawPath(p, color = hairline, style = stroke)
        }
    } else {
        // Subtle midline spine & popliteal crease silhouette cues
        val backKnees = listOf(
            listOf(35f to 157f, 43f to 157f, 43f to 164f, 35f to 164f),
            listOf(57f to 157f, 65f to 157f, 65f to 164f, 57f to 164f)
        )
        backKnees.forEach { poly ->
            val p = buildNormalizedPath(poly, px, py)
            drawPath(p, color = fill)
            drawPath(p, color = hairline, style = stroke)
        }
        drawLine(
            color = hairline,
            start = Offset(px(50f), py(32f)),
            end = Offset(px(50f), py(104f)),
            strokeWidth = (0.7f * scale).coerceAtLeast(1f)
        )
    }
}

private fun buildNormalizedPath(
    points: List<Pair<Float, Float>>,
    px: (Float) -> Float,
    py: (Float) -> Float
): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(px(points[0].first), py(points[0].second))
    for (i in 1 until points.size) {
        lineTo(px(points[i].first), py(points[i].second))
    }
    close()
}

private fun mirrorX(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> =
    points.map { (x, y) -> (100f - x) to y }

private val FRONT_REGIONS: List<AnatomicalMuscleRegion> = listOf(
    AnatomicalMuscleRegion.NECK_FRONT,
    AnatomicalMuscleRegion.UPPER_TRAPS_FRONT,
    AnatomicalMuscleRegion.FRONT_DELTS,
    AnatomicalMuscleRegion.LATERAL_DELTS_FRONT,
    AnatomicalMuscleRegion.CHEST,
    AnatomicalMuscleRegion.SERRATUS,
    AnatomicalMuscleRegion.ABS,
    AnatomicalMuscleRegion.OBLIQUES,
    AnatomicalMuscleRegion.BICEPS,
    AnatomicalMuscleRegion.FOREARMS_FRONT,
    AnatomicalMuscleRegion.HIP_FLEXORS,
    AnatomicalMuscleRegion.ADDUCTORS,
    AnatomicalMuscleRegion.QUADS,
    AnatomicalMuscleRegion.SHINS,
    AnatomicalMuscleRegion.FEET_ANKLES_FRONT
)

private val BACK_REGIONS: List<AnatomicalMuscleRegion> = listOf(
    AnatomicalMuscleRegion.NECK_BACK,
    AnatomicalMuscleRegion.UPPER_TRAPS_BACK,
    AnatomicalMuscleRegion.REAR_DELTS,
    AnatomicalMuscleRegion.LATERAL_DELTS_BACK,
    AnatomicalMuscleRegion.MID_UPPER_BACK,
    AnatomicalMuscleRegion.LATS,
    AnatomicalMuscleRegion.LOWER_BACK,
    AnatomicalMuscleRegion.TRICEPS,
    AnatomicalMuscleRegion.FOREARMS_BACK,
    AnatomicalMuscleRegion.GLUTE_MED,
    AnatomicalMuscleRegion.GLUTES,
    AnatomicalMuscleRegion.HAMSTRINGS,
    AnatomicalMuscleRegion.CALVES,
    AnatomicalMuscleRegion.FEET_ANKLES_BACK
)

private fun regionPolygons(region: AnatomicalMuscleRegion): List<List<Pair<Float, Float>>> = when (region) {
    // ---- FRONT ----
    AnatomicalMuscleRegion.NECK_FRONT -> listOf(
        listOf(45f to 24f, 55f to 24f, 56f to 33f, 44f to 33f)
    )
    AnatomicalMuscleRegion.UPPER_TRAPS_FRONT -> {
        val left = listOf(44f to 27f, 44f to 34f, 30f to 37f, 36f to 32f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.FRONT_DELTS -> {
        val left = listOf(26f to 38f, 32f to 37f, 34f to 44f, 30f to 54f, 25f to 52f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.LATERAL_DELTS_FRONT -> {
        val left = listOf(21f to 40f, 26f to 38f, 25f to 52f, 19f to 50f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.CHEST -> {
        val left = listOf(49f to 36f, 49f to 56f, 36f to 56f, 32f to 46f, 34f to 38f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.SERRATUS -> {
        val left = listOf(34f to 55f, 39f to 57f, 38f to 69f, 33f to 67f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.ABS -> listOf(
        listOf(42f to 57f, 58f to 57f, 57f to 96f, 43f to 96f)
    )
    AnatomicalMuscleRegion.OBLIQUES -> {
        val left = listOf(33f to 67f, 42f to 62f, 43f to 95f, 35f to 97f, 32f to 82f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.BICEPS -> {
        val left = listOf(22f to 52f, 29f to 54f, 26f to 78f, 18f to 77f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.FOREARMS_FRONT -> {
        val left = listOf(18f to 78f, 25f to 79f, 16f to 108f, 10f to 107f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.HIP_FLEXORS -> {
        val left = listOf(34f to 97f, 45f to 97f, 44f to 112f, 33f to 108f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.ADDUCTORS -> {
        val left = listOf(44f to 112f, 49f to 112f, 46f to 146f, 41f to 146f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.QUADS -> {
        val left = listOf(32f to 108f, 44f to 112f, 44f to 155f, 33f to 155f, 30f to 132f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.SHINS -> {
        val left = listOf(34f to 165f, 43f to 165f, 42f to 198f, 34f to 198f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.FEET_ANKLES_FRONT -> {
        val left = listOf(34f to 199f, 42f to 199f, 43f to 212f, 31f to 212f)
        listOf(left, mirrorX(left))
    }

    // ---- BACK ----
    AnatomicalMuscleRegion.NECK_BACK -> listOf(
        listOf(45f to 24f, 55f to 24f, 56f to 31f, 44f to 31f)
    )
    AnatomicalMuscleRegion.UPPER_TRAPS_BACK -> listOf(
        listOf(44f to 28f, 56f to 28f, 68f to 37f, 50f to 43f, 32f to 37f)
    )
    AnatomicalMuscleRegion.REAR_DELTS -> {
        val left = listOf(25f to 38f, 32f to 37f, 35f to 44f, 30f to 54f, 24f to 52f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.LATERAL_DELTS_BACK -> {
        val left = listOf(20f to 40f, 25f to 38f, 24f to 52f, 19f to 50f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.MID_UPPER_BACK -> listOf(
        listOf(35f to 40f, 50f to 43f, 65f to 40f, 61f to 62f, 50f to 68f, 39f to 62f)
    )
    AnatomicalMuscleRegion.LATS -> {
        val left = listOf(31f to 51f, 39f to 62f, 42f to 82f, 34f to 83f, 28f to 62f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.LOWER_BACK -> listOf(
        listOf(42f to 68f, 58f to 68f, 61f to 96f, 50f to 100f, 39f to 96f)
    )
    AnatomicalMuscleRegion.TRICEPS -> {
        val left = listOf(21f to 52f, 28f to 55f, 25f to 78f, 18f to 77f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.FOREARMS_BACK -> {
        val left = listOf(18f to 78f, 25f to 79f, 16f to 108f, 10f to 107f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.GLUTE_MED -> {
        val left = listOf(34f to 95f, 45f to 98f, 40f to 106f, 31f to 106f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.GLUTES -> {
        val left = listOf(32f to 106f, 49f to 101f, 49f to 123f, 34f to 122f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.HAMSTRINGS -> {
        val left = listOf(33f to 123f, 48f to 124f, 45f to 156f, 34f to 156f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.CALVES -> {
        val left = listOf(34f to 165f, 44f to 165f, 43f to 192f, 34f to 192f)
        listOf(left, mirrorX(left))
    }
    AnatomicalMuscleRegion.FEET_ANKLES_BACK -> {
        val left = listOf(35f to 193f, 42f to 193f, 43f to 212f, 33f to 212f)
        listOf(left, mirrorX(left))
    }
}
