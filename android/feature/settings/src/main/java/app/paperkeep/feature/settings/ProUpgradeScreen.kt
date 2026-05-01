package app.paperkeep.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

internal const val TAG_PRO_UPGRADE_SCREEN = "pro_upgrade_screen"
internal const val TAG_PRO_UPGRADE_BUTTON = "pro_upgrade_button"
internal const val TAG_PRO_ALREADY_OWNED = "pro_already_owned"
internal const val TAG_PRO_RESTORE_BUTTON = "pro_restore_button"
internal const val TAG_PRO_UNAVAILABLE = "pro_unavailable"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpgradeScreen(
    onBack: () -> Unit,
    viewModel: ProUpgradeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? android.app.Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paperkeep Pro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .testTag(TAG_PRO_UPGRADE_SCREEN),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Go Pro — Once, Forever",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = uiState.formattedPrice,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProFeatureRow("No ads — ever")
                    ProFeatureRow("Unlimited AI summaries")
                    ProFeatureRow("Batch export any page count")
                    ProFeatureRow("Pro visual theme")
                    ProFeatureRow("Priority future features")
                }
            }

            Spacer(Modifier.height(24.dp))

            when {
                uiState.isPro -> {
                    Text(
                        text = "You already own Paperkeep Pro!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(TAG_PRO_ALREADY_OWNED),
                    )
                }
                !uiState.isIapAvailable -> {
                    Text(
                        text = "Pro is unavailable on this device.\nPurchases require an unmodified device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(TAG_PRO_UNAVAILABLE),
                    )
                }
                else -> {
                    Button(
                        onClick = { activity?.let { viewModel.launchPurchase(it) } },
                        enabled = uiState.canPurchase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TAG_PRO_UPGRADE_BUTTON),
                    ) {
                        Text(if (uiState.isPurchasePending) "Processing…" else "Upgrade to Pro")
                    }

                    Spacer(Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = { viewModel.restorePurchases() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TAG_PRO_RESTORE_BUTTON),
                    ) {
                        Text("Restore Purchase")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProFeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
