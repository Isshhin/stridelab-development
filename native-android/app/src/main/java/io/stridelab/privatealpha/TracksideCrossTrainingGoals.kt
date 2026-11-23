package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val crossCoral = StrideLabVisualPalette.strengthCoral

private data class StrengthGoalChoice(
    val title: String,
    val detail: String,
    val policyGoal: String,
    val icon: Int
)

private val strengthGoalChoices = listOf(
    StrengthGoalChoice("Build Strength", "Get stronger and lift heavier with progressive overload.",
        "General strength", R.drawable.trackside_figma_activity),
    StrengthGoalChoice("Build Muscle", "Hypertrophy-focused volume for muscle size and definition.",
        "Muscle development", R.drawable.trackside_figma_loop),
    StrengthGoalChoice("Physique Development", "Balanced aesthetics and muscular symmetry across the week.",
        "Muscle development", R.drawable.trackside_figma_activity),
    StrengthGoalChoice("Maintain Fitness", "Efficient strength endurance to stay resilient year-round.",
        "Strength endurance", R.drawable.trackside_figma_loop)
) + listOf(
    "Strength for Runners" to "Develop single-leg stability, glutes, calves and core for running.",
    "Upper Body & Posture" to "Train shoulders and upper body while leaving the legs fresh.",
    "Full Body" to "Combine lower-body stability with upper-body pushing and pulling."
).map { (goal, detail) -> StrengthGoalChoice(goal, detail, goal, R.drawable.trackside_figma_activity) }

internal fun strengthChoicePolicy(title: String) =
    strengthGoalChoices.firstOrNull { it.title == title }?.policyGoal
        ?: "General strength"

/**
 * Art-directed Strength Goal selection screen.
 * Pure AMOLED obsidian cards, crisp Strength Coral icon tiles (zero lime-on-lime icons!),
 * high-contrast typography and circular selection check badges.
 */
@Composable
internal fun TracksideStrengthGoalScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val active = strengthGoalChoices.firstOrNull { it.title == selected }
        ?: strengthGoalChoices.first()

    TracksideBuilderFrame(
        progressStep = 2,
        toolbarTitle = "Strength goal",
        eyebrow = "STRENGTH / GOAL",
        headline = "What are you\nworking toward?",
        description = "StrideLab shapes your sets, rep ranges, and progression around your goal.",
        ctaLabel = "Continue",
        onBack = onBack,
        onContinue = onContinue,
        descriptionBottomGap = 18,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        strengthGoalChoices.forEachIndexed { index, choice ->
            val chosen = active.title == choice.title
            Surface(
                onClick = { onSelect(choice.title) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .testTag("builder-strength-goal-$index"),
                color = if (chosen) crossCoral.copy(alpha = 0.16f) else PlanBuilderPalette.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    if (chosen) 1.5.dp else 1.dp,
                    if (chosen) crossCoral else PlanBuilderPalette.border
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (chosen) crossCoral else PlanBuilderPalette.surfaceRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(choice.icon),
                            contentDescription = null,
                            tint = if (chosen) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            choice.title,
                            color = if (chosen) crossCoral else PlanBuilderPalette.text,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            choice.detail,
                            color = if (chosen) PlanBuilderPalette.text.copy(alpha = 0.85f) else PlanBuilderPalette.secondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
            if (index < strengthGoalChoices.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Art-directed Cross-Training Goals & Ability screen.
 * Pure AMOLED obsidian cards with discipline-specific semantic color coding.
 */
@Composable
internal fun TracksideCrossTrainingGoalsScreen(
    request: PlanRequest,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onEditStrengthGoal: () -> Unit,
    onGoal: (String, String) -> Unit,
    onCount: (String, Int) -> Unit,
    onExperience: (String, Int) -> Unit
) {
    val active = listOf("str", "cal", "cond").filter {
        WeeklyPlacement.count(request, it) > 0
    }

    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle = "Cross-training",
        eyebrow = "PLAN / DISCIPLINES",
        headline = "Tune your\ncross-training",
        description = "Set goals, weekly frequency, and ability for each supporting discipline.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = active.isNotEmpty(),
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        active.forEachIndexed { idx, discipline ->
            val accent = StrideLabVisualPalette.disciplineColor(discipline)
            Text(
                when (discipline) {
                    "str" -> "STRENGTH"
                    "cal" -> "CALISTHENICS"
                    else -> "CONDITIONING"
                },
                color = accent,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            if (discipline == "str") {
                Surface(
                    onClick = onEditStrengthGoal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .testTag("builder-strength-edit-goal"),
                    color = PlanBuilderPalette.surface,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, PlanBuilderPalette.border)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Strength goal", fontSize = 13.5.sp, color = PlanBuilderPalette.text, fontWeight = FontWeight.Bold)
                            Text(Engine1Engines.goal(request, "str"), fontSize = 12.sp, color = PlanBuilderPalette.secondary)
                        }
                        Text("Edit ›", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Engine1Engines.goals.getValue(discipline).forEach { goal ->
                        val chosen = Engine1Engines.goal(request, discipline) == goal
                        Surface(
                            onClick = { onGoal(discipline, goal) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("builder-cross-goal-$discipline-$goal"),
                            color = if (chosen) accent.copy(alpha = 0.16f) else PlanBuilderPalette.surface,
                            shape = RoundedCornerShape(13.dp),
                            border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) accent else PlanBuilderPalette.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    goal,
                                    color = if (chosen) accent else PlanBuilderPalette.secondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (chosen) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            val count = WeeklyPlacement.count(request, discipline)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PlanBuilderPalette.surface,
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, PlanBuilderPalette.border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SESSIONS / WEEK",
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = PlanBuilderPalette.muted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            count.toString(),
                            fontSize = 28.sp,
                            color = PlanBuilderPalette.text,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    val limit = if (discipline == "cond") 3 else 4
                    listOf(-1, 1).forEach { delta ->
                        val enabled = count + delta in 1..limit
                        Surface(
                            onClick = { onCount(discipline, count + delta) },
                            enabled = enabled,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(42.dp)
                                .testTag("builder-cross-count-$discipline-$delta"),
                            color = PlanBuilderPalette.surfaceRaised,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PlanBuilderPalette.borderStrong)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (delta < 0) "−" else "+",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) PlanBuilderPalette.text else PlanBuilderPalette.disabled
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (discipline == "str") {
                Text(
                    "STRENGTH EXPERIENCE",
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    color = PlanBuilderPalette.muted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                TracksideCrossAbilityRow(
                    listOf("Starting", "Intermediate", "Experienced"),
                    request.strengthExperience, accent, "strength"
                ) { onExperience("str", it) }
            }
            if (discipline == "cal") {
                listOf(
                    Triple("PUSHING ABILITY", request.calLevel, "push"),
                    Triple("PULLING ABILITY", request.pullLevel, "pull"),
                    Triple("CORE ABILITY", request.coreLevel, "core")
                ).forEach { (label, selected, key) ->
                    Text(
                        label,
                        fontSize = 10.sp,
                        letterSpacing = 1.3.sp,
                        color = PlanBuilderPalette.muted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    TracksideCrossAbilityRow(
                        listOf("Starting", "Progressing", "Confident"),
                        selected, accent, key
                    ) { onExperience(key, it) }
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (idx < active.lastIndex) Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun TracksideCrossAbilityRow(
    options: List<String>,
    chosen: Int,
    accent: Color,
    tag: String,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PlanBuilderPalette.surface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, PlanBuilderPalette.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = chosen == index
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("builder-cross-ability-$tag-$index"),
                    color = if (selected) accent else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (selected) PlanBuilderPalette.accentOn else PlanBuilderPalette.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
