package com.kismat.path

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.concurrent.Executors

/** Home-screen water widget backed by the same local WaterStore as the app. */
class WaterTrackingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateOne(context, manager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                refreshAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        internal const val ACTION_ADD_250 = "com.kismat.path.widget.ADD_250_ML"
        private const val SECTION_WATER = "water"
        private const val ADD_REQUEST_BASE = 8100
        private const val OPEN_REQUEST_BASE = 8200
        private const val COMPACT_WIDTH_THRESHOLD_DP = 230
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, WaterTrackingWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isEmpty()) return
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val layout = if (minWidthDp < COMPACT_WIDTH_THRESHOLD_DP) {
                R.layout.widget_water_tracking_compact
            } else {
                R.layout.widget_water_tracking
            }
            val views = RemoteViews(context.packageName, layout)
            val total = WaterStore.todayEntries(context).sumOf { it.amountMl.toLong() }
            val goal = WaterStore.configuredGoal(context)
            val mascotPose = waterMascotPose(total, WaterStore.goal(context))
            val numberFormat = NumberFormat.getIntegerInstance()

            val totalLabel = "${numberFormat.format(total)} ml"
            views.setTextViewText(R.id.widget_total, totalLabel)
            val totalTextSize = when {
                totalLabel.length <= 7 -> 21f
                totalLabel.length <= 9 -> 18f
                totalLabel.length <= 11 -> 16f
                else -> 14f
            }
            views.setTextViewTextSize(R.id.widget_total, TypedValue.COMPLEX_UNIT_SP, totalTextSize)
            views.setContentDescription(R.id.widget_total, "$total milliliters logged today")

            if (goal == null) {
                views.setTextViewText(R.id.widget_goal, "no goal")
                views.setTextViewText(R.id.widget_remaining, "Set a goal in SipStep")
                views.setViewVisibility(R.id.widget_progress, View.GONE)
                views.setViewVisibility(R.id.widget_add_button, View.VISIBLE)
                views.setContentDescription(R.id.widget_root, "Water tracking. $total milliliters logged today. Daily goal not set.")
            } else {
                val remaining = (goal - total).coerceAtLeast(0)
                val progress = ((total.toDouble() / goal.toDouble()) * 100).toInt().coerceIn(0, 100)
                val reachedGoal = total >= goal
                views.setTextViewText(R.id.widget_goal, "of ${numberFormat.format(goal)} ml")
                views.setTextViewText(
                    R.id.widget_remaining,
                    if (reachedGoal) "Goal met - nice work!" else "${numberFormat.format(remaining)} ml to go"
                )
                views.setProgressBar(R.id.widget_progress, 100, progress, false)
                views.setContentDescription(
                    R.id.widget_progress,
                    "${progress}% of daily water goal, $total of $goal milliliters"
                )
                views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
                // Avoid offering more water after the daily goal is reached.
                views.setViewVisibility(R.id.widget_add_button, if (reachedGoal) View.GONE else View.VISIBLE)
                views.setContentDescription(
                    R.id.widget_root,
                    if (reachedGoal) "Water tracking. $total milliliters logged. Daily goal met."
                    else "Water tracking. $total of $goal milliliters logged, $remaining milliliters remaining."
                )
            }

            views.setImageViewResource(R.id.widget_mascot, mascotPose.image)
            views.setContentDescription(R.id.widget_mascot, when (mascotPose) {
                MascotPose.DEHYDRATED -> "Wello looks dry; water progress is just starting."
                MascotPose.DRINKING -> "Wello drinks water and looks refreshed."
                MascotPose.REFRESHED -> "Wello feels refreshed as water progress grows."
                MascotPose.BOTTLE_CHEER -> "Wello cheers with a water bottle near the daily goal."
                MascotPose.STAR -> "Wello celebrates reaching the daily water goal."
                MascotPose.OVERFULL -> "Wello shows water progress above the daily goal."
                else -> "Wello waves beside your water progress."
            })
            views.setContentDescription(R.id.widget_add_button, "Add 250 milliliters of water")
            views.setOnClickPendingIntent(R.id.widget_root, openWaterPendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_add_button, addWaterPendingIntent(context, appWidgetId))
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun addWaterPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WaterWidgetActionReceiver::class.java)
                .setAction(ACTION_ADD_250)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            return PendingIntent.getBroadcast(
                context,
                ADD_REQUEST_BASE + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openWaterPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SECTION, SECTION_WATER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                OPEN_REQUEST_BASE + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/** Non-exported action receiver, invoked only through the widget's immutable PendingIntent. */
class WaterWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WaterTrackingWidgetProvider.ACTION_ADD_250) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        worker.execute {
            try {
                val entry = WaterStore.addWidgetServing(appContext)
                if (entry != null) MascotIconController.onWaterLogged(appContext)
                else WaterTrackingWidgetProvider.refreshAll(appContext)
            } catch (error: Exception) {
                Log.e(TAG, "Could not add widget water entry", error)
                WaterTrackingWidgetProvider.refreshAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SipStepWaterWidget"
        val worker = Executors.newSingleThreadExecutor()
    }
}
