package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Batch E3 isolated, NON-ROUTED design candidate matched to editable Figma
 * 834:5 / 834:69 / 834:133 / 834:190 / 834:247.
 *
 * Not connected to plan generation, repository, release flags, or navigation.
 * Values and callbacks are injected; no synthetic data reaches app state.
 * Existing Trackside PR #36 and Engine1.5 PR #37 are unchanged.
 */
private object ReviewUiColors {
    val accent = Color(0xFFFB3955)
    val action = Color(0xFFC6323D)
    val primary = Color(0xFFF4F7FB)
    val secondary = Color(0xFFB4C6D3)
    val frameA = Color(0xFF0B141B)
    val frameB = Color(0xFF04080C)
    val cardA = Color(0xFF193544)
    val cardB = Color(0xFF101F2A)
    val cardBorder = Color(0xFF4D6B7D)
    val warning = Color(0xFF1C303A)
}

@Composable
internal fun TracksideEngine15ReviewCandidateScreen(
    model: TracksideReviewCandidate,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    showPreviewNavigation: Boolean = true
) {
    val stage = model.stage
    Box(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(ReviewUiColors.frameA, ReviewUiColors.frameB)))
            .testTag("engine15-review-candidate")
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 17.dp, end = 17.dp, top = 16.dp,
                    bottom = if (showPreviewNavigation) 119.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    TracksideReviewCandidateCopy.title(stage),
                    modifier = Modifier.weight(1f).semantics { heading() }
                        .testTag("engine15-review-title"),
                    color = ReviewUiColors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    maxLines = 2
                )
                ReviewStatusChip(stage)
            }

            Text(
                TracksideReviewCandidateCopy.eyebrow(stage),
                color = Color(0xFFFF7D75),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("engine15-review-eyebrow")
            )

            Spacer(Modifier.height(3.dp))

            ReviewCard(tag = "engine15-review-original") {
                ReviewEyebrow(model.workoutDateLabel, ReviewUiColors.accent)
                Text(
                    if (stage == TracksideReviewStage.ACCEPTED_REST) "Recovery rest" else model.workoutTitle,
                    color = ReviewUiColors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ReviewMetric(
                        if (stage == TracksideReviewStage.ACCEPTED_TIME || stage == TracksideReviewStage.ACCEPTED_REST)
                            "CURRENT TIME" else "ORIGINAL TIME",
                        "${model.activeMinutes} min",
                        modifier = Modifier.weight(1f),
                        accent = stage == TracksideReviewStage.ACCEPTED_TIME ||
                            stage == TracksideReviewStage.ACCEPTED_REST
                    )
                    ReviewMetric(
                        if (stage == TracksideReviewStage.ACCEPTED_TIME) "DISTANCE TARGET" else
                            if (stage == TracksideReviewStage.ACCEPTED_REST) "RUNNING TARGET" else
                                "ORIGINAL DISTANCE",
                        model.activeDistanceTargetKm?.let { String.format(Locale.ROOT, "%.1f km", it) } ?: "None",
                        modifier = Modifier.weight(1f)
                    )
                }
                ReviewSupport(
                    when(stage) {
                        TracksideReviewStage.PREVIEW ->
                            "The original workout stays scheduled until you approve."
                        TracksideReviewStage.PENDING ->
                            "Currently active: ${model.plannedMinutes} min · ${model.originalDistanceLabel} original plan."
                        TracksideReviewStage.ACCEPTED_TIME ->
                            "Time-only. Original pace and structured steps do not apply."
                        TracksideReviewStage.ACCEPTED_REST ->
                            "No workout, distance, or steps are required today."
                        TracksideReviewStage.STALE ->
                            "The original ${model.plannedMinutes} min / ${model.originalDistanceLabel} plan is still active."
                    }
                )
            }

            ReviewCard(tag = "engine15-review-change") {
                ReviewEyebrow(when(stage) {
                    TracksideReviewStage.PREVIEW -> "REVIEW-ONLY PROPOSAL"
                    TracksideReviewStage.PENDING -> "EXPLICIT APPROVAL REQUIRED"
                    TracksideReviewStage.ACCEPTED_TIME -> "ORIGINAL PRESCRIPTION PRESERVED"
                    TracksideReviewStage.ACCEPTED_REST -> "ORIGINAL WORKOUT HISTORY"
                    TracksideReviewStage.STALE -> "PREVIOUS REVIEW EXPIRED"
                })
                Text(
                    TracksideReviewCandidateCopy.changeTitle(model),
                    color = ReviewUiColors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    modifier = Modifier.testTag("engine15-review-change-title")
                )
                ReviewSupport(when(stage) {
                    TracksideReviewStage.PREVIEW -> {
                        if (model.proposedMinutes == 0) {
                            "A recovery rest is proposed. No catch-up mileage."
                        } else {
                            val percentage = ((model.plannedMinutes - model.proposedMinutes!!) * 100 /
                                model.plannedMinutes)
                            "${percentage}% less time. No extra distance or harder pace."
                        }
                    }
                    TracksideReviewStage.PENDING -> if (model.proposedMinutes == 0)
                        "This reviewed rest option is not active yet."
                    else "This time-only option is not active yet."
                    TracksideReviewStage.ACCEPTED_TIME ->
                        "The original distance stays in your history, not as a current goal."
                    TracksideReviewStage.ACCEPTED_REST ->
                        "Original ${model.originalDistanceLabel} prescription remains in saved history."
                    TracksideReviewStage.STALE ->
                        "The saved effort or plan details changed after staging."
                })
                if (stage == TracksideReviewStage.PREVIEW || stage == TracksideReviewStage.PENDING) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReviewMetric("CURRENT", "${model.plannedMinutes} min", Modifier.weight(1f))
                        ReviewMetric("SUGGESTED", "${model.proposedMinutes} min", Modifier.weight(1f),
                            accent = true)
                    }
                }
                Surface(
                    color = ReviewUiColors.warning,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.fillMaxWidth().testTag("engine15-review-disclosure")
                ) {
                    Text(
                        TracksideReviewCandidateCopy.disclosure(model),
                        color = ReviewUiColors.secondary,
                        fontSize = 11.6.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)
                    )
                }
            }

            ReviewCard(tag = "engine15-review-evidence") {
                ReviewEyebrow("WHY THIS APPEARED")
                Text(
                    if (stage == TracksideReviewStage.ACCEPTED_TIME) "Approved based on your recorded effort"
                    else if (stage == TracksideReviewStage.STALE) "Source record was edited"
                    else model.sourceHeading,
                    color = ReviewUiColors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag("engine15-review-evidence-heading")
                )
                ReviewSupport(model.sourceDetail)
                ReviewSupport(model.uncertainty)
            }

            Text(
                when(stage) {
                    TracksideReviewStage.PREVIEW -> "Nothing has been staged or applied."
                    TracksideReviewStage.PENDING -> "Choose one action. No silent schedule changes."
                    TracksideReviewStage.ACCEPTED_TIME ->
                        "Saved on this device. The original record is retained."
                    TracksideReviewStage.ACCEPTED_REST -> "Rest was explicitly accepted and saved offline."
                    TracksideReviewStage.STALE ->
                        "Refresh the preview. Never silently retry an old approval."
                },
                color = ReviewUiColors.secondary,
                fontSize = 11.sp,
                modifier = Modifier.testTag("engine15-review-footnote")
            )

            val (primary, secondary) = TracksideReviewCandidateCopy.actions(model)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewAction(primary, true, Modifier.weight(1f), onPrimary, "engine15-review-primary")
                ReviewAction(secondary, false, Modifier.weight(1f), onSecondary, "engine15-review-secondary")
            }
        }
        if (showPreviewNavigation) {
            TracksideReviewPreviewNavigation(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 25.dp)
            )
        }
    }
}

@Composable
private fun ReviewStatusChip(stage: TracksideReviewStage) {
    val completed = stage in setOf(TracksideReviewStage.ACCEPTED_TIME, TracksideReviewStage.ACCEPTED_REST)
    val stale = stage == TracksideReviewStage.STALE
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (completed) Color(0xFF173C37) else if (stale) Color(0xFF263843) else Color(0xFF42202D),
        border = BorderStroke(0.8.dp,
            if (completed) Color(0xFF387E73) else if (stale) Color(0xFF587383) else Color(0xFF9E3851))
    ) {
        Text(
            TracksideReviewCandidateCopy.chip(stage),
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 9.6.sp,
            color = if (completed) Color(0xFF65D9C2) else if (stale)
                ReviewUiColors.secondary else ReviewUiColors.primary
        )
    }
}

@Composable
private fun ReviewCard(
    tag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = ReviewUiColors.cardB,
        border = BorderStroke(1.dp, ReviewUiColors.cardBorder)
    ) {
        Column(
            modifier = Modifier.background(
                Brush.horizontalGradient(listOf(ReviewUiColors.cardA, ReviewUiColors.cardB))
            ).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content
        )
    }
}

@Composable
private fun ReviewEyebrow(label: String, tint: Color = ReviewUiColors.secondary) {
    Text(label, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun ReviewSupport(text: String) {
    Text(text, color = ReviewUiColors.secondary, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
private fun ReviewMetric(
    label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ReviewEyebrow(label)
        Text(value, color = if (accent) Color(0xFFFF7D75) else ReviewUiColors.primary,
            fontWeight = FontWeight.Bold, fontSize = 23.sp, maxLines = 2)
    }
}

@Composable
private fun ReviewAction(
    label: String, primary: Boolean, modifier: Modifier,
    onClick: () -> Unit, tag: String
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 49.dp).testTag(tag),
        shape = RoundedCornerShape(12.dp),
        color = if (primary) ReviewUiColors.action else Color(0xFF203442),
        contentColor = ReviewUiColors.primary,
        border = if (primary) null else BorderStroke(1.dp, Color(0xFF59758A))
    ) {
        Box(Modifier.defaultMinSize(minHeight = 49.dp).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.4.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/**
 * Preview-only copy of native Trackside global navigation. This is deliberately
 * inert: the real app shell already owns the navigation callbacks and should
 * pass showPreviewNavigation=false when an approved route is later added.
 */
@Composable
private fun TracksideReviewPreviewNavigation(modifier: Modifier = Modifier) {
    val tabs = listOf(
        Triple("Today", R.drawable.trackside_figma_home, false),
        Triple("Training", R.drawable.trackside_figma_calendar, true),
        Triple("Activity", R.drawable.trackside_figma_activity, false),
        Triple("Library", R.drawable.trackside_figma_library, false),
        Triple("More", R.drawable.trackside_figma_more, false)
    )
    Row(
        modifier.fillMaxWidth().height(67.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.horizontalGradient(listOf(
                Color(0xFF25323D), Color(0xFF101B22), Color(0xFF081219)
            )))
            .border(1.dp, Color(0xFF677987), RoundedCornerShape(32.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .semantics { invisibleToUser() }
            .testTag("engine15-preview-navigation"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (label, iconRes, active) ->
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (active) Color(0x553B1522) else Color.Transparent)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(painterResource(iconRes), contentDescription = null,
                    tint = if (active) Color(0xFFFF344E) else Color(0xFFB7C5CF),
                    modifier = Modifier.size(19.dp))
                Text(label, color = if (active) Color(0xFFFF344E) else Color(0xFFB7C5CF),
                    fontSize = 9.6.sp, maxLines = 1)
            }
        }
    }
}

@Preview(name = "E3 review preview · 412", widthDp = 412, heightDp = 950, showBackground = true)
@Preview(name = "E3 review preview · 360 · text 1.3×", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun PreviewEngine15Proposal() {
    StrideTheme {
        TracksideEngine15ReviewCandidateScreen(
            model = TracksideReviewCandidate(
                TracksideReviewStage.PREVIEW, "Easy run", "THU 05 NOV · RUNNING",
                60, 8.0, 45, "Mon 02 Nov · Recorded easy run",
                "You logged 35 min and effort 9/10."
            ), onPrimary = {}, onSecondary = {}
        )
    }
}

@Preview(name = "E3 review pending · 412", widthDp = 412, heightDp = 950)
@Preview(name = "E3 review pending · 360 · text 1.3×", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun PreviewEngine15Pending() {
    StrideTheme {
        TracksideEngine15ReviewCandidateScreen(
            model = TracksideReviewCandidate(
                TracksideReviewStage.PENDING, "Easy run", "THU 05 NOV · RUNNING",
                60, 8.0, 45, "Mon 02 Nov · Recorded easy run",
                "35 min actual · RPE 9/10 (self-reported)."
            ), onPrimary = {}, onSecondary = {}
        )
    }
}

@Preview(name = "E3 accepted time only · 412", widthDp = 412, heightDp = 950)
@Preview(name = "E3 accepted time · 360 · text 1.3×", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun PreviewEngine15AcceptedTime() {
    StrideTheme {
        TracksideEngine15ReviewCandidateScreen(
            model = TracksideReviewCandidate(
                TracksideReviewStage.ACCEPTED_TIME, "Easy run", "THU 05 NOV · RUNNING",
                60, 8.0, 45, "Mon 02 Nov · Recorded easy run",
                "Previous easy run · 35 min · RPE 9/10."
            ), onPrimary = {}, onSecondary = {}
        )
    }
}

@Preview(name = "E3 accepted rest · 412", widthDp = 412, heightDp = 950)
@Preview(name = "E3 rest · 360 · text 1.3×", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun PreviewEngine15AcceptedRest() {
    StrideTheme {
        TracksideEngine15ReviewCandidateScreen(
            model = TracksideReviewCandidate(
                TracksideReviewStage.ACCEPTED_REST, "Easy run", "THU 05 NOV · RUNNING",
                60, 8.0, 0, "Mon 02 Nov · Illness reported",
                "You explicitly selected Illness as the missed reason.",
                "This is not a diagnosis and was never applied automatically."
            ), onPrimary = {}, onSecondary = {}
        )
    }
}

@Preview(name = "E3 stale review · 412", widthDp = 412, heightDp = 950)
@Preview(name = "E3 stale · 360 · text 1.3×", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun PreviewEngine15Stale() {
    StrideTheme {
        TracksideEngine15ReviewCandidateScreen(
            model = TracksideReviewCandidate(
                TracksideReviewStage.STALE, "Easy run", "THU 05 NOV · RUNNING",
                60, 8.0, 45, "Mon 02 Nov · Recorded easy run",
                "The earlier RPE or log revision no longer matches.",
                "A changed record invalidates the old recommendation."
            ), onPrimary = {}, onSecondary = {}
        )
    }
}

/** Additional zero-minute pending-rest candidate: no training target changes before approval. */
@Preview(name = "E3 pending rest · 360 · text 1.3×", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun PreviewEngine15PendingRest() {
    StrideTheme {
        TracksideEngine15ReviewCandidateScreen(
            model = TracksideReviewCandidate(
                TracksideReviewStage.PENDING, "Easy run", "THU 05 NOV · RUNNING",
                60, 8.0, 0, "Mon 02 Nov · Illness reported",
                "You explicitly selected Illness as the missed reason.",
                "This is not a diagnosis and was never applied automatically."
            ), onPrimary = {}, onSecondary = {}
        )
    }
}
