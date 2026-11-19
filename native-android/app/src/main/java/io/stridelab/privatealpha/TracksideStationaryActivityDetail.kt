package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Discipline-aware activity detail for stationary workouts.
 * No GPS hero, running splits, pace targets, or guessed strength sets/reps.
 * Edit and Share are delegated to the same source-owner dialogs as running.
 */
@Composable
internal fun TracksideStationaryActivityDetail(
    activity: ActivityRecord,
    recorded: ActivityDetail?,
    owner: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
    onRefreshSource: (() -> Unit)? = null,
    refreshEnabled: Boolean = false
) {
    val type = activity.type.lowercase(Locale.ROOT)
    val discipline = when {
        "strength" in type || "weight" in type || "resistance" in type -> "Strength"
        "calisthenics" in type -> "Calisthenics"
        "yoga" in type -> "Yoga"
        "pilates" in type -> "Pilates"
        "mobility" in type || "stretch" in type -> "Mobility"
        "conditioning" in type || "hiit" in type || "cardio" in type -> "Conditioning"
        else -> "Workout"
    }
    val hrCount = recorded?.samples.orEmpty().count {
        it.hr?.isFinite() == true && it.hr > 0.0
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF080B0A))
            .testTag("activity-stationary-detail"),
        contentPadding = PaddingValues(
            start = 18.dp, end = 18.dp, top = 16.dp, bottom = 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "stationary-header") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("activity-back")
                ) { Text("‹", color = TracksidePalette.lime, fontSize = 28.sp) }
                Text(
                    discipline,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    color = TracksidePalette.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (onEdit != null) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("activity-edit")
                    ) { Text("Edit", color = TracksidePalette.lime, fontSize = 13.sp) }
                }
                TextButton(
                    onClick = onShare,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("activity-share")
                ) { Text("Share", color = TracksidePalette.lime, fontSize = 13.sp) }
            }
        }
        item(key = "stationary-overview") {
            Column(
                Modifier.fillMaxWidth().testTag("activity-parity-overview"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "RECORDED " + discipline.uppercase(Locale.ROOT),
                    fontSize = 11.sp, letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold, color = TracksidePalette.lime
                )
                Text(
                    activity.title, fontSize = 27.sp, lineHeight = 33.sp,
                    color = TracksidePalette.text, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("activity-preview-title")
                )
                Text(
                    activity.dateLabel + " · " + activity.sourceLabel,
                    color = TracksidePalette.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        item(key = "stationary-metrics") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF151A18),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF364039))
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TracksidePrimaryActivityMetrics(activity)
                }
            }
        }
        item(key = "stationary-data-note") {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Recorded data",
                    color = TracksidePalette.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (hrCount >= 2)
                        "This workout contains recorded heart-rate samples. Values are from the saved source recording."
                    else
                        "A detailed heart-rate stream is not available. Only recorded summary measurements are displayed.",
                    color = TracksidePalette.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Text(
                    "Exercise names, sets and repetitions are not available in this saved activity record; none have been estimated.",
                    color = TracksidePalette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        if (onRefreshSource != null) {
            item(key = "stationary-refresh-source") {
                OutlinedButton(
                    onClick = onRefreshSource,
                    enabled = refreshEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("activity-stationary-refresh"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF364039))
                ) {
                    Text(
                        if (recorded == null) "Load recorded sensors" else "Refresh recorded sensors",
                        color = if (refreshEnabled) TracksidePalette.lime
                            else TracksidePalette.textMuted
                    )
                }
            }
        }
        if (recorded != null && hrCount >= 2) {
            item(key = "stationary-hr-charts") {
                Column(
                    Modifier.fillMaxWidth().testTag("activity-stationary-heart-rate"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Heart rate",
                        color = TracksidePalette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TracksideStationaryHeartRateCharts(recorded, owner)
                }
            }
        }
    }
}
