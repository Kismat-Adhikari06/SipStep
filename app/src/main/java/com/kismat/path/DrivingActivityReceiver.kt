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
        StepStore.setDrivingRecognitionStatus(context, "ready")
        val activities = result.probableActivities
        val vehicleConfidence = activities
            .filter { it.type == DetectedActivity.IN_VEHICLE }
            .maxOfOrNull { it.confidence } ?: 0
        val walkingConfidence = activities
            .filter {
                it.type == DetectedActivity.WALKING ||
                    it.type == DetectedActivity.RUNNING ||
                    it.type == DetectedActivity.ON_FOOT
            }
            .maxOfOrNull { it.confidence } ?: 0

        // UNKNOWN, STILL, bicycle and other labels are not evidence that someone
        // has left a vehicle. Keep the current state until Android is confident
        // about either a vehicle trip or walking on foot.
        when {
            vehicleConfidence >= VEHICLE_CONFIDENCE_THRESHOLD &&
                vehicleConfidence >= walkingConfidence -> StepStore.setVehicleDetected(context, true)

            walkingConfidence >= WALKING_CONFIDENCE_THRESHOLD &&
                walkingConfidence > vehicleConfidence -> StepStore.setVehicleDetected(context, false)
        }
    }

    private companion object {
        const val VEHICLE_CONFIDENCE_THRESHOLD = 65
        const val WALKING_CONFIDENCE_THRESHOLD = 60
    }
}
