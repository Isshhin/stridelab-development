package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Synthetic UI only; no sign-in or device state changes. */
class BuildIdentificationUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun aboutClearlyIdentifiesInstalledPackageAndVersionCode() {
        compose.activity.setContent {
            StrideTheme {
                TracksideMoreDetailScreen(
                    page = MorePage.About,
                    state = TrainingState(initializing = false),
                    onBack = {},
                    onSignIn = {},
                    onSystemAppSettings = {},
                    syncGarmin = null
                )
            }
        }
        compose.onNodeWithText("Installed build", substring = true).assertExists()
        compose.onNodeWithText("Package: " + BuildConfig.APPLICATION_ID, substring = true)
            .assertExists()
        compose.onNodeWithText("Version code: " + BuildConfig.VERSION_CODE, substring = true)
            .assertExists()
    }
}
