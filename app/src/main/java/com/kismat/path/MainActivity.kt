package com.kismat.path

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val BrandBlue = Color(0xFF1675E8)
private val Ink = Color(0xFF182533)
private val Muted = Color(0xFF667585)
private val Cloud = Color(0xFFF3F7FB)

class MainActivity : ComponentActivity() {
    private var sectionSelection: ((String) -> Unit)? = null

    override fun onResume() {
        super.onResume()
        MascotIconController.onAppOpened(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this@MainActivity
            var permissionGranted by remember { mutableStateOf(hasActivityPermission()) }
            var steps by remember { mutableLongStateOf(StepStore.steps(context)) }
            var headerMascotImage by remember { mutableIntStateOf(MascotIconController.currentIcon(context).image) }
            var sensorAvailable by remember { mutableStateOf(StepStore.sensorAvailable(context)) }
            var drivingFilterEnabled by remember { mutableStateOf(StepStore.drivingFilterEnabled(context)) }
            var vehicleDetected by remember { mutableStateOf(StepStore.vehicleDetected(context)) }
            var stepsPaused by remember { mutableStateOf(StepStore.isPaused(context)) }
            var manuallyPaused by remember { mutableStateOf(StepStore.isManuallyPaused(context)) }
            var refreshKey by remember { mutableIntStateOf(0) }
            var waterRefresh by remember { mutableIntStateOf(0) }
            var selectedSection by remember {
                mutableStateOf(intent.getStringExtra(EXTRA_OPEN_SECTION)?.takeIf { it in setOf("steps", "water", "profile") } ?: "steps")
            }
            SideEffect { sectionSelection = { section -> selectedSection = section } }
            var pendingReminderEnable by remember { mutableStateOf(false) }
            var pendingDailyReminderMinute by remember { mutableIntStateOf(-1) }
            var pendingDrivingFilterEnable by remember { mutableStateOf(false) }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                permissionGranted = granted
                if (granted) {
                    startTracking()
                    if (pendingDrivingFilterEnable) {
                        StepStore.setDrivingFilterEnabled(context, true)
                        refreshDrivingDetection()
                    }
                    refreshKey++
                }
                pendingDrivingFilterEnable = false
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (pendingReminderEnable && granted) {
                    WaterStore.setReminderEnabled(context, true)
                    WaterReminderScheduler.scheduleNext(context)
                }
                if (pendingDailyReminderMinute >= 0 && granted) {
                    WaterStore.addDailyReminder(context, pendingDailyReminderMinute)
                    WaterReminderScheduler.scheduleDailyReminder(context, pendingDailyReminderMinute)
                }
                pendingReminderEnable = false
                pendingDailyReminderMinute = -1
                waterRefresh++
            }

            LaunchedEffect(permissionGranted) {
                if (permissionGranted) startTracking()
            }

            LaunchedEffect(refreshKey) {
                while (true) {
                    steps = StepStore.steps(context)
                    headerMascotImage = MascotIconController.currentIcon(context).image
                    sensorAvailable = StepStore.sensorAvailable(context)
                    drivingFilterEnabled = StepStore.drivingFilterEnabled(context)
                    vehicleDetected = StepStore.vehicleDetected(context)
                    stepsPaused = StepStore.isPaused(context)
                    manuallyPaused = StepStore.isManuallyPaused(context)
                    delay(750)
                }
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BrandBlue,
                    onPrimary = Color.White,
                    background = Cloud,
                    surface = Color.White,
                    onSurface = Ink
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Cloud) {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding()
                    ) {
                        AppHeader(selectedSection, headerMascotImage)
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            when (selectedSection) {
                                "water" -> WaterScreen(
                                    refreshToken = waterRefresh,
                                    onAddDailyReminder = { minute ->
                                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                            pendingDailyReminderMinute = minute
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            WaterStore.addDailyReminder(context, minute)
                                            WaterReminderScheduler.scheduleDailyReminder(context, minute)
                                            waterRefresh++
                                        }
                                    },
                                    onRemoveDailyReminder = { minute ->
                                        WaterReminderScheduler.cancelDailyReminder(context, minute)
                                        WaterStore.removeDailyReminder(context, minute)
                                        waterRefresh++
                                    },
                                    onExactAlarmAccessChanged = { waterRefresh++ },
                                    onReminderToggle = { enabled ->
                                        if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                            pendingReminderEnable = true
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            WaterStore.setReminderEnabled(context, enabled)
                                            if (enabled) WaterReminderScheduler.scheduleNext(context) else WaterReminderScheduler.cancel(context)
                                            waterRefresh++
                                        }
                                    }
                                )
                                "profile" -> ProfileScreen()
                                else -> StepsScreen(
                                    steps = steps,
                                    sensorAvailable = sensorAvailable,
                                    permissionGranted = permissionGranted,
                                    drivingFilterEnabled = drivingFilterEnabled,
                                    vehicleDetected = vehicleDetected,
                                    stepsPaused = stepsPaused,
                                    manuallyPaused = manuallyPaused,
                                    onEnableSteps = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        else permissionGranted = true
                                    },
                                    onToggleDrivingFilter = { enabled ->
                                        if (!enabled) {
                                            StepStore.setDrivingFilterEnabled(context, false)
                                            refreshDrivingDetection()
                                        } else if (hasActivityPermission()) {
                                            StepStore.setDrivingFilterEnabled(context, true)
                                            refreshDrivingDetection()
                                        } else {
                                            pendingDrivingFilterEnable = true
                                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        }
                                    },
                                    onToggleManualPause = {
                                        StepStore.toggleManualPause(context)
                                        stepsPaused = StepStore.isPaused(context)
                                        manuallyPaused = StepStore.isManuallyPaused(context)
                                    },
                                    onResetSteps = {
                                        StepStore.reset(context)
                                        MascotIconController.onStepsReset(context)
                                        steps = 0L
                                    }
                                )
                            }
                        }

                        NavigationBar(containerColor = Color.White) {
                            listOf("steps", "water", "profile").forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedSection == tab,
                                    onClick = { selectedSection = tab },
                                    icon = { NavigationGlyph(tab, selectedSection == tab) },
                                    label = { Text(tab.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_SECTION)
            ?.takeIf { it in setOf("steps", "water", "profile") }
            ?.let { sectionSelection?.invoke(it) }
    }

    private fun hasActivityPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun startTracking() {
        if (!hasActivityPermission()) return
        ContextCompat.startForegroundService(this, Intent(this, StepCounterService::class.java))
    }

    private fun refreshDrivingDetection() {
        if (!hasActivityPermission()) return
        ContextCompat.startForegroundService(
            this,
            Intent(this, StepCounterService::class.java).setAction(StepCounterService.ACTION_REFRESH_DRIVING_DETECTION)
        )
    }

    companion object {
        const val EXTRA_OPEN_SECTION = "com.kismat.path.OPEN_SECTION"
    }
}

@androidx.compose.runtime.Composable
private fun AppHeader(section: String, mascotImage: Int) {
    val title = when (section) { "water" -> "Water"; "profile" -> "Profile"; else -> "Steps" }
    val subtitle = when (section) {
        "water" -> "Your daily hydration"
        "profile" -> "Your details, stored on this phone"
        else -> "Your movement, one step at a time"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).background(Color(0xFF071830), CircleShape), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(mascotImage),
                contentDescription = "Wello mascot",
                modifier = Modifier.fillMaxSize().padding(3.dp),
                contentScale = ContentScale.Fit
            )
        }
        Column {
            Text("SipStep", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(title, color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@androidx.compose.runtime.Composable
private fun NavigationGlyph(tab: String, selected: Boolean) {
    val tint = if (selected) BrandBlue else Muted
    Canvas(Modifier.size(24.dp)) {
        when (tab) {
            "water" -> {
                val drop = Path().apply {
                    moveTo(size.width * .5f, size.height * .04f)
                    cubicTo(size.width * .40f, size.height * .24f, size.width * .12f, size.height * .53f, size.width * .15f, size.height * .68f)
                    cubicTo(size.width * .15f, size.height * .88f, size.width * .31f, size.height * .98f, size.width * .5f, size.height * .98f)
                    cubicTo(size.width * .69f, size.height * .98f, size.width * .85f, size.height * .88f, size.width * .85f, size.height * .68f)
                    cubicTo(size.width * .88f, size.height * .53f, size.width * .60f, size.height * .24f, size.width * .5f, size.height * .04f)
                    close()
                }
                drawPath(drop, tint)
            }
            "profile" -> {
                drawCircle(tint, radius = size.minDimension * .19f, center = Offset(size.width * .5f, size.height * .29f))
                val shoulders = Path().apply {
                    moveTo(size.width * .12f, size.height * .94f)
                    cubicTo(size.width * .13f, size.height * .60f, size.width * .87f, size.height * .60f, size.width * .88f, size.height * .94f)
                }
                drawPath(shoulders, tint, style = Stroke(width = size.minDimension * .13f))
            }
            else -> {
                drawCircle(tint, radius = size.minDimension * .12f, center = Offset(size.width * .33f, size.height * .19f))
                drawCircle(tint, radius = size.minDimension * .12f, center = Offset(size.width * .68f, size.height * .31f))
                drawCircle(tint, radius = size.minDimension * .17f, center = Offset(size.width * .35f, size.height * .53f))
                drawCircle(tint, radius = size.minDimension * .17f, center = Offset(size.width * .70f, size.height * .73f))
            }
        }
    }
}
