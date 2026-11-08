@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

internal data class LibraryEntry(val raw:JSONObject) {
    val id get()=raw.getString("id")
    val title get()=raw.getString("n")
    val discipline get()=raw.getString("t")
    val guide get()=raw.optBoolean("guide",false)
    fun text(key:String)=raw.optString(key,"")
    fun list(key:String):List<String> = raw.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
}
internal fun readLibrary(context:Context):List<LibraryEntry> = context.assets.open("native-library/catalog.json").bufferedReader().use {
    val rows=JSONObject(it.readText()).getJSONArray("entries"); (0 until rows.length()).map { i -> LibraryEntry(rows.getJSONObject(i)) }
}

/** Validated offline V2 educational catalog. Never grants skill stages or changes workout prescriptions. */
internal fun readCuratedLibrary(context: Context): List<LibraryEntry> {
    val catalog = ExerciseLibraryV2.load { path -> context.assets.open(path) }
    return catalog.records.map { record ->
        val domain = when {
            "running" in record.categories -> "run"
            "strength_gym" in record.categories -> "str"
            "calisthenics" in record.categories -> "cal"
            "bodyweight" in record.categories -> "body"
            "conditioning" in record.categories -> "cond"
            else -> "mob"
        }
        val equipment = record.equipmentOptions.joinToString(" or ") { option ->
            if (option.isEmpty()) "No equipment" else option.sorted().joinToString(" + ") { it.replace('_', ' ') }
        }
        val sources = record.provenance.mapNotNull { p ->
            catalog.sources.firstOrNull { it.id == p.sourceId }?.title
        }.distinct()
        val prerequisites = record.prerequisites.map { catalog.find(it)?.name ?: it }.sorted()
        val raw = JSONObject()
            .put("id", record.id).put("n", record.name).put("t", domain)
            .put("guide", record.kind == "guide")
            .put("level", record.difficulty.replaceFirstChar { it.uppercase() })
            .put("eq", equipment)
            .put("pattern", record.movementPatterns.joinToString(", ") { it.replace('_', ' ') })
            .put("purpose", "Primary focus: " + record.primaryMuscles.joinToString(", ") { it.replace('_', ' ') })
            .put("secondaryMuscles", record.secondaryMuscles.joinToString(", ") { it.replace('_', ' ') })
            .put("setupNotes", record.setupNotes.joinToString("\n"))
            .put("prerequisiteNotes", record.prerequisiteNotes.joinToString("\n"))
            .put("caveats", record.caveats.joinToString("\n"))
            .put("tech", JSONArray(record.technique))
            .put("mistake", "")
            .put("reg", record.easier).put("prog", record.harder)
            .put("alts", JSONArray(record.alternatives))
            .put("prereq", if (prerequisites.isEmpty()) "No recorded skill prerequisites" else prerequisites.joinToString(", "))
            .put("educationalOnly", true)
            .put("sources", sources.joinToString(" · "))
            .put("mediaNote", "Illustrations are hidden until their rights and fit are verified.")
        LibraryEntry(raw)
    }
}

@Composable internal fun LibraryScreen(state:TrainingState=TrainingState(initializing=false),actions:PlanActions=PlanActions(),onOpen:(String,String)->Unit={_,_->},onTemplate:(String)->Unit={},onPractice:(LibraryEntry)->Unit = {}) {
    val context=LocalContext.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var failure by remember { mutableStateOf(false) }
    var legacyFallback by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val curated = withContext(Dispatchers.IO) { runCatching { readCuratedLibrary(context) } }
        if (curated.isSuccess) entries = curated.getOrThrow()
        else {
            val legacy = withContext(Dispatchers.IO) { runCatching { readLibrary(context) } }
            if (legacy.isSuccess) {
                entries = legacy.getOrThrow()
                legacyFallback = true
            } else failure = true
        }
    }
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("all") }
    var view by rememberSaveable { mutableStateOf("Exercises") };val guides=view=="Movement guides"
    var level by rememberSaveable { mutableStateOf("All levels") };var pattern by rememberSaveable { mutableStateOf("All movements") };var equipment by rememberSaveable { mutableStateOf("All equipment") };var collectionFilter by rememberSaveable { mutableStateOf("All saved") }
    var collectionName by rememberSaveable { mutableStateOf("") }; var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(view) { if(view=="Workouts")view="Exercises" }
    val matches=entries.filter { (view=="Saved" || it.guide==guides) && (filter=="all" || it.discipline==filter) && (it.title+" "+it.text("eq")+" "+it.text("pattern")).contains(query,true) &&
        (level=="All levels" || it.text("level")==level) && (pattern=="All movements" || it.text("pattern")==pattern) && (equipment=="All equipment" || it.text("eq")==equipment) &&
        (view!="Saved" || if(collectionFilter=="All saved") it.id in state.shelf.favorites || state.shelf.collections.values.any { ids->it.id in ids } else if(collectionFilter=="Favorites")it.id in state.shelf.favorites else it.id in (state.shelf.collections[collectionFilter] ?: emptySet())) }
    val logged=state.plans.flatMap { p -> p.sessions.filter { it.log!=null || state.workoutDrafts.containsKey(p.id to it.id) }.map { p to it } }.sortedByDescending { it.second.log?.at }
    Box(Modifier.fillMaxSize().testTag(if(entries.isNotEmpty())"library-ready" else "library-loading")) { StrideLibraryFrame() {
        item { Text("Explore movements, equipment, technique and progressions offline.",style=MaterialTheme.typography.bodyLarge) }
        item { StrideWorkoutSection("On-device catalog") {
            Text(entries.count { !it.guide }.toString() + " exercises · " +
                entries.count { it.guide }.toString() + " guides",
                style=MaterialTheme.typography.titleLarge,color=StrideWorkoutVisuals.white)
            Text(if(legacyFallback) "V2 verification failed; showing preserved legacy entries." else
                "Validated on-device catalog · educational only, not an automatic workout prescription.",
                style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
        } }
        if(view !in setOf("Sessions","Templates")) {
        item { Text("${matches.size} ${if(view=="Saved") "saved entries" else if(guides) "educational references · not tracked workouts" else "curated entries"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Exercises","Templates","Saved","Sessions","Movement guides").forEach { name -> StrideLibraryChip(view==name,{view=name},name) } } }
        if(view=="Templates") {
            item { Text("Reusable session targets are separate from dated drafts and actual logs. Review ability and equipment before approving a session.");SignalButton("Create session template",{onTemplate("new")}) }
            item { StrideWorkoutSection("Lower body strength") {Text("Accepted Phase 4E template: back squat, Romanian deadlift and Bulgarian split squat. Configure targets and suitable equipment in the native session builder.");OutlinedButton(onClick={onTemplate("lower")}) { Text("Review lower-body template") } } }
            items(state.templates,key={"template-${it.id}"}) { template -> StrideWorkoutSection(template.name) {Text("${template.exercises.size} movements · ${template.minutes} planned minutes");Text(template.exercises.joinToString { it.name });OutlinedButton(onClick={onTemplate("saved-${template.id}")}) { Text("Prepare from saved template") } } }
        } else if(view=="Sessions") {
            item { Text("Workout records and saved entry drafts. Only confirmed completions count as completed training.") }
            if(logged.isEmpty())item { Text("No workout logs saved for this account yet. Start with an approved training plan.") }
            items(logged,key={it.second.id}) { (p,session) -> PlannedWorkoutCard(session,state.workoutDrafts.containsKey(p.id to session.id)) { onOpen(p.id,session.id) } }
        } else {
        if(view=="Saved") item {
            Text(if(state.account==null) "Preparing your private offline library." else "Favorites and collections are stored on this device for your account.",style=MaterialTheme.typography.bodySmall)
            ShelfFilter("Collection",listOf("All saved","Favorites")+state.shelf.collections.keys.sorted(),collectionFilter) { collectionFilter=it }
        }
        item { OutlinedTextField(query,{query=it},label={Text("Search exercises")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={focus.clearFocus();keyboard?.hide()})) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("all" to "All","run" to "Running","str" to "Strength","cal" to "Calisthenics","body" to "Bodyweight","cond" to "Conditioning","mob" to "Mobility").forEach { (id,label) -> StrideLibraryChip(selected=filter==id,onClick={filter=id},label=label) } } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ShelfFilter("Level",listOf("All levels")+entries.filter { view=="Saved" || it.guide==guides }.map { it.text("level") }.distinct().sorted(),level) { level=it }
            ShelfFilter("Movement",listOf("All movements")+entries.filter { view=="Saved" || it.guide==guides }.map { it.text("pattern") }.filter { it.isNotBlank() }.distinct().sorted(),pattern) { pattern=it }
            ShelfFilter("Equipment",listOf("All equipment")+entries.filter { view=="Saved" || it.guide==guides }.map { it.text("eq") }.filter { it.isNotBlank() }.distinct().sorted(),equipment) { equipment=it }
        } }
        item { if(level!="All levels"||pattern!="All movements"||equipment!="All equipment"||filter!="all"||query.isNotEmpty())TextButton(onClick={level="All levels";pattern="All movements";equipment="All equipment";filter="all";query=""}) { Text("Clear search and filters") } }
        if(failure) item { Text("The offline library could not be opened. Original assets remain preserved in Settings.") }
        if(matches.isEmpty() && entries.isNotEmpty()) item { Text("No entries match. Try a broader search or another discipline.") }
        items(matches,key={it.id}) { entry ->
            Surface(
                onClick={ focus.clearFocus();keyboard?.hide();selected=entry.id },
                color=Color.Transparent,
                modifier=Modifier.fillMaxWidth().testTag("library-entry-${entry.id}")
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical=10.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(disciplineLabel(entry.discipline)+"  ·  "+entry.text("level"),
                        style=MaterialTheme.typography.labelSmall,
                        color=if(entry.discipline=="run") StrideWorkoutVisuals.lime
                            else disciplineColor(entry.discipline))
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(entry.title,style=MaterialTheme.typography.titleMedium,
                            modifier=Modifier.weight(1f),color=StrideWorkoutVisuals.white)
                        if(entry.id in state.shelf.favorites)
                            Text("Saved",style=MaterialTheme.typography.labelSmall,
                                color=StrideWorkoutVisuals.lime)
                    }
                    entry.text("eq").takeIf { it.isNotBlank() }?.let {
                        Text(it,style=MaterialTheme.typography.bodySmall,
                            color=StrideWorkoutVisuals.muted)
                    }
                    entry.text("purpose").takeIf { it.isNotBlank() }?.let {
                        Text(it,style=MaterialTheme.typography.bodyMedium,
                            color=StrideWorkoutVisuals.white)
                    }
                    HorizontalDivider(color=StrideWorkoutVisuals.divider,
                        modifier=Modifier.padding(top=9.dp))
                }
            }
        }
        }
    } }
    entries.firstOrNull { it.id==selected }?.let { entry ->
        ModalBottomSheet(onDismissRequest={focus.clearFocus();keyboard?.hide();selected=null},
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
            containerColor=StrideWorkoutVisuals.card,
            contentColor=StrideWorkoutVisuals.white) {
            MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(
                primary=StrideWorkoutVisuals.lime,
                onPrimary=Color(0xFF10120D),
                surface=StrideWorkoutVisuals.card,
                onSurface=StrideWorkoutVisuals.white,
                onSurfaceVariant=StrideWorkoutVisuals.muted,
                outline=StrideWorkoutVisuals.divider
            )) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Text(disciplineLabel(entry.discipline)+" · "+entry.text("level"),style=MaterialTheme.typography.labelMedium,color=disciplineColor(entry.discipline))
                Text(entry.title,style=MaterialTheme.typography.headlineMedium)
                LibraryImage(entry)
                Text(entry.text("eq")+" · "+entry.text("pattern"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.text("purpose"),style=MaterialTheme.typography.bodyLarge)
                if(entry.text("secondaryMuscles").isNotBlank())
                    Text("Secondary: "+entry.text("secondaryMuscles"),style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
                if(entry.text("setupNotes").isNotBlank()) {
                    Text("SETUP",style=MaterialTheme.typography.labelMedium,color=StrideWorkoutVisuals.muted)
                    Text(entry.text("setupNotes"),style=MaterialTheme.typography.bodyMedium)
                }
                if(entry.guide || entry.raw.optBoolean("educationalOnly",false))
                    Text("Educational reference. Use reviewed training plans or session templates for executable workouts; this catalog does not automatically prescribe exercises.",style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
                else SignalButton(if(entry.discipline=="run")"Build a running plan" else "Prepare practice session", { focus.clearFocus();keyboard?.hide();selected=null; onPractice(entry) })
                if(state.account!=null) {
                    OutlinedButton(onClick={actions.shelf(entry.id,null,false)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(if(entry.id in state.shelf.favorites) "Remove favorite" else "Save favorite") }
                }
                Text("TECHNIQUE & STRUCTURE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                entry.list("tech").forEachIndexed { i,cue -> Text("${i+1}. $cue") }
                if(entry.text("mistake").isNotBlank())
                    Text(entry.text("mistake"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(entry.text("caveats").isNotBlank()) {
                    Text("CAUTIONS",style=MaterialTheme.typography.labelMedium,color=StrideWorkoutVisuals.muted)
                    Text(entry.text("caveats"),style=MaterialTheme.typography.bodySmall)
                }
                if(entry.text("prerequisiteNotes").isNotBlank())
                    Text(entry.text("prerequisiteNotes"),style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
                if(entry.text("prereq").isNotBlank()) {
                    Text("PREREQUISITES",style=MaterialTheme.typography.labelMedium,color=StrideWorkoutVisuals.lime)
                    Text(entry.text("prereq"),style=MaterialTheme.typography.bodyMedium)
                }
                if(entry.text("sources").isNotBlank()) {
                    Text("SOURCE PROVENANCE",style=MaterialTheme.typography.labelMedium,color=StrideWorkoutVisuals.muted)
                    Text(entry.text("sources"),style=MaterialTheme.typography.bodySmall)
                }
                if(entry.text("mediaNote").isNotBlank())
                    Text(entry.text("mediaNote"),style=MaterialTheme.typography.bodySmall,color=StrideWorkoutVisuals.muted)
                Text("MAKE IT EASIER",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(entry.text("reg"))
                Text("PROGRESSION",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(entry.text("prog"))
                Text("Advance only when the current option is controlled and repeatable. A completed workout does not automatically grant a skill stage.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val alternatives=entry.list("alts").mapNotNull { id -> entries.find { it.id==id } }
                if(alternatives.isNotEmpty()) { Text("ALTERNATIVES FOR YOUR SETUP",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    Text("Equipment, range and loading differ. Use the new prescription; never transfer a previous load automatically.",style=MaterialTheme.typography.bodySmall)
                    alternatives.forEach { other -> OutlinedButton(onClick={selected=other.id},modifier=Modifier.fillMaxWidth()) { Text(other.title+" · "+other.text("eq")) } }
                }
                if(state.account!=null) {
                    OutlinedTextField(collectionName,{collectionName=it.take(40)},label={Text("Collection name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedButton(onClick={actions.shelf(entry.id,collectionName,false)},enabled=collectionName.isNotBlank()&&!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Add to collection") }
                    state.shelf.collections.filterValues { entry.id in it }.keys.forEach { name -> TextButton(onClick={actions.shelf(entry.id,name,true)}) { Text("Remove from $name") } }
                } else Text("Your local profile is still initializing.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={focus.clearFocus();keyboard?.hide();selected=null},modifier=Modifier.fillMaxWidth()) { Text("Close") }
            }
            }
        }
    }
}
@Composable internal fun LibraryImage(entry:LibraryEntry,compact:Boolean=false) {
    val metadata=entry.raw.optJSONObject("image") ?: return
    val context=LocalContext.current; val uri=LocalUriHandler.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,entry.id) { withContext(Dispatchers.Main.immediate) { value=withContext(Dispatchers.IO) { runCatching { context.assets.open(metadata.getString("nativeFile")).use(BitmapFactory::decodeStream) }.getOrNull() } } }
    bitmap?.let { Image(it.asImageBitmap(),contentDescription=metadata.getString("label"),contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().heightIn(max=if(compact)180.dp else 230.dp).background(Color.White,RoundedCornerShape(20.dp)).padding(12.dp)) }
    val fit=metadata.getJSONArray("fit")
    if(!compact || fit.getString(0)!="exact")Text(fit.getString(1),style=MaterialTheme.typography.bodySmall)
    var details by rememberSaveable(entry.id) { mutableStateOf(false) }
    Text(metadata.getString("author")+" · "+metadata.getString("license").let { if(it.length<=40)it else "License details below" },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(!compact)TextButton(onClick={details=!details}) { Text(if(details) "Hide image provenance" else "Image source & license") }
    if(details) {
        Text(metadata.getString("label")+"\n"+metadata.getString("license")+"\n"+metadata.optString("changes"),style=MaterialTheme.typography.bodySmall)
        metadata.optString("source").takeIf { it.startsWith("https://") }?.let { link -> TextButton(onClick={uri.openUri(link)}) { Text("Open original image source") } }
        metadata.optString("licenseUrl").takeIf { it.startsWith("https://") }?.let { link -> TextButton(onClick={uri.openUri(link)}) { Text("Open license") } }
    }
}
internal fun disciplineLabel(id:String)=when(id) { "run"->"Running";"str"->"Strength";"cal"->"Calisthenics";"body"->"Bodyweight";"mob"->"Mobility";else->"Conditioning" }
internal fun disciplineColor(id:String)=when(id) { "run"->TracksidePalette.running;"str"->TracksidePalette.strength;"cal"->TracksidePalette.calisthenics;"mob"->TracksidePalette.recovery;"body"->TracksidePalette.calisthenics;else->Color(0xFFFFB46A) }
@Composable internal fun SignalButton(text:String,onClick:()->Unit,enabled:Boolean=true) { Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(50),contentPadding=PaddingValues(20.dp,16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) { Text(text,style=MaterialTheme.typography.titleMedium) } }


@Composable internal fun SignalChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit,modifier:Modifier=Modifier) {
    FilterChip(selected,onClick,label=label,modifier=modifier,shape=RoundedCornerShape(50),border=androidx.compose.foundation.BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),colors=FilterChipDefaults.filterChipColors(containerColor=androidx.compose.ui.graphics.Color.Transparent,labelColor=MaterialTheme.colorScheme.onSurface,selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))
}

@Composable internal fun ShelfFilter(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true}) { Text("$label: $selected") }
        DropdownMenu(expanded,{expanded=false}) { options.forEach { value -> DropdownMenuItem(text={Text(value)},onClick={onSelect(value);expanded=false}) } }
    }
}
