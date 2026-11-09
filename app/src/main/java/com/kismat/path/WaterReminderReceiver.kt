package com.kismat.path

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

internal object WaterReminderScheduler {
    private const val ACTION_INTERVAL = "com.kismat.path.WATER_INTERVAL_REMINDER"
    private const val ACTION_DAILY = "com.kismat.path.WATER_DAILY_REMINDER"
    private const val EXTRA_DAILY_MINUTE = "daily_minute"
    private const val REQUEST_CODE = 4102

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WaterReminderReceiver::class.java).setAction(ACTION_INTERVAL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun dailyPendingIntent(context: Context, minute: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        DAILY_REQUEST_BASE + minute,
        Intent(context, WaterReminderReceiver::class.java)
            .setAction(ACTION_DAILY)
            .putExtra(EXTRA_DAILY_MINUTE, minute),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleNext(context: Context) {
        cancel(context)
        if (!WaterStore.reminderEnabled(context)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleAlarm(alarmManager, nextReminderTime(context), pendingIntent(context))
    }

    fun nextReminderTime(context: Context): Long {
        val next = Calendar.getInstance()
        val startMinute = WaterStore.reminderStart(context)
        val endMinute = WaterStore.reminderEnd(context)
        val interval = WaterStore.reminderInterval(context).coerceIn(15, 1440)
        next.add(Calendar.MINUTE, interval)
        if (next.get(Calendar.HOUR_OF_DAY) * 60 + next.get(Calendar.MINUTE) >= endMinute) {
            next.add(Calendar.DAY_OF_YEAR, 1)
            next.set(Calendar.HOUR_OF_DAY, startMinute / 60)
            next.set(Calendar.MINUTE, startMinute % 60)
            next.set(Calendar.SECOND, 0)
        } else {
            val minute = next.get(Calendar.HOUR_OF_DAY) * 60 + next.get(Calendar.MINUTE)
            if (minute < startMinute) {
                next.set(Calendar.HOUR_OF_DAY, startMinute / 60)
                next.set(Calendar.MINUTE, startMinute % 60)
                next.set(Calendar.SECOND, 0)
            }
        }
        return next.timeInMillis
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    fun scheduleDailyReminders(context: Context) {
        WaterStore.dailyReminderMinutes(context).forEach { scheduleDailyReminder(context, it) }
    }

    fun scheduleDailyReminder(context: Context, minute: Int) {
        if (minute !in 0..1439) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleAlarm(alarmManager, next.timeInMillis, dailyPendingIntent(context, minute))
    }

    fun cancelDailyReminder(context: Context, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(dailyPendingIntent(context, minute))
    }

    fun canSchedulePrecisely(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, whenMillis: Long, operation: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, operation)
        } else {
            // Keep reminders working when the user hasn't granted exact alarm access.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, operation)
        }
    }

    private const val DAILY_REQUEST_BASE = 5000
    internal const val EXTRA_DAILY_MINUTE_KEY = EXTRA_DAILY_MINUTE
    internal const val ACTION_DAILY_KEY = ACTION_DAILY
    internal const val ACTION_INTERVAL_KEY = ACTION_INTERVAL
}

class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val isDaily = intent?.action == WaterReminderScheduler.ACTION_DAILY_KEY
        if (isDaily) {
            val minute = intent?.getIntExtra(WaterReminderScheduler.EXTRA_DAILY_MINUTE_KEY, -1) ?: -1
            if (minute !in 0..1439 || minute !in WaterStore.dailyReminderMinutes(context)) return
            postReminder(context, formatClock(minute))
            WaterReminderScheduler.scheduleDailyReminder(context, minute)
            return
        }
        if (!WaterStore.reminderEnabled(context)) return

        postReminder(context, null)
        WaterReminderScheduler.scheduleNext(context)
    }

    private fun postReminder(context: Context, timeLabel: String?) {
        createNotificationChannel(context)
        val canNotify = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canNotify || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val totalMl = WaterStore.todayEntries(context).sumOf { it.amountMl }
        val firstConcern = MascotIconController.onWaterReminder(context)
        val mascotResource = if (firstConcern) R.drawable.mascot_dry else MascotIconController.currentIcon(context).image
        val mascot = BitmapFactory.decodeResource(context.resources, mascotResource)
        val name = ProfileStore.displayName(context)
        val goalMl = WaterStore.goal(context).coerceAtLeast(1)
        val isDry = totalMl.toFloat() / goalMl < .2f
        val title = when {
            firstConcern && name.isNotBlank() -> "Hey, $name, have you had any water yet?"
            firstConcern -> "Hey, have you had any water yet?"
            isDry && name.isNotBlank() -> "$name, Wello is feeling dry"
            isDry -> "Wello is feeling dry"
            name.isBlank() && timeLabel != null -> "Water reminder · $timeLabel"
            name.isBlank() -> "Time for a sip of water"
            timeLabel != null -> "$name, your water reminder · $timeLabel"
            else -> "$name, time for a sip of water"
        }
        val content = when {
            firstConcern -> "Nothing logged today yet. A small drink when you can is a good start."
            isDry -> "$totalMl of $goalMl ml logged today. Time for a sip, if you need one."
            totalMl > goalMl -> "You’re above your $goalMl ml daily goal ($totalMl ml today)."
            totalMl == goalMl -> "You’ve reached your $goalMl ml daily goal today."
            totalMl == 0 -> "No water logged yet today. Start with a glass when you can."
            else -> "$totalMl of $goalMl ml today. A little water break can help you stay on track."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kismat_mark)
            .setLargeIcon(mascot)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(mascot))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 4103, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Water reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gentle reminders to drink water"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    companion object {
        // Android locks channel importance once a channel is created.
        private const val CHANNEL_ID = "kismat_water_reminders_v2"
        private const val NOTIFICATION_ID = 4104
    }
}

class WaterBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            (intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED && WaterReminderScheduler.canSchedulePrecisely(context))
        ) {
            WaterReminderScheduler.scheduleNext(context)
            WaterReminderScheduler.scheduleDailyReminders(context)
            MascotIconController.onBoot(context)
        }
    }
}

private fun formatClock(minute: Int): String {
    val hour = minute / 60
    val marker = if (hour < 12) "am" else "pm"
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute % 60, marker)
}
