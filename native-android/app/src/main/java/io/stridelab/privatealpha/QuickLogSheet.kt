package io.stridelab.privatealpha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * #121212 Quick-Log ModalBottomSheet opened from the center + button in the docked navigation bar.
 * Surfaces active in-progress drafts (conditional), a direct universal "Start empty workout" CTA,
 * and direct jumps to Workout templates & Custom scheduler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickLogBottomSheet(
    owner: String?,
    board: FreeformBoard?,
    onStartEmpty: () -> Unit,
    onResumeDraft: (String) -> Unit,
    onDiscardDraft: ((String) -> Unit)? = null,
    onOpenTemplates: () -> Unit,
    onOpenWorkoutsHub: () -> Unit,
    onDismiss: () -> Unit
) {
    val inProgressDrafts = remember(board, owner) {
        board?.workouts.orEmpty()
            .filter { !it.isComplete && (owner == null || it.owner == owner) }
            .sortedByDescending { it.modifiedAt }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StrideLabSurfaceTokens.surfaceElevated,
        contentColor = StrideLabSurfaceTokens.ink,
        modifier = Modifier.testTag("quick-log-sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log workout",
                    color = StrideLabSurfaceTokens.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("quick-log-close")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close quick log",
                        tint = StrideLabSurfaceTokens.inkSecondary
                    )
                }
            }

            if (inProgressDrafts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "IN PROGRESS",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    inProgressDrafts.take(3).forEach { draft ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(StrideLabSurfaceTokens.surfaceSubtle)
                                .clickable {
                                    onDismiss()
                                    onResumeDraft(draft.id)
                                }
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                .testTag("quick-log-resume-${draft.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(draft.discipline.markColor)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = draft.title,
                                    color = StrideLabSurfaceTokens.ink,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Resume · ${draft.performedOn}",
                                    color = StrideLabSurfaceTokens.lime,
                                    fontSize = 12.sp,
                                    style = TabularTextStyle,
                                    maxLines = 1
                                )
                            }
                            if (onDiscardDraft != null) {
                                IconButton(
                                    onClick = { onDiscardDraft(draft.id) },
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("quick-log-discard-${draft.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Discard draft ${draft.title}",
                                        tint = freeformPalette.highCoral
                                    )
                                }
                            }
                        }
                    }
                }
                StrideLabHairline()
            }

            Button(
                onClick = {
                    onDismiss()
                    onStartEmpty()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StrideLabSurfaceTokens.lime,
                    contentColor = StrideLabSurfaceTokens.onLime
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("quick-log-start-empty")
            ) {
                Text(
                    text = "Start empty workout",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            StrideLabHairline()

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ROUTINES & SCHEDULE",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                StrideLabFlatRow(
                    title = "Workout templates",
                    subtitle = "Saved routines and one-tap starts",
                    accentColor = StrideLabSurfaceTokens.lime,
                    onClick = {
                        onDismiss()
                        onOpenTemplates()
                    },
                    trailing = {
                        Text("›", color = StrideLabSurfaceTokens.inkMuted, fontSize = 18.sp)
                    },
                    modifier = Modifier.testTag("quick-log-templates")
                )
                StrideLabFlatRow(
                    title = "Workout Hub",
                    subtitle = "Curated library, guided sessions & custom workouts",
                    accentColor = StrideLabSurfaceTokens.inkMuted,
                    showBottomHairline = false,
                    onClick = {
                        onDismiss()
                        onOpenWorkoutsHub()
                    },
                    trailing = {
                        Text("›", color = StrideLabSurfaceTokens.inkMuted, fontSize = 18.sp)
                    },
                    modifier = Modifier.testTag("quick-log-hub")
                )
            }
        }
    }
}
