package com.kismat.path

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class WaterEntry(val id: Long, val amountMl: Int, val loggedAt: Long = id)

internal object WaterStore {
    private const val PREFS = "kismat_water"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_GOAL = "goal_ml"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_INTERVAL = "reminder_interval_minutes"
    private const val KEY_REMINDER_START = "reminder_start_minute"
    private const val KEY_REMINDER_END = "reminder_end_minute"
    private const val KEY_DAILY_REMINDER_MINUTES = "daily_reminder_minutes"
    private val mutationLock = Any()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun goal(context: Context): Int = synchronized(mutationLock) { prefs(context).getInt(KEY_GOAL, 2000) }
    fun configuredGoal(context: Context): Int? = synchronized(mutationLock) {
        val store = prefs(context)
        if (store.contains(KEY_GOAL)) store.getInt(KEY_GOAL, 2000).coerceIn(250, 10000) else null
    }

    fun setGoal(context: Context, value: Int) {
        synchronized(mutationLock) {
            val saved = prefs(context).edit().putInt(KEY_GOAL, value.coerceIn(250, 10000)).commit()
            check(saved) { "Could not save the daily water goal." }
        }
        WaterTrackingWidgetProvider.refreshAll(context)
    }
    fun reminderEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REMINDER_ENABLED, false)
    fun setReminderEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    fun reminderInterval(context: Context): Int = prefs(context).getInt(KEY_REMINDER_INTERVAL, 60)
    fun setReminderInterval(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_REMINDER_INTERVAL, minutes).apply()
    fun reminderStart(context: Context): Int = prefs(context).getInt(KEY_REMINDER_START, 8 * 60)
    fun setReminderStart(context: Context, minute: Int) = prefs(context).edit().putInt(KEY_REMINDER_START, minute).apply()
    fun reminderEnd(context: Context): Int = prefs(context).getInt(KEY_REMINDER_END, 21 * 60)
    fun setReminderEnd(context: Context, minute: Int) = prefs(context).edit().putInt(KEY_REMINDER_END, minute).apply()
    fun dailyReminderMinutes(context: Context): List<Int> = prefs(context).getString(KEY_DAILY_REMINDER_MINUTES, "")
        .orEmpty().split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..1439 }.distinct().sorted()

    fun addDailyReminder(context: Context, minute: Int) {
        if (minute !in 0..1439) return
        val times = dailyReminderMinutes(context).toMutableSet().apply { add(minute) }.sorted()
        prefs(context).edit().putString(KEY_DAILY_REMINDER_MINUTES, times.joinToString(",")).apply()
    }

    fun removeDailyReminder(context: Context, minute: Int) {
        val times = dailyReminderMinutes(context).filterNot { it == minute }
        prefs(context).edit().putString(KEY_DAILY_REMINDER_MINUTES, times.joinToString(",")).apply()
    }

    fun todayEntries(context: Context): List<WaterEntry> = synchronized(mutationLock) {
        todayEntriesLocked(context)
    }

    fun add(context: Context, amountMl: Int): WaterEntry {
        val entry = synchronized(mutationLock) { addLocked(context, amountMl) }
        WaterTrackingWidgetProvider.refreshAll(context)
        return entry
    }

    /** Adds one widget serving only if the goal has not already been reached. */
    fun addWidgetServing(context: Context): WaterEntry? {
        val entry = synchronized(mutationLock) {
            val store = prefs(context)
            val goal = if (store.contains(KEY_GOAL)) store.getInt(KEY_GOAL, 2000).coerceIn(250, 10000) else null
            if (goal != null && todayEntriesLocked(context).sumOf { it.amountMl } >= goal) null
            else addLocked(context, 250)
        }
        if (entry != null) WaterTrackingWidgetProvider.refreshAll(context)
        return entry
    }

    fun remove(context: Context, id: Long) {
        val changed = synchronized(mutationLock) {
            val json = readEntries(context)
            val kept = JSONArray()
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                if (item.optLong("id") != id) kept.put(item)
            }
            if (kept.length() == json.length()) false else {
                val saved = prefs(context).edit().putString(KEY_ENTRIES, kept.toString()).commit()
                check(saved) { "Could not remove the water entry." }
                true
            }
        }
        if (changed) WaterTrackingWidgetProvider.refreshAll(context)
    }

    fun resetToday(context: Context) {
        val changed = synchronized(mutationLock) {
            val today = localDayKey(System.currentTimeMillis())
            val json = readEntries(context)
            val kept = JSONArray()
            var removedAny = false
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val loggedAt = item.optLong("time", item.optLong("id"))
                if (localDayKey(loggedAt) == today) removedAny = true else kept.put(item)
            }
            if (!removedAny) false else {
                val saved = prefs(context).edit().putString(KEY_ENTRIES, kept.toString()).commit()
                check(saved) { "Could not reset today's water entries." }
                true
            }
        }
        if (changed) WaterTrackingWidgetProvider.refreshAll(context)
    }

    private fun readEntries(context: Context): JSONArray = try {
        JSONArray(prefs(context).getString(KEY_ENTRIES, "[]"))
    } catch (_: Exception) {
        JSONArray()
    }

    private fun todayEntriesLocked(context: Context): List<WaterEntry> {
        val today = localDayKey(System.currentTimeMillis())
        val json = readEntries(context)
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("id")
            val loggedAt = item.optLong("time", id)
            if (localDayKey(loggedAt) == today) {
                WaterEntry(id, item.optInt("ml").coerceAtLeast(0), loggedAt)
            } else null
        }.sortedWith(compareByDescending<WaterEntry> { it.loggedAt }.thenByDescending { it.id })
    }

    private fun addLocked(context: Context, amountMl: Int): WaterEntry {
        val json = readEntries(context)
        val now = System.currentTimeMillis()
        val existingIds = (0 until json.length()).mapNotNull { json.optJSONObject(it)?.optLong("id") }.toHashSet()
        var id = now
        while (id in existingIds) id++
        val amount = amountMl.coerceIn(1, 5000)
        json.put(JSONObject().put("id", id).put("time", now).put("ml", amount))
        val saved = prefs(context).edit().putString(KEY_ENTRIES, json.toString()).commit()
        check(saved) { "Could not save the water entry." }
        return WaterEntry(id, amount, now)
    }

    private fun localDayKey(timestamp: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date(timestamp))
}
