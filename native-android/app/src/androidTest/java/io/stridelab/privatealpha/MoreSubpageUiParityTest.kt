package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Current More visual shell / navigation regression, using synthetic source
 * summaries and inert backup callbacks. Does not access personal records.
 */
class MoreSubpageUiParityTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun backupSourceEligibilityAndSafetyCopyRemainVisible() {
        compose.activity.setContent {
            StrideTheme {
                LocalActivityArchiveScreen(
                    cloudAvailable = false,
                    onBack = {},
                    onExport = { error("This view test must not export a ZIP.") },
                    onRestore = { _,_ -> error("This view test must not restore a ZIP.") }
                )
            }
        }
        compose.onNodeWithTag("screen")
            .performScrollToNode(androidx.compose.ui.test.hasText(
                "ZIP files are NOT encrypted", substring = true))
        compose.onNodeWithText("ZIP files are NOT encrypted", substring = true).assertExists()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("archive-local-garmin"))
        compose.onNodeWithTag("archive-local-garmin").assertIsEnabled()
        compose.onNodeWithTag("archive-cloud-account").assertIsNotEnabled()
    }

    @Test fun localAccountAndGarminConnectionRemainIndependent() {
        val connected = mutableStateOf(false)
        val account = mutableStateOf<String?>(null)
        compose.activity.setContent {
            StrideTheme {
                TracksideMoreScreen(
                    TrainingState(initializing = false, account = account.value, localGarminOwner = "local-garmin-fixture"),
                    {}, {}, {}, {}, {}, {},
                    garminConnected = connected.value,
                    onActivityBackup = {}
                )
            }
        }
        compose.onNodeWithText("Not connected").assertExists()
        compose.runOnIdle { connected.value = true }
        compose.onNodeWithText("Local Mode").assertExists()
        compose.onNodeWithText("Not signed in · Saved on this device").assertExists()
        compose.onNodeWithTag("more-sign-in").assertIsEnabled()
        compose.onNodeWithText("Signed in").assertDoesNotExist()
        compose.onNodeWithTag("more-garmin-status", useUnmergedTree = true)
            .assert(hasText("Connected") and hasAnyAncestor(hasTestTag("more-row-garmin")))
        compose.onNode(hasText("Connected") and hasAnyAncestor(hasTestTag("more-profile-card")),
            useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Running analytics").assertDoesNotExist()
        compose.onNodeWithText("Log strength and conditioning").assertDoesNotExist()
        compose.onNodeWithTag("more-settings-shortcut").assertDoesNotExist()

        compose.runOnIdle { account.value = "runner@example.test" }
        compose.onNode(hasText("runner@example.test") and hasAnyAncestor(hasTestTag("more-profile-card")))
            .assertExists()
        compose.onNodeWithText("Signed in").assertExists()
        compose.onNodeWithTag("more-sign-in").assertDoesNotExist()
        compose.onNodeWithTag("more-garmin-status", useUnmergedTree = true).assert(hasText("Connected"))
    }

    @Test fun legacyMoreDestinationsAreAbsentFromTheNavigationGraph() {
        lateinit var nav: NavHostController
        compose.activity.setContent {
            StrideTheme {
                nav = rememberNavController()
                NativeStrideContent(TrainingState(initializing = false), true, NativeActions({}, { _, _ -> }), nav)
            }
        }
        compose.runOnIdle {
            assertNotNull(nav.graph.findNode("settings"))
            assertNotNull(nav.graph.findNode("more/activity-backup"))
            assertNull(nav.graph.findNode("more/analytics"))
            assertNull(nav.graph.findNode("more/analytics/compare"))
            assertNull(nav.graph.findNode("more/manual-logs"))
        }
    }
}
