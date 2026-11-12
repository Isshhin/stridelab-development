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
                        restoreGarminCloud = { 0 }
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

    @Test fun guestCanOpenGarminWithoutStrideLabSignInAndCloudControlsStayHidden() {
        val state=TrainingState(initializing=false,
            localGarminOwner="local-garmin-synthetic-guest")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state=state,online=false,
                        actions=NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("more-row-garmin"))
        compose.onNodeWithTag("more-row-garmin").performClick()
        compose.onNodeWithTag("garmin-modal-sheet").assertExists()
        compose.onNodeWithTag("garmin-connection-card").assertExists()
        compose.onNodeWithTag("garmin-cloud-toggle").assertDoesNotExist()
        compose.onNodeWithTag("garmin-cloud-sign-in-optional").assertExists()
        compose.onNodeWithTag("garmin-modal-close").performClick()
        compose.onNodeWithTag("garmin-modal-sheet").assertDoesNotExist()
        compose.onNodeWithTag("more-sign-in").assertExists()
    }

    @Test fun signedOutGarminHistoryIsVisibleInTrainingAndActivityDetail() {
        val started=java.time.LocalDate.now().atStartOfDay(
            java.time.ZoneId.systemDefault()).toInstant()
        val activity=ActivityRecord(
            "garmin-local-guest-run","Guest Garmin Run","running","garmin",
            started,5000.0,1800.0,1750.0,350.0,143.0,10.0
        )
        val state=TrainingState(
            initializing=false, localGarminOwner="local-garmin-synthetic-guest",
            localGarminIds=setOf(activity.id), activities=listOf(activity)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(state, false,
                        NativeActions({}, {}, {}, {}, { _, _ -> }))
                }
            }
        }
        compose.onNodeWithTag("nav-training").performClick()
        compose.onNodeWithTag("training-unified-timeline")
            .performScrollToNode(hasTestTag("timeline-recorded-garmin-local-guest-run"))
        compose.onNodeWithTag("timeline-recorded-garmin-local-guest-run")
            .assertExists().performClick()
        compose.onNodeWithTag("activity-map-hero").assertExists()
        compose.onNodeWithText("Guest Garmin Run").assertExists()
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
