package io.stridelab.privatealpha

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertExists
import org.junit.Rule
import org.junit.Test

class PlanBuilderRouteRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedResearchPanelSurvivesCompositionStateRestore() {
        val restoration = StateRestorationTester(compose)
        val state = TrainingState(initializing = false, localPlanOwner = "local-plan-m10d-test")
        restoration.setContent {
            StrideTheme {
                PlanBuilder(state, PlanActions(), discipline = "run", onDone = {},
                    onSignIn = {}, onPreview = { _, _ -> })
            }
        }
        fun next() = compose.onNodeWithTag("builder-figma-continue").performClick()

        next() // discipline
        next() // running route
        compose.onNodeWithTag("builder-distance-10K").performClick()
        next() // race goal
        compose.onNodeWithTag("builder-experience-1").performClick()
        next() // running ability
        next() // running schedule
        next() // plan dates -> final availability screen

        compose.onNodeWithTag("builder-availability-research-toggle")
            .performScrollTo().performClick()
        compose.onNodeWithTag("builder-experimental-research-panel").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("builder-experimental-research-panel").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertExists()
    }
}
