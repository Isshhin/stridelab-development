package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Paused-plan presentation only. Resuming remains the existing revision-gated action. */
@Composable
internal fun TracksideParityPausedPlan(
    plan: PlanSnapshot,
    busy: Boolean,
    actions: PlanActions,
    onBack: () -> Unit
) {
    StridePlanScreen("Paused plan","Training",onBack) {
        item {
            Text("Your plan is on hold",
                style=MaterialTheme.typography.headlineMedium,
                color=StrideWorkoutVisuals.white)
            Text("Completed workouts and saved training history are preserved.",
                style=MaterialTheme.typography.bodyMedium,
                color=StrideWorkoutVisuals.muted)
        }
        if(plan.engineVersion==ENGINE15_VERSION && Engine1Engines.canManage(plan)) {
            item { Engine15ScheduleReview(plan,actions,busy) }
            item { Engine15RecommendationReview(plan,actions,busy) }
        }
        item {
            StrideWorkoutSection("When you resume") {
                Text("Review upcoming workouts. Keep, shift, or regenerate future sessions when supported by your saved plan.",
                    color=StrideWorkoutVisuals.white,
                    style=MaterialTheme.typography.bodyLarge)
                Text("Completed sessions remain unchanged.",
                    color=StrideWorkoutVisuals.muted,
                    style=MaterialTheme.typography.bodyMedium)
                StrideWorkoutPrimaryButton(
                    label="Resume plan",
                    onClick={ actions.approve(plan.id,plan.revision) },
                    enabled=!busy && Engine1Engines.canManage(plan) &&
                        plan.sessions.none { it.status=="unassigned" },
                    modifier=Modifier.testTag("manage-resume")
                )
            }
        }
    }
}
