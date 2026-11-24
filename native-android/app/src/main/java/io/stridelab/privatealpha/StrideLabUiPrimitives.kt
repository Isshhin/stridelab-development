package io.stridelab.privatealpha

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared AMOLED-first surface tokens, 8-point spacing rhythm, tabular numerals,
 * flat row/divider primitives, and zero-recomposition delayed skeletons.
 */
internal object StrideLabSurfaceTokens {
    val canvas = Color(0xFF000000)
    val background = Color(0xFF000000)
    val surfaceElevated = Color(0xFF121212)
    val surfaceSubtle = Color(0xFF181A1D)
    val surfaceMuted = Color(0xFF181A1D)
    val hairline = Color(0xFF1F242B)
    val ink = Color(0xFFF5F7FA)
    val inkSecondary = Color(0xFFA0A8B3)
    val inkMuted = Color(0xFF6B737E)
    val lime = Color(0xFFD8FB70)
    val limeMuted = Color(0x26D8FB70)
    val amber = Color(0xFFFFC857)
    val coral = Color(0xFFFF707A)
    val onLime = Color(0xFF080A08)
    val skeletonBase = Color(0xFF16181B)
    val skeletonHighlight = Color(0xFF23272E)

    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val minTouchTarget = 48.dp
}

internal val TabularTextStyle = TextStyle(fontFeatureSettings = "tnum")

internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

@Composable
internal fun StrideLabHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = StrideLabSurfaceTokens.hairline
    )
}

@Composable
internal fun StrideLabSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionTag: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = StrideLabSurfaceTokens.space24),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = StrideLabSurfaceTokens.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(
                        minWidth = StrideLabSurfaceTokens.minTouchTarget,
                        minHeight = StrideLabSurfaceTokens.minTouchTarget
                    )
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    color = StrideLabSurfaceTokens.lime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun StrideLabFlatRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color? = null,
    showBottomHairline: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StrideLabSurfaceTokens.minTouchTarget)
                .then(
                    if (onClick != null) {
                        Modifier.strideLabPressFeedback(
                            shape = RoundedCornerShape(12.dp),
                            highlightBorderColor = accentColor ?: StrideLabSurfaceTokens.lime,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StrideLabSurfaceTokens.space12)
        ) {
            if (accentColor != null) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = StrideLabSurfaceTokens.inkSecondary,
                        fontSize = 12.5.sp,
                        style = TabularTextStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = trailing
                )
            }
        }
        if (showBottomHairline) {
            StrideLabHairline()
        }
    }
}

internal fun isSystemReducedMotion(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

@Composable
internal fun rememberDelayedSkeletonVisible(loading: Boolean, delayMs: Long = 180L): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(loading, delayMs) {
        if (!loading) {
            visible = false
        } else {
            kotlinx.coroutines.delay(delayMs)
            visible = true
        }
    }
    return visible
}

private val LocalSkeletonShimmerProgress = compositionLocalOf<State<Float>?> { null }

@Composable
internal fun SkeletonShimmerContainer(
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val staticMode = reducedMotion || remember(ctx) { isSystemReducedMotion(ctx) }
    val progressState: State<Float> = if (staticMode) {
        remember { mutableFloatStateOf(0f) }
    } else {
        val transition = rememberInfiniteTransition(label = "neutral-skeleton-group")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "neutral-skeleton-group-sweep"
        )
    }
    CompositionLocalProvider(LocalSkeletonShimmerProgress provides progressState, content = content)
}

@Composable
internal fun SkeletonCrossfade(
    loading: Boolean,
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val staticMode = reducedMotion || remember(ctx) { isSystemReducedMotion(ctx) }
    Crossfade(
        targetState = loading,
        animationSpec = tween(durationMillis = if (staticMode) 0 else 180),
        modifier = modifier,
        label = "skeleton-crossfade"
    ) { isLoading ->
        if (isLoading) skeleton() else content()
    }
}

internal fun Modifier.neutralSkeletonBrush(
    cornerRadius: Dp = 8.dp,
    reducedMotion: Boolean = false
): Modifier = composed {
    val ctx = LocalContext.current
    val staticMode = reducedMotion || remember(ctx) { isSystemReducedMotion(ctx) }
    val sharedProgress = LocalSkeletonShimmerProgress.current
    val progressState: State<Float> = when {
        staticMode -> remember { mutableFloatStateOf(0f) }
        sharedProgress != null -> sharedProgress
        else -> {
            val transition = rememberInfiniteTransition(label = "neutral-skeleton")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "neutral-skeleton-sweep"
            )
        }
    }

    drawWithCache {
        val radiusPx = cornerRadius.toPx()
        val base = StrideLabSurfaceTokens.skeletonBase
        val highlight = StrideLabSurfaceTokens.skeletonHighlight
        onDrawBehind {
            val corner = CornerRadius(radiusPx, radiusPx)
            if (staticMode || size.width <= 0f) {
                drawRoundRect(color = base, cornerRadius = corner)
            } else {
                val band = (size.width * 0.45f).coerceAtLeast(1f)
                val travel = size.width + band * 2f
                val startX = progressState.value * travel - band
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(startX - band * 0.5f, 0f),
                        end = Offset(startX + band * 0.5f, size.height * 0.35f)
                    ),
                    cornerRadius = corner
                )
            }
        }
    }
}

@Composable
internal fun SkeletonText(
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.6f,
    height: Dp = 14.dp,
    reducedMotion: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction.coerceIn(0.05f, 1f))
            .height(height)
            .neutralSkeletonBrush(cornerRadius = 6.dp, reducedMotion = reducedMotion)
    )
}

@Composable
internal fun SkeletonMetric(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SkeletonText(widthFraction = 0.55f, height = 24.dp, reducedMotion = reducedMotion)
        SkeletonText(widthFraction = 0.42f, height = 11.dp, reducedMotion = reducedMotion)
    }
}

@Composable
internal fun SkeletonWorkoutRow(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .neutralSkeletonBrush(cornerRadius = 2.dp, reducedMotion = reducedMotion)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonText(widthFraction = 0.58f, height = 15.dp, reducedMotion = reducedMotion)
                SkeletonText(widthFraction = 0.40f, height = 12.dp, reducedMotion = reducedMotion)
            }
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .neutralSkeletonBrush(cornerRadius = 10.dp, reducedMotion = reducedMotion)
            )
        }
        StrideLabHairline()
    }
}

@Composable
internal fun SkeletonExerciseRow(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonText(widthFraction = 0.48f, height = 16.dp, reducedMotion = reducedMotion)
        repeat(2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(28.dp)
                        .height(16.dp)
                        .neutralSkeletonBrush(cornerRadius = 4.dp, reducedMotion = reducedMotion)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .neutralSkeletonBrush(cornerRadius = 8.dp, reducedMotion = reducedMotion)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .neutralSkeletonBrush(cornerRadius = 8.dp, reducedMotion = reducedMotion)
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .neutralSkeletonBrush(cornerRadius = 14.dp, reducedMotion = reducedMotion)
                )
            }
        }
        StrideLabHairline()
    }
}

@Composable
internal fun SkeletonCalendar(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonText(widthFraction = 0.36f, height = 14.dp, reducedMotion = reducedMotion)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(7) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .neutralSkeletonBrush(cornerRadius = 10.dp, reducedMotion = reducedMotion)
                )
            }
        }
    }
}

@Composable
internal fun SkeletonActivityRow(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .neutralSkeletonBrush(cornerRadius = 2.dp, reducedMotion = reducedMotion)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SkeletonText(widthFraction = 0.56f, height = 14.dp, reducedMotion = reducedMotion)
                SkeletonText(widthFraction = 0.40f, height = 12.dp, reducedMotion = reducedMotion)
            }
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 36.dp)
                    .neutralSkeletonBrush(cornerRadius = 8.dp, reducedMotion = reducedMotion)
            )
        }
        StrideLabHairline()
    }
}

@Composable
internal fun SkeletonHeroTrainingCard(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonText(widthFraction = 0.34f, height = 11.dp, reducedMotion = reducedMotion)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(20.dp)
                            .neutralSkeletonBrush(cornerRadius = 6.dp, reducedMotion = reducedMotion)
                    )
                    SkeletonText(widthFraction = 0.72f, height = 18.dp, reducedMotion = reducedMotion)
                    SkeletonText(widthFraction = 0.52f, height = 13.dp, reducedMotion = reducedMotion)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 16.dp)
                            .neutralSkeletonBrush(cornerRadius = 4.dp, reducedMotion = reducedMotion)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 26.dp)
                            .neutralSkeletonBrush(cornerRadius = 4.dp, reducedMotion = reducedMotion)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 16.dp)
                            .neutralSkeletonBrush(cornerRadius = 4.dp, reducedMotion = reducedMotion)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkeletonOverviewMetricsCard(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeletonText(widthFraction = 0.32f, height = 11.dp, reducedMotion = reducedMotion)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(3) {
                    SkeletonMetric(
                        modifier = Modifier.weight(1f),
                        reducedMotion = reducedMotion
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkeletonConsistencyHeatmap(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonText(widthFraction = 0.44f, height = 11.dp, reducedMotion = reducedMotion)
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(7) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .neutralSkeletonBrush(cornerRadius = 8.dp, reducedMotion = reducedMotion)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 18.dp)
                        .neutralSkeletonBrush(cornerRadius = 9.dp, reducedMotion = reducedMotion)
                )
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 18.dp)
                        .neutralSkeletonBrush(cornerRadius = 9.dp, reducedMotion = reducedMotion)
                )
            }
        }
    }
}

@Composable
internal fun SkeletonActivitySummaryCard(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 4.dp)
                            .neutralSkeletonBrush(cornerRadius = 2.dp, reducedMotion = reducedMotion)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 84.dp, height = 14.dp)
                            .neutralSkeletonBrush(cornerRadius = 5.dp, reducedMotion = reducedMotion)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 22.dp)
                        .neutralSkeletonBrush(cornerRadius = 11.dp, reducedMotion = reducedMotion)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkeletonText(widthFraction = 0.28f, height = 10.dp, reducedMotion = reducedMotion)
                    SkeletonText(widthFraction = 0.68f, height = 16.dp, reducedMotion = reducedMotion)
                }
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 42.dp)
                        .neutralSkeletonBrush(cornerRadius = 8.dp, reducedMotion = reducedMotion)
                )
            }
            StrideLabHairline()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        SkeletonText(widthFraction = 0.65f, height = 10.dp, reducedMotion = reducedMotion)
                        SkeletonText(widthFraction = 0.80f, height = 16.dp, reducedMotion = reducedMotion)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkeletonTrainingHeaderCard(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 140.dp, height = 34.dp)
                        .neutralSkeletonBrush(cornerRadius = 17.dp, reducedMotion = reducedMotion)
                )
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 24.dp)
                        .neutralSkeletonBrush(cornerRadius = 12.dp, reducedMotion = reducedMotion)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .neutralSkeletonBrush(cornerRadius = 10.dp, reducedMotion = reducedMotion)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkeletonCoachingCard(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonText(widthFraction = 0.45f, height = 12.dp, reducedMotion = reducedMotion)
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 22.dp)
                        .neutralSkeletonBrush(cornerRadius = 11.dp, reducedMotion = reducedMotion)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .neutralSkeletonBrush(cornerRadius = 12.dp, reducedMotion = reducedMotion)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SkeletonActivityDetailStreams(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StrideLabSurfaceTokens.surfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .neutralSkeletonBrush(cornerRadius = 12.dp, reducedMotion = reducedMotion)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    SkeletonMetric(modifier = Modifier.weight(1f), reducedMotion = reducedMotion)
                }
            }
            repeat(2) {
                SkeletonText(widthFraction = if (it == 0) 0.92f else 0.78f, height = 14.dp, reducedMotion = reducedMotion)
            }
        }
    }
}

@Composable
internal fun SkeletonShareCardPreview(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio.coerceIn(0.45f, 1.5f))
            .neutralSkeletonBrush(cornerRadius = 16.dp, reducedMotion = reducedMotion)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonText(widthFraction = 0.50f, height = 20.dp, reducedMotion = reducedMotion)
            SkeletonText(widthFraction = 0.35f, height = 13.dp, reducedMotion = reducedMotion)
        }
    }
}

@Composable
internal fun SkeletonChipRow(
    count: Int = 3,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count.coerceAtLeast(1)) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 34.dp)
                    .neutralSkeletonBrush(cornerRadius = 17.dp, reducedMotion = reducedMotion)
            )
        }
    }
}

/**
 * Tactile press highlight + gentle spring scale for clickable StrideLab cards and rows.
 * Respects reduced-motion accessibility settings cleanly.
 */
internal fun Modifier.strideLabPressFeedback(
    interactionSource: MutableInteractionSource? = null,
    reducedMotion: Boolean? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    pressedBackgroundColor: Color? = null,
    highlightBorderColor: Color? = null,
    onClick: () -> Unit
): Modifier = composed {
    val activeInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val context = LocalContext.current
    val isReduced = reducedMotion ?: remember(context) { isSystemReducedMotion(context) }
    val pressed by activeInteraction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !isReduced) 0.978f else 1f,
        animationSpec = if (isReduced) snap() else spring(dampingRatio = 0.75f, stiffness = 650f),
        label = "stridelab-press-scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (pressed && enabled) {
            pressedBackgroundColor ?: Color(0xFF1B1E24)
        } else {
            Color.Transparent
        },
        animationSpec = tween(if (isReduced) 0 else 120),
        label = "stridelab-press-bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (pressed && enabled && highlightBorderColor != null) {
            highlightBorderColor.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(if (isReduced) 0 else 120),
        label = "stridelab-press-border"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(bgColor)
        .then(if (highlightBorderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
        .clickable(
            interactionSource = activeInteraction,
            indication = null,
            enabled = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
}

@Composable
internal fun StrideLabInteractiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = StrideLabSurfaceTokens.lime,
    containerColor: Color = StrideLabSurfaceTokens.surfaceElevated,
    borderColor: Color = StrideLabSurfaceTokens.hairline,
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotion(context) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val animMs = if (reducedMotion) 0 else 130
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reducedMotion) 0.978f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.75f, stiffness = 650f),
        label = "stridelab-card-scale"
    )
    val animatedContainer by animateColorAsState(
        targetValue = if (pressed && enabled) Color(0xFF1A1E24) else containerColor,
        animationSpec = tween(animMs),
        label = "stridelab-card-bg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (pressed && enabled) accentColor.copy(alpha = 0.62f) else borderColor,
        animationSpec = tween(animMs),
        label = "stridelab-card-border"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        shape = shape,
        color = animatedContainer,
        border = BorderStroke(1.dp, animatedBorder),
        content = { Column(content = content) }
    )
}
