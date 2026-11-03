package io.stridelab.privatealpha

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.Instant
import java.time.LocalDate

/**
 * Source-only Figma comparison gallery for Android Studio's Compose Preview.
 *
 * These are explicitly synthetic design fixtures, never user records. The
 * current project's Compose ui-tooling-preview dependency already supports
 * @Preview; viewing a preview may require IDE compilation but does NOT
 * assemble or install the Android app on a phone.
 *
 * Compare a screenshot at matching 412x950 or 360x800 against the exact
 * APPROVED MASTER GALLERY Figma frame, not a generic style reference.
 */
private val previewPlan = PlanRequest(
    start=LocalDate.of(2026,11,9),
    weeks=8,
    days=setOf(1,2,3,4,5,7),
    longDay=7,
    runDays=5,
    strengthDays=0,
    calDays=0,
    qualityDays=setOf(2),
    qualityCount=1,
    experience=2,
    weeklyKm=70.0,
    longestKm=20.0,
    policyChoice="coordinated"
)

@Preview(name="551-580 · Disciplines · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedDisciplines() {
    StrideTheme {
        TracksideFigmaDisciplineScreen(
            request=previewPlan.copy(runDays=5,strengthDays=2),
            onToggle={},onBack={},onContinue={}
        )
    }
}

@Preview(name="551-603 · Running goal · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedRunningGoal() {
    StrideTheme {
        TracksideFigmaRunningGoalScreen(
            selected="race",onSelect={},onBack={},onContinue={}
        )
    }
}

@Preview(name="614-1236 · Running goal · 360", widthDp=360, heightDp=800,
    showBackground=true)
@Composable
private fun PreviewApprovedRunningGoalCompact() {
    StrideTheme {
        TracksideFigmaRunningGoalScreen(
            selected="race",onSelect={},onBack={},onContinue={}
        )
    }
}

@Preview(name="551-796 · Beat PR · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedRaceGoal() {
    StrideTheme {
        TracksideFigmaRaceGoalScreen(
            intent="pr",distance="10K",onIntent={},
            onDistance={},onBack={},onContinue={}
        )
    }
}

@Preview(name="551-865 · Current ability · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedRunningAbility() {
    StrideTheme {
        TracksideFigmaRunningAbilityScreen(
            experience=1,weeklyKm=30.0,longestKm=14.0,
            onExperience={},onWeeklyKm={},onLongestKm={},
            onBack={},onContinue={},onAdvanced={}
        )
    }
}

@Preview(name="614-195 · Running week · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedRunningSetup() {
    StrideTheme {
        TracksideFigmaRunningSetup(
            request=previewPlan,
            selectedDay=2,
            onSelectedDay={},
            custom=true,onCustom={},qualityPreference="Threshold",
            onQualityPreference={},onRunningCount={},onChooseType={_,_->},
            onBack={},onContinue={}
        )
    }
}

@Preview(name="614-1374 · Advanced running · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedAdvancedRunning() {
    StrideTheme {
        TracksideFigmaAdvancedRunning(
            guided=true,weeklyKm=70.0,qualityCount=1,maxQualityCount=2,
            selectedQualityTypes=setOf("Threshold","Tempo"),
            onWeeklyKm={},onQualityCount={},onToggleQuality={},
            onBack={},onContinue={}
        )
    }
}

@Preview(name="614-88 · Availability · 412", widthDp=412, heightDp=950,
    showBackground=true)
@Composable
private fun PreviewApprovedAvailability() {
    StrideTheme {
        TracksideFigmaAvailabilityScreen(
            request=previewPlan.copy(runDays=5,strengthDays=2,calDays=2,
                policyChoice="multi-session"),
            onDay={},onMultiSessionToggle={},onBack={},onContinue={}
        )
    }
}

@Preview(name="551-235 · Activity atmospheric · 412", widthDp=412,
    heightDp=950,showBackground=true)
@Composable
private fun PreviewApprovedActivityAtmospheric() {
    StrideTheme { TracksidePreviewActivity(matte=false) }
}

@Preview(name="551-365 · Activity matte · 412", widthDp=412,
    heightDp=950,showBackground=true)
@Composable
private fun PreviewApprovedActivityMatte() {
    StrideTheme { TracksidePreviewActivity(matte=true) }
}

@Composable
private fun TracksidePreviewActivity(matte: Boolean) {
    val synthetic=ActivityRecord(
        "synthetic-figma-preview",
        "Evening Run · preview",
        "run",
        "Synthetic preview, not measured",
        Instant.parse("2026-10-29T10:14:00Z"),
        7400.0,
        2882.0,
        2792.0,
        379.0,
        147.0,
        64.0
    )
    val state=TrainingState(
        initializing=false,account="preview-only",
        activities=listOf(synthetic)
    )
    TracksideFigmaActivityDetailSurface(
        activity=synthetic,recorded=null,history=listOf(synthetic),
        state=state,
        actions=NativeActions({}, {}, {}, {}, {_,_->}),
        online=false,
        selectedTab=0,onTab={},matte=matte,onToggleMatte={},onBack={}
    )
}
