package com.kismat.path

import android.content.Context

internal object StepStore {
    private const val PREFS = "kismat_steps"
    private const val KEY_STEPS = "steps"
    private const val KEY_LAST_SENSOR_TOTAL = "last_sensor_total"
    private const val KEY_SENSOR_AVAILABLE = "sensor_available"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun steps(context: Context): Long = prefs(context).getLong(KEY_STEPS, 0L)

    fun lastSensorTotal(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SENSOR_TOTAL, -1L)

    fun sensorAvailable(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SENSOR_AVAILABLE, true)

    fun setSensorAvailable(context: Context, available: Boolean) {
        prefs(context).edit().putBoolean(KEY_SENSOR_AVAILABLE, available).apply()
    }

    fun saveSensorTotal(context: Context, total: Long) {
        prefs(context).edit().putLong(KEY_LAST_SENSOR_TOTAL, total).apply()
    }

    fun addSteps(context: Context, delta: Long) {
        if (delta <= 0L) return
        prefs(context).edit().putLong(KEY_STEPS, steps(context) + delta).apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().putLong(KEY_STEPS, 0L).apply()
    }
}
