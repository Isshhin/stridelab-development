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

@Composable internal fun LibraryScreen(state:TrainingState=TrainingState(initializing=false),actions:PlanActions=PlanActions(),onOpen:(String,String)->Unit={_,_->},onPractice:(LibraryEntry)->Unit = {}) {
    val context=LocalContext.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var failure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { readLibrary(context) } }.onSuccess { entries=it }.onFailure { failure=true } }
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("all") }
    var view by rememberSaveable { mutableStateOf("Workouts") };val guides=view=="Movement guides"
    var level by rememberSaveable { mutableStateOf("All levels") };var pattern by rememberSaveable { mutableStateOf("All movements") };var equipment by rememberSaveable { mutableStateOf("All equipment") };var collectionFilter by rememberSaveable { mutableStateOf("All saved") }
    var collectionName by rememberSaveable { mutableStateOf("") }; var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val matches=entries.filter { it.guide==guides && (filter=="all" || it.discipline==filter) && (it.title+" "+it.text("eq")+" "+it.text("pattern")).contains(query,true) &&
        (level=="All levels" || it.text("level")==level) && (pattern=="All movements" || it.text("pattern")==pattern) && (equipment=="All equipment" || it.text("eq")==equipment) &&
        (view!="Saved" || if(collectionFilter=="All saved") it.id in state.shelf.favorites || state.shelf.collections.values.any { ids->it.id in ids } else if(collectionFilter=="Favorites")it.id in state.shelf.favorites else it.id in (state.shelf.collections[collectionFilter] ?: emptySet())) }
    val logged=state.plans.flatMap { p -> p.sessions.filter { it.log!=null }.map { p to it } }.sortedByDescending { it.second.log?.at }
    Box(Modifier.fillMaxSize().testTag(if(entries.isNotEmpty())"library-ready" else "library-loading")) { Screen("Library","RUN, LIFT, MOVE") {
        item { Text("Explore the purpose, learn the technique and find an option for your setup.",style=MaterialTheme.typography.bodyLarge) }
        if(view!="Sessions") {
        item { Text("${matches.size} ${if(guides) "educational references · not tracked workouts" else "curated entries"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Workouts","Saved","Sessions","Movement guides").forEach { name -> SignalChip(view==name,{view=name},label={Text(name)}) } } }
        if(view=="Sessions") {
            item { Text("Actual saved workout logs, including partially recorded sessions. Targets are never counted as completed.") }
            if(logged.isEmpty())item { Text("No workout logs saved for this account yet. Start with an approved training plan.") }
            items(logged,key={it.second.id}) { (p,session) -> PlannedWorkoutCard(session) { onOpen(p.id,session.id) } }
        } else {
        if(view=="Saved") item {
            Text(if(state.account==null) "Sign in to keep a private offline library." else "Favorites and collections are stored on this device for your account.",style=MaterialTheme.typography.bodySmall)
            ShelfFilter("Collection",listOf("All saved","Favorites")+state.shelf.collections.keys.sorted(),collectionFilter) { collectionFilter=it }
        }
        item { OutlinedTextField(query,{query=it},label={Text("Search exercises")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),singleLine=true) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("all" to "All","run" to "Running","str" to "Strength","cal" to "Calisthenics").forEach { (id,label) -> SignalChip(selected=filter==id,onClick={filter=id},label={Text(label)}) } } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ShelfFilter("Level",listOf("All levels")+entries.filter { it.guide==guides }.map { it.text("level") }.distinct().sorted(),level) { level=it }
            ShelfFilter("Movement",listOf("All movements")+entries.filter { it.guide==guides }.map { it.text("pattern") }.filter { it.isNotBlank() }.distinct().sorted(),pattern) { pattern=it }
            ShelfFilter("Equipment",listOf("All equipment")+entries.filter { it.guide==guides }.map { it.text("eq") }.filter { it.isNotBlank() }.distinct().sorted(),equipment) { equipment=it }
        } }
        item { if(level!="All levels"||pattern!="All movements"||equipment!="All equipment"||filter!="all"||query.isNotEmpty())TextButton(onClick={level="All levels";pattern="All movements";equipment="All equipment";filter="all";query=""}) { Text("Clear search and filters") } }
        if(failure) item { Text("The offline library could not be opened. Original assets remain preserved in Settings.") }
        if(matches.isEmpty() && entries.isNotEmpty()) item { Text("No entries match. Try a broader search or another discipline.") }
        items(matches,key={it.id}) { entry -> SignalCard(Modifier.clickable { focus.clearFocus();keyboard?.hide();selected=entry.id }) {
            Text(disciplineLabel(entry.discipline)+" · "+entry.text("level"),style=MaterialTheme.typography.labelMedium,color=disciplineColor(entry.discipline))
            Text(entry.title+if(entry.id in state.shelf.favorites) " ★" else "",style=MaterialTheme.typography.titleLarge)
            Text(entry.text("eq"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.text("purpose"),style=MaterialTheme.typography.bodyMedium)
        } }
        }
    } }
    entries.firstOrNull { it.id==selected }?.let { entry ->
        ModalBottomSheet(onDismissRequest={focus.clearFocus();keyboard?.hide();selected=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                LibraryImage(entry)
                Text(disciplineLabel(entry.discipline)+" · "+entry.text("level"),style=MaterialTheme.typography.labelMedium,color=disciplineColor(entry.discipline))
                Text(entry.title,style=MaterialTheme.typography.headlineMedium)
                Text(entry.text("eq")+" · "+entry.text("pattern"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.text("purpose"),style=MaterialTheme.typography.bodyLarge)
                Text("TECHNIQUE & STRUCTURE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                entry.list("tech").forEachIndexed { i,cue -> Text("${i+1}. $cue") }
                Text(entry.text("mistake"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("MAKE IT EASIER",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(entry.text("reg"))
                Text("PROGRESSION",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(entry.text("prog"))
                Text("Advance only when the current option is controlled and repeatable. A completed workout does not automatically grant a skill stage.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val alternatives=entry.list("alts").mapNotNull { id -> entries.find { it.id==id } }
                if(alternatives.isNotEmpty()) { Text("ALTERNATIVES FOR YOUR SETUP",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    Text("Equipment, range and loading differ. Use the new prescription; never transfer a previous load automatically.",style=MaterialTheme.typography.bodySmall)
                    alternatives.forEach { other -> OutlinedButton(onClick={selected=other.id},modifier=Modifier.fillMaxWidth()) { Text(other.title+" · "+other.text("eq")) } }
                }
                if(entry.guide) Text("Educational reference. This is not an executable workout.",style=MaterialTheme.typography.bodySmall)
                else SignalButton("Build a plan with this discipline", { focus.clearFocus();keyboard?.hide();selected=null; onPractice(entry) })
                if(state.account!=null) {
                    OutlinedButton(onClick={actions.shelf(entry.id,null,false)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(if(entry.id in state.shelf.favorites) "Remove favorite" else "Save favorite") }
                    OutlinedTextField(collectionName,{collectionName=it.take(40)},label={Text("Collection name")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    OutlinedButton(onClick={actions.shelf(entry.id,collectionName,false)},enabled=collectionName.isNotBlank()&&!state.busy,modifier=Modifier.fillMaxWidth()) { Text("Add to collection") }
                    state.shelf.collections.filterValues { entry.id in it }.keys.forEach { name -> TextButton(onClick={actions.shelf(entry.id,name,true)}) { Text("Remove from $name") } }
                } else Text("Sign in to save favorites and collections for your account.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={focus.clearFocus();keyboard?.hide();selected=null},modifier=Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}
@Composable private fun LibraryImage(entry:LibraryEntry) {
    val metadata=entry.raw.optJSONObject("image") ?: return
    val context=LocalContext.current; val uri=LocalUriHandler.current
    val bitmap by produceState<android.graphics.Bitmap?>(null,entry.id) { value=withContext(Dispatchers.IO) { runCatching { context.assets.open(metadata.getString("nativeFile")).use(BitmapFactory::decodeStream) }.getOrNull() } }
    bitmap?.let { Image(it.asImageBitmap(),contentDescription=metadata.getString("label"),contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().heightIn(max=230.dp).background(Color.White,RoundedCornerShape(20.dp)).padding(12.dp)) }
    val fit=metadata.getJSONArray("fit")
    Text(fit.getString(0).uppercase()+" · "+fit.getString(1),style=MaterialTheme.typography.bodySmall)
    Text(metadata.getString("label")+"\n"+metadata.getString("author")+" · "+metadata.getString("license"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    metadata.optString("source").takeIf { it.startsWith("https://") }?.let { link -> TextButton(onClick={uri.openUri(link)}) { Text("Image source & attribution") } }
}
internal fun disciplineLabel(id:String)=when(id) { "run"->"Running";"str"->"Strength";"cal"->"Calisthenics";else->"Conditioning" }
internal fun disciplineColor(id:String)=when(id) { "run"->Color(0xFF6FB4FF);"str"->Color(0xFFFF9A62);else->Color(0xFFC79BFF) }
@Composable internal fun SignalButton(text:String,onClick:()->Unit,enabled:Boolean=true) { Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(50),contentPadding=PaddingValues(20.dp,16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) { Text(text,style=MaterialTheme.typography.titleMedium) } }


@Composable internal fun SignalChip(selected:Boolean,onClick:()->Unit,label:@Composable ()->Unit) {
    FilterChip(selected,onClick,label=label,shape=RoundedCornerShape(50),border=androidx.compose.foundation.BorderStroke(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),colors=FilterChipDefaults.filterChipColors(containerColor=androidx.compose.ui.graphics.Color.Transparent,labelColor=MaterialTheme.colorScheme.onSurface,selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))
}

@Composable internal fun ShelfFilter(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true}) { Text("$label: $selected") }
        DropdownMenu(expanded,{expanded=false}) { options.forEach { value -> DropdownMenuItem(text={Text(value)},onClick={onSelect(value);expanded=false}) } }
    }
}
