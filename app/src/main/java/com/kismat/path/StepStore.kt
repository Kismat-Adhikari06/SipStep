package com.kismat.path

import android.content.Context

internal object StepStore {
    private const val PREFS = "kismat_steps"
    private const val KEY_STEPS = "steps"
    private const val KEY_LAST_SENSOR_TOTAL = "last_sensor_total"
    private const val KEY_SENSOR_AVAILABLE = "sensor_available"
    private const val KEY_DRIVING_FILTER_ENABLED = "driving_filter_enabled"
    private const val KEY_VEHICLE_DETECTED = "vehicle_detected"
    private const val KEY_MANUAL_OVERRIDE = "manual_pause_override"
    private const val KEY_DRIVING_RECOGNITION_AVAILABLE = "driving_recognition_available"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun steps(context: Context): Long = prefs(context).getLong(KEY_STEPS, 0L)

    fun lastSensorTotal(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SENSOR_TOTAL, -1L)

    fun sensorAvailable(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SENSOR_AVAILABLE, true)

    fun drivingFilterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DRIVING_FILTER_ENABLED, false)

    fun setDrivingFilterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DRIVING_FILTER_ENABLED, enabled).apply()
        if (!enabled) setVehicleDetected(context, false)
    }

    fun vehicleDetected(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VEHICLE_DETECTED, false)

    fun drivingRecognitionStatus(context: Context): String =
        prefs(context).getString(KEY_DRIVING_RECOGNITION_AVAILABLE, "unknown") ?: "unknown"

    fun setDrivingRecognitionStatus(context: Context, status: String) {
        prefs(context).edit().putString(KEY_DRIVING_RECOGNITION_AVAILABLE, status).apply()
    }

    fun setVehicleDetected(context: Context, detected: Boolean) {
        val editor = prefs(context).edit().putBoolean(KEY_VEHICLE_DETECTED, detected)
        if (!detected && manualOverride(context) == MANUAL_RESUME) {
            editor.putString(KEY_MANUAL_OVERRIDE, MANUAL_NONE)
        }
        editor.apply()
    }

    fun isManuallyPaused(context: Context): Boolean =
        manualOverride(context) == MANUAL_PAUSE

    fun isPaused(context: Context): Boolean = when (manualOverride(context)) {
        MANUAL_PAUSE -> true
        MANUAL_RESUME -> false
        else -> drivingFilterEnabled(context) && vehicleDetected(context)
    }

    fun toggleManualPause(context: Context) {
        val override = if (isPaused(context)) MANUAL_RESUME else MANUAL_PAUSE
        prefs(context).edit().putString(KEY_MANUAL_OVERRIDE, override).apply()
    }

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

    private fun manualOverride(context: Context): String =
        prefs(context).getString(KEY_MANUAL_OVERRIDE, MANUAL_NONE) ?: MANUAL_NONE

    private const val MANUAL_NONE = "none"
    private const val MANUAL_PAUSE = "pause"
    private const val MANUAL_RESUME = "resume"
}
