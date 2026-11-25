package com.maheshkatakam.stash.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.maheshkatakam.stash.CaptureActivity
import com.maheshkatakam.stash.MainActivity
import com.maheshkatakam.stash.R

/** Home-screen widget: open Stash, or jot a note, checklist or voice note in one tap. */
class QuickCaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(28.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_w_box),
                    contentDescription = null,
                    modifier = GlanceModifier.size(22.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    "Stash",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            WidgetButton(context, R.drawable.ic_w_note, "New note", CaptureActivity.ACTION_QUICK_NOTE)
            WidgetButton(context, R.drawable.ic_w_list, "New checklist", CaptureActivity.ACTION_QUICK_LIST)
            WidgetButton(context, R.drawable.ic_w_mic, "Voice note", CaptureActivity.ACTION_QUICK_VOICE)
        }
    }

    @Composable
    private fun WidgetButton(context: Context, icon: Int, label: String, action: String) {
        Spacer(GlanceModifier.width(8.dp))
        val intent = Intent(context, CaptureActivity::class.java).setAction(action)
        Box(
            modifier = GlanceModifier
                .size(44.dp)
                .cornerRadius(22.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
            )
        }
    }
}

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()
}

/** Quick Settings tile: swipe down, tap "Stash note", type. */
class QuickCaptureTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, CaptureActivity::class.java)
            .setAction(CaptureActivity.ACTION_QUICK_NOTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launch = {
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { launch() } else launch()
    }
}
