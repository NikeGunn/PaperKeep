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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.paperkeep.R
import app.paperkeep.MainActivity

/** 2×1 "Scan Now" home-screen widget — taps directly open the camera. */
class ScanNowWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ScanNowWidgetContent(context) }
    }
}

@Composable
private fun ScanNowWidgetContent(context: Context) {
    val launchIntent = Intent(context, MainActivity::class.java).apply {
        action = "app.paperkeep.action.OPEN_CAMERA"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFF59E0B)))
            .clickable(actionStartActivity(launchIntent))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = context.getString(R.string.widget_tap_to_scan),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

/** BroadcastReceiver that hosts ScanNowWidget. */
class ScanNowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScanNowWidget()
}
