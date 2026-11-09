package com.kismat.path

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class StepCounterService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var counterSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startTrackingNotification()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        counterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        StepStore.setSensorAvailable(this, counterSensor != null)
        // Request prompt delivery with no extra batching. The hardware counter still
        // decides when a step has happened, but the UI can reflect it sooner.
        counterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, 0)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        val currentTotal = event.values[0].toLong()
        val lastTotal = StepStore.lastSensorTotal(this)

        when {
            lastTotal < 0L -> StepStore.saveSensorTotal(this, currentTotal)
            currentTotal >= lastTotal -> {
                val previousSteps = StepStore.steps(this)
                StepStore.addSteps(this, currentTotal - lastTotal)
                StepStore.saveSensorTotal(this, currentTotal)
                MascotIconController.onStepCountChanged(this, previousSteps, StepStore.steps(this))
            }
            else -> {
                // TYPE_STEP_COUNTER restarts from zero after reboot. Preserve the
                // steps accumulated since reboot instead of silently dropping them.
                val previousSteps = StepStore.steps(this)
                StepStore.addSteps(this, currentTotal)
                StepStore.saveSensorTotal(this, currentTotal)
                MascotIconController.onStepCountChanged(this, previousSteps, StepStore.steps(this))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    private fun startTrackingNotification() {
        val name = ProfileStore.displayName(this)
        val steps = StepStore.steps(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kismat_mark)
            .setContentTitle(if (name.isBlank()) "Wello is counting your steps" else "$name, Wello is counting your steps")
            .setContentText("$steps steps counted today")
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "kismat_step_tracking"
        private const val NOTIFICATION_ID = 21
    }
}
