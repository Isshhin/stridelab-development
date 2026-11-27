package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DockTabItem(
    val route: String,
    val label: String,
    val drawable: Int
)

/**
 * Edge-to-edge AMOLED docked navigation bar with 5 balanced slots:
 * Today | Training | [ + ] | Activity | More
 */
@Composable
internal fun TracksideFloatingNavigation(
    route: String?,
    onTab: (String) -> Unit,
    onQuickLog: (() -> Unit)? = null
) {
    val selectedRoute = when {
        route == "home" || route == null -> "home"
        route == "training" || route in setOf(
            "builder/{discipline}", "workout/{planId}/{sessionId}",
            "manage/{planId}/{date}", "move/{planId}/{sessionId}",
            "review-move/{planId}/{sessionId}/{date}",
            "missed/{planId}/{sessionId}"
        ) -> "training"
        route == "activities" || route == "activity/{id}" -> "activities"
        route == "settings" -> "settings"
        else -> null
    }

    val leftTabs = listOf(
        DockTabItem("home", "Today", R.drawable.trackside_figma_home),
        DockTabItem("training", "Training", R.drawable.trackside_figma_calendar)
    )
    val rightTabs = listOf(
        DockTabItem("activities", "Activity", R.drawable.trackside_figma_activity),
        DockTabItem("settings", "More", R.drawable.trackside_figma_more)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0B0D))
            .navigationBarsPadding()
            .testTag("floating-navigation-overlay")
    ) {
        StrideLabHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftTabs.forEach { tab ->
                DockedNavTab(
                    tab = tab,
                    selected = selectedRoute == tab.route,
                    onClick = { onTab(tab.route) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Center #D8FB70 Quick-Log (+) CTA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StrideLabSurfaceTokens.lime)
                        .clickable { onQuickLog?.invoke() }
                        .testTag("nav-quick-log")
                        .semantics {
                            contentDescription = "Log workout"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = StrideLabSurfaceTokens.onLime,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            rightTabs.forEach { tab ->
                DockedNavTab(
                    tab = tab,
                    selected = selectedRoute == tab.route,
                    onClick = { onTab(tab.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DockedNavTab(
    tab: DockTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkMuted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = StrideLabSurfaceTokens.minTouchTarget)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .testTag("nav-${tab.route}")
            .semantics {
                contentDescription = tab.label
                role = Role.Tab
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(tab.drawable),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
