package io.stridelab.privatealpha

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Batch B device checks. Test data is synthetic, never a user's account.
 * The workflow runs this suite at 360x800dp/1.3x and 412x950dp/1.5x.
 */
class TracksideResponsiveQaTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private val start = LocalDate.now().plusDays(1)

    private fun snapshot(): PlanSnapshot = PlanSnapshot(
        id = "responsive-plan", owner = "synthetic-athlete",
        engineVersion = ENGINE1_VERSION, policyId = ENGINE1_POLICY,
        createdAt = Instant.EPOCH, request = PlanRequest(start = start, weeks = 2),
        sessions = listOf(
            PlannedSession("responsive-run", start, "run", "Long easy aerobic run", 70, 10.0, "easy"),
            PlannedSession("responsive-strength", start, "str", "Upper-body strength", 40, null, "strength"),
            PlannedSession("responsive-next", start.plusDays(2), "cal", "Push/pull", 35, null, "calisthenics")
        ),
        explanations = emptyList(), status = "approved"
    )

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    Box(Modifier.fillMaxSize().padding(12.dp)) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = compose.activity
        val config = context.resources.configuration
        val scale = (config.fontScale * 100f).toInt()
        val fileName = "${name}-${config.screenWidthDp}dp-font${scale}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            // MediaStore survives target-app data resets between instrumentation tests.
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StrideLabBatchB")
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val encoded = ByteArrayOutputStream().use { buffer ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, buffer)) {
                "Unable to encode screenshot: $fileName"
            }
            buffer.toByteArray()
        }
        assertTrue("QA screenshot encode empty: $fileName", encoded.isNotEmpty())
        // MediaStore.SIZE can remain zero until the media provider scans the new file.
        // Verify the encoded bytes and output stream directly; CI subsequently verifies
        // all five persisted PNG files are present and nonempty on the device.
        values.put(MediaStore.Images.Media.IS_PENDING, 1)
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Unable to create MediaStore screenshot: $fileName"
        }
        val resolver = context.contentResolver
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { out ->
                out.write(encoded)
                out.flush()
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Test fun approvedBuilderStrengthGoalAndSetupRenderAtCurrentViewport() {
        val state=TrainingState(initializing=false,account="synthetic-responsive")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),
                        discipline="str",onDone={},onSignIn={},
                        onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("figma-builder-frame").assertExists()
        screenshot("builder-disciplines")
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-goal-1").assertExists()
        screenshot("builder-strength-goal")
        compose.onNodeWithTag("builder-strength-goal-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-weekly").assertExists()
        compose.onNodeWithTag("builder-strength-split").assertExists()
        screenshot("builder-strength-setup")
        compose.onNodeWithTag("builder-strength-customize").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        screenshot("builder-strength-unassigned")
        // The weekday picker is below the fold at 360dp/130%. A real user
        // must scroll it into view before tapping; do not dispatch an offscreen tap.
        compose.onNodeWithTag("builder-strength-weekday-1").performScrollTo()
        compose.onNodeWithTag("builder-strength-weekday-1").assertIsDisplayed()
        compose.onNodeWithTag("builder-strength-weekday-1").performClick()
        // Capture before asserting, so an unexpected placement-state failure is diagnosable.
        screenshot("builder-strength-after-first-tap")
        compose.onNodeWithText("1 of 2 placed").assertExists()
        compose.onNodeWithTag("builder-strength-edit-schedule").performScrollTo()
        compose.onNodeWithTag("builder-strength-edit-schedule").performClick()
        compose.onNodeWithTag("builder-strength-session-1").performScrollTo()
        compose.onNodeWithTag("builder-strength-session-1").performClick()
        compose.onNodeWithTag("builder-strength-weekday-4").performScrollTo()
        compose.onNodeWithTag("builder-strength-weekday-4").assertIsDisplayed()
        compose.onNodeWithTag("builder-strength-weekday-4").performClick()
        // Always capture placement diagnostics, even if the final state is wrong.
        screenshot("builder-strength-two-taps")
        compose.onNodeWithText("2 of 2 placed").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-strength-placed")
        compose.onNodeWithTag("builder-strength-auto").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-strength-auto")
    }

    @Test fun strengthFigmaReferenceAndLargeFontPreferenceContentStayVisible() {
        // Figma 698:3: Monday Upper and Thursday Lower, Saturday unavailable.
        // Capture a matching manual scheduling state without an open editor.
        val request=PlanRequest(
            runDays=0,strengthDays=2,
            days=setOf(1,2,3,4,5,7),
            weeklySlots=mapOf("str" to listOf(1,4)),
            disciplineGoals=mapOf("str" to "Muscle development"),
            equipment=mapOf("db" to 1,"bands" to 1,"bb" to 1)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaStrengthSetup(
                        request=request,
                        selectedSlot=0,onSelectedSlot={},
                        onChangeMode={},onCount={},onAssign={_,_->},
                        onClear={},onEquipment={_,_->},
                        onBack={},onEditGoal={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-strength-customize").assertIsDisplayed()
        compose.onNodeWithTag("builder-strength-weekly").assertExists()
        compose.onNodeWithTag("builder-strength-split").assertExists()
        val config = compose.activity.resources.configuration
        if(config.fontScale <= 1.01f) {
            val weekly=compose.onNodeWithTag("builder-strength-weekly")
                .getUnclippedBoundsInRoot()
            assertTrue("Weekly card taller than Figma baseline tolerance: " +
                (weekly.bottom-weekly.top), (weekly.bottom-weekly.top).value <= 250f)
            if(config.screenHeightDp >= 900) {
                val split=compose.onNodeWithTag("builder-strength-split")
                    .getUnclippedBoundsInRoot()
                val continueCta=compose.onNodeWithTag("builder-figma-continue")
                    .getUnclippedBoundsInRoot()
                assertTrue("Training split overlaps Continue CTA",
                    split.bottom <= continueCta.top - 2.dp)
            }
        }
        // These bounds checks are intentionally run in every viewport/font matrix.
        listOf(
            "builder-strength-setup-goal" to "Build muscle",
            "builder-strength-setup-emphasis" to "Not available in Engine 1"
        ).forEach { (tag,label) ->
            val card=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val text=compose.onNodeWithText(label,useUnmergedTree=true)
                .getUnclippedBoundsInRoot()
            assertTrue("$label extends below its preference card",
                text.bottom <= card.bottom - 1.dp)
        }
        val mode=compose.onNodeWithTag("builder-strength-auto")
            .getUnclippedBoundsInRoot()
        val modeLabel=compose.onNodeWithText("Let StrideLab decide",
            useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Mode label extends past selector",
            modeLabel.right <= mode.right + 1.dp)
        screenshot("builder-strength-figma-reference")
    }

    @Test fun approvedCalisthenicsWeekAndUnassignedStatesRender() {
        val state=TrainingState(initializing=false,account="synthetic-responsive")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),
                        discipline="cal",onDone={},onSignIn={},
                        onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cross-goal-cal-Pull-up progression").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cali-weekly").assertExists()
        screenshot("builder-calisthenics-setup")
        compose.onNodeWithTag("builder-cali-customize").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        screenshot("builder-calisthenics-unassigned")
        compose.onNodeWithTag("builder-cali-day-3").performClick()
        compose.onNodeWithTag("builder-cali-session-type-Pull").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("builder-cali-day-5").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-calisthenics-assigned")
    }

    @Test fun calisthenicsFigmaReferenceAndLargeFontSectionsRemainReachable() {
        // Figma 614:335: Wednesday Push, Friday Pull, Saturday unavailable.
        // Match the approved manual state with the editor focused on Wednesday.
        val request=PlanRequest(
            runDays=0,strengthDays=0,calDays=2,
            days=setOf(1,2,3,4,5,7),
            weeklySlots=mapOf("cal" to listOf(3,5)),
            calLevel=1,pullLevel=0,coreLevel=1
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaCalisthenicsSetup(
                        request=request,
                        selectedSlot=0,onSelectedSlot={},
                        onMode={},onCount={},onAssign={_,_->},
                        onClear={},onAbility={_,_->},
                        onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-cali-customize").assertIsDisplayed()
        val mode=compose.onNodeWithTag("builder-cali-auto")
            .getUnclippedBoundsInRoot()
        val modeLabel=compose.onNodeWithText("Let StrideLab decide",
            useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Calisthenics mode label clipped horizontally",
            modeLabel.left >= mode.left - 1.dp &&
            modeLabel.right <= mode.right + 1.dp)
        assertTrue("Calisthenics mode label clipped vertically",
            modeLabel.bottom <= mode.bottom + 1.dp)
        compose.onNodeWithTag("builder-cali-weekly").assertExists()
        compose.onNodeWithTag("builder-cali-starting-levels").assertExists()
        compose.onNodeWithText("2 of 2 placed").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-calisthenics-figma-reference")

        val config=compose.activity.resources.configuration
        if(config.fontScale <= 1.01f) {
            val weekly=compose.onNodeWithTag("builder-cali-weekly")
                .getUnclippedBoundsInRoot()
            assertTrue("Calisthenics weekly preview exceeds reference tolerance: " +
                (weekly.bottom-weekly.top), (weekly.bottom-weekly.top).value <= 500f)
            if(config.screenHeightDp >= 900) {
                val levels=compose.onNodeWithTag("builder-cali-starting-levels")
                    .getUnclippedBoundsInRoot()
                val cta=compose.onNodeWithTag("builder-figma-continue")
                    .getUnclippedBoundsInRoot()
                assertTrue("Calisthenics starting levels overlap fixed Continue",
                    levels.bottom <= cta.top - 2.dp)
            }
        }
        // With larger fonts these sections can be below the fold but must scroll
        // into view. Preserve the fixed CTA without shrinking readable content.
        compose.onNodeWithTag("builder-cali-starting-levels").performScrollTo()
        compose.onNodeWithTag("builder-cali-starting-levels").assertIsDisplayed()
    }

    @Test fun activityFixedMapAndIndependentSheetRenderAtCurrentViewport() {
        val recorded=ActivityRecord(
            "synthetic-responsive-activity","Recorded Evening Run","run","manual",
            Instant.parse("2026-11-01T09:00:00Z"),
            7400.0,2882.0,2792.0,379.0,147.0,64.0
        )
        val state=TrainingState(initializing=false,
            account="synthetic-responsive",activities=listOf(recorded))
        val matte=mutableStateOf(false)
        val tab=mutableStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaActivityDetailSurface(
                        activity=recorded,recorded=null,history=listOf(recorded),
                        state=state,
                        actions=NativeActions({}, {}, {}, {}, { _, _ -> }),
                        online=false,selectedTab=tab.value,onTab={ tab.value=it },
                        matte=matte.value,onToggleMatte={matte.value=!matte.value},
                        onBack={}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-map-hero").assertIsDisplayed()
        compose.onNodeWithTag("activity-sheet-scroll").assertExists()
        screenshot("activity-atmospheric")
        compose.onNodeWithTag("activity-change-glass-variant").performClick()
        screenshot("activity-matte")
        compose.onNodeWithTag("activity-sheet-scroll").performTouchInput { swipeUp() }
        compose.onNodeWithTag("activity-map-hero").assertIsDisplayed()
        screenshot("activity-map-persistent")
    }

    @Test fun builderAvailabilityAndDraftReviewRenderAtCurrentViewport() {
        val state=TrainingState(initializing=false,account="synthetic-responsive")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),
                        onDone={},onSignIn={},onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("available-day-6").assertExists()
        screenshot("builder-availability")
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertExists()
        screenshot("builder-draft-review")
    }

    @Test fun todayHeroAtLargeFontPreservesTargetsAndVisibleAction() {
        val run = snapshot().sessions.first()
        var opened = false
        show {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                TracksideTodayHero(run) { opened = true }
            }
        }
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithTag("today-map-illustration", useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("Open planned workout", useUnmergedTree = true).assertExists()
        screenshot("today")
        compose.onNodeWithTag("today-hero").performClick()
        compose.runOnIdle { assertTrue("Hero action not usable", opened) }
    }

    @Test fun trainingWeekRetainsReadableTouchTargetsAtScaledFont() {
        val plan = snapshot()
        val selected = mutableStateOf(start)
        show {
            TracksideTrainingWeekChart(plan, selected.value) { selected.value = it }
        }
        compose.onNodeWithTag("training-week-chart").assertExists()
        val day = compose.onNodeWithTag("calendar-day-$start")
        val bounds = day.getUnclippedBoundsInRoot()
        assertTrue("Calendar touch width ${bounds.right - bounds.left} smaller than 48dp", (bounds.right - bounds.left).value >= 48f)
        assertTrue("Calendar touch height ${bounds.bottom - bounds.top} smaller than 48dp", (bounds.bottom - bounds.top).value >= 48f)
        screenshot("training")
        day.performClick()
        compose.runOnIdle { assertTrue("Selected day changed", selected.value == start) }
    }


    @Test fun selectedSundayIsFullyVisibleAtLargeFontAndCompactWidth() {
        val plan = snapshot()
        val sunday = start.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        show { TracksideTrainingWeekChart(plan, sunday) {} }
        compose.waitForIdle()
        val selectedDay = compose.onNodeWithTag("calendar-day-$sunday")
        selectedDay.assertIsDisplayed()
        val dayBounds = selectedDay.getUnclippedBoundsInRoot()
        val chartBounds = compose.onNodeWithTag("training-week-chart").getUnclippedBoundsInRoot()
        assertTrue("Selected Sunday extends beyond the visible calendar viewport",
            dayBounds.right <= chartBounds.right + 1.dp)
    }

    @Test fun completeAppShellKeepsTrainingNavigationReachableWithLargeText() {
        // Component screenshots above cannot detect bottom-navigation and system-inset
        // collisions. Exercise the actual NavHost using only synthetic plan records.
        val plan = snapshot()
        val state = TrainingState(
            initializing = false, account = "synthetic-athlete", plans = listOf(plan)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state, true, NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-training").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Training")
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("training-manage-plan"))
        compose.onNodeWithTag("nav-settings").assertIsDisplayed()
        screenshot("training-full-shell")

        compose.onNodeWithTag("training-manage-plan").performClick()
        compose.onNodeWithTag("manage-back").assertExists()
        compose.onNodeWithTag("nav-training").assertIsSelected()
        screenshot("manage-full-shell")
        compose.onNodeWithTag("manage-back").performClick()
        compose.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                compose.onNodeWithTag("screen-title").assertTextEquals("Training")
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("nav-training").assertIsSelected()
    }

    @Test fun manageActionsCanBeReachedWithLargeText() {
        val plan = snapshot()
        show {
            TracksideManagePlanScreen(plan, start, false, PlanActions(),
                onBack = {}, onBuildDraft = {}, onWorkout = {})
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-adjust-weeks"))
        val action = compose.onNodeWithTag("manage-adjust-weeks")
        action.assertIsDisplayed()
        val bounds = action.getUnclippedBoundsInRoot()
        assertTrue("Manage action smaller than 48dp", (bounds.bottom - bounds.top).value >= 48f)
        screenshot("manage-actions")
        action.performClick()
        compose.onNodeWithText("Create a reviewed draft?").assertExists()
    }
}
