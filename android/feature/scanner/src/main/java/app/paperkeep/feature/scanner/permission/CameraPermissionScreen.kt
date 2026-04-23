package app.paperkeep.feature.scanner.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Test tags exposed as constants so tests reference them without magic strings.
const val TAG_RATIONALE_SCREEN = "camera_rationale_screen"
const val TAG_GRANT_BUTTON = "camera_grant_button"
const val TAG_OPEN_SETTINGS_BUTTON = "camera_open_settings_button"
const val TAG_CAMERA_READY = "camera_ready_screen"

/**
 * Root composable that owns the camera permission lifecycle.
 *
 * Shows:
 *  - Rationale screen when [CameraPermissionState.ShowRationale]
 *  - "Open Settings" screen when [CameraPermissionState.PermanentlyDenied]
 *  - [content] when [CameraPermissionState.Granted]
 *
 * [onBack] is called when the user presses the back-equivalent in the rationale screen.
 */
@Composable
fun CameraPermissionGate(
    onBack: () -> Unit = {},
    viewModel: CameraPermissionViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val canShowRationale = !granted && CameraPermissionViewModel.isGranted(context)
        viewModel.onPermissionResult(
            granted = granted,
            canShowRationale = !granted && granted // false when denied permanently
        )
    }

    LaunchedEffect(Unit) {
        viewModel.checkInitialPermission(context)
    }

    when (state) {
        CameraPermissionState.Granted -> content()

        CameraPermissionState.NotRequested,
        CameraPermissionState.ShowRationale -> {
            CameraRationaleScreen(
                onGrant = { launcher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )
        }

        CameraPermissionState.PermanentlyDenied -> {
            CameraPermanentDenialScreen(
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                },
                onBack = onBack,
            )
        }
    }
}

/**
 * Rationale screen — shown when permission has not been granted yet or was
 * denied once (can still ask again).
 */
@Composable
fun CameraRationaleScreen(
    onGrant: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag(TAG_RATIONALE_SCREEN),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera access needed",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Paperkeep uses your camera to scan documents. " +
                "No images are uploaded unless you choose to sync.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGrant,
            modifier = Modifier
                .testTag(TAG_GRANT_BUTTON)
                .semantics { contentDescription = "Grant camera permission" },
        ) {
            Text("Allow camera access")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "Go back" },
        ) {
            Text("Not now")
        }
    }
}

/**
 * Permanent denial screen — shown when the user has selected "Don't ask again".
 * The only action is to open system Settings.
 */
@Composable
fun CameraPermanentDenialScreen(
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera permission required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You've permanently denied camera access. " +
                "Open Settings to enable it for Paperkeep.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .testTag(TAG_OPEN_SETTINGS_BUTTON)
                .semantics { contentDescription = "Open app settings" },
        ) {
            Text("Open Settings")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "Go back" },
        ) {
            Text("Cancel")
        }
    }
}
