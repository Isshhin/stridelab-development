package io.stridelab.privatealpha

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

@Composable internal fun RecordedActivityDetail(detail: ActivityDetail, supportsPace: Boolean) {
    var selectedIndex by rememberSaveable(detail.activityId) { mutableStateOf<Int?>(null) }
    val selected=selectedIndex?.let { detail.samples.getOrNull(it) }
    SignalCard {
        Text("RECORDED STREAMS",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        val date = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(detail.fetchedAt))
        Text("Saved $date · ${detail.samples.size} source samples · available offline",style=MaterialTheme.typography.bodySmall)
        if(detail.samples.isEmpty()) Text("No recorded time-series or GPS points were returned. Summary metrics remain available.")
        else {
            Text(selected?.let { "Selected source sample ${it.index} · elapsed ${ActivityRecord.duration(it.seconds)}" } ?: "Tap a chart to inspect the nearest recorded sample. No interpolation is used.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={selectedIndex=((selectedIndex ?: 1)-1).coerceAtLeast(0)},enabled=selectedIndex!=0) { Text("Previous sample") }
                TextButton(onClick={selectedIndex=((selectedIndex ?: -1)+1).coerceAtMost(detail.samples.lastIndex)},enabled=selectedIndex!=detail.samples.lastIndex) { Text("Next sample") }
            }
            if(supportsPace) SampleGraph("Pace", "sec/km",detail.samples,selected,{selectedIndex=it}) { it.pace }
            SampleGraph("Heart rate","bpm",detail.samples,selected,{selectedIndex=it}) { it.hr }
            SampleGraph("Elevation","m",detail.samples,selected,{selectedIndex=it}) { it.altitude }
            val points = detail.samples.filter { it.latitude != null && it.longitude != null }
            if(points.size < 2) Text("No recorded GPS route. Indoor activities and sources without coordinates cannot show a route.")
            else {
                Text("Recorded GPS trace",style=MaterialTheme.typography.titleMedium)
                Text("${points.size} coordinate samples · north up · offline trace without street tiles. Gaps over two minutes and antimeridian crossings are left disconnected.",style=MaterialTheme.typography.bodySmall)
                val color = MaterialTheme.colorScheme.primary
                val projection = cos(Math.toRadians(points.map { it.latitude!! }.average())).coerceAtLeast(.01)
                val minX = points.minOf { it.longitude!! * projection }; val maxX=points.maxOf { it.longitude!! * projection }
                val minY = points.minOf { it.latitude!! }; val maxY=points.maxOf { it.latitude!! }
                Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription="Recorded GPS route trace, ${points.size} coordinate samples. Street map unavailable offline." }) {
                    val spanX=(maxX-minX).coerceAtLeast(.00001); val spanY=(maxY-minY).coerceAtLeast(.00001)
                    val scale=minOf((size.width-24)/spanX,(size.height-24)/spanY)
                    fun point(p: ActivitySample) = Offset(((size.width-spanX*scale)/2+(p.longitude!!*projection-minX)*scale).toFloat(),((size.height-spanY*scale)/2+(maxY-p.latitude!!)*scale).toFloat())
                    for((a,b) in detail.samples.zipWithNext()) if(a.latitude!=null && a.longitude!=null && b.latitude!=null && b.longitude!=null && b.seconds-a.seconds<=120 && abs(a.longitude-b.longitude)<180) drawLine(color,point(a),point(b),3.dp.toPx())
                }
            }
        }
        Text("Source laps",style=MaterialTheme.typography.titleMedium)
        if(detail.laps.isEmpty()) Text("No recorded laps returned. No kilometer splits have been invented.")
        // Keep the detail list bounded; the source may include many tiny device laps.
        detail.laps.take(100).forEach { lap ->
            val distance=lap.distance?.let { String.format(Locale.getDefault(),"%.2f km",it/1000) } ?: "distance not recorded"
            val duration=lap.seconds?.let { ActivityRecord.duration(it) } ?: "time not recorded"
            Text("Lap ${lap.index+1} · $distance · $duration",style=MaterialTheme.typography.bodyLarge)
            val pace=if(supportsPace) lap.pace?.let { "${ActivityRecord.duration(it)} /km" } else null
            Text(listOfNotNull(pace,lap.hr?.let { String.format(Locale.getDefault(),"%.0f bpm",it) }).joinToString(" · ").ifEmpty { "Pace / heart rate not recorded" },style=MaterialTheme.typography.bodySmall)
        }
        if(detail.laps.size>100) Text("Showing first 100 of ${detail.laps.size} recorded laps.")
    }
}

@Composable private fun SampleGraph(label: String, unit: String, samples: List<ActivitySample>, selected:ActivitySample?,onSelect:(Int)->Unit,value: (ActivitySample)->Double?) {
    val valid=samples.mapNotNull { p -> value(p)?.let { p.seconds to it } }
    Text(label,style=MaterialTheme.typography.titleMedium)
    if(valid.size<2 || valid.last().first<=valid.first().first) { Text("No usable $label time-series recorded.",style=MaterialTheme.typography.bodySmall); return }
    val low=valid.minOf { it.second }; val high=valid.maxOf { it.second }
    val description=String.format(Locale.getDefault(),"%s · %.1f–%.1f %s · %d samples · elapsed %s to %s",label,low,high,unit,valid.size,ActivityRecord.duration(valid.first().first),ActivityRecord.duration(valid.last().first))
    Text(description,style=MaterialTheme.typography.bodySmall)
    Text(if(label=="Pace") "Faster pace is higher (seconds per km)" else "Higher line = higher recorded value",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    val color=MaterialTheme.colorScheme.primary
    selected?.let { sample -> Text(value(sample)?.let { n -> "Selected: "+(if(label=="Pace") ActivityRecord.duration(n)+" /km" else String.format(Locale.getDefault(),"%.1f %s",n,unit))+" at "+ActivityRecord.duration(sample.seconds) } ?: "$label was not recorded at this selected sample.",style=MaterialTheme.typography.bodyMedium) }
    Canvas(Modifier.fillMaxWidth().height(110.dp).testTag("stream-$label").semantics { contentDescription=description }.pointerInput(samples) {
        detectTapGestures { tap ->
            val target=valid.first().first+(tap.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f)*(valid.last().first-valid.first().first)
            samples.indices.minByOrNull { abs(samples[it].seconds-target) }?.let(onSelect)
        }
    }) {
        val span=(high-low).coerceAtLeast(1.0); val time=(valid.last().first-valid.first().first).coerceAtLeast(1.0)
        fun point(s: ActivitySample,n:Double) = Offset(((s.seconds-valid.first().first)/time*size.width).toFloat(),(if(label=="Pace") 6+(n-low)/span*(size.height-12) else size.height-(n-low)/span*(size.height-12)-6).toFloat())
        for(i in 0..2)drawLine(color.copy(alpha=.15f),Offset(0f,6f+i*(size.height-12)/2),Offset(size.width,6f+i*(size.height-12)/2),1f)
        selected?.let { sample -> value(sample)?.let { n -> val dot=point(sample,n);drawLine(color.copy(alpha=.5f),Offset(dot.x,0f),Offset(dot.x,size.height),1.dp.toPx());drawCircle(color,4.dp.toPx(),dot) } }
        // Native canvas with bounded input; never bridge missing measurements or long recording gaps.
        for((a,b) in samples.zipWithNext()) {
            val av=value(a); val bv=value(b)
            if(av!=null && bv!=null && b.seconds-a.seconds<=120) drawLine(color,point(a,av),point(b,bv),2.dp.toPx())
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(ActivityRecord.duration(valid.first().first),style=MaterialTheme.typography.labelSmall);Text(ActivityRecord.duration(valid.last().first),style=MaterialTheme.typography.labelSmall) }
    Text("Elapsed time → · gaps left blank · recorded values, not an intensity recommendation",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
