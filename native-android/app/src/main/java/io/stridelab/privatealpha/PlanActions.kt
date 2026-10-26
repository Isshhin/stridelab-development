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
    val saveTemplate:(SessionTemplate)->Unit={},val prepareSession:(SessionTemplate,PlanRequest,LocalDate)->Unit={_,_,_->}
)
