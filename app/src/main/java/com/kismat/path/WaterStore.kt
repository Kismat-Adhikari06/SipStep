package com.kismat.path

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class WaterEntry(val id: Long, val amountMl: Int)

internal object WaterStore {
    private const val PREFS = "kismat_water"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_GOAL = "goal_ml"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_INTERVAL = "reminder_interval_minutes"
    private const val KEY_REMINDER_START = "reminder_start_minute"
    private const val KEY_REMINDER_END = "reminder_end_minute"
    private const val KEY_DAILY_REMINDER_MINUTES = "daily_reminder_minutes"
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun goal(context: Context): Int = prefs(context).getInt(KEY_GOAL, 2000)
    fun setGoal(context: Context, value: Int) = prefs(context).edit().putInt(KEY_GOAL, value.coerceIn(250, 10000)).apply()
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

    fun todayEntries(context: Context): List<WaterEntry> {
        val today = dateFormat.format(Date())
        val json = try { JSONArray(prefs(context).getString(KEY_ENTRIES, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            if (dateFormat.format(Date(item.optLong("id"))) == today) {
                WaterEntry(item.optLong("id"), item.optInt("ml").coerceAtLeast(0))
            } else null
        }.sortedByDescending { it.id }
    }

    fun add(context: Context, amountMl: Int) {
        val json = try { JSONArray(prefs(context).getString(KEY_ENTRIES, "[]")) } catch (_: Exception) { JSONArray() }
        json.put(JSONObject().put("id", System.currentTimeMillis()).put("ml", amountMl.coerceIn(1, 5000)))
        prefs(context).edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    fun remove(context: Context, id: Long) {
        val json = try { JSONArray(prefs(context).getString(KEY_ENTRIES, "[]")) } catch (_: Exception) { JSONArray() }
        val kept = JSONArray()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            if (item.optLong("id") != id) kept.put(item)
        }
        prefs(context).edit().putString(KEY_ENTRIES, kept.toString()).apply()
    }

    fun resetToday(context: Context) {
        val todayIds = todayEntries(context).mapTo(hashSetOf()) { it.id }
        if (todayIds.isEmpty()) return
        val json = try { JSONArray(prefs(context).getString(KEY_ENTRIES, "[]")) } catch (_: Exception) { JSONArray() }
        val kept = JSONArray()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            if (item.optLong("id") !in todayIds) kept.put(item)
        }
        prefs(context).edit().putString(KEY_ENTRIES, kept.toString()).apply()
    }
}
