package com.scanvault.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConflictResolutionScreen(
    viewModel: ConflictResolutionViewModel = hiltViewModel(),
    onResolved: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text("Sync Conflict", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (state == null) {
            Text("No conflict to resolve.")
            return
        }

        val conflict = state!!

        Text("Document: ${conflict.docId}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConflictVersionCard(
                title = "Server version",
                version = conflict.serverVersion,
                timestamp = conflict.serverTimestamp,
                isSelected = conflict.choice == ConflictChoice.SERVER,
                modifier = Modifier.weight(1f),
            )
            ConflictVersionCard(
                title = "Local version",
                version = conflict.localVersion,
                timestamp = conflict.localTimestamp,
                isSelected = conflict.choice == ConflictChoice.LOCAL,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.resolveConflict(ConflictChoice.SERVER) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Keep Server")
            }
            Button(
                onClick = { viewModel.resolveConflict(ConflictChoice.LOCAL) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Keep Local")
            }
        }

        if (conflict.choice != ConflictChoice.UNDECIDED) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onResolved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirm Resolution")
            }
        }
    }
}

@Composable
private fun ConflictVersionCard(
    title: String,
    version: String,
    timestamp: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("v$version", style = MaterialTheme.typography.bodySmall)
            Text(timestamp, style = MaterialTheme.typography.labelSmall)
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
