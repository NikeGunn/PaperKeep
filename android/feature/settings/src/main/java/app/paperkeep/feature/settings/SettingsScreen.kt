package app.paperkeep.feature.settings

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Test tags
const val TAG_SETTINGS_SCREEN = "settings_screen"
const val TAG_SETTINGS_SECTION_SECURITY = "settings_section_security"
const val TAG_SETTINGS_SECTION_SCANNING = "settings_section_scanning"
const val TAG_SETTINGS_SECTION_BACKUP = "settings_section_backup"
const val TAG_SETTINGS_SECTION_ABOUT = "settings_section_about"

/**
 * Settings screen for Paperkeep v2.
 *
 * Sections:
 *  - Security (biometric lock placeholder — Phase 2)
 *  - Scanning defaults (placeholder — Phase 2)
 *  - Backup & Restore (placeholder — wired in Phase 4)
 *  - About (version, licenses, privacy policy)
 *
 * No account, sync, or cloud language — those modules are deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appVersion: String = "",
    onNavigateBack: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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

            SettingsSectionHeader(
                icon = Icons.Filled.Lock,
                title = "Security",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_SECURITY),
            )
            SettingsPlaceholderRow("Biometric lock", "Available in Phase 2")
            SettingsPlaceholderRow("Screenshot protection", "Available in Phase 2")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(
                icon = Icons.Filled.CameraAlt,
                title = "Scanning",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_SCANNING),
            )
            SettingsPlaceholderRow("Default filter", "Available in Phase 2")
            SettingsPlaceholderRow("Language packs", "Available in Phase 2")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(
                icon = Icons.Filled.Storage,
                title = "Backup & Restore",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_BACKUP),
            )
            SettingsPlaceholderRow(
                label = "Local backup",
                description = "Encrypted backup to your chosen folder — coming in Phase 4",
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(
                icon = Icons.Filled.Info,
                title = "About",
                modifier = Modifier.testTag(TAG_SETTINGS_SECTION_ABOUT),
            )
            if (appVersion.isNotEmpty()) {
                SettingsInfoRow(label = "Version", value = appVersion)
            }
            SettingsPlaceholderRow("Privacy policy", "View privacy policy")
            SettingsPlaceholderRow("Open-source licenses", "View licenses")
        }
    }
}

@Composable
private fun OfflinePill(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "100% offline" },
    ) {
        Text(
            text = "100% offline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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
private fun SettingsPlaceholderRow(
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .semantics { contentDescription = label },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
