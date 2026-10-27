package io.stridelab.privatealpha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Experimental read-only review presentation. Approval actions remain disabled until
 * a real transactional persistence adapter has passed migration/offline verification.
 */
@Composable
internal fun Engine2ProposalReviewCard(
    preview: Engine2ProposalPreview,
    persistenceVerified: Boolean = false,
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
    onPostpone: () -> Unit = {}
) {
    val canDecide = persistenceVerified && preview.eligibleForApproval
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Review proposed adjustment", style = MaterialTheme.typography.titleMedium)
            Text("Scheduled: " + preview.scheduledDate)
            Text("Current session: " + preview.originalMinutes + " min")
            Text("Proposed session: " + preview.proposedMinutes + " min")
            Text("Weekly scheduled time: " + preview.originalWeekMinutes + " → " +
                preview.proposedWeekMinutes + " min")
            Text(preview.reason, style = MaterialTheme.typography.bodyMedium)
            Text("Supporting source records: " + preview.evidenceRecordIds.size,
                style = MaterialTheme.typography.bodySmall)
            if (!preview.eligibleForApproval) {
                Text("This proposal is no longer current. Review the latest saved plan before deciding.",
                    color = MaterialTheme.colorScheme.error)
            } else if (!persistenceVerified) {
                Text("Preview only. Approvals require verified offline saving and revision protection.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = canDecide) { Text("Approve") }
                OutlinedButton(onClick = onReject, enabled = canDecide) { Text("Keep original") }
            }
            OutlinedButton(onClick = onPostpone, enabled = canDecide) {
                Text("Postpone decision")
            }
        }
    }
}
