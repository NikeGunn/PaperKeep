package app.paperkeep.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    onNavigateBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AccountManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showChangePasswordSheet by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (uiState.event) {
            AccountManagementEvent.PASSWORD_CHANGED -> {
                snackbarHostState.showSnackbar("Password changed successfully")
                showChangePasswordSheet = false
                viewModel.clearEvent()
            }
            AccountManagementEvent.ACCOUNT_DELETED -> {
                viewModel.clearEvent()
                onAccountDeleted()
            }
            AccountManagementEvent.LOGGED_OUT -> {
                viewModel.clearEvent()
                onLoggedOut()
            }
            null -> Unit
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.semantics {
                    contentDescription = "Loading"
                })
            }

            // Change Password
            OutlinedButton(
                onClick = { showChangePasswordSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Change password" },
                enabled = !uiState.isLoading,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Change Password")
            }

            // Logout
            OutlinedButton(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Logout" },
                enabled = !uiState.isLoading,
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Logout")
            }

            Spacer(Modifier.height(32.dp))

            // Delete Account — destructive, red
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Delete account" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                enabled = !uiState.isLoading,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Account")
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account?") },
            text = {
                Text(
                    "This permanently deletes your account and all synced data. " +
                    "Local documents remain on this device. This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    },
                    modifier = Modifier.semantics { contentDescription = "Confirm delete account" },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.semantics { contentDescription = "Cancel delete account" },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Change password dialog
    if (showChangePasswordSheet) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordSheet = false },
            onConfirm = { current, new ->
                // In production: wrappedKey and kdfSalt come from local Keystore storage.
                // For now pass empty arrays — the repository layer reads them before calling.
                viewModel.changePassword(
                    currentPassword = current,
                    newPassword = new,
                    currentWrappedKey = ByteArray(0),
                    kdfSalt = ByteArray(0),
                )
            },
            isLoading = uiState.isLoading,
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (current: String, new: String) -> Unit,
    isLoading: Boolean,
) {
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    val mismatch = newPw.isNotEmpty() && confirmPw.isNotEmpty() && newPw != confirmPw

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentPw,
                    onValueChange = { currentPw = it },
                    label = { Text("Current password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Current password field" },
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "New password field" },
                )
                OutlinedTextField(
                    value = confirmPw,
                    onValueChange = { confirmPw = it },
                    label = { Text("Confirm new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) ({ Text("Passwords do not match") }) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Confirm password field" },
                )
            }
        },
        confirmButton = {
            Row {
                if (isLoading) CircularProgressIndicator()
                else TextButton(
                    onClick = { onConfirm(currentPw, newPw) },
                    enabled = currentPw.isNotEmpty() && newPw.isNotEmpty() && !mismatch,
                    modifier = Modifier.semantics { contentDescription = "Confirm password change" },
                ) { Text("Change") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "Cancel password change" },
            ) { Text("Cancel") }
        },
    )
}
