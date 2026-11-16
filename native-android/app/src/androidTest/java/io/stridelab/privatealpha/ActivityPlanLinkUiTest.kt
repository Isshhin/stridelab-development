package io.stridelab.privatealpha

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Selection highlight is a local UI state; only confirmation links a source. */
class ActivityPlanLinkUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun highlightsOneEligibleRunAndWaitsForExplicitLinkConfirmation() {
        val date=LocalDate.now().minusDays(1)
        val today=date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val session=PlannedSession("planned-1",date,"run","Easy run",45,8.0,"easy")
        val plan=PlanSnapshot("plan","synthetic",ENGINE15_VERSION,ENGINE15_POLICY,
            Instant.EPOCH,PlanRequest(policyChoice="engine15"),
            listOf(session),emptyList(),status="approved")
        val activity=ActivityRecord("activity","Morning run","running","garmin",
            today,8000.0,3000.0,2800.0,null,null,null)
        var linked:String?=null
        compose.setContent {
            StrideTheme {
                ActivityPlanLink(
                    state=TrainingState(initializing=false,plans=listOf(plan)),
                    activity=activity,
                    actions=PlanActions(link={ _,_,sessionId,sourceId ->
                        linked="$sessionId:$sourceId"
                    })
                )
            }
        }
        compose.onNodeWithTag("activity-link-run-action").performClick()
        compose.onNodeWithTag("activity-link-bottom-sheet").assertExists()
        compose.onNodeWithTag("activity-link-confirm").assertExists()
        compose.onNodeWithTag("activity-link-option-plan-planned-1").performClick()
        compose.onNodeWithTag("activity-link-confirm").assertIsEnabled()
        compose.runOnIdle { assertEquals(null,linked) }
        compose.onNodeWithTag("activity-link-confirm").performClick()
        compose.runOnIdle { assertEquals("planned-1:activity",linked) }
    }
}
