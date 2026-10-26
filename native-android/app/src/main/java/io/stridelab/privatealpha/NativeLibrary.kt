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

@Composable internal fun LibraryScreen(onPractice:(LibraryEntry)->Unit = {}) {
    val context=LocalContext.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var entries by remember { mutableStateOf<List<LibraryEntry>>(emptyList()) }
    var failure by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { readLibrary(context) } }.onSuccess { entries=it }.onFailure { failure=true } }
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("all") }
    var guides by rememberSaveable { mutableStateOf(false) }; var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val matches=entries.filter { it.guide==guides && (filter=="all" || it.discipline==filter) && (it.title+" "+it.text("eq")+" "+it.text("pattern")).contains(query,true) }
    Screen("Library","RUN, LIFT, MOVE") {
        item { Text("Explore the purpose, learn the technique and find an option for your setup.",style=MaterialTheme.typography.bodyLarge) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { SignalChip(selected=!guides,onClick={guides=false},label={Text("Workouts")});SignalChip(selected=guides,onClick={guides=true},label={Text("Movement guides")}) } }
        item { OutlinedTextField(query,{query=it},label={Text("Search exercises")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),singleLine=true) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("all" to "All","run" to "Running","str" to "Strength","cal" to "Calisthenics").forEach { (id,label) -> SignalChip(selected=filter==id,onClick={filter=id},label={Text(label)}) } } }
        item { Text("${matches.size} ${if(guides) "educational references · not tracked workouts" else "curated entries"}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(failure) item { Text("The offline library could not be opened. Original assets remain preserved in Settings.") }
        if(matches.isEmpty() && entries.isNotEmpty()) item { Text("No entries match. Try a broader search or another discipline.") }
        items(matches,key={it.id}) { entry -> SignalCard(Modifier.clickable { focus.clearFocus();keyboard?.hide();selected=entry.id }) {
            Text(disciplineLabel(entry.discipline)+" · "+entry.text("level"),style=MaterialTheme.typography.labelMedium,color=disciplineColor(entry.discipline))
            Text(entry.title,style=MaterialTheme.typography.titleLarge)
            Text(entry.text("eq"),color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.text("purpose"),style=MaterialTheme.typography.bodyMedium)
        } }
    }
    entries.firstOrNull { it.id==selected }?.let { entry ->
        ModalBottomSheet(onDismissRequest={selected=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface) {
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
                else SignalButton("Build a plan with this discipline", { selected=null; onPractice(entry) })
                TextButton(onClick={selected=null},modifier=Modifier.fillMaxWidth()) { Text("Close") }
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
