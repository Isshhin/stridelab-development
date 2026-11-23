package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The top loading indicator is an overlay: loading must not move the dock. */
class TracksideLoadingLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun loadingDoesNotShiftTabDockOrRecreateItsDestination() {
        val trainingState = mutableStateOf(TrainingState(initializing = false))
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state = trainingState.value,
                        online = true,
                        actions = NativeActions({}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("loading").assertDoesNotExist()
        compose.onNodeWithTag("nav-home").assertExists()
        val before = compose.onNodeWithTag("nav-home").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread {
            trainingState.value = trainingState.value.copy(busy = true)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("loading").assertExists()
        val whileLoading = compose.onNodeWithTag("nav-home").fetchSemanticsNode().boundsInRoot
        assertEquals("Loading must not move the navigation dock", before, whileLoading)
        compose.runOnUiThread {
            trainingState.value = trainingState.value.copy(busy = false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("loading").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("nav-home").fetchSemanticsNode().boundsInRoot)
    }
}
