package app.paperkeep.feature.scanner.idcard

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Test tags
const val TAG_ID_CARD_SCREEN     = "id_card_screen"
const val TAG_ID_STEP_INDICATOR  = "id_step_indicator"
const val TAG_ID_FRONT_PREVIEW   = "id_front_preview"
const val TAG_ID_CONFIRM_BUTTON  = "id_confirm_button"
const val TAG_ID_RETAKE_BUTTON   = "id_retake_button"
const val TAG_ID_COMPOSITE       = "id_composite_preview"

/**
 * Root entry point for the ID card two-step flow.
 *
 * Shows a step indicator ("1 of 2" / "2 of 2") and hands control to the
 * appropriate sub-step composable based on [IdCardFlowState].
 *
 * [onScanRequested]  called when the user wants to (re)start a camera capture
 * [onDone]           called with the composite bitmap when both sides are done
 * [onCancel]         back / cancel navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardScreen(
    onScanRequested: () -> Unit,
    onDone: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    viewModel: IdCardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ID / Card scan") },
            )
        },
        modifier = Modifier.testTag(TAG_ID_CARD_SCREEN),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            StepIndicator(
                step = if (state is IdCardFlowState.AwaitingBack || state is IdCardFlowState.Done) 2 else 1,
                total = 2,
                modifier = Modifier.testTag(TAG_ID_STEP_INDICATOR),
            )

            Spacer(Modifier.height(24.dp))

            when (val s = state) {
                IdCardFlowState.AwaitingFront -> AwaitingFrontStep(
                    onScan = onScanRequested,
                    onCancel = onCancel,
                )

                is IdCardFlowState.AwaitingBack -> AwaitingBackStep(
                    frontPreview = s.frontPreview,
                    onScan = onScanRequested,
                    onRetakeFront = { viewModel.retakeFront(); onScanRequested() },
                )

                is IdCardFlowState.Done -> {
                    onDone(s.composite)
                }

                is IdCardFlowState.Error -> ErrorStep(
                    message = s.message,
                    onRetry = { viewModel.reset(); onScanRequested() },
                )
            }
        }
    }
}

// ── Sub-steps ─────────────────────────────────────────────────────────────────

@Composable
private fun AwaitingFrontStep(onScan: () -> Unit, onCancel: () -> Unit) {
    Icon(
        imageVector = Icons.Default.CreditCard,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Text("Scan the front side", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text("Position the card within the frame", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(32.dp))
    Button(onClick = onScan, modifier = Modifier.fillMaxWidth().testTag(TAG_ID_CONFIRM_BUTTON)) {
        Text("Scan front →")
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

@Composable
private fun AwaitingBackStep(
    frontPreview: Bitmap,
    onScan: () -> Unit,
    onRetakeFront: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Front captured", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(12.dp))
    Image(
        bitmap = frontPreview.asImageBitmap(),
        contentDescription = "Front side preview",
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .aspectRatio(85.6f / 54f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(TAG_ID_FRONT_PREVIEW),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onRetakeFront, modifier = Modifier.testTag(TAG_ID_RETAKE_BUTTON)) {
        Text("Retake front")
    }
    Spacer(Modifier.height(24.dp))
    Text("Now scan the back side", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text("Flip the card and position it in the frame", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onScan, modifier = Modifier.fillMaxWidth().testTag(TAG_ID_CONFIRM_BUTTON)) {
        Text("Scan back →")
    }
}

@Composable
private fun ErrorStep(message: String, onRetry: () -> Unit) {
    Text("Something went wrong", style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    Text(message, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onRetry) { Text("Try again") }
}

@Composable
private fun StepIndicator(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index + 1 == step
            val done   = index + 1 < step
            Box(
                modifier = Modifier
                    .size(if (active) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active || done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Step $step of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
