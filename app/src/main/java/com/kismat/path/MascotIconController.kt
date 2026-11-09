package com.kismat.path

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal enum class MascotAppIcon(val launcherAlias: String, val image: Int) {
    FRIENDLY("LauncherFriendly", R.drawable.mascot_app_icon_foreground),
    DRY("LauncherDry", R.drawable.mascot_dry),
    WAVING("LauncherWaving", R.drawable.mascot_wave),
    DRINKING("LauncherDrinking", R.drawable.mascot_drink),
    WALKING("LauncherWalking", R.drawable.mascot_walk),
    BOTTLE_CHEER("LauncherCheering", R.drawable.mascot_bottle_cheer),
    GOAL("LauncherGoal", R.drawable.mascot_star),
    OVERFULL("LauncherOverfull", R.drawable.mascot_overfull),
    BROKEN("LauncherBroken", R.drawable.mascot_broken)
}

internal object MascotIconController {
    private const val PREFS = "mascot_icon_state"
    private const val KEY_ICON = "icon"
    private const val KEY_SOURCE = "source"
    private const val KEY_LAST_ACTIVITY = "last_activity"
    private const val KEY_LAST_STEP = "last_step"
    private const val KEY_IDLE_NOTIFIED_FOR = "idle_notified_for"
    private const val KEY_LAST_MILESTONE = "last_step_milestone"
    private const val KEY_WATER_ICON = "water_icon"

    private const val IDLE_DELAY_MS = 24L * 60 * 60 * 1000
    private const val WALKING_DELAY_MS = 60L * 60 * 1000
    private const val ACTION_IDLE_CHECK = "com.kismat.path.MASCOT_IDLE_CHECK"
    private const val ACTION_WALKING_EXPIRED = "com.kismat.path.MASCOT_WALKING_EXPIRED"
    private const val ALARM_IDLE = 7301
    private const val ALARM_WALKING = 7302
    private const val IDLE_NOTIFICATION = 7303
    private const val STEP_NOTIFICATION_BASE = 7400
    private const val CHANNEL_MOMENTS = "mascot_moments_v1"

    private val allIcons = MascotAppIcon.entries

    fun onAppOpened(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_ACTIVITY, now)
            .putLong(KEY_IDLE_NOTIFIED_FOR, 0L)
            .putString(KEY_WATER_ICON, MascotAppIcon.FRIENDLY.name)
            .apply()
        setIcon(context, MascotAppIcon.FRIENDLY, "app")
        scheduleIdleCheck(context, now + IDLE_DELAY_MS)
    }

    fun onWaterLogged(context: Context) {
        recordActivity(context)
        val icon = waterIcon(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATER_ICON, icon.name).apply()
        setIcon(context, icon, "water")
    }

    fun onWaterReminder(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastActivity = p.getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity > 0L && System.currentTimeMillis() - lastActivity >= IDLE_DELAY_MS) {
            setIcon(context, MascotAppIcon.BROKEN, "idle")
            return
        }
        val icon = waterIcon(context)
        p.edit().putString(KEY_WATER_ICON, icon.name).apply()
        setIcon(context, icon, "water")
    }

    fun currentIcon(context: Context): MascotAppIcon = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_ICON, null)
        ?.let { name -> MascotAppIcon.entries.firstOrNull { it.name == name } }
        ?: MascotAppIcon.FRIENDLY

    fun onStepCountChanged(context: Context, previous: Long, current: Long) {
        if (current <= previous) return
        val now = System.currentTimeMillis()
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasWalking = p.getString(KEY_SOURCE, "") == "steps"
        p.edit().putLong(KEY_LAST_ACTIVITY, now).putLong(KEY_LAST_STEP, now)
            .putLong(KEY_IDLE_NOTIFIED_FOR, 0L).apply()
        setIcon(context, MascotAppIcon.WALKING, "steps")
        if (!wasWalking) scheduleWalkingExpiry(context, now + WALKING_DELAY_MS)

        val thresholds = longArrayOf(2_500L, 5_000L, 10_000L)
        val crossed = thresholds.lastOrNull { previous < it && current >= it }
        val lastMilestone = p.getLong(KEY_LAST_MILESTONE, 0L)
        if (crossed != null && crossed > lastMilestone) {
            p.edit().putLong(KEY_LAST_MILESTONE, crossed).apply()
            postStepMilestone(context, crossed)
        }
    }

    fun onStepsReset(context: Context) {
        recordActivity(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_MILESTONE, 0L).putLong(KEY_LAST_STEP, 0L).apply()
        setIcon(context, MascotAppIcon.FRIENDLY, "app")
    }

    fun onBoot(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastActivity = p.getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity == 0L) return
        val now = System.currentTimeMillis()
        scheduleIdleCheck(context, maxOf(now + 1_000L, lastActivity + IDLE_DELAY_MS))
        val lastStep = p.getLong(KEY_LAST_STEP, 0L)
        if (lastStep > 0L && now - lastStep < WALKING_DELAY_MS && p.getString(KEY_SOURCE, "") == "steps") {
            scheduleWalkingExpiry(context, lastStep + WALKING_DELAY_MS)
        }
    }

    internal fun onIdleCheck(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastActivity = p.getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity == 0L) return
        val dueAt = lastActivity + IDLE_DELAY_MS
        val now = System.currentTimeMillis()
        if (now < dueAt) {
            scheduleIdleCheck(context, dueAt)
            return
        }

        setIcon(context, MascotAppIcon.BROKEN, "idle")
        if (p.getLong(KEY_IDLE_NOTIFIED_FOR, 0L) != lastActivity) {
            p.edit().putLong(KEY_IDLE_NOTIFIED_FOR, lastActivity).apply()
            postIdleNudge(context)
        }
    }

    internal fun onWalkingExpired(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString(KEY_SOURCE, "") != "steps") return
        val lastStep = p.getLong(KEY_LAST_STEP, 0L)
        if (System.currentTimeMillis() - lastStep < WALKING_DELAY_MS) {
            scheduleWalkingExpiry(context, lastStep + WALKING_DELAY_MS)
            return
        }
        val icon = MascotAppIcon.entries.firstOrNull { it.name == p.getString(KEY_WATER_ICON, null) }
            ?: MascotAppIcon.FRIENDLY
        setIcon(context, icon, if (icon == MascotAppIcon.FRIENDLY) "app" else "water")
    }

    private fun recordActivity(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_ACTIVITY, System.currentTimeMillis())
            .putLong(KEY_IDLE_NOTIFIED_FOR, 0L).apply()
    }

    private fun waterIcon(context: Context): MascotAppIcon {
        val total = WaterStore.todayEntries(context).sumOf { it.amountMl }
        val progress = total.toFloat() / WaterStore.goal(context).coerceAtLeast(1)
        return when {
            progress > 1f -> MascotAppIcon.OVERFULL
            progress >= 1f -> MascotAppIcon.GOAL
            progress < .2f -> MascotAppIcon.DRY
            progress < .8f -> MascotAppIcon.DRINKING
            else -> MascotAppIcon.BOTTLE_CHEER
        }
    }

    private fun setIcon(context: Context, icon: MascotAppIcon, source: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = p.getString(KEY_ICON, null)
        val previousSource = p.getString(KEY_SOURCE, null)
        if (previous == icon.name && previousSource == source) return
        p.edit().putString(KEY_ICON, icon.name).putString(KEY_SOURCE, source).apply()
        if (previous == icon.name) return

        val packageManager = context.packageManager
        val target = ComponentName(context.packageName, "${context.packageName}.${icon.launcherAlias}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val changes = allIcons.map { candidate ->
                PackageManager.ComponentEnabledSetting(
                    ComponentName(context.packageName, "${context.packageName}.${candidate.launcherAlias}"),
                    if (candidate == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            packageManager.setComponentEnabledSettings(changes)
        } else {
            packageManager.setComponentEnabledSetting(
                target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            allIcons.filterNot { it == icon }.forEach { candidate ->
                packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, "${context.packageName}.${candidate.launcherAlias}"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private fun scheduleIdleCheck(context: Context, at: Long) = schedule(context, ACTION_IDLE_CHECK, ALARM_IDLE, at)
    private fun scheduleWalkingExpiry(context: Context, at: Long) = schedule(context, ACTION_WALKING_EXPIRED, ALARM_WALKING, at)

    private fun schedule(context: Context, action: String, requestCode: Int, at: Long) {
        val intent = Intent(context, MascotStateReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun postIdleNudge(context: Context) {
        if (!canNotify(context)) return
        createMomentChannel(context)
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.mascot_broken)
        val name = ProfileStore.displayName(context)
        val title = if (name.isBlank()) "Wello misses you" else "$name, Wello misses you"
        val notification = NotificationCompat.Builder(context, CHANNEL_MOMENTS)
            .setSmallIcon(R.drawable.ic_kismat_mark)
            .setLargeIcon(bitmap)
            .setContentTitle(title)
            .setContentText("Open SipStep when you’re ready; Wello will be here when you return.")
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, IDLE_NOTIFICATION))
            .build()
        NotificationManagerCompat.from(context).notify(IDLE_NOTIFICATION, notification)
    }

    private fun postStepMilestone(context: Context, milestone: Long) {
        if (!canNotify(context)) return
        createMomentChannel(context)
        val pose = if (milestone >= 10_000L) R.drawable.mascot_star else R.drawable.mascot_walk
        val bitmap = BitmapFactory.decodeResource(context.resources, pose)
        val name = ProfileStore.displayName(context)
        val title = if (name.isBlank()) "Nice work!" else "Nice work, $name!"
        val message = if (milestone >= 10_000L) "$milestone steps counted. You reached your step goal!"
        else "$milestone steps counted. Wello is cheering you on."
        val notification = NotificationCompat.Builder(context, CHANNEL_MOMENTS)
            .setSmallIcon(R.drawable.ic_kismat_mark)
            .setLargeIcon(bitmap)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, STEP_NOTIFICATION_BASE + milestone.toInt()))
            .build()
        NotificationManagerCompat.from(context).notify(STEP_NOTIFICATION_BASE + milestone.toInt(), notification)
    }

    private fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun createMomentChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_MOMENTS, "Mascot moments", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Gentle reminders and movement milestones from your mascot"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal const val IDLE_ACTION = ACTION_IDLE_CHECK
    internal const val WALKING_EXPIRED_ACTION = ACTION_WALKING_EXPIRED
}

class MascotStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            MascotIconController.IDLE_ACTION -> MascotIconController.onIdleCheck(context)
            MascotIconController.WALKING_EXPIRED_ACTION -> MascotIconController.onWalkingExpired(context)
        }
    }
}
