package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Secondary training-flow scaffold. Reuses the approved Today/More colors
 * without mutating the global theme or plan/engine state.
 */
@Composable
internal fun StridePlanScreen(
    title:String, subtitle:String, onBack:()->Unit,
    content:LazyListScope.()->Unit
) {
    val colors=MaterialTheme.colorScheme.copy(
        primary=StrideWorkoutVisuals.lime,
        onPrimary=Color(0xFF10120D),
        background=StrideWorkoutVisuals.black,
        onBackground=StrideWorkoutVisuals.white,
        surface=StrideWorkoutVisuals.card,
        onSurface=StrideWorkoutVisuals.white,
        onSurfaceVariant=StrideWorkoutVisuals.muted,
        outline=StrideWorkoutVisuals.divider
    )
    MaterialTheme(colorScheme=colors) {
        Surface(color=StrideWorkoutVisuals.black,
            contentColor=StrideWorkoutVisuals.white,
            modifier=Modifier.fillMaxSize()) {
            LazyColumn(
                modifier=Modifier.fillMaxSize().testTag("screen")
                    .semantics { paneTitle=title },
                contentPadding=PaddingValues(start=22.dp,end=22.dp,top=8.dp,
                    bottom=if(LocalFloatingDockVisible.current) 112.dp else 36.dp),
                verticalArrangement=Arrangement.spacedBy(20.dp)
            ) {
                item(key="plan-screen-header") {
                    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            IconButton(onClick=onBack,
                                modifier=Modifier.size(48.dp).testTag("manage-back")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription="Back to Training")
                            }
                            Text(title,style=MaterialTheme.typography.titleLarge,
                                fontWeight=FontWeight.SemiBold,
                                modifier=Modifier.padding(start=4.dp))
                        }
                        Text(subtitle.uppercase(),fontSize=11.sp,
                            color=StrideWorkoutVisuals.lime)
                    }
                }
                content()
            }
        }
    }
}

@Composable
internal fun StridePlanActionRow(
    title:String, subtitle:String, enabled:Boolean,
    modifier:Modifier=Modifier, onClick:()->Unit
) {
    Surface(onClick=onClick,enabled=enabled,
        color=Color.Transparent,
        contentColor=if(enabled) StrideWorkoutVisuals.white else StrideWorkoutVisuals.muted,
        modifier=modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp)
                .padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(title,style=MaterialTheme.typography.titleMedium,
                        color=if(enabled) StrideWorkoutVisuals.white else StrideWorkoutVisuals.muted)
                    Text(subtitle,style=MaterialTheme.typography.bodySmall,
                        color=StrideWorkoutVisuals.muted)
                }
                Text("›",fontSize=24.sp,color=if(enabled) StrideWorkoutVisuals.lime
                    else StrideWorkoutVisuals.muted)
            }
            HorizontalDivider(color=StrideWorkoutVisuals.divider)
        }
    }
}
