package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun StrideLabMetricBadge(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun StrideLabFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = StrideLabSurfaceTokens.lime
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accentColor else StrideLabSurfaceTokens.surfaceSubtle,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accentColor else StrideLabSurfaceTokens.hairline
        )
    ) {
        Text(
            text = label,
            color = if (selected) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.inkMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
internal fun StrideLabEmptyStatePanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StrideLabSurfaceTokens.surfaceElevated,
        border = BorderStroke(1.dp, StrideLabSurfaceTokens.hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = StrideLabSurfaceTokens.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = StrideLabSurfaceTokens.inkMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StrideLabSurfaceTokens.lime,
                        contentColor = StrideLabSurfaceTokens.onLime
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorkoutHubVisualThumbnail(
    category: WorkoutHubCategory,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        StrideLabSurfaceTokens.surfaceElevated
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            when (category) {
                WorkoutHubCategory.RUNNING -> {
                    val path = Path().apply {
                        moveTo(w * 0.14f, h * 0.72f)
                        cubicTo(w * 0.34f, h * 0.72f, w * 0.36f, h * 0.32f, w * 0.56f, h * 0.42f)
                        cubicTo(w * 0.70f, h * 0.49f, w * 0.74f, h * 0.24f, w * 0.88f, h * 0.28f)
                    }
                    drawPath(path, color = accentColor, style = stroke)
                    drawCircle(accentColor, radius = w * 0.055f, center = Offset(w * 0.88f, h * 0.28f))
                }
                WorkoutHubCategory.STRENGTH -> {
                    drawLine(accentColor, Offset(w * 0.16f, h * 0.5f), Offset(w * 0.84f, h * 0.5f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                    drawRoundRect(accentColor, Offset(w * 0.22f, h * 0.28f), Size(w * 0.10f, h * 0.44f), CornerRadius(w * 0.04f))
                    drawRoundRect(accentColor, Offset(w * 0.68f, h * 0.28f), Size(w * 0.10f, h * 0.44f), CornerRadius(w * 0.04f))
                }
                WorkoutHubCategory.CALISTHENICS -> {
                    drawLine(accentColor.copy(alpha = 0.7f), Offset(w * 0.18f, h * 0.26f), Offset(w * 0.82f, h * 0.26f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                    drawCircle(accentColor, radius = w * 0.12f, center = Offset(w * 0.36f, h * 0.60f), style = stroke)
                    drawCircle(accentColor, radius = w * 0.12f, center = Offset(w * 0.64f, h * 0.60f), style = stroke)
                }
                WorkoutHubCategory.CONDITIONING -> {
                    val path = Path().apply {
                        moveTo(w * 0.12f, h * 0.55f)
                        lineTo(w * 0.30f, h * 0.55f)
                        lineTo(w * 0.42f, h * 0.26f)
                        lineTo(w * 0.56f, h * 0.76f)
                        lineTo(w * 0.68f, h * 0.44f)
                        lineTo(w * 0.88f, h * 0.44f)
                    }
                    drawPath(path, color = accentColor, style = stroke)
                }
                WorkoutHubCategory.PLYOMETRICS -> {
                    val path = Path().apply {
                        moveTo(w * 0.24f, h * 0.68f)
                        lineTo(w * 0.50f, h * 0.42f)
                        lineTo(w * 0.76f, h * 0.68f)
                        moveTo(w * 0.30f, h * 0.44f)
                        lineTo(w * 0.50f, h * 0.24f)
                        lineTo(w * 0.70f, h * 0.44f)
                    }
                    drawPath(path, color = accentColor, style = stroke)
                }
                WorkoutHubCategory.ISOMETRICS -> {
                    drawRoundRect(accentColor.copy(alpha = 0.35f), Offset(w * 0.22f, h * 0.26f), Size(w * 0.56f, h * 0.48f), CornerRadius(w * 0.08f))
                    drawLine(accentColor, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.78f, h * 0.50f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                }
                WorkoutHubCategory.MOBILITY -> {
                    drawArc(accentColor, startAngle = 200f, sweepAngle = 220f, useCenter = false, topLeft = Offset(w * 0.22f, h * 0.22f), size = Size(w * 0.56f, h * 0.56f), style = stroke)
                    drawCircle(accentColor, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.50f))
                }
                WorkoutHubCategory.RECOVERY -> {
                    drawCircle(accentColor.copy(alpha = 0.4f), radius = w * 0.28f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                    drawCircle(accentColor, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                }
            }
        }
    }
}
