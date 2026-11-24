package io.stridelab.privatealpha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal const val blueprintWorkoutRoute =
    "blueprint-workout/{planId}/{sessionId}/{generatedAt}/{loginGeneration}"

internal fun resolveBlueprintWorkout(
    state: TrainingState,
    planId: String,
    sessionId: String,
    generatedAt: String,
    loginGeneration: Long
): Pair<PlanSnapshot, PlannedSession>? {
    val owner = state.account ?: state.localPlanOwner ?: return null
    val draft = state.planPreview?.takeIf { it.id == planId } ?: state.blueprintDraft(planId) ?: return null
    if (state.loginGeneration != loginGeneration || draft.status != "draft" ||
        draft.owner != owner || draft.id != planId || draft.createdAt.toString() != generatedAt
    ) return null
    val shown = state.blueprintDraft(planId)?.takeIf {
        it.status == "draft" && it.owner == owner && it.id == draft.id &&
            it.createdAt == draft.createdAt
    } ?: return null
    return shown.sessions.singleOrNull { it.id == sessionId }?.let { shown to it }
}

@Composable
internal fun BlueprintWorkoutScreen(
    state: TrainingState,
    planId: String,
    sessionId: String,
    generatedAt: String,
    loginGeneration: Long,
    onBack: () -> Unit
) {
    val resolved = resolveBlueprintWorkout(state, planId, sessionId, generatedAt, loginGeneration)
    Screen(
        if (resolved == null) "Workout unavailable" else "Workout preview",
        "BLUEPRINT",
        hideHeader = true
    ) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.testTag("back-to-blueprint")) {
                Text("‹ Back to Blueprint", color = WorkoutDetailPalette.text)
            }
        }
        item {
            Text(
                if (resolved == null) "Workout unavailable" else "Blueprint workout preview",
                color = WorkoutDetailPalette.text,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.testTag("blueprint-workout-title")
            )
            Text(
                if (resolved == null) "This preview is no longer available for the current profile."
                else "Read-only preview · Activate the plan when you're ready.",
                color = WorkoutDetailPalette.textMuted
            )
        }
        if (resolved != null) {
            val (plan, session) = resolved
            val shown = plan.cardPresentation(session)
            if (session.discipline == "run") item {
                val target = Engine15SessionPresentation.forSession(plan, session.id)
                var selectedStep by rememberSaveable(plan.id, session.id) { mutableStateOf<Int?>(null) }
                TracksidePlannedRunningPreview(
                    session = session, target = target, displayTitle = shown.effectiveTitle,
                    onBack = onBack, planStart = plan.request.start,
                    selectedStepIndex = selectedStep, onSelectStep = { selectedStep = it },
                    planningPaceSecPerKm = plan.request.planningPace, showInlineTopBar = false
                )
            } else {
                item {
                    Text(shown.effectiveTitle, color = WorkoutDetailPalette.text,
                        style = MaterialTheme.typography.titleLarge)
                    Text("${session.date} · ${shown.effectiveMinutes} min", color = WorkoutDetailPalette.textMuted)
                }
                item(key = "blueprint-muscle-focus") {
                    val catalog = rememberAugmentedExerciseCatalog()
                    val focus = remember(shown.effectiveExercises, catalog) {
                        MuscleFocusProjector.forPlannedSession(shown.effectiveExercises, catalog)
                    }
                    SessionMuscleFocusSection(
                        focus = focus,
                        provenance = "Planned session focus"
                    )
                }
                items(shown.effectiveExercises.size) { index ->
                    val exercise = shown.effectiveExercises[index]
                    SignalCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(exercise.name, color = WorkoutDetailPalette.text,
                                style = MaterialTheme.typography.titleMedium)
                            Text(workoutDoseLabel(session, exercise, exercise.sets),
                                color = WorkoutDetailPalette.text)
                            Text(exercise.cue, color = WorkoutDetailPalette.textMuted)
                        }
                    }
                }
            }
        }
    }
}
