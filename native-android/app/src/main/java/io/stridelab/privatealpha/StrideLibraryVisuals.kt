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

/** Library and session preparation follow the modern native StrideLab palette. */
@Composable
internal fun StrideLibraryFrame(
    title:String = "Library",
    subtitle:String = "RUN · LIFT · MOVE",
    onBack:(()->Unit)?=null,
    content:LazyListScope.()->Unit
) {
    MaterialTheme(colorScheme=MaterialTheme.colorScheme.copy(
        primary=StrideWorkoutVisuals.lime,
        onPrimary=Color(0xFF10120D),
        background=StrideWorkoutVisuals.black,
        onBackground=StrideWorkoutVisuals.white,
        surface=StrideWorkoutVisuals.card,
        onSurface=StrideWorkoutVisuals.white,
        onSurfaceVariant=StrideWorkoutVisuals.muted,
        outline=StrideWorkoutVisuals.divider
    )) {
        Surface(color=StrideWorkoutVisuals.black,contentColor=StrideWorkoutVisuals.white,
            modifier=Modifier.fillMaxSize()) {
            LazyColumn(
                modifier=Modifier.fillMaxSize().testTag("screen")
                    .semantics { paneTitle=title },
                contentPadding=PaddingValues(start=22.dp,end=22.dp,top=12.dp,
                    bottom=if(LocalFloatingDockVisible.current) 112.dp else 36.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)
            ) {
                item(key="library-header") {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        if(onBack!=null) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                IconButton(onClick=onBack,modifier=Modifier.size(48.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription="Back to Library")
                                }
                                Text(title,style=MaterialTheme.typography.titleLarge,
                                    modifier=Modifier.padding(start=4.dp))
                            }
                        } else {
                            Text(title,style=MaterialTheme.typography.headlineMedium,
                                fontWeight=FontWeight.SemiBold)
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
internal fun StrideLibraryChip(
    selected:Boolean, onClick:()->Unit, label:String,
    modifier:Modifier=Modifier
) {
    FilterChip(
        selected=selected,onClick=onClick,
        label={ Text(label) },
        modifier=modifier.heightIn(min=48.dp),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(50),
        colors=FilterChipDefaults.filterChipColors(
            containerColor=Color(0xFF17191A),
            labelColor=StrideWorkoutVisuals.muted,
            selectedContainerColor=StrideWorkoutVisuals.lime,
            selectedLabelColor=Color(0xFF10120D)
        ),
        border=androidx.compose.foundation.BorderStroke(1.dp,
            if(selected) StrideWorkoutVisuals.lime else StrideWorkoutVisuals.divider)
    )
}
