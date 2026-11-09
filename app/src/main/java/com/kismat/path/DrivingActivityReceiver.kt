package com.kismat.path

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/** Receives low-power activity estimates and only changes the driving state on confident results. */
class DrivingActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!StepStore.drivingFilterEnabled(context)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val activity = result.mostProbableActivity ?: return

        when {
            activity.type == DetectedActivity.IN_VEHICLE && activity.confidence >= CONFIDENCE_THRESHOLD ->
                StepStore.setVehicleDetected(context, true)

            activity.type != DetectedActivity.IN_VEHICLE && activity.confidence >= CONFIDENCE_THRESHOLD ->
                StepStore.setVehicleDetected(context, false)
        }
    }

    private companion object {
        const val CONFIDENCE_THRESHOLD = 75
    }
}
