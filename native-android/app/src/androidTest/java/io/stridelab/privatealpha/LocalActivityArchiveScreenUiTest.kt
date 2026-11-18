package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Editor-only UI tests, with inert callbacks. No actual user data or filesystem changes. */
class LocalActivityArchiveScreenUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun signedOutUserCanSelectLocalGarminButNotCloudAccount() {
        compose.activity.setContent {
            StrideTheme {
                LocalActivityArchiveScreen(
                    cloudAvailable = false,
                    onBack = {},
                    onExport = { throw AssertionError("An export must not start from this test.") },
                    onRestore = { _,_ -> throw AssertionError("A restore must not start from this test.") }
                )
            }
        }
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("archive-source"))
        compose.onNodeWithTag("archive-local-garmin").assertIsEnabled()
        compose.onNodeWithTag("archive-cloud-account").assertIsNotEnabled()
        compose.onNodeWithText("ZIP files are NOT encrypted", substring = true).assertExists()
    }

    @Test fun signedInUserCanExplicitlySelectCloudOwnedActivities() {
        compose.activity.setContent {
            StrideTheme {
                LocalActivityArchiveScreen(
                    cloudAvailable = true,
                    onBack = {},
                    onExport = { throw AssertionError("An export must not start from this test.") },
                    onRestore = { _,_ -> throw AssertionError("A restore must not start from this test.") }
                )
            }
        }
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("archive-source"))
        compose.onNodeWithTag("archive-cloud-account").assertIsEnabled().performClick()
        compose.onNodeWithText(
            "Restore only while signed in to the same StrideLab account.",
            substring = true
        ).assertExists()
    }
}
