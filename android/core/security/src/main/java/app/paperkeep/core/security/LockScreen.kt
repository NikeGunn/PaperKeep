package app.paperkeep.core.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

const val TAG_LOCK_SCREEN = "lock_screen"
const val TAG_LOCK_BUTTON = "lock_unlock_button"

/**
 * Full-screen lock gate shown when [LockController.isLocked] is true.
 *
 * Behaviour:
 *  - On first composition, fires the biometric prompt immediately (no tap required),
 *    matching the UX of banking apps (Google Pay, Samsung Pass, etc.).
 *  - The "Unlock" button acts as a re-trigger for when the system dismisses the
 *    prompt (e.g. user looked away, hardware timeout, or cancelled).
 *  - [onRequestBiometric] is provided by MainActivity which has the FragmentActivity
 *    context required by BiometricPrompt. It calls [onSuccess] when auth succeeds
 *    and [onFailure] with a message otherwise.
 *  - [onUnlocked] is called after a successful authentication result to update
 *    [LockController] state.
 */
@Composable
fun LockScreen(
    onRequestBiometric: (onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-trigger the biometric prompt the moment this screen appears.
    LaunchedEffect(Unit) {
        onRequestBiometric(onUnlocked) { /* dismissed/failed — user can retry via button */ }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag(TAG_LOCK_SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Paperkeep is locked",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Authenticate to access your documents",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Re-trigger button — shown after the auto-prompt fires in case the user
        // dismissed it, looked away, or the system timed out.
        Button(
            onClick = {
                onRequestBiometric(onUnlocked) { /* dismissed again — stays locked */ }
            },
            modifier = Modifier
                .testTag(TAG_LOCK_BUTTON)
                .semantics { contentDescription = "Unlock Paperkeep" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Unlock",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
