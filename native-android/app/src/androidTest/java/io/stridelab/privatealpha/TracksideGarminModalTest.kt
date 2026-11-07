package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Pure navigation/UI check; no Garmin credentials, network or downloads. */
class TracksideGarminModalTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun cloudBackupStartsDisabledAndRequiresExplicitConfirmation() {
        val owner = "synthetic-garmin-cloud-consent"
        compose.runOnUiThread {
            GarminSyncPreferences(compose.activity).setCloudBackup(owner, false)
            compose.activity.setContent {
                StrideTheme {
                    GarminSettingsCard(
                        accountId = owner,
                        backupGarminCloud = { 0 },
                        restoreGarminCloud = { 0 },
                        cloudSignedIn = true
                    )
                }
            }
        }
        compose.onNodeWithTag("garmin-cloud-backup-now").assertDoesNotExist()
        compose.onNodeWithTag("garmin-cloud-toggle").performClick()
        compose.onNodeWithText("Enable Garmin cloud backup?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("garmin-cloud-backup-now").assertDoesNotExist()
        compose.onNodeWithTag("garmin-cloud-toggle").performClick()
        compose.onNodeWithText("Enable backup").performClick()
        compose.onNodeWithTag("garmin-cloud-backup-now").assertExists()
        compose.onNodeWithTag("garmin-cloud-restore").assertExists()
        compose.runOnIdle {
            org.junit.Assert.assertTrue(
                GarminSyncPreferences(compose.activity).cloudBackup(owner)
            )
            GarminSyncPreferences(compose.activity).setCloudBackup(owner, false)
        }
    }

    @Test fun garminOpensAboveMoreAndClosesWithoutCreatingAnotherPage() {
        val state = TrainingState(initializing = false,
            account = "synthetic-modal-account")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = state,
                        online = false,
                        actions = NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("More")
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("more-row-garmin"))
        compose.onNodeWithTag("more-row-garmin").performClick()
        compose.onNodeWithTag("garmin-modal-sheet").assertExists()
        compose.onNodeWithTag("garmin-connection-card").assertExists()
        compose.onNodeWithTag("garmin-modal-close").performClick()
        compose.onNodeWithTag("garmin-modal-sheet").assertDoesNotExist()
        compose.onNodeWithTag("screen-title").assertTextEquals("More")
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }
}
