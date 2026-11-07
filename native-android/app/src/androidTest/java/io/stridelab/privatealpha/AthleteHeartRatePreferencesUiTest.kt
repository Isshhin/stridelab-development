package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AthleteHeartRatePreferencesUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun preferencesSaveAndClearAccountScopedMaximumHeartRate() {
        val account = "synthetic-athlete-hr"
        val preferences = AthleteHeartRatePreferences(compose.activity)
        preferences.setMaximumHr(account, null)

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideMoreDetailScreen(
                        page = MorePage.Preferences,
                        state = TrainingState(initializing = false, account = account),
                        onBack = {},
                        onSignIn = {},
                        onLegacyBackups = {},
                        onSystemAppSettings = {},
                        syncGarmin = null
                    )
                }
            }
        }

        compose.onNodeWithTag("athlete-hr-preference").assertExists()
        compose.onNodeWithTag("athlete-max-hr-input").performTextInput("197")
        compose.onNodeWithTag("athlete-max-hr-save").performClick()
        compose.runOnIdle {
            assertEquals(197, preferences.maximumHr(account))
        }
        compose.onNodeWithText("197").assertExists()

        compose.onNodeWithTag("athlete-max-hr-clear").performClick()
        compose.runOnIdle {
            assertNull(preferences.maximumHr(account))
        }
    }
}
