package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class SkillNodeStatus(val label: String) {
    MASTERED("Mastered"),
    UNLOCKED("Ready"),
    LOCKED("Locked")
}

internal data class CalisthenicsNodeProgress(
    val node: CalisthenicsDagNode,
    val status: SkillNodeStatus,
    val bestReps: Int?,
    val bestHoldSeconds: Int?,
    val prerequisiteNames: List<String>
)

internal data class CalisthenicsDomainJourney(
    val domain: CalisthenicsDomain,
    val title: String,
    val nodes: List<CalisthenicsNodeProgress>,
    val masteredCount: Int,
    val nextMilestone: CalisthenicsNodeProgress?
)

internal object CalisthenicsSkillJourneyProjector {
    fun domainLabel(domain: CalisthenicsDomain): String = when (domain) {
        CalisthenicsDomain.PULL_UP -> "Pull-Up"
        CalisthenicsDomain.PUSH_UP -> "Push-Up"
        CalisthenicsDomain.DIP -> "Dip"
        CalisthenicsDomain.HANDSTAND_SKILL -> "Handstand & Balance"
        CalisthenicsDomain.CORE_AND_L_SIT -> "Core & L-Sit"
        CalisthenicsDomain.SINGLE_LEG_SQUAT -> "Single-Leg Squat"
        CalisthenicsDomain.WEIGHTED_CALISTHENICS -> "Weighted Calisthenics"
        CalisthenicsDomain.ISOMETRIC_STATIC_HOLDS -> "Static Holds"
    }

    fun buildJourneys(
        plans: List<PlanSnapshot>,
        freeformWorkouts: List<FreeformWorkout>,
        ownerId: String?
    ): List<CalisthenicsDomainJourney> {
        val nodes = Engine17CalisthenicsDagCatalog.buildNodes()
        val nodeById = nodes.associateBy { it.exercise.id }

        val bestRepsById = mutableMapOf<String, Int>()
        val bestHoldById = mutableMapOf<String, Int>()

        fun record(idOrName: String, reps: Int?, holdSec: Int?) {
            val key = idOrName.lowercase()
            if (reps != null && reps > 0) {
                bestRepsById[key] = maxOf(bestRepsById[key] ?: 0, reps)
            }
            if (holdSec != null && holdSec > 0) {
                bestHoldById[key] = maxOf(bestHoldById[key] ?: 0, holdSec)
            }
        }

        for (plan in plans.filter { ownerId == null || it.owner == ownerId }) {
            for (session in plan.sessions) {
                if (session.status != "completed") continue
                val log = session.log ?: continue
                for (set in log.sets) {
                    record(set.exerciseId, set.reps, set.seconds)
                }
            }
        }
        for (workout in freeformWorkouts.filter {
            (ownerId == null || it.owner == ownerId) && it.completedAt != null
        }) {
            for (ex in workout.exercises) {
                for (set in ex.sets.filter { it.completed }) {
                    val reps = set.actual.quantity.trim().toIntOrNull()
                    val sec = set.actual.seconds.trim().toIntOrNull()
                    ex.catalogId?.let { record(it, reps, sec) }
                    record(ex.name, reps, sec)
                }
            }
        }

        fun isNodeMastered(node: CalisthenicsDagNode): Boolean {
            val idKey = node.exercise.id.lowercase()
            val nameKey = node.exercise.name.lowercase()
            val bestReps = maxOf(bestRepsById[idKey] ?: 0, bestRepsById[nameKey] ?: 0)
            val bestHold = maxOf(bestHoldById[idKey] ?: 0, bestHoldById[nameKey] ?: 0)
            return if (node.exercise.isIsometricHold) {
                val targetHold = node.defaultHoldSeconds ?: 20
                bestHold >= targetHold
            } else {
                bestReps >= node.bypassCleanRepsRequired
            }
        }

        val masteredIds = nodes.filter(::isNodeMastered).map { it.exercise.id }.toSet()

        fun gateSatisfied(gate: CalisthenicsPrerequisiteGate): Boolean = when (gate) {
            CalisthenicsPrerequisiteGate.None -> true
            is CalisthenicsPrerequisiteGate.RequireAll -> gate.nodeIds.all { it in masteredIds }
            is CalisthenicsPrerequisiteGate.RequireAny -> gate.nodeIds.any { it in masteredIds }
        }

        fun prereqNames(gate: CalisthenicsPrerequisiteGate): List<String> = when (gate) {
            CalisthenicsPrerequisiteGate.None -> emptyList()
            is CalisthenicsPrerequisiteGate.RequireAll -> gate.nodeIds.mapNotNull { nodeById[it]?.exercise?.name }
            is CalisthenicsPrerequisiteGate.RequireAny -> gate.nodeIds.mapNotNull { nodeById[it]?.exercise?.name }
        }

        return CalisthenicsDomain.entries.mapNotNull { domain ->
            val domainNodes = nodes.filter { it.domain == domain }
                .sortedWith(compareBy<CalisthenicsDagNode> { it.difficultyTier }.thenBy { it.exercise.name })
            if (domainNodes.isEmpty()) return@mapNotNull null

            val progressed = domainNodes.map { node ->
                val idKey = node.exercise.id.lowercase()
                val nameKey = node.exercise.name.lowercase()
                val reps = maxOf(bestRepsById[idKey] ?: 0, bestRepsById[nameKey] ?: 0).takeIf { it > 0 }
                val hold = maxOf(bestHoldById[idKey] ?: 0, bestHoldById[nameKey] ?: 0).takeIf { it > 0 }
                val status = when {
                    node.exercise.id in masteredIds -> SkillNodeStatus.MASTERED
                    gateSatisfied(node.gate) -> SkillNodeStatus.UNLOCKED
                    else -> SkillNodeStatus.LOCKED
                }
                CalisthenicsNodeProgress(
                    node = node,
                    status = status,
                    bestReps = reps,
                    bestHoldSeconds = hold,
                    prerequisiteNames = prereqNames(node.gate)
                )
            }
            val masteredCount = progressed.count { it.status == SkillNodeStatus.MASTERED }
            val next = progressed.firstOrNull { it.status == SkillNodeStatus.UNLOCKED }
                ?: progressed.firstOrNull { it.status == SkillNodeStatus.LOCKED }
            CalisthenicsDomainJourney(
                domain = domain,
                title = domainLabel(domain),
                nodes = progressed,
                masteredCount = masteredCount,
                nextMilestone = next
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalisthenicsSkillJourneySheet(
    journeys: List<CalisthenicsDomainJourney>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDomainName by rememberSaveable { mutableStateOf(journeys.firstOrNull()?.domain?.name ?: "") }
    val activeJourney = journeys.firstOrNull { it.domain.name == selectedDomainName } ?: journeys.firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StrideLabSurfaceTokens.surfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("calisthenics-skill-sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "CALISTHENICS SKILL JOURNEYS",
                color = StrideLabVisualPalette.calisthenicsViolet,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = "Prerequisite Progression Tree",
                color = StrideLabSurfaceTokens.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                journeys.take(4).forEach { journey ->
                    StrideLabFilterPill(
                        label = journey.title,
                        selected = activeJourney?.domain == journey.domain,
                        accentColor = StrideLabVisualPalette.calisthenicsViolet,
                        onClick = { selectedDomainName = journey.domain.name }
                    )
                }
            }
            if (journeys.size > 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    journeys.drop(4).forEach { journey ->
                        StrideLabFilterPill(
                            label = journey.title,
                            selected = activeJourney?.domain == journey.domain,
                            accentColor = StrideLabVisualPalette.calisthenicsViolet,
                            onClick = { selectedDomainName = journey.domain.name }
                        )
                    }
                }
            }

            activeJourney?.let { journey ->
                Text(
                    text = "${journey.masteredCount} of ${journey.nodes.size} milestones mastered",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 12.sp
                )
                journey.nodes.forEachIndexed { idx, item ->
                    val statusColor = when (item.status) {
                        SkillNodeStatus.MASTERED -> StrideLabSurfaceTokens.lime
                        SkillNodeStatus.UNLOCKED -> StrideLabVisualPalette.calisthenicsViolet
                        SkillNodeStatus.LOCKED -> StrideLabSurfaceTokens.inkMuted
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StrideLabSurfaceTokens.surfaceSubtle,
                        border = BorderStroke(
                            1.dp,
                            if (item.status == SkillNodeStatus.LOCKED) StrideLabSurfaceTokens.hairline
                            else statusColor.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                if (idx < journey.nodes.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(26.dp)
                                            .background(StrideLabSurfaceTokens.hairline)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.node.exercise.name,
                                        color = StrideLabSurfaceTokens.ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StrideLabMetricBadge(item.status.label, statusColor)
                                }
                                val requirement = if (item.node.exercise.isIsometricHold) {
                                    "Mastery: ${item.node.defaultHoldSeconds ?: 20}s hold"
                                } else {
                                    "Mastery: ${item.node.bypassCleanRepsRequired} clean reps"
                                }
                                val prText = when {
                                    item.bestHoldSeconds != null -> " · Best: ${item.bestHoldSeconds}s"
                                    item.bestReps != null -> " · Best: ${item.bestReps} reps"
                                    else -> ""
                                }
                                Text(
                                    text = "$requirement$prText",
                                    color = StrideLabSurfaceTokens.inkSecondary,
                                    fontSize = 12.sp
                                )
                                if (item.prerequisiteNames.isNotEmpty() && item.status == SkillNodeStatus.LOCKED) {
                                    Text(
                                        text = "Requires: ${item.prerequisiteNames.joinToString(", ")}",
                                        color = StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                item.node.exercise.techniqueCues.firstOrNull()?.let { cue ->
                                    Text(
                                        text = cue,
                                        color = StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
