package io.stridelab.privatealpha

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import org.junit.Rule
import org.junit.Test

class PlanBuilderRouteRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedResearchRouteSurvivesCompositionStateRestore() {
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
        next() // plan dates
        next() // availability

        compose.onNodeWithTag("builder-route-research").performScrollTo().performClick().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("builder-route-research").assertIsSelected()
    }
}
