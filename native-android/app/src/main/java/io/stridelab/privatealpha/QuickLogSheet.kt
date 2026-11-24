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
 * Surfaces active in-progress drafts (conditional), the 2x3 discipline empty-workout grid,
 * and direct jumps to Workout templates & Custom scheduler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickLogBottomSheet(
    owner: String?,
    board: FreeformBoard?,
    onStartEmpty: (ManualDiscipline) -> Unit,
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
                                    text = "Resume · ${draft.discipline.label}",
                                    color = StrideLabSurfaceTokens.lime,
                                    fontSize = 12.sp,
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "START EMPTY WORKOUT",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                ManualDiscipline.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { discipline ->
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onStartEmpty(discipline)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = StrideLabSurfaceTokens.surfaceSubtle,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("quick-log-discipline-${discipline.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(discipline.markColor)
                                    )
                                    Text(
                                        text = discipline.label,
                                        color = StrideLabSurfaceTokens.ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
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
                    title = "Workouts hub & custom schedule",
                    subtitle = "Recurring workouts and manual schedule",
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
