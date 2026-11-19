package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AthleteHeartRatePreferencesUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun preferencesSaveAllThreeZoneMethodsAndClearAthleteSettings() {
        val account = "synthetic-athlete-hr"
        val preferences = AthleteHeartRatePreferences(compose.activity)
        preferences.clear(account)

        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideMoreDetailScreen(
                        page = MorePage.Preferences,
                        state = TrainingState(initializing = false, account = account),
                        onBack = {},

                        onSystemAppSettings = {}
                    )
                }
            }
        }

        compose.onNodeWithTag("athlete-hr-preference").assertExists()
        compose.onNodeWithTag("athlete-hr-method-max_bpm").assertExists()
        compose.onNodeWithTag("athlete-hr-method-lthr_percent").assertExists()
        compose.onNodeWithTag("athlete-hr-method-hrr_percent").assertExists()

        compose.onNodeWithTag("athlete-max-hr-input").performTextInput("197")
        compose.onNodeWithTag("athlete-max-hr-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(HeartRateZoneMethod.MAX_BPM, preferences.method(account))
            assertEquals(197, preferences.maximumHr(account))
        }

        compose.onNodeWithTag("athlete-hr-method-lthr_percent").performClick()
        compose.onNodeWithTag("athlete-lthr-input").performTextInput("170")
        compose.onNodeWithTag("athlete-max-hr-save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(HeartRateZoneMethod.LTHR_PERCENT, preferences.method(account))
            assertEquals(170, preferences.lactateThresholdHr(account))
        }

        compose.onNodeWithTag("athlete-hr-method-hrr_percent").performClick()
        compose.onNodeWithTag("athlete-hr-method-hrr_percent").assertIsSelected()
        compose.onNodeWithTag("athlete-resting-hr-input").performTextInput("52")
        compose.onNodeWithTag("athlete-hr-method-hrr_percent").assertIsSelected()
        compose.onNodeWithTag("athlete-max-hr-save").assertIsEnabled()
        compose.onNodeWithTag("athlete-max-hr-save").performScrollTo().performClick()
        compose.onNodeWithTag("athlete-hr-method-hrr_percent").assertIsSelected()
        compose.runOnIdle {
            assertEquals(HeartRateZoneMethod.HRR_PERCENT, preferences.method(account))
            assertEquals(197, preferences.maximumHr(account))
            assertEquals(52, preferences.restingHr(account))
        }

        compose.onNodeWithTag("athlete-max-hr-clear").performClick()
        compose.runOnIdle {
            assertNull(preferences.maximumHr(account))
            assertNull(preferences.lactateThresholdHr(account))
            assertNull(preferences.restingHr(account))
            assertEquals(HeartRateZoneMethod.MAX_BPM, preferences.method(account))
        }
    }
}
