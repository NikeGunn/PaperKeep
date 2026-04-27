package app.paperkeep.feature.settings.storage

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paperkeep.core.backup.storage.StorageReport

const val TAG_STORAGE_SCREEN = "storage_screen"
const val TAG_STORAGE_PIE = "storage_pie"
const val TAG_STORAGE_CLEAR_CACHE = "storage_clear_cache"
const val TAG_STORAGE_CLEAR_CRASH = "storage_clear_crash"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StorageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier.testTag(TAG_STORAGE_SCREEN),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                "Per-bucket on-device storage. Tap an action to free space — your scans are never deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            StoragePieChart(report = state.report, modifier = Modifier.testTag(TAG_STORAGE_PIE))

            Spacer(modifier = Modifier.height(16.dp))

            BucketRow("Scans (encrypted)", state.report.scansBytes, scansColor)
            BucketRow("OCR text (encrypted)", state.report.ocrBytes, ocrColor)
            BucketRow("Share/export cache", state.report.exportsBytes + state.report.sharesBytes, exportsColor)
            BucketRow("Crash logs (encrypted)", state.report.crashBytes, crashColor)
            BucketRow("Other", state.report.otherBytes, otherColor)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Actions", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = viewModel::clearTransientCaches,
                    modifier = Modifier.testTag(TAG_STORAGE_CLEAR_CACHE),
                ) { Text("Clear share cache") }
                OutlinedButton(
                    onClick = viewModel::clearCrashLogs,
                    modifier = Modifier.testTag(TAG_STORAGE_CLEAR_CRASH),
                ) { Text("Clear crash logs") }
            }

            if (state.message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(state.message!!)
                    }
                }
            }
        }
    }
}

private val scansColor = Color(0xFF7A4F00)
private val ocrColor = Color(0xFFB78940)
private val exportsColor = Color(0xFFE0B873)
private val crashColor = Color(0xFFD9534F)
private val otherColor = Color(0xFFB0B0B0)

@Composable
private fun StoragePieChart(
    report: StorageReport,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val total = report.totalBytes.coerceAtLeast(1L)
        val arcs = listOf(
            report.scansBytes to scansColor,
            report.ocrBytes to ocrColor,
            (report.exportsBytes + report.sharesBytes) to exportsColor,
            report.crashBytes to crashColor,
            report.otherBytes to otherColor,
        )

        Canvas(
            modifier = Modifier
                .size(200.dp)
                .padding(8.dp),
        ) {
            var start = -90f
            for ((bytes, color) in arcs) {
                if (bytes <= 0) continue
                val sweep = 360f * bytes / total
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    style = Stroke(width = size.minDimension * 0.20f),
                )
                start += sweep
            }
        }
        Text(
            "${formatBytes(report.totalBytes)}",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun BucketRow(
    label: String,
    bytes: Long,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = color,
            shape = CircleShape,
            modifier = Modifier.size(12.dp),
        ) {}
        Spacer(modifier = Modifier.size(8.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KiB"
    b < 1024L * 1024 * 1024 -> "%.1f MiB".format(b / 1024.0 / 1024.0)
    else -> "%.2f GiB".format(b / 1024.0 / 1024.0 / 1024.0)
}
