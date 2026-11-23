package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * More subpage presentation only. Matches the current native More screen,
 * rather than the old gradient Screen/SignalCard implementation.
 * No activity analysis, repository, or backup behavior belongs here.
 */
internal object MoreFeatureColors {
    val background = Color(0xFF080B0A)
    val surface = Color(0xFF151A18)
    val border = Color(0xFF364039)
    val divider = Color(0xFF303833)
    val text = Color(0xFFF1F4F1)
    val muted = Color(0xFFAAB4AD)
    val subdued = Color(0xFF82918A)
    val lime = Color(0xFFD8FB70)
}

@Composable
internal fun MoreFeatureScreen(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    backEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .background(MoreFeatureColors.background)
            .testTag("screen")
            .semantics { paneTitle = title },
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp,
            bottom = if (LocalFloatingDockVisible.current) 110.dp else 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "more-feature-header") {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = backEnabled,
                    modifier = Modifier.size(48.dp).testTag(backTag),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MoreFeatureColors.lime)
                ) {
                    Text("‹", fontSize = 30.sp)
                }
                Text(
                    text = title,
                    fontSize = 27.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoreFeatureColors.text,
                    modifier = Modifier.weight(1f).testTag("screen-title")
                        .semantics { heading() }
                )
            }
        }
        content()
    }
}

@Composable
internal fun MoreFeatureCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MoreFeatureColors.surface,
        contentColor = MoreFeatureColors.text,
        border = BorderStroke(1.dp, MoreFeatureColors.border),
        shape = RoundedCornerShape(19.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
internal fun MoreFeatureEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.SemiBold,
        color = MoreFeatureColors.subdued
    )
}

@Composable
internal fun MoreFeatureMuted(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = MoreFeatureColors.muted
    )
}

@Composable
internal fun MoreFeatureChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        color = if (selected) MoreFeatureColors.lime else MoreFeatureColors.surface,
        contentColor = if (selected) MoreFeatureColors.background else
            if (enabled) MoreFeatureColors.text else MoreFeatureColors.subdued,
        border = BorderStroke(
            1.dp, if (selected) MoreFeatureColors.lime else MoreFeatureColors.border
        ),
        shape = RoundedCornerShape(13.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun MoreFeaturePrimaryAction(
    text: String, onClick: () -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MoreFeatureColors.lime,
            contentColor = MoreFeatureColors.background,
            disabledContainerColor = MoreFeatureColors.border,
            disabledContentColor = MoreFeatureColors.subdued
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
internal fun MoreFeatureSecondaryAction(
    text: String, onClick: () -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MoreFeatureColors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MoreFeatureColors.text,
            disabledContentColor = MoreFeatureColors.subdued
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) { Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}

@Composable
internal fun MoreFeatureDivider() {
    HorizontalDivider(thickness = 1.dp, color = MoreFeatureColors.divider)
}
