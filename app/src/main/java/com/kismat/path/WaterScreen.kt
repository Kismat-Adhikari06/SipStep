package com.kismat.path

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WaterBlue = Color(0xFF1675E8)
private val WaterInk = Color(0xFF182533)
private val WaterMuted = Color(0xFF667585)
private val WaterCanvas = Color(0xFFF3F7FB)
private val AboveGoal = Color(0xFF9B5A00)

@Composable
internal fun WaterScreen(
    refreshToken: Int,
    onReminderToggle: (Boolean) -> Unit,
    onAddDailyReminder: (Int) -> Unit,
    onRemoveDailyReminder: (Int) -> Unit,
    onExactAlarmAccessChanged: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries = remember { mutableStateListOf<WaterEntry>() }
    val dailyReminderMinutes = remember { mutableStateListOf<Int>() }
    var goal by remember { mutableIntStateOf(WaterStore.goal(context)) }
    var remindersEnabled by remember { mutableStateOf(WaterStore.reminderEnabled(context)) }
    var reminderInterval by remember { mutableIntStateOf(WaterStore.reminderInterval(context)) }
    var reminderStart by remember { mutableIntStateOf(WaterStore.reminderStart(context)) }
    var reminderEnd by remember { mutableIntStateOf(WaterStore.reminderEnd(context)) }
    var showCustomAmount by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var customAmount by remember { mutableStateOf("") }
    var goalInput by remember { mutableStateOf(goal.toString()) }
    var intervalInput by remember { mutableStateOf(reminderInterval.toString()) }
    var exactAlarmAllowed by remember { mutableStateOf(WaterReminderScheduler.canSchedulePrecisely(context)) }
    val exactAlarmSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exactAlarmAllowed = WaterReminderScheduler.canSchedulePrecisely(context)
        if (exactAlarmAllowed) {
            WaterReminderScheduler.scheduleNext(context)
            WaterReminderScheduler.scheduleDailyReminders(context)
        }
        onExactAlarmAccessChanged()
    }

    LaunchedEffect(refreshToken) {
        entries.clear()
        entries.addAll(WaterStore.todayEntries(context))
        dailyReminderMinutes.clear()
        dailyReminderMinutes.addAll(WaterStore.dailyReminderMinutes(context))
        goal = WaterStore.goal(context)
        remindersEnabled = WaterStore.reminderEnabled(context)
        exactAlarmAllowed = WaterReminderScheduler.canSchedulePrecisely(context)
        reminderInterval = WaterStore.reminderInterval(context)
        reminderStart = WaterStore.reminderStart(context)
        reminderEnd = WaterStore.reminderEnd(context)
    }

    val totalMl = entries.sumOf { it.amountMl }
    val rawProgress = totalMl.toFloat() / goal.coerceAtLeast(1)
    val isAboveGoal = rawProgress > 1f
    val progress = rawProgress.coerceIn(0f, 1f)
    val dateLabel = remember(refreshToken) { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()) }
    val avatarPose = when {
        rawProgress > 1f -> MascotPose.OVERFULL
        rawProgress >= 1f -> MascotPose.STAR
        rawProgress < .2f -> MascotPose.DEHYDRATED
        rawProgress < .45f -> MascotPose.DRINKING
        rawProgress < .8f -> MascotPose.REFRESHED
        else -> MascotPose.BOTTLE_CHEER
    }
    val nextReminder = when {
        remindersEnabled -> "Every $reminderInterval min"
        dailyReminderMinutes.isNotEmpty() -> dailyReminderMinutes.joinToString(", ") { formatMinute(it) }
        else -> "Add reminder"
    }
    val currentMinute = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    Box(
        Modifier.fillMaxSize().background(WaterCanvas)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFFEFD)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dateLabel, color = WaterInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("DAILY GOAL", color = WaterMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        val ringSize = minOf(160.dp, maxWidth * .66f)
                        val mascotSize = minOf(88.dp, maxWidth * .28f)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WaterProgress(progress, isAboveGoal, Modifier.size(ringSize))
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MascotSprite(
                                    pose = avatarPose,
                                    description = when (avatarPose) {
                                        MascotPose.DEHYDRATED -> "Wello looks dry; hydration progress is just starting."
                                        MascotPose.OVERFULL -> "Wello is splashing above the chosen daily water goal."
                                        MascotPose.STAR -> "Wello celebrates reaching the water goal."
                                        MascotPose.DRINKING -> "Wello drinks water and looks refreshed."
                                        MascotPose.REFRESHED -> "Wello feels refreshed after a drink and gives you a thumbs-up."
                                        MascotPose.CELEBRATE -> "Wello celebrates progress toward the water goal."
                                        MascotPose.BOTTLE_CHEER -> "Wello cheers while holding up a water bottle, nearly at the daily goal."
                                        else -> "Wello greets you as you make progress on your water goal."
                                    },
                                    modifier = Modifier.size(mascotSize)
                                )
                                Text("Wello", color = WaterMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$totalMl", color = WaterBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(" / $goal ml", color = WaterInk, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = { goalInput = goal.toString(); showGoalDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) { Text("Daily goal  ·  Edit", color = WaterMuted, fontSize = 13.sp) }
                    if (isAboveGoal) {
                        Text("You’re above your daily goal.", color = AboveGoal, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        listOf(100, 150, 200, 250, 300).forEach { amount ->
                            QuickWaterButton(amount, isAboveGoal, Modifier.weight(1f)) {
                                entries.add(0, WaterStore.add(context, amount))
                                MascotIconController.onWaterLogged(context)
                            }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedButton(
                                onClick = { customAmount = ""; showCustomAmount = true },
                                modifier = Modifier.size(44.dp), shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("+", fontSize = 24.sp, color = WaterBlue, lineHeight = 24.sp) }
                            Text("Add new", fontSize = 10.sp, color = WaterMuted, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }

                    Surface(color = Color.White, shape = RoundedCornerShape(17.dp), shadowElevation = 2.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Row(
                                Modifier.fillMaxWidth().height(38.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Today’s records", color = WaterInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${entries.size} ${if (entries.size == 1) "cup" else "cups"}", color = WaterBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    if (entries.isNotEmpty()) {
                                        TextButton(
                                            onClick = { showResetConfirmation = true },
                                            contentPadding = PaddingValues(horizontal = 5.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) { Text("Reset", color = WaterMuted, fontSize = 11.sp) }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().height(42.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("◷", color = WaterBlue, fontSize = 20.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                                Text("Reminders", color = WaterMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(nextReminder, color = if (remindersEnabled) WaterInk else WaterBlue, fontSize = 13.sp)
                                TextButton(
                                    onClick = { intervalInput = reminderInterval.toString(); showReminderDialog = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                    modifier = Modifier.width(52.dp)
                                ) { Text(if (remindersEnabled) "Edit" else "Set", color = WaterBlue, fontSize = 12.sp) }
                            }
                            dailyReminderMinutes.forEach { minute ->
                                Row(
                                    Modifier.fillMaxWidth().height(34.dp).padding(start = 32.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${if (minute <= currentMinute) "Tomorrow" else "Daily"} at ${formatMinute(minute)}",
                                        color = WaterBlue,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            onRemoveDailyReminder(minute)
                                            dailyReminderMinutes.remove(minute)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) { Text("Remove", color = WaterMuted, fontSize = 11.sp) }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFEAF0F5))
                            if (entries.isEmpty()) {
                                Text("Add your first drink to start today’s log.", color = WaterMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                            } else {
                                entries.take(4).forEachIndexed { index, entry ->
                                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("●", color = Color(0xFF9BC8EB), fontSize = 11.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                                        Text("${entry.amountMl} ml", color = WaterInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(entry.loggedAt)), color = WaterMuted, fontSize = 12.sp)
                                        TextButton(
                                            onClick = {
                                                WaterStore.remove(context, entry.id)
                                                entries.remove(entry)
                                                MascotIconController.onWaterLogged(context)
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.width(60.dp)
                                        ) { Text("Remove", color = WaterMuted, fontSize = 11.sp) }
                                    }
                                    if (index < entries.size - 1 && index < 3) HorizontalDivider(color = Color(0xFFEAF0F5))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showCustomAmount) {
        AlertDialog(
            onDismissRequest = { showCustomAmount = false },
            title = { Text("Add a drink") },
            text = { OutlinedTextField(customAmount, { customAmount = it.filter(Char::isDigit).take(4) }, label = { Text("Amount in ml") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val ml = customAmount.toIntOrNull()
                    if (ml != null && ml > 0) {
                        entries.add(0, WaterStore.add(context, ml))
                        MascotIconController.onWaterLogged(context)
                        showCustomAmount = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showCustomAmount = false }) { Text("Cancel") } }
        )
    }

    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Daily water goal") },
            text = { OutlinedTextField(goalInput, { goalInput = it.filter(Char::isDigit).take(5) }, label = { Text("Goal in ml") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val value = goalInput.toIntOrNull()
                    if (value != null && value >= 250) {
                        WaterStore.setGoal(context, value)
                        goal = value.coerceAtMost(10000)
                        showGoalDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") } }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset today’s water?") },
            text = { Text("This clears today’s drinks and progress. Your goal and reminders will stay the same.") },
            confirmButton = {
                TextButton(onClick = {
                    WaterStore.resetToday(context)
                    entries.clear()
                    MascotIconController.onWaterLogged(context)
                    showResetConfirmation = false
                }) { Text("Reset today") }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("Water reminders") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Turn on reminders", color = WaterInk, fontWeight = FontWeight.Medium)
                            Text("A vibrating notification at each reminder", color = WaterMuted, fontSize = 12.sp)
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = {
                            remindersEnabled = it
                            onReminderToggle(it)
                        })
                    }
                    OutlinedTextField(
                        value = intervalInput,
                        onValueChange = { intervalInput = it.filter(Char::isDigit).take(4) },
                        label = { Text("Repeat every (minutes)") },
                        supportingText = { Text("Choose 15 to 1,440 minutes") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Active hours for repeating timer", color = WaterMuted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                reminderStart = hour * 60 + minute
                                WaterStore.setReminderStart(context, reminderStart)
                                if (remindersEnabled) WaterReminderScheduler.scheduleNext(context)
                            }, reminderStart / 60, reminderStart % 60, false).show()
                        }, modifier = Modifier.weight(1f)) { Text("From ${formatMinute(reminderStart)}") }
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                reminderEnd = hour * 60 + minute
                                WaterStore.setReminderEnd(context, reminderEnd)
                                if (remindersEnabled) WaterReminderScheduler.scheduleNext(context)
                            }, reminderEnd / 60, reminderEnd % 60, false).show()
                        }, modifier = Modifier.weight(1f)) { Text("To ${formatMinute(reminderEnd)}") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Specific daily times", color = WaterInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val now = Calendar.getInstance()
                            TimePickerDialog(context, { _, hour, minute -> onAddDailyReminder(hour * 60 + minute) }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
                        }) { Text("+ Add time") }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (exactAlarmAllowed) {
                            Text("On-time alarm access is enabled.", color = Color(0xFF287547), fontSize = 12.sp)
                        } else {
                            Text("Android may delay these reminders. Allow alarm access for on-time alerts.", color = WaterMuted, fontSize = 12.sp, lineHeight = 17.sp)
                            OutlinedButton(
                                onClick = {
                                    exactAlarmSettings.launch(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Allow alarm access") }
                        }
                    }
                    if (dailyReminderMinutes.isEmpty()) {
                        Text("Add reminders for exact times, like 8:30 am or 2:00 pm.", color = WaterMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    } else {
                        dailyReminderMinutes.forEach { minute ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(formatMinute(minute), color = WaterInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onRemoveDailyReminder(minute) }) { Text("Remove", color = WaterMuted) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val interval = intervalInput.toIntOrNull()
                    if (interval != null && interval in 15..1440) {
                        reminderInterval = interval
                        WaterStore.setReminderInterval(context, interval)
                        if (remindersEnabled) WaterReminderScheduler.scheduleNext(context)
                        showReminderDialog = false
                    }
                }) { Text("Save timer") }
            },
            dismissButton = { TextButton(onClick = { showReminderDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuickWaterButton(amount: Int, isAboveGoal: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAboveGoal) Color(0xFFFFF1DF) else if (amount == 100) WaterBlue else Color(0xFFEAF5FC),
                contentColor = if (isAboveGoal) AboveGoal else if (amount == 100) Color.White else WaterBlue
            ),
            contentPadding = PaddingValues(0.dp)
        ) { Text("$amount", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Text("${amount}ml", fontSize = 10.sp, color = WaterMuted, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun WaterProgress(progress: Float, isAboveGoal: Boolean, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(if (isAboveGoal) Color(0xFFE9C898) else Color(0xFFB9D7F2), radius = radius, center = center, style = Stroke(width = 5.dp.toPx()))
            val clipCircle = Path().apply { addOval(Rect(center.x - radius + 5.dp.toPx(), center.y - radius + 5.dp.toPx(), center.x + radius - 5.dp.toPx(), center.y + radius - 5.dp.toPx())) }
            clipPath(clipCircle) {
                val waterTop = size.height * (1f - progress * .82f)
                val wave = Path().apply {
                    moveTo(0f, waterTop)
                    cubicTo(size.width * .22f, waterTop - 10.dp.toPx(), size.width * .36f, waterTop + 10.dp.toPx(), size.width * .53f, waterTop + 4.dp.toPx())
                    cubicTo(size.width * .70f, waterTop - 5.dp.toPx(), size.width * .82f, waterTop + 9.dp.toPx(), size.width, waterTop)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(Color(0xFF3198FF), Color(0xFF1767DA)), startY = waterTop, endY = size.height))
            }
        }
        Text(if (isAboveGoal) "100%+" else "${(progress * 100).toInt()}%", color = WaterInk, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatMinute(minute: Int): String {
    val hour = (minute / 60) % 24
    val marker = if (hour < 12) "am" else "pm"
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute % 60, marker)
}
