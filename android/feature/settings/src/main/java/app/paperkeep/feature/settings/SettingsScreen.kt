package app.paperkeep.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.common.crash.CrashLogStore
import app.paperkeep.core.security.LockTimeout
import app.paperkeep.core.ui.theme.AppTheme

// ── Test tags ─────────────────────────────────────────────────────────────────
const val TAG_SETTINGS_SCREEN = "settings_screen"
const val TAG_SETTINGS_SECTION_SECURITY = "settings_section_security"
const val TAG_SETTINGS_SECTION_SCANNING = "settings_section_scanning"
const val TAG_SETTINGS_SECTION_BACKUP = "settings_section_backup"
const val TAG_SETTINGS_SECTION_ABOUT = "settings_section_about"
const val TAG_BIOMETRIC_TOGGLE = "settings_biometric_toggle"
const val TAG_SCREENSHOT_TOGGLE = "settings_screenshot_toggle"
const val TAG_LOCK_TIMEOUT = "settings_lock_timeout"
const val TAG_THEME_PICKER = "settings_theme_picker"
const val TAG_OLED_TOGGLE = "settings_oled_toggle"
const val TAG_SETTINGS_SECTION_PRO = "settings_section_pro"
const val TAG_SETTINGS_PRO_UPGRADE_ROW = "settings_pro_upgrade_row"

/**
 * Full Settings screen for Paperkeep v2.
 *
 * Sections (P2.13):
 *  - Security: biometric lock toggle, timeout picker, screenshot protection
 *  - Scanning: default filter, language packs
 *  - Backup & Restore: placeholder (Phase 4)
 *  - About: version, OSS licenses, privacy policy (native Compose, no WebView)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appVersion: String = "",
    onNavigateBack: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenProUpgrade: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimeoutPicker by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showCrashLogs by remember { mutableStateOf(false) }

    if (showTimeoutPicker) {
        LockTimeoutDialog(
            currentTimeout = uiState.lockTimeout,
            onSelect = { timeout ->
                viewModel.setLockTimeout(timeout)
                showTimeoutPicker = false
            },
            onDismiss = { showTimeoutPicker = false },
        )
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentTheme = uiState.appTheme,
            onSelect = { theme ->
                viewModel.setAppTheme(theme)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false },
        )
    }

    if (showCrashLogs) {
        CrashLogsDialog(
            context = context,
            onDismiss = { showCrashLogs = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        modifier = modifier.testTag(TAG_SETTINGS_SCREEN),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            OfflinePill()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Security section ─────────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.Lock,
                title = "Security",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_SECURITY),
            )

            SwitchSettingsRow(
                label = "Biometric lock",
                description = if (uiState.isBiometricAvailable)
                    "Lock app when minimised"
                else
                    "No biometric hardware or lock screen configured",
                checked = uiState.biometricLockEnabled,
                enabled = uiState.isBiometricAvailable,
                onCheckedChange = viewModel::setBiometricLock,
                testTag = TAG_BIOMETRIC_TOGGLE,
            )

            if (uiState.biometricLockEnabled) {
                ClickableSettingsRow(
                    label = "Auto-lock timeout",
                    value = uiState.lockTimeout.label,
                    onClick = { showTimeoutPicker = true },
                    testTag = TAG_LOCK_TIMEOUT,
                )
            }

            SwitchSettingsRow(
                label = "Screenshot protection",
                description = "Prevent screenshots in recents and screen share",
                checked = uiState.screenshotProtectionEnabled,
                enabled = true,
                onCheckedChange = viewModel::setScreenshotProtection,
                testTag = TAG_SCREENSHOT_TOGGLE,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Appearance section ────────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.Language,
                title = "Appearance",
            )
            ClickableSettingsRow(
                label = "Theme",
                value = uiState.appTheme.label,
                description = "Light, dark, or follow system setting",
                onClick = { showThemePicker = true },
                testTag = TAG_THEME_PICKER,
            )
            if (uiState.appTheme == AppTheme.DARK ||
                uiState.appTheme == AppTheme.SYSTEM) {
                SwitchSettingsRow(
                    label = "OLED true black",
                    description = "Pure black background for AMOLED screens (saves battery)",
                    checked = uiState.oledTrueBlack,
                    enabled = true,
                    onCheckedChange = viewModel::setOledTrueBlack,
                    testTag = TAG_OLED_TOGGLE,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Scanning section ─────────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.CameraAlt,
                title = "Scanning",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_SCANNING),
            )
            InfoSettingsRow(
                label = "Default filter",
                description = "Advanced filter options available in Phase 2",
            )
            ClickableSettingsRow(
                label = "Language packs",
                value = "Latin (bundled)",
                description = "Download packs for Devanagari, Chinese, Arabic…",
                onClick = {},
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Pro upgrade section ──────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.CheckCircle,
                title = "Paperkeep Pro",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_PRO),
            )
            ClickableSettingsRow(
                label = "Upgrade to Pro",
                value = "$4.99",
                description = "No ads · Unlimited summaries · Batch export · Pro theme",
                onClick = onOpenProUpgrade,
                testTag = TAG_SETTINGS_PRO_UPGRADE_ROW,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Backup section ───────────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.Storage,
                title = "Backup & Restore",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_BACKUP),
            )
            ClickableSettingsRow(
                label = "Local backup",
                value = "Open",
                description = "Encrypted backup to Drive / Dropbox / SD card / USB — your pick.",
                onClick = onOpenBackup,
            )
            ClickableSettingsRow(
                label = "Storage",
                value = "Manage",
                description = "See per-bucket usage and free up cache.",
                onClick = onOpenStorage,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── About section ────────────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.Info,
                title = "About",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_ABOUT),
            )

            if (appVersion.isNotEmpty()) {
                InfoSettingsRow(label = "Version", description = appVersion)
            }

            ClickableSettingsRow(
                label = "Privacy policy",
                value = "View",
                onClick = { showPrivacyPolicy = true },
            )
            InfoSettingsRow(
                label = "Open-source licenses",
                description = "Kotlin, Jetpack Compose, Room, ML Kit, and more",
            )
            InfoSettingsRow(
                label = "Data collection",
                description = "None. 100% on-device. No account required.",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Crash logs section ────────────────────────────────────────────
            SettingsSectionHeader(
                icon = Icons.Filled.Info,
                title = "Diagnostics",
            )
            ClickableSettingsRow(
                label = "Crash logs",
                value = "View",
                description = "Encrypted on-device crash reports (never auto-sent)",
                onClick = { showCrashLogs = true },
                testTag = "settings_crash_logs",
            )
        }
    }
}

// ── Composable helpers ────────────────────────────────────────────────────────

@Composable
private fun OfflinePill(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "100% offline" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "100% offline — no data leaves your device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SwitchSettingsRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = "",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "$label: ${if (checked) "on" else "off"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier,
        )
    }
}

@Composable
private fun ClickableSettingsRow(
    label: String,
    value: String = "",
    description: String = "",
    onClick: () -> Unit,
    testTag: String = "",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "$label: $value" }
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoSettingsRow(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "$label: $description" },
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun LockTimeoutDialog(
    currentTimeout: LockTimeout,
    onSelect: (LockTimeout) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-lock timeout") },
        text = {
            Column {
                LockTimeout.entries.forEach { timeout ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(timeout) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTimeout == timeout,
                            onClick = { onSelect(timeout) },
                        )
                        Text(
                            text = timeout.label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CrashLogsDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val logs = remember { CrashLogStore.listFiles(context) }
    var cleared by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crash Logs") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (cleared || logs.isEmpty()) {
                    Text(
                        text = "No crash logs stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "${logs.size} encrypted log(s) stored on device. Tap 'Clear' to delete them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    logs.take(5).forEach { file ->
                        Text(
                            text = "• ${file.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nothing is auto-uploaded. Use 'Clear' to delete all logs from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (!cleared && logs.isNotEmpty()) {
                TextButton(onClick = {
                    CrashLogStore.clear(context)
                    cleared = true
                }) { Text("Clear all") }
            }
        },
    )
}

@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(theme) }
                            .padding(vertical = 4.dp)
                            .semantics { contentDescription = theme.label },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onSelect(theme) },
                        )
                        Text(
                            text = theme.label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Policy") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Paperkeep collects no personal data.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "All documents are stored exclusively on your device, encrypted with AES-256-GCM using a key that never leaves Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "No account is required. No telemetry is collected. No analytics SDKs are included. The only third-party network calls are to Google AdMob (ads) and Play Billing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Full policy: docs/PRIVACY_POLICY.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
