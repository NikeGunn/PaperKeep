package app.paperkeep.feature.settings.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.backup.RestoreStrategy
import app.paperkeep.core.backup.password.PasswordStrengthMeter
import app.paperkeep.core.backup.reminder.BackupReminderCadence
import app.paperkeep.core.backup.saf.SafIntents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG_BACKUP_SCREEN = "backup_screen"
const val TAG_BACKUP_CTA = "backup_cta"
const val TAG_RESTORE_CTA = "restore_cta"
const val TAG_BACKUP_PASSWORD_FIELD = "backup_password_field"
const val TAG_BACKUP_PASSWORD_METER = "backup_password_meter"
const val TAG_BACKUP_REMINDER_CADENCE = "backup_reminder_cadence"
const val TAG_BACKUP_HISTORY_LIST = "backup_history_list"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showCadenceDialog by remember { mutableStateOf(false) }

    // Pending password held in remember while SAF picker is up. Cleared after either
    // side of the round-trip completes.
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingStrategy by remember { mutableStateOf(RestoreStrategy.MERGE) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pw = pendingPassword
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && pw != null) {
            viewModel.onBackupTargetPicked(uri, pw)
        }
        pendingPassword = null
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pw = pendingPassword
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && pw != null) {
            viewModel.onRestoreSourcePicked(uri, pw, pendingStrategy)
        }
        pendingPassword = null
    }

    if (showCreateDialog) {
        CreateBackupDialog(
            onConfirm = { pw ->
                pendingPassword = pw
                showCreateDialog = false
                createLauncher.launch(SafIntents.create(SafIntents.defaultFilename()))
            },
            onDismiss = { showCreateDialog = false },
            onPasswordChange = { viewModel.estimatePassword(it) },
            strength = state.passwordStrength,
        )
    }

    if (showRestoreDialog) {
        RestoreBackupDialog(
            onConfirm = { pw, strategy ->
                pendingPassword = pw
                pendingStrategy = strategy
                showRestoreDialog = false
                openLauncher.launch(SafIntents.open())
            },
            onDismiss = { showRestoreDialog = false },
        )
    }

    if (showCadenceDialog) {
        CadenceDialog(
            current = state.cadence,
            onSelect = {
                viewModel.setReminderCadence(it)
                showCadenceDialog = false
            },
            onDismiss = { showCadenceDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier.testTag(TAG_BACKUP_SCREEN),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "Encrypted backup of your scans, settings, and OCR. Argon2id KDF + AES-256-GCM. " +
                    "The backup file is portable — pick any location: Drive, Dropbox, SD card, USB.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showCreateDialog = true },
                    enabled = state.mode == BackupUiState.Mode.IDLE || state.mode == BackupUiState.Mode.DONE_BACKUP || state.mode == BackupUiState.Mode.DONE_RESTORE || state.mode == BackupUiState.Mode.ERROR,
                    modifier = Modifier.testTag(TAG_BACKUP_CTA),
                ) { Text("Create backup") }
                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    enabled = state.mode == BackupUiState.Mode.IDLE || state.mode == BackupUiState.Mode.DONE_BACKUP || state.mode == BackupUiState.Mode.DONE_RESTORE || state.mode == BackupUiState.Mode.ERROR,
                    modifier = Modifier.testTag(TAG_RESTORE_CTA),
                ) { Text("Restore") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ReminderRow(
                cadence = state.cadence,
                onClick = { showCadenceDialog = true },
                modifier = Modifier.testTag(TAG_BACKUP_REMINDER_CADENCE),
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (state.mode) {
                BackupUiState.Mode.RUNNING_BACKUP, BackupUiState.Mode.RUNNING_RESTORE -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (state.mode == BackupUiState.Mode.RUNNING_BACKUP) "Encrypting backup…"
                        else "Decrypting and restoring…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                BackupUiState.Mode.DONE_BACKUP, BackupUiState.Mode.DONE_RESTORE -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(state.message ?: "Done", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.acknowledge() }) { Text("OK") }
                        }
                    }
                }
                BackupUiState.Mode.ERROR -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                state.errorMessage ?: "Failed",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.acknowledge() }) { Text("Dismiss") }
                        }
                    }
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("History", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (backups.isEmpty()) {
                Text(
                    "No backups yet. Tap Create backup above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().testTag(TAG_BACKUP_HISTORY_LIST)) {
                    items(backups, key = { it.id }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                Text(
                                    "${sdf.format(Date(entry.createdAt))} · ${entry.documentCount} docs · " +
                                        "${entry.pageCount} pages · ${entry.sizeBytes / 1024} KiB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "sha256: ${entry.sha256.take(16)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBackupDialog(
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    onPasswordChange: (CharArray) -> Unit,
    strength: PasswordStrengthMeter.Estimate?,
) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val matches = pw.isNotEmpty() && pw == confirm
    val canSubmit = matches && (strength?.meetsMinimumLength == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a backup password") },
        text = {
            Column {
                Text(
                    "This password protects your backup with Argon2id and AES-256-GCM. " +
                        "If you lose it, the backup cannot be recovered — store it somewhere safe.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = {
                        pw = it
                        onPasswordChange(it.toCharArray())
                    },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.testTag(TAG_BACKUP_PASSWORD_FIELD),
                )
                if (strength != null) {
                    PasswordMeter(
                        strength = strength,
                        modifier = Modifier.testTag(TAG_BACKUP_PASSWORD_METER),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirm.isNotEmpty() && !matches,
                )
                if (confirm.isNotEmpty() && !matches) {
                    Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pw.toCharArray()) },
                enabled = canSubmit,
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RestoreBackupDialog(
    onConfirm: (CharArray, RestoreStrategy) -> Unit,
    onDismiss: () -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(RestoreStrategy.MERGE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from backup") },
        text = {
            Column {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("Backup password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Conflict strategy", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = strategy == RestoreStrategy.MERGE,
                        onClick = { strategy = RestoreStrategy.MERGE },
                    )
                    Text("Merge — keep my current docs, add restored ones in a new folder")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = strategy == RestoreStrategy.REPLACE,
                        onClick = { strategy = RestoreStrategy.REPLACE },
                    )
                    Text("Replace — wipe my current docs first")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pw.toCharArray(), strategy) },
                enabled = pw.isNotEmpty(),
            ) { Text("Pick file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordMeter(
    strength: PasswordStrengthMeter.Estimate,
    modifier: Modifier = Modifier,
) {
    val color = when (strength.score) {
        PasswordStrengthMeter.Score.VERY_WEAK -> MaterialTheme.colorScheme.error
        PasswordStrengthMeter.Score.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrengthMeter.Score.FAIR -> MaterialTheme.colorScheme.tertiary
        PasswordStrengthMeter.Score.STRONG -> MaterialTheme.colorScheme.primary
        PasswordStrengthMeter.Score.VERY_STRONG -> MaterialTheme.colorScheme.primary
    }
    val pct = (strength.bitsOfEntropy / 100.0).coerceIn(0.0, 1.0).toFloat()
    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
        Text(
            "${strength.score.label} · ${"%.0f".format(strength.bitsOfEntropy)} bits",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        if (strength.warnings.isNotEmpty()) {
            for (w in strength.warnings) {
                Text("• $w", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReminderRow(
    cadence: BackupReminderCadence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Backup reminder", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (cadence) {
                        BackupReminderCadence.NEVER -> "Off"
                        BackupReminderCadence.WEEKLY -> "Every week"
                        BackupReminderCadence.MONTHLY -> "Every month"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text("Change") }
        }
    }
}

@Composable
private fun CadenceDialog(
    current: BackupReminderCadence,
    onSelect: (BackupReminderCadence) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup reminder") },
        text = {
            Column {
                for (c in BackupReminderCadence.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = current == c, onClick = { onSelect(c) })
                        Text(
                            when (c) {
                                BackupReminderCadence.NEVER -> "Never"
                                BackupReminderCadence.WEEKLY -> "Every week"
                                BackupReminderCadence.MONTHLY -> "Every month"
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
