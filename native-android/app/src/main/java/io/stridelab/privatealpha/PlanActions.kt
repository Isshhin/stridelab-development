package io.stridelab.privatealpha

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDate

internal data class PlanActions(
    val generate:(PlanRequest)->Unit={},val approve:(String,Int)->Unit={_,_->},val pause:(String,Int)->Unit={_,_->},val archive:(String,Int)->Unit={_,_->},
    val move:(String,Int,String,LocalDate)->Unit={_,_,_,_->},val record:(String,Int,String,WorkoutLog,Boolean)->Unit={_,_,_,_,_->},
    val missed:(String,Int,String,String,Boolean)->Unit={_,_,_,_,_->},val acceptSuggestion:(String,Int,String)->Unit={_,_,_->},
    val feedback:(String,Int,String,String,String?)->Unit={_,_,_,_,_->},
    val shelf:(String,String?,Boolean)->Unit={_,_,_->},val restore:(String)->Unit={},val link:(String,Int,String,String?)->Unit={_,_,_,_->},val swap:(String,Int,String,String)->Unit={_,_,_,_->},
    val saveTemplate:(SessionTemplate)->Unit={},val prepareSession:(SessionTemplate,PlanRequest,LocalDate)->Unit={_,_,_->},
    val lockWorkout:(String,Int,String,Boolean)->Unit={_,_,_,_->},
    val previewAvailability:suspend (String,Set<Int>)->Engine15Availability.Impact?={_,_->null},
    val applyAvailability:(String,Int,Set<Int>,Engine15Availability.Impact)->Unit={_,_,_,_->},
    val longDay:(String,Int,Int)->Unit={_,_,_->},
    val qualityDays:(String,Int,Set<Int>)->Unit={_,_,_->},
    val previewAdjustments:(String)->List<Engine15Feedback.Proposal> = { emptyList() },
    val stageAdjustment:(Engine15Feedback.Proposal)->Unit={},
    val dismissPreview:(Engine15Feedback.Proposal)->Unit={},
    val dismissPending:(String,Int,String)->Unit={_,_,_->},
    val pendingError:(String,String)->String?={_,_->null},
    val stageWorkoutDraft:(WorkoutEntryDraft)->Unit={},
    val saveRunResponse:(String,Int,String,Int?,String?,String?)->Unit={_,_,_,_,_,_->},
    val previewWeek:(String,Engine15WeeklyReview.Input)->Engine15WeeklyReview.Review?={_,_->null},
    val acceptWeek:(Engine15WeeklyReview.Review)->Unit={},
    val saveResearchReview:(PlanRequest,IndependentAndroidPreviewConstraints)->Unit={_,_->}
)
