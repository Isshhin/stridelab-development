package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Staged UI assertions for Figma 551:580/603/796/865, 720:546,
 * 614:88/412, and 551:235. Synthetic offline data only.
 * Development verification is authorized; this suite must pass on an emulator.
 */
class TracksideApprovedBuilderActivityUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun runningBuilderUsesApprovedStagesNotLegacyChipPage() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(
                        state=state, actions=PlanActions(),
                        onDone={}, onSignIn={},
                        onPreview={_,_->}
                    )
                }
            }
        }
        compose.onNodeWithTag("figma-builder-frame").assertExists()
        compose.onNodeWithText("Choose your", substring=true).assertExists()
        compose.onNodeWithTag("discipline-run").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-option-race").assertExists()
        compose.onNodeWithText("running goal?", substring=true).assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-distance-10K").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-experience-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-run-plus").assertExists()
        compose.onNodeWithTag("builder-running-day-2").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("available-day-6").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithText("Your training week").assertExists()
        compose.onNodeWithText("Chosen weekdays are requests",
            substring=true).assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertExists()
    }

    @Test fun aRunDayChosenInTheEditorAppearsInDraftReview() {
        val state=TrainingState(initializing=false,account="synthetic-ui")
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
        // The approved compact weekly view opens its inline editor on day tap.
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-run-session-2").performScrollTo()
        compose.onNodeWithTag("builder-run-session-2").performClick()
        compose.onNodeWithTag("builder-running-day-6").performScrollTo()
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-running-assignment-progress").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("available-day-6").assertIsEnabled()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-requested-day-6").assertExists()
        compose.onNodeWithText("Easy 2 · requested",substring=true).assertExists()
    }

    @Test fun pinnedWeekdayRemainsEditableWithAnExplicitClearConfirmation() {
        val selected=androidx.compose.runtime.mutableStateOf(
            PlanRequest(runDays=3,days=setOf(1,3,7),longDay=7,
                weeklySlots=mapOf("run" to listOf(7,3,1)))
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaAvailabilityScreen(
                        request=selected.value,
                        onDay={ day ->
                            selected.value=selected.value.copy(
                                days=if(day in selected.value.days)
                                    selected.value.days-day
                                else selected.value.days+day)
                        },
                        onRemovePinnedDay={ day ->
                            selected.value=WeeklyPlacement.makeUnavailable(
                                selected.value,day)
                        },
                        onMultiSessionToggle={},onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("available-day-7").assertIsEnabled()
        compose.onNodeWithTag("available-day-7").performClick()
        compose.onNodeWithText("Change Sunday availability?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(7,3,1),selected.value.weeklySlots["run"])
        compose.onNodeWithTag("available-day-7").performClick()
        compose.onNodeWithTag("builder-confirm-remove-pinned-day").performClick()
        assertEquals(listOf(0,3,1),selected.value.weeklySlots["run"])
        assertEquals(false,7 in selected.value.days)
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
    }

    @Test fun guidedGoalAndAdvancedPreferencesStayWithinApprovedBuilder() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state, actions=PlanActions(),
                        onDone={}, onSignIn={}, onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-option-faster").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-focus-10K").assertExists()
        compose.onNodeWithTag("builder-guided-advanced").performClick()
        compose.onNodeWithTag("builder-advanced-mileage").assertExists()
        compose.onNodeWithTag("builder-advanced-quality-Threshold").assertExists()
        compose.onNodeWithTag("builder-advanced-quality-count-1").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-guided-mileage").assertExists()
    }

    @Test fun targetTimeIntentCannotSilentlyEnterUnsupportedEngineGoal() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state, actions=PlanActions(),
                        onDone={}, onSignIn={}, onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-option-time").performClick()
        compose.onNodeWithTag("builder-target-time-unavailable").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-option-pr").performClick()
        compose.onNodeWithTag("builder-race-add-pr").performScrollTo()
        compose.onNodeWithTag("builder-race-add-pr").performClick()
        compose.onNodeWithText("PR entry").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }

    @Test fun availabilityRequiresExplicitMultiSessionPolicyForSevenSessionsInSixDays() {
        val state = TrainingState(initializing = false, account = "synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state, actions=PlanActions(),
                        onDone={}, onSignIn={}, onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("discipline-str").performClick()
        compose.onNodeWithTag("discipline-cal").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-goal-0").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-weekly").assertExists()
        compose.onNodeWithTag("builder-strength-auto").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cross-ability-strength-0").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cali-weekly").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("available-day-6").assertIsEnabled()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-enable-multi-session").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.onNodeWithText("Engine 1 only permits selected pairings",
            substring=true).assertExists()
        compose.onNodeWithTag("builder-other-discipline-editor").assertExists()
        compose.onNodeWithTag("builder-discipline-day-tab-str").assertExists()
    }

    @Test fun manualRunAssignmentCanSwapAndUseSaturdayWithoutLosingSessions() {
        val initial=PlanRequest(
            runDays=4,experience=2,weeklyKm=45.0,
            longDay=7,qualityCount=1,qualityDays=setOf(2),
            days=setOf(1,2,3,4,5,7)
        )
        val selected=WeeklyPlacement.days(initial,"run")
        assertEquals(4,selected.size)
        val first=WeeklyPlacement.put(initial,"run",2,6)
        assertEquals(6,first.weeklySlots["run"]!![2])
        assertTrue(6 in first.days)
        val swapped=WeeklyPlacement.put(first,"run",3,6)
        assertEquals(6,swapped.weeklySlots["run"]!![3])
        assertEquals(first.weeklySlots["run"]!![3],swapped.weeklySlots["run"]!![2])
        assertEquals(4,swapped.weeklySlots["run"]!!.distinct().size)
    }

    @Test fun runningWeekUiDirectTapPinsAndSwapsChosenWeekdays() {
        val saved=androidx.compose.runtime.mutableStateOf(
            PlanRequest(runDays=4,experience=2,weeklyKm=45.0,
                days=setOf(1,2,3,4,5,7),longDay=7,
                qualityCount=1,qualityDays=setOf(2))
        )
        val selected=androidx.compose.runtime.mutableIntStateOf(2)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaRunningSetup(
                        request=saved.value,
                        selectedSlot=selected.intValue,
                        onSelectSlot={ selected.intValue=it },
                        custom=true,onCustom={},
                        qualityPreference="Threshold",onQualityPreference={},
                        onQualityCount={},onRunningCount={},
                        onAssignDay={ day ->
                            saved.value=WeeklyPlacement.put(
                                saved.value,"run",selected.intValue,day)
                        },
                        onClearSlot={
                            saved.value=WeeklyPlacement.clear(
                                saved.value,"run",selected.intValue)
                        },
                        onBack={},onContinue={}
                    )
                }
            }
        }
        // First tap reveals the editor; second tap after selecting a workout
        // performs the existing real WeekPlacement.put/slot-swap action.
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-run-session-2").performScrollTo()
        compose.onNodeWithTag("builder-run-session-2").performClick()
        compose.onNodeWithTag("builder-running-day-6").performClick()
        assertEquals(6,saved.value.weeklySlots["run"]!![2])
        assertEquals(4,saved.value.weeklySlots["run"]!!.distinct().size)
        assertEquals(true,6 in saved.value.days)
        compose.onNodeWithTag("builder-run-session-3").performScrollTo()
        compose.onNodeWithTag("builder-run-session-3").performClick()
        compose.onNodeWithTag("builder-running-day-6").performScrollTo()
        compose.onNodeWithTag("builder-running-day-6").performClick()
        assertEquals(6,saved.value.weeklySlots["run"]!![3])
        assertEquals(4,saved.value.weeklySlots["run"]!!.distinct().size)
    }

    @Test fun strengthAndCaliUiDaySelectionPersistsPerDiscipline() {
        val saved=androidx.compose.runtime.mutableStateOf(
            PlanRequest(runDays=0,strengthDays=2,calDays=1,
                days=setOf(1,2,3,4,5,7))
        )
        val discipline=androidx.compose.runtime.mutableStateOf("str")
        val slot=androidx.compose.runtime.mutableIntStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideFigmaOtherDisciplineSlots(
                        request=saved.value,
                        selectedDiscipline=discipline.value,
                        selectedSlot=slot.intValue,
                        onSelectDiscipline={ discipline.value=it },
                        onSelectSlot={ slot.intValue=it },
                        onAssignDay={ d,i,day ->
                            saved.value=WeeklyPlacement.put(saved.value,d,i,day)
                        },
                        onClearSlot={ d,i ->
                            saved.value=WeeklyPlacement.clear(saved.value,d,i)
                        },
                        onAuto={ d ->
                            saved.value=saved.value.copy(
                                weeklySlots=saved.value.weeklySlots-d)
                        },
                        onCount={ _,_ -> }
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-other-day-1").performClick()
        assertEquals(1,saved.value.weeklySlots["str"]!![0])
        compose.onNodeWithTag("builder-discipline-day-tab-cal").performClick()
        compose.onNodeWithTag("builder-other-day-6").performClick()
        assertEquals(6,saved.value.weeklySlots["cal"]!![0])
        assertEquals(1,saved.value.weeklySlots["str"]!![0])
    }

    @Test fun strengthOnlyGoalsUseFullScreenCardsAndEditableStrengthAbility() {
        val state=TrainingState(initializing=false,account="synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),discipline="str",
                        onDone={},onSignIn={},onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-goal-1").assertExists()
        compose.onNodeWithTag("builder-strength-goal-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-weekly").assertExists()
        compose.onNodeWithTag("builder-strength-setup-equipment").performClick()
        compose.onNodeWithTag("builder-strength-equipment-db").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-customize").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-strength-weekday-1").performClick()
        compose.onNodeWithTag("builder-strength-edit-schedule").performScrollTo()
        compose.onNodeWithTag("builder-strength-edit-schedule").performClick()
        compose.onNodeWithTag("builder-strength-session-1").performScrollTo()
        compose.onNodeWithTag("builder-strength-session-1").performClick()
        compose.onNodeWithTag("builder-strength-weekday-4").performScrollTo()
        compose.onNodeWithTag("builder-strength-weekday-4").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-edit-goal").assertExists()
        compose.onNodeWithTag("builder-cross-ability-strength-2").performClick()
        compose.onNodeWithTag("builder-cross-count-str-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
    }

    @Test fun calisthenicsOnlyGoalsAndIndependentAbilitiesAreNative() {
        val state=TrainingState(initializing=false,account="synthetic-ui")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),discipline="cal",
                        onDone={},onSignIn={},onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cross-goal-cal-Pull-up progression").assertExists()
        compose.onNodeWithTag("builder-cross-ability-push-1").performClick()
        compose.onNodeWithTag("builder-cross-ability-pull-2").performClick()
        compose.onNodeWithTag("builder-cross-ability-core-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cali-weekly").assertExists()
        compose.onNodeWithTag("builder-cali-customize").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-cali-day-3").performClick()
        compose.onNodeWithTag("builder-cali-session-type-Pull").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("builder-cali-day-5").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
    }

    @Test fun activityMapRemainsBehindIndependentScrollingGlassSheet() {
        val activity = ActivityRecord(
            "synthetic-run", "Recorded Park Run", "run", "manual",
            Instant.parse("2026-11-01T09:00:00Z"),
            7400.0, 2830.0, 2790.0, 379.0, 150.0, 64.0
        )
        val state = TrainingState(
            initializing = false, account = "synthetic-ui",
            activities = listOf(activity)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state,false,
                        NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-activities").performClick()
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("activity-synthetic-run"))
        compose.onNodeWithTag("activity-synthetic-run").performClick()
        compose.onNodeWithTag("activity-map-hero").assertIsDisplayed()
        compose.onNodeWithTag("activity-sheet-scroll").assertExists()
        compose.onNodeWithTag("activity-parity-overview").assertExists()
        compose.onNodeWithTag("activity-change-glass-variant").performClick()
        compose.onNodeWithTag("activity-matte-overview").assertExists()
    }
}
