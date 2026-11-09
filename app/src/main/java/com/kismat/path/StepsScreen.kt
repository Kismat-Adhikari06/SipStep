package com.kismat.path

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat

private val StepBlue = Color(0xFF1675E8)
private val StepInk = Color(0xFF182533)
private val StepMuted = Color(0xFF667585)
private const val REFERENCE_GOAL = 10000L

@Composable
internal fun StepsScreen(
    steps: Long,
    sensorAvailable: Boolean,
    permissionGranted: Boolean,
    drivingFilterEnabled: Boolean,
    drivingRecognitionStatus: String,
    vehicleDetected: Boolean,
    stepsPaused: Boolean,
    manuallyPaused: Boolean,
    onEnableSteps: () -> Unit,
    onToggleDrivingFilter: (Boolean) -> Unit,
    onToggleManualPause: () -> Unit,
    onResetSteps: () -> Unit
) {
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showDrivingExplanation by remember { mutableStateOf(false) }
    val progress = (steps.toFloat() / REFERENCE_GOAL).coerceIn(0f, 1f)
    val formattedSteps = NumberFormat.getIntegerInstance().format(steps)
    val mascotPose = when {
        steps >= REFERENCE_GOAL -> MascotPose.STAR
        steps < 1_000L -> MascotPose.TIRED
        else -> MascotPose.WALKING
    }

    Column(Modifier.fillMaxSize()) {
      Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val mascotSize = if (maxWidth < 270.dp) 68.dp else 84.dp
                    val countSize = when {
                        maxWidth < 260.dp -> 44.sp
                        maxWidth < 320.dp -> 54.sp
                        else -> 62.sp
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("YOUR STEPS", color = StepBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(formattedSteps, color = StepInk, fontSize = countSize, lineHeight = countSize * 1.12f, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Text("steps counted", color = StepMuted, fontSize = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MascotSprite(
                            pose = mascotPose,
                            description = when (mascotPose) {
                                MascotPose.TIRED -> "Wello sits tired while you get started with your steps."
                                MascotPose.STAR -> "Wello proudly celebrates reaching the step goal."
                                else -> "Wello marches alongside your step progress."
                            },
                            modifier = Modifier.size(mascotSize)
                        )
                        Text("Wello", color = StepMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color(0xFFE6EFF8))) {
                    Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(CircleShape).background(StepBlue))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progress * 100).toInt()}%", color = StepBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("10,000 step reference", color = StepMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { if (!permissionGranted) onEnableSteps() else showResetConfirmation = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = if (permissionGranted) ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEAF2FA), contentColor = StepBlue
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Text(if (permissionGranted) "Reset steps" else "Enable step counting", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).background(if (permissionGranted && sensorAvailable) Color(0xFFE8F5EF) else Color(0xFFFFF4DF)).padding(horizontal = 11.dp, vertical = 8.dp)) {
                        Text(if (permissionGranted && sensorAvailable) "ON" else "OFF", color = if (permissionGranted && sensorAvailable) Color(0xFF287547) else Color(0xFF946000), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Step tracking", color = StepInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            when {
                                !permissionGranted -> "Allow activity access to start counting."
                                !sensorAvailable -> "This phone doesn’t have a step counter sensor."
                                else -> "Using your phone’s built-in step sensor."
                            },
                            color = StepMuted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic driving pause", color = StepInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            when {
                                stepsPaused && vehicleDetected -> "Paused while vehicle activity is detected"
                                stepsPaused && manuallyPaused -> "Paused by you"
                                drivingFilterEnabled && drivingRecognitionStatus == "unavailable" -> "Android activity recognition unavailable; use manual pause"
                                drivingFilterEnabled && drivingRecognitionStatus != "ready" -> "Waiting for Android activity recognition"
                                drivingFilterEnabled -> "Listening for confident vehicle and walking estimates"
                                else -> "Off · steps count as usual"
                            },
                            color = if (stepsPaused) Color(0xFF946000) else StepMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    Switch(
                        checked = drivingFilterEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showDrivingExplanation = true else onToggleDrivingFilter(false)
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    "Detection may be delayed; steps already counted stay saved.",
                    color = StepMuted, fontSize = 11.sp, lineHeight = 15.sp
                )
                Button(
                    onClick = onToggleManualPause,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = if (stepsPaused) ButtonDefaults.buttonColors(containerColor = Color(0xFFEAF2FA), contentColor = StepBlue) else ButtonDefaults.buttonColors(containerColor = StepBlue)
                ) {
                    Text(
                        when {
                            stepsPaused && vehicleDetected -> "Resume steps anyway"
                            stepsPaused -> "Resume steps"
                            else -> "Pause steps manually"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
      }
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset your steps?") },
            text = { Text("The displayed count will return to zero. Step tracking will continue.", textAlign = TextAlign.Start) },
            confirmButton = {
                TextButton(onClick = { onResetSteps(); showResetConfirmation = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showDrivingExplanation) {
        AlertDialog(
            onDismissRequest = { showDrivingExplanation = false },
            title = { Text("Pause steps during vehicle trips?") },
            text = {
                Text(
                    "SipStep uses Android’s activity estimate to pause step counting when it confidently detects a vehicle trip. It resumes when another activity is confidently detected. This can be delayed or wrong, so you can also pause or resume steps yourself. Activity estimates stay on this phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDrivingExplanation = false
                    onToggleDrivingFilter(true)
                }) { Text("Enable driving pause") }
            },
            dismissButton = {
                TextButton(onClick = { showDrivingExplanation = false }) { Text("Not now") }
            }
        )
    }
}
