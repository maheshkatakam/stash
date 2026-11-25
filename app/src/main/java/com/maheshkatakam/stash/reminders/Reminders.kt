package com.maheshkatakam.stash.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.maheshkatakam.stash.MainActivity
import com.maheshkatakam.stash.R
import com.maheshkatakam.stash.StashApp
import com.maheshkatakam.stash.data.ItemFull
import com.maheshkatakam.stash.data.displayTitle
import kotlinx.coroutines.launch
import kotlin.math.abs

object ReminderScheduler {
    const val ACTION_FIRE = "com.maheshkatakam.stash.REMINDER_FIRE"
    const val ACTION_SNOOZE = "com.maheshkatakam.stash.REMINDER_SNOOZE"
    const val EXTRA_ID = "itemId"
    const val EXTRA_AT = "at"

    fun schedule(context: Context, itemId: Long, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = maxOf(at, System.currentTimeMillis() + 2_000)
        val pi = firePendingIntent(context, itemId, at)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun cancel(context: Context, itemId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(firePendingIntent(context, itemId, 0L))
    }

    private fun firePendingIntent(context: Context, itemId: Long, at: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("stash://reminder/$itemId"))
            .putExtra(EXTRA_ID, itemId)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders you set on notes, lists and saved items"
        }
        nm.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, full: ItemFull) {
        if (!canPost(context)) return
        val id = full.item.id
        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("stash://item/$id"))
                .putExtra(MainActivity.EXTRA_OPEN_ITEM, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_SNOOZE)
                .setData(Uri.parse("stash://snooze/$id"))
                .putExtra(ReminderScheduler.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = full.displayTitle.ifBlank {
            full.item.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "Reminder"
        }
        val text = when {
            full.entries.isNotEmpty() -> full.entries.filter { !it.checked && it.text.isNotBlank() }
                .sortedBy { it.position }
                .joinToString("\n") { "☐ " + it.text.trim() }
                .ifBlank { "All done ✓" }
            full.item.body.isNotBlank() && full.item.title.isNotBlank() -> full.item.body
            full.item.linkDescription != null -> full.item.linkDescription
            full.item.url != null -> full.item.url
            else -> "Tap to open"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_stash)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Snooze 1 hour", snooze)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.toInt(), notification)
        } catch (e: SecurityException) {
            // Notifications were turned off; nothing else to do.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id <= 0) return
        val app = context.applicationContext as StashApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_FIRE -> fire(context, app, id, intent.getLongExtra(ReminderScheduler.EXTRA_AT, 0L))
                    ReminderScheduler.ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(context).cancel(id.toInt())
                        app.repository.setReminder(id, System.currentTimeMillis() + 60 * 60 * 1000L)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, app: StashApp, id: Long, scheduledAt: Long) {
        val full = app.repository.getItem(id) ?: return
        val at = full.item.reminderAt ?: return
        if (full.item.trashedAt != null) return
        // An alarm left over from an older reminder time: ignore it.
        if (scheduledAt != 0L && abs(at - scheduledAt) > 60_000) return
        if (at > System.currentTimeMillis() + 60_000) {
            ReminderScheduler.schedule(context, id, at)
            return
        }
        Notifications.showReminder(context, full)
        app.repository.clearFiredReminder(id)
    }
}

/** Alarms are wiped when the phone restarts or the app updates, so they're set again here. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as StashApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.repository.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
