package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** UI-only regression coverage for private, manual-export feedback. */
class PlanFeedbackFormUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private fun syntheticPlan() = PlanSnapshot(
        id="feedback-synthetic",owner="synthetic-owner",
        engineVersion=ENGINE1_VERSION,policyId=ENGINE1_POLICY,
        createdAt=Instant.EPOCH,
        request=PlanRequest(start=LocalDate.now(),weeks=2),
        sessions=emptyList(),explanations=emptyList(),status="approved"
    )

    @Test fun feedbackRequiresThreeCharactersAndPreservesCategoryAndLocalAction() {
        val plan=syntheticPlan()
        var savedCategory:String?=null
        var savedText:String?=null
        var savedSession:String?="not-called"
        var calls=0
        val actions=PlanActions(feedback={ planId,revision,category,text,sessionId ->
            assertEquals(plan.id,planId)
            assertEquals(plan.revision,revision)
            savedCategory=category
            savedText=text
            savedSession=sessionId
            calls++
        })
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme { PlanFeedbackForm(plan,actions) }
            }
        }
        compose.onNodeWithTag("plan-feedback-form").assertExists()
        compose.onNodeWithTag("plan-feedback-save").assertIsNotEnabled()
        compose.onNodeWithText("Nothing is sent automatically.",substring=true).assertExists()
        compose.onNodeWithTag("plan-feedback-category-inappropriate-prescription")
            .performClick().assertIsSelected()
        compose.onNodeWithTag("plan-feedback-text").performTextInput("ab")
        compose.onNodeWithTag("plan-feedback-save").assertIsNotEnabled()
        compose.onNodeWithTag("plan-feedback-text").performTextInput("c")
        compose.onNodeWithTag("plan-feedback-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1,calls)
            assertEquals("Inappropriate prescription",savedCategory)
            assertEquals("abc",savedText)
            assertNull(savedSession)
        }
    }
}
