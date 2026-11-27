package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HistoryBaselineCard(
    suggestion: HistoryBaselineSuggestion,
    useWeekly: (Double) -> Unit,
    useLongest: (Double) -> Unit
) {
    FreeformPanel(Modifier.testTag("builder-confirmed-history-suggestion")) {
        Text(
            "From completed workouts",
            color = StrideLabSurfaceTokens.ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Distance is recorded for every logged run in ${suggestion.loggedWeeks} of the last 4 finished weeks." +
                if (suggestion.undistancedRuns == 0) "" else
                    " ${suggestion.undistancedRuns} run${if (suggestion.undistancedRuns == 1) "" else "s"} missing distance.",
            color = StrideLabSurfaceTokens.inkSecondary,
            style = MaterialTheme.typography.bodyMedium.tabular()
        )
        suggestion.weeklyKm?.let { weekly ->
            Text(
                "The weekly average uses only those weeks. You can edit both distances after applying them.",
                color = StrideLabSurfaceTokens.inkMuted,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                onClick = { useWeekly(weekly) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("builder-use-history-weekly")
            ) {
                Text(
                    "Use ${freeformNumber(weekly)} km/week",
                    color = StrideLabSurfaceTokens.lime,
                    style = TabularTextStyle,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        suggestion.longestKm?.let { longest ->
            TextButton(
                onClick = { useLongest(longest) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("builder-use-history-longest")
            ) {
                Text(
                    "Use ${freeformNumber(longest)} km as longest",
                    color = StrideLabSurfaceTokens.lime,
                    style = TabularTextStyle,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
