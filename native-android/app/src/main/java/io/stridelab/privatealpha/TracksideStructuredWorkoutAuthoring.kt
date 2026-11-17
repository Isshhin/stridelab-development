package io.stridelab.privatealpha

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

internal data class WorkoutStepDraft(
    val id:Long,val purpose:String,val timed:Boolean,val amount:String,
    val equivalent:String,val effort:String,val groupId:String?=null
) {
    fun encode()=JSONObject().put("id",id).put("purpose",purpose).put("timed",timed)
        .put("amount",amount).put("equivalent",equivalent).put("effort",effort).put("groupId",groupId)
    fun toStep():RunningStepV2? {
        val extent=amount.toIntOrNull()?.takeIf { it>0 } ?: return null
        val distance=equivalent.toIntOrNull()?.takeIf { it>0 }
        if(timed && extent>14400 || !timed && extent>120000 || distance!=null && distance>120000)return null
        if(timed && distance==null)return null
        if(!timed && equivalent.isNotBlank())return null
        val target=effort.ifBlank { "No intensity target supplied." }
        return if(timed) RunningStepV2(purpose,target,seconds=extent,distanceEquivalentMeters=distance)
        else RunningStepV2(purpose,target,meters=extent)
    }
    companion object {
        fun decode(j:JSONObject)=WorkoutStepDraft(j.getLong("id"),j.getString("purpose"),
            j.getBoolean("timed"),j.getString("amount"),j.getString("equivalent"),j.getString("effort"),j.optString("groupId").takeIf { it.isNotEmpty() })
        fun from(step:RunningStepV2,id:Long)=WorkoutStepDraft(id,step.purpose,step.seconds!=null,
            (step.seconds ?: step.meters!!).toString(),step.distanceEquivalentMeters?.toString().orEmpty(),step.effort)
    }
}

internal fun workoutStepBlockStart(steps:List<WorkoutStepDraft>,index:Int):Int {
    val group=steps[index].groupId ?: return index
    var start=index
    while(start>0 && steps[start-1].groupId==group)start--
    return start
}
internal fun workoutStepBlockEnd(steps:List<WorkoutStepDraft>,index:Int):Int {
    val group=steps[index].groupId ?: return index
    var end=index
    while(end<steps.lastIndex && steps[end+1].groupId==group)end++
    return end
}
internal fun moveWorkoutStepBlock(steps:List<WorkoutStepDraft>,index:Int,direction:Int):List<WorkoutStepDraft> {
    val start=workoutStepBlockStart(steps,index);val end=workoutStepBlockEnd(steps,index)
    if(direction<0) {
        if(start==0)return steps
        val previousStart=workoutStepBlockStart(steps,start-1)
        return steps.take(previousStart)+steps.subList(start,end+1)+steps.subList(previousStart,start)+steps.drop(end+1)
    }
    if(end==steps.lastIndex)return steps
    val nextEnd=workoutStepBlockEnd(steps,end+1)
    return steps.take(start)+steps.subList(end+1,nextEnd+1)+steps.subList(start,end+1)+steps.drop(nextEnd+1)
}
internal fun updateWorkoutStep(steps:List<WorkoutStepDraft>,index:Int,update:(WorkoutStepDraft)->WorkoutStepDraft):List<WorkoutStepDraft> {
    val selected=steps[index]
    return steps.map { step ->
        if(selected.groupId!=null && step.groupId==selected.groupId && step.purpose==selected.purpose)update(step)
        else if(selected.groupId==null && step.id==selected.id)update(step)
        else step
    }
}
internal fun removeWorkoutStepBlock(steps:List<WorkoutStepDraft>,index:Int):List<WorkoutStepDraft> {
    val selected=steps[index]
    return if(selected.groupId==null)steps.filterNot { it.id==selected.id }
    else steps.filterNot { it.groupId==selected.groupId }
}

internal data class WorkoutDraft(val family:String,val steps:List<WorkoutStepDraft>) {
    fun encode()=JSONObject().put("family",family).put("steps",JSONArray(steps.map { it.encode() }))
    companion object {
        fun decode(j:JSONObject)=WorkoutDraft(j.getString("family"),
            j.getJSONArray("steps").let { a -> (0 until a.length()).map { WorkoutStepDraft.decode(a.getJSONObject(it)) } })
        fun from(workout:RunningPrescription,idStart:Long):WorkoutDraft {
            var id=idStart+10000
            var groupNumber=0
            val rows=mutableListOf<WorkoutStepDraft>()
            if(workout.mixedSegments!=null) {
                var index=0
                MixedRunningStepGrouping.of(workout.mixedSegments).forEach { block ->
                    when(block) {
                        is MixedRunningStepBlock.Single -> rows+=WorkoutStepDraft.from(workout.mixedSegments[index++],id++)
                        is MixedRunningStepBlock.Repeat -> {
                            val group="group-${++groupNumber}"
                            repeat(block.times) { rep ->
                                rows+=WorkoutStepDraft.from(workout.mixedSegments[index++],id++).copy(groupId=group)
                                if(block.recovery!=null && (rep<block.times-1 || block.recoveryAfterLast))
                                    rows+=WorkoutStepDraft.from(workout.mixedSegments[index++],id++).copy(groupId=group)
                            }
                        }
                    }
                }
            } else {
                var index=0
                RunningStepGrouping.of(workout.segments).forEach { block ->
                    when(block) {
                        is RunningStepBlock.Single -> {
                            val s=workout.segments[index++]
                            rows+=WorkoutStepDraft.from(RunningStepV2(s.purpose,s.effort,meters=s.meters),id++)
                        }
                        is RunningStepBlock.Repeat -> {
                            val group="group-${++groupNumber}"
                            repeat(block.times) { rep ->
                                val work=workout.segments[index++]
                                rows+=WorkoutStepDraft.from(RunningStepV2(work.purpose,work.effort,meters=work.meters),id++).copy(groupId=group)
                                if(block.recovery!=null && (rep<block.times-1 || block.recoveryAfterLast)) {
                                    val rest=workout.segments[index++]
                                    rows+=WorkoutStepDraft.from(RunningStepV2(rest.purpose,rest.effort,meters=rest.meters),id++).copy(groupId=group)
                                }
                            }
                        }
                    }
                }
            }
            return WorkoutDraft(workout.family ?: "Intervals",rows)
        }
    }
}

@Composable
internal fun TracksideStructuredWorkoutAuthoring(
    weeks:Int,
    qualityCount:Int,
    saved:Map<String,RunningPrescription>,
    onSave:(Map<String,RunningPrescription>)->Unit,
    onRemove:(String)->Unit
) {
    if(qualityCount !in 1..2 || weeks !in 1..16)return
    val loadingWeeks=(1..weeks).filter { (it-1)%4!=3 }
    var selectedWeek by rememberSaveable(weeks) { mutableIntStateOf(loadingWeeks.first()) }
    var selectedSlot by rememberSaveable(qualityCount) { mutableIntStateOf(1) }
    var nextId by rememberSaveable { mutableLongStateOf(1L) }
    var nextGroupId by rememberSaveable { mutableIntStateOf(1) }
    var draftsJson by rememberSaveable { mutableStateOf("{}") }
    var family by rememberSaveable { mutableStateOf("Threshold") }
    var reps by rememberSaveable { mutableStateOf("4") }
    var workAmount by rememberSaveable { mutableStateOf("800") }
    var recoveryAmount by rememberSaveable { mutableStateOf("400") }
    var groupTimed by rememberSaveable { mutableStateOf(false) }
    var workEquivalent by rememberSaveable { mutableStateOf("") }
    var recoveryEquivalent by rememberSaveable { mutableStateOf("") }
    var workEffort by rememberSaveable { mutableStateOf("") }
    var recoveryEffort by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val key="w$selectedWeek-run$selectedSlot"
    val storedDrafts=remember(draftsJson) {
        val json=JSONObject(draftsJson)
        json.keys().asSequence().associateWith { WorkoutDraft.decode(json.getJSONObject(it)) }
    }
    val existing=saved[key]
    val current=storedDrafts[key] ?: existing?.let { WorkoutDraft.from(it,nextId) }
        ?: WorkoutDraft(family,emptyList())
    val required=loadingWeeks.size*qualityCount
    val savedCount=saved.keys.count { k ->
        val match=Regex("w([1-9][0-9]*)-run([1-9][0-9]*)").matchEntire(k) ?: return@count false
        match.groupValues[1].toIntOrNull() in loadingWeeks &&
            match.groupValues[2].toIntOrNull() in 1..qualityCount
    }
    val staleKeys=saved.keys.filter { k ->
        val match=Regex("w([1-9][0-9]*)-run([1-9][0-9]*)").matchEntire(k) ?: return@filter true
        match.groupValues[1].toIntOrNull() !in loadingWeeks || match.groupValues[2].toIntOrNull() !in 1..qualityCount
    }
    fun persistDraft(draft:WorkoutDraft) {
        val json=JSONObject(draftsJson).put(key,draft.encode())
        draftsJson=json.toString()
        family=draft.family
    }
    fun updateSteps(steps:List<WorkoutStepDraft>)=persistDraft(current.copy(steps=steps))
    fun addStep(purpose:String,timed:Boolean=false,amount:String="",effort:String="") {
        updateSteps(current.steps+WorkoutStepDraft(nextId++,purpose,timed,amount,"",effort))
    }
    fun parseWorkout():RunningPrescription? {
        val steps=current.steps.mapNotNull { it.toStep() }
        if(steps.size!=current.steps.size) {
            error="Complete every step and enter an explicit distance equivalent for each timed step."
            return null
        }
        return try { Engine15UserWorkoutAuthoring.build(current.family,steps) }
        catch(e:Exception) { error=e.message ?: "The workout structure is invalid.";null }
    }

    Column(Modifier.fillMaxWidth().testTag("builder-structured-workout"),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("USER-DEFINED STRUCTURED WORKOUTS",style=MaterialTheme.typography.titleSmall)
        Text("Targets here are user supplied, not coach prescriptions. Define one workout for each loading week and quality slot; the generator checks its total against the scheduled mileage.",
            style=MaterialTheme.typography.bodySmall)
        Text("Defined $savedCount of $required loading-week workouts",
            modifier=Modifier.testTag("builder-workout-completion"),style=MaterialTheme.typography.labelLarge)
        staleKeys.forEach { stale ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Unused saved workout: $stale",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={onRemove(stale)},modifier=Modifier.testTag("builder-workout-remove-stale-$stale")) {
                    Text("Remove")
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            loadingWeeks.forEach { week ->
                FilterChip(selected=week==selectedWeek,onClick={selectedWeek=week;error=null},
                    label={Text("Week $week")},modifier=Modifier.testTag("builder-workout-week-$week"))
            }
        }
        if(qualityCount==2) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (1..2).forEach { slot ->
                FilterChip(selected=slot==selectedSlot,onClick={selectedSlot=slot;error=null},
                    label={Text("Quality $slot")},modifier=Modifier.testTag("builder-workout-slot-$slot"))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Threshold","Tempo","Intervals","Hills","Fartlek","Progression","Strides").forEach { option ->
                FilterChip(selected=current.family==option,onClick={persistDraft(current.copy(family=option))},
                    label={Text(option)},modifier=Modifier.testTag("builder-workout-family-$option"))
            }
        }
        current.steps.forEachIndexed { index,step ->
            ElevatedCard(Modifier.fillMaxWidth().testTag("builder-workout-step-${step.id}")) {
                Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        if(step.groupId==null) {
                            listOf("WARM_UP","WORK","RECOVERY","TRANSITION","COOL_DOWN","EASY").forEach { purpose ->
                                FilterChip(selected=step.purpose==purpose,
                                    onClick={updateSteps(updateWorkoutStep(current.steps,index) { it.copy(purpose=purpose) })},
                                    label={Text(purpose.replace('_',' '),style=MaterialTheme.typography.labelSmall)})
                            }
                        } else {
                            val repetitionCount=current.steps.count { it.groupId==step.groupId && it.purpose=="WORK" }
                            Text("Repeat group · $repetitionCount repetitions · ${step.purpose.replace('_',' ').lowercase()}",style=MaterialTheme.typography.labelMedium,
                                modifier=Modifier.testTag("builder-workout-group-${step.groupId}"))
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected=!step.timed,onClick={updateSteps(updateWorkoutStep(current.steps,index) { it.copy(timed=false,equivalent="") })},label={Text("Distance")})
                        FilterChip(selected=step.timed,onClick={updateSteps(updateWorkoutStep(current.steps,index) { it.copy(timed=true) })},label={Text("Time")})
                    }
                    OutlinedTextField(step.amount,{v->updateSteps(updateWorkoutStep(current.steps,index) { it.copy(amount=v.filter(Char::isDigit)) })},
                        label={Text(if(step.timed)"Duration (seconds)" else "Distance (meters)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-amount-${step.id}"))
                    if(step.timed) OutlinedTextField(step.equivalent,{v->updateSteps(updateWorkoutStep(current.steps,index) { it.copy(equivalent=v.filter(Char::isDigit)) })},
                        label={Text("User-supplied distance equivalent (meters)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-equivalent-${step.id}"))
                    OutlinedTextField(step.effort,{v->updateSteps(updateWorkoutStep(current.steps,index) { it.copy(effort=v) })},
                        label={Text("User-defined target / effort (optional)")},modifier=Modifier.fillMaxWidth().testTag("builder-workout-effort-${step.id}"))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        val blockStart=workoutStepBlockStart(current.steps,index);val blockEnd=workoutStepBlockEnd(current.steps,index)
                        TextButton(enabled=blockStart>0,onClick={updateSteps(moveWorkoutStepBlock(current.steps,index,-1))},modifier=Modifier.testTag("builder-workout-up-${step.id}")){Text("Move up")}
                        TextButton(enabled=blockEnd<current.steps.lastIndex,onClick={updateSteps(moveWorkoutStepBlock(current.steps,index,1))},modifier=Modifier.testTag("builder-workout-down-${step.id}")){Text("Move down")}
                        TextButton(onClick={updateSteps(removeWorkoutStepBlock(current.steps,index))},modifier=Modifier.testTag("builder-workout-remove-${step.id}")){Text(if(step.groupId==null)"Remove" else "Remove repeat group")}
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("WARM_UP","WORK","RECOVERY","TRANSITION","COOL_DOWN").forEach { purpose ->
                OutlinedButton(onClick={addStep(purpose)},modifier=Modifier.testTag("builder-workout-add-${purpose.lowercase()}")) { Text("Add ${purpose.replace('_',' ').lowercase()}") }
            }
        }
        Text("Add a repetition group",style=MaterialTheme.typography.labelLarge)
        OutlinedTextField(reps,{reps=it.filter(Char::isDigit)},label={Text("Repetitions")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-repetitions"))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected=!groupTimed,onClick={groupTimed=false},label={Text("Distance repeats")})
            FilterChip(selected=groupTimed,onClick={groupTimed=true},label={Text("Time repeats")})
        }
        OutlinedTextField(workAmount,{workAmount=it.filter(Char::isDigit)},label={Text(if(groupTimed)"Work duration (seconds)" else "Work distance (meters)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-group-work"))
        if(groupTimed) OutlinedTextField(workEquivalent,{workEquivalent=it.filter(Char::isDigit)},label={Text("Work distance equivalent (meters)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-group-work-equivalent"))
        OutlinedTextField(recoveryAmount,{recoveryAmount=it.filter(Char::isDigit)},label={Text(if(groupTimed)"Recovery duration (seconds; optional)" else "Recovery distance (meters; optional)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-group-recovery"))
        if(groupTimed && recoveryAmount.isNotBlank()) OutlinedTextField(recoveryEquivalent,{recoveryEquivalent=it.filter(Char::isDigit)},label={Text("Recovery distance equivalent (meters)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("builder-workout-group-recovery-equivalent"))
        OutlinedTextField(workEffort,{workEffort=it},label={Text("Work target / effort")},modifier=Modifier.fillMaxWidth().testTag("builder-workout-group-effort"))
        OutlinedTextField(recoveryEffort,{recoveryEffort=it},label={Text("Recovery target / effort")},modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={
            error=null
            val count=reps.toIntOrNull()?.takeIf { it in 2..20 }
            val work=workAmount.toIntOrNull()?.takeIf { it>0 }
            val recovery=recoveryAmount.toIntOrNull()?.takeIf { it>0 }
            if(count==null || work==null || groupTimed && workEquivalent.toIntOrNull()==null) {
                error="Enter 2–20 repetitions, a positive work amount, and an explicit work distance equivalent for timed repeats."
            } else {
                var groupError:String?=null
                val groupId="group-${nextGroupId++}"
                val rows=buildList {
                    repeat(count) { i ->
                        add(WorkoutStepDraft(nextId++,"WORK",groupTimed,work.toString(),if(groupTimed)workEquivalent else "",workEffort,groupId))
                        if(i<count-1 && recovery!=null) {
                            if(groupTimed && recoveryEquivalent.toIntOrNull()==null) groupError="Enter a distance equivalent for the timed recovery."
                            else add(WorkoutStepDraft(nextId++,"RECOVERY",groupTimed,recovery.toString(),if(groupTimed)recoveryEquivalent else "",recoveryEffort.ifBlank { "No intensity target supplied." },groupId))
                        }
                    }
                }
                if(groupError!=null) error=groupError
                else updateSteps(current.steps+rows)
            }
        },modifier=Modifier.testTag("builder-workout-add-repeat-group")){Text("Add $reps repetitions")}
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("builder-workout-error")) }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={
                val workout=parseWorkout()
                if(workout!=null) { onSave(saved+(key to workout));error=null }
            },enabled=current.steps.isNotEmpty(),modifier=Modifier.testTag("builder-workout-save")){Text("Save this week")}
            val missing=loadingWeeks.filter { "w$it-run$selectedSlot" !in saved }
            if(missing.isNotEmpty()) OutlinedButton(onClick={
                val workout=parseWorkout()
                if(workout!=null) {
                    val updated=saved.toMutableMap()
                    updated[key]=workout
                    missing.forEach { week->updated.putIfAbsent("w$week-run$selectedSlot",workout) }
                    onSave(updated);error=null
                }
            },enabled=current.steps.isNotEmpty(),modifier=Modifier.testTag("builder-workout-copy-missing")){
                Text("Copy to ${missing.size} missing loading weeks")
            }
            if(existing!=null) TextButton(onClick={onRemove(key);error=null},modifier=Modifier.testTag("builder-workout-delete-saved")){Text("Remove saved workout")}
        }
    }
}
