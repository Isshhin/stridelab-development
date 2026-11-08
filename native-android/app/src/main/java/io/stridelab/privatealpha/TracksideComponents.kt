package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reusable Trackside 3.0 content surface. No side accent stripes or implied metrics. */
@Composable
internal fun TracksideGlassCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    trainingPalette: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (trainingPalette) TracksideTrainingPalette.surface else TracksidePalette.surface,
        contentColor = TracksidePalette.text,
        border = BorderStroke(1.dp, if (trainingPalette)
            TracksideTrainingPalette.border else Color(0xFF4D6B7D))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(if (trainingPalette)
                        listOf(TracksideTrainingPalette.surface, Color(0xFF191B15))
                    else listOf(Color(0xFF193544), Color(0xFF101F2A)))
                )
                .padding(if (compact) 13.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
            content = content
        )
    }
}

/** Single header for native screens; preserve the existing page title and profile routes. */
@Composable
internal fun TracksideScreenHeader(
    title: String,
    eyebrow: String,
    compactTitle: Boolean = false,
    onProfileClick: () -> Unit,
    dense: Boolean = false,
    titleBadge: String? = null
) {
    if (eyebrow == "ACTIVITY DETAIL") return
    if (title == "Today") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.bodySmall,
                color = TracksidePalette.textMuted)
            Text("Today", fontSize = 29.sp, fontWeight = FontWeight.Bold,
                color = TracksidePalette.text,
                modifier = Modifier.testTag("screen-title").semantics { heading() })
        }
        return
    }
    if (title == "Training" || title == "Manage plan") {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold,
                color = TracksidePalette.text,
                modifier = Modifier.weight(1f).testTag("screen-title").semantics { heading() })
            if (titleBadge != null) {
                Surface(
                    modifier = Modifier.testTag("screen-title-badge"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TracksidePalette.text),
                    color = Color.Transparent
                ) {
                    Text(
                        titleBadge,
                        Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                        fontSize = 10.6f.sp,
                        color = TracksidePalette.text,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BrandMark(Modifier.size(24.dp))
            Text(
                text = eyebrow,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = TracksidePalette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("header-settings")
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings and profile",
                    tint = TracksidePalette.text)
            }
        }
        Spacer(Modifier.height(if (dense) 5.dp else 12.dp))
        Text(
            text = title,
            style = if (compactTitle) MaterialTheme.typography.headlineSmall else
                MaterialTheme.typography.headlineLarge.copy(fontSize = 29.sp, lineHeight = 35.sp),
            color = TracksidePalette.text,
            modifier = Modifier.testTag("screen-title").semantics { heading() }
        )
    }
}

/** Small section caption for training graphs, lists, and activity groups. */
@Composable
internal fun TracksideSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB4C6D3)
        )
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TracksidePalette.textMuted)
        }
    }
}
