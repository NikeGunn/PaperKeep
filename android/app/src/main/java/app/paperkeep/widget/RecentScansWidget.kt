package app.paperkeep.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.paperkeep.MainActivity
import app.paperkeep.R

/**
 * 4×1 "Recent Scans" home-screen widget.
 *
 * Shows up to 3 recent document titles. Tapping a title deep-links to the
 * library screen. The "+" button launches the camera directly.
 *
 * Note: Glance widgets cannot inject Hilt ViewModels directly. Data refresh
 * is triggered by [GlanceAppWidget.update] called from the repository observer
 * in MainActivity or via WorkManager (deferred for P5). For now the widget
 * shows the app name and "Open library" — the full data-binding variant
 * requires a GlanceStateDefinition + WorkManager update (P5).
 */
class RecentScansWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { RecentScansWidgetContent(context) }
    }
}

@Composable
private fun RecentScansWidgetContent(context: Context) {
    val libraryIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val cameraIntent = Intent(context, MainActivity::class.java).apply {
        action = "app.paperkeep.action.OPEN_CAMERA"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF17120C)))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: label + hint
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(libraryIntent))
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_recent_scans_title),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFFFB951)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = context.getString(R.string.widget_open_library),
                style = TextStyle(
                    color = ColorProvider(Color(0xFFEBE0D4)),
                    fontSize = 11.sp,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.width(4.dp))

        // Right: scan button
        Box(
            modifier = GlanceModifier
                .background(ColorProvider(Color(0xFFF59E0B)))
                .clickable(actionStartActivity(cameraIntent))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+ ${context.getString(R.string.action_scan)}",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

/** BroadcastReceiver that hosts RecentScansWidget. */
class RecentScansWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentScansWidget()
}
