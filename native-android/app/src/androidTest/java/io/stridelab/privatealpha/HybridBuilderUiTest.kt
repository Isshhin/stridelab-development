package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class HybridBuilderUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun allSixFocusCardsStaySelectedWithSeparateResistanceTracks() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(TrainingState(initializing = false, account = "hybrid-ui"),
                        PlanActions(), onDone = {}, onSignIn = {}, onPreview = { _, _ -> })
                }
            }
        }
        val tracks = listOf("run", "plyo", "iso", "str", "cal", "cond")
        for (track in tracks) {
            val focus = compose.onNodeWithTag("discipline-$track")
            focus.performScrollTo()
            if (!focus.fetchSemanticsNode().config[SemanticsProperties.Selected]) focus.performClick()
            focus.assertIsSelected()
        }
        tracks.forEach { compose.onNodeWithTag("discipline-$it").assertIsSelected() }
        compose.onNodeWithTag("discipline-plyo").performScrollTo().performClick()
        compose.onNodeWithTag("discipline-plyo").assertIsNotSelected()
        compose.onNodeWithTag("discipline-iso").assertIsSelected()
        compose.onNodeWithTag("discipline-str").assertIsSelected()
    }
}
