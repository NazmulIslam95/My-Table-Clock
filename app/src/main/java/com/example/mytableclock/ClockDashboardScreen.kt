package com.example.mytableclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mytableclock.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI

// Default digital font family
val digitalFontFamily = FontFamily.Default

enum class ClockFace {
    DIGITAL,
    ANALOG,
    SPLIT,
    MINIMALIST
}

// Font options
data class FontOption(val name: String, val fontFamily: FontFamily)

@Composable
fun ClockDashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // SharedPreferences
    val sharedPrefs = remember {
        context.getSharedPreferences("clock_configuration", Context.MODE_PRIVATE)
    }

    // Persistent settings
    var backgroundColorInt by rememberSaveable { mutableStateOf(sharedPrefs.getInt("bg_color_key", 0xFF0A0A0F.toInt())) }
    var textColorInt by rememberSaveable { mutableStateOf(sharedPrefs.getInt("text_color_key", 0xFF00FFCC.toInt())) }
    var is24HourFormat by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("format_24h_key", true)) }
    var showSeconds by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("show_seconds_key", true)) }
    var clockFace by rememberSaveable { mutableStateOf(ClockFace.valueOf(sharedPrefs.getString("clock_face_key", ClockFace.DIGITAL.name) ?: ClockFace.DIGITAL.name)) }
    var autoBrightness by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("auto_brightness_key", false)) }
    var screenBrightness by rememberSaveable { mutableStateOf(sharedPrefs.getFloat("screen_brightness_key", 0.5f)) }
    var burnInProtection by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("burn_in_protection_key", true)) }
    var dimMode by rememberSaveable { mutableStateOf(false) }
    var selectedFontFamily by rememberSaveable { mutableStateOf<FontFamily?>(null) }

    // UI states
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Live data
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var batteryPercentage by remember { mutableStateOf(100) }
    var isBatteryCharging by remember { mutableStateOf(false) }

    // Alarm state
    var savedAlarmTime by rememberSaveable { mutableStateOf<Long?>(null) }
    var alarmHour by rememberSaveable { mutableStateOf(8) }
    var alarmMinute by rememberSaveable { mutableStateOf(0) }
    var alarmTitle by rememberSaveable { mutableStateOf("Wake up!") }

    // Battery receiver with proper cleanup
    DisposableEffect(context) {
        val batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    batteryPercentage = ((level / scale.toFloat()) * 100).toInt()

                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)

        onDispose {
            context.unregisterReceiver(batteryReceiver)
        }
    }

    // Time ticker with alignment
    LaunchedEffect(showSeconds) {
        while (true) {
            currentTime = LocalDateTime.now()
            val delayMs = if (showSeconds) 1000 else 60000
            delay(delayMs.toLong())
        }
    }

    // Burn-in protection with pixel shifting
    LaunchedEffect(burnInProtection) {
        if (burnInProtection) {
            while (true) {
                delay(60000) // Shift every minute
                offsetX = (Random.nextInt(-8, 8)).toFloat()
                offsetY = (Random.nextInt(-8, 8)).toFloat()
            }
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    // Auto-brightness control
    LaunchedEffect(autoBrightness, screenBrightness) {
        if (!autoBrightness) {
            try {
                val brightness = (screenBrightness * 255).toInt()
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val window = (context as? android.app.Activity)?.window
                    val lp = window?.attributes
                    lp?.screenBrightness = screenBrightness
                    window?.attributes = lp
                }
            } catch (e: Exception) {
                // Permission might not be granted
            }
        }
    }

    // Double-tap to dim
    val hapticFeedback = remember { view }
    val isDimmed = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (showColorPicker)
                    Brush.radialGradient(
                        colors = listOf(Color(textColorInt), Color(backgroundColorInt)),
                        center = Offset(200f, 200f),
                        radius = 500f
                    )
                else Brush.verticalGradient(
                    colors = listOf(Color(backgroundColorInt), Color(backgroundColorInt).copy(alpha = 0.95f))
                )
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        dimMode = !dimMode
                    },
                    onTap = {
                        if (dimMode) dimMode = false
                    }
                )
            }
            .then(if (burnInProtection) Modifier.offset(x = offsetX.dp, y = offsetY.dp) else Modifier)
            .padding(24.dp)
    ) {
        if (dimMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { dimMode = false }
            ) {
                Text(
                    text = "Tap anywhere to wake",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            return@Box
        }

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(backgroundColorInt).copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isBatteryCharging -> Icons.Default.BatteryChargingFull
                            batteryPercentage <= 20 -> Icons.Default.BatteryAlert
                            else -> Icons.Default.BatteryStd
                        },
                        contentDescription = "Battery",
                        tint = when {
                            batteryPercentage <= 20 && !isBatteryCharging -> Color.Red
                            else -> Color(textColorInt)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$batteryPercentage%",
                        color = Color(textColorInt),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Settings, Alarm, Brightness buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Brightness toggle
                IconButton(
                    onClick = { autoBrightness = !autoBrightness },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color(backgroundColorInt).copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        if (autoBrightness) Icons.Default.BrightnessAuto else Icons.Default.BrightnessMedium,
                        contentDescription = "Brightness",
                        tint = Color(textColorInt),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Alarm button
                IconButton(
                    onClick = { showAlarmDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color(backgroundColorInt).copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = "Alarm",
                        tint = if (savedAlarmTime != null) Color.Yellow else Color(textColorInt),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings button
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color(backgroundColorInt).copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(textColorInt),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Main clock display with offset for burn-in protection
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            when (clockFace) {
                ClockFace.DIGITAL -> DigitalClockFace(
                    currentTime = currentTime,
                    is24HourFormat = is24HourFormat,
                    showSeconds = showSeconds,
                    textColor = Color(textColorInt),
                    fontFamily = selectedFontFamily ?: digitalFontFamily
                )
                ClockFace.ANALOG -> AnalogClockFace(
                    currentTime = currentTime,
                    textColor = Color(textColorInt),
                    backgroundColor = Color(backgroundColorInt)
                )
                ClockFace.SPLIT -> SplitClockFace(
                    currentTime = currentTime,
                    is24HourFormat = is24HourFormat,
                    showSeconds = showSeconds,
                    textColor = Color(textColorInt),
                    fontFamily = selectedFontFamily ?: digitalFontFamily
                )
                ClockFace.MINIMALIST -> MinimalistClockFace(
                    currentTime = currentTime,
                    is24HourFormat = is24HourFormat,
                    textColor = Color(textColorInt),
                    fontFamily = selectedFontFamily ?: digitalFontFamily
                )
            }
        }

        // Date display at bottom
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
            color = Color(textColorInt).copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Clock Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.heightIn(max = 500.dp)
                ) {
                    // Tab Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("Display", "Appearance", "System").forEachIndexed { index, tab ->
                            FilterChip(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                label = { Text(tab, fontSize = 12.sp) }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> SettingsDisplayTab(
                            is24HourFormat = is24HourFormat,
                            on24HourFormatChange = {
                                is24HourFormat = it
                                sharedPrefs.edit().putBoolean("format_24h_key", it).apply()
                            },
                            showSeconds = showSeconds,
                            onShowSecondsChange = {
                                showSeconds = it
                                sharedPrefs.edit().putBoolean("show_seconds_key", it).apply()
                            },
                            clockFace = clockFace,
                            onClockFaceChange = {
                                clockFace = it
                                sharedPrefs.edit().putString("clock_face_key", it.name).apply()
                            },
                            selectedFontFamily = selectedFontFamily,
                            onFontChange = { font ->
                                selectedFontFamily = font?.fontFamily
                                // Save font selection if needed
                            }
                        )
                        1 -> SettingsAppearanceTab(
                            backgroundColorInt = backgroundColorInt,
                            onBackgroundChange = { color ->
                                backgroundColorInt = color
                                sharedPrefs.edit().putInt("bg_color_key", color).apply()
                            },
                            textColorInt = textColorInt,
                            onTextColorChange = { color ->
                                textColorInt = color
                                sharedPrefs.edit().putInt("text_color_key", color).apply()
                            },
                            burnInProtection = burnInProtection,
                            onBurnInProtectionChange = {
                                burnInProtection = it
                                sharedPrefs.edit().putBoolean("burn_in_protection_key", it).apply()
                            }
                        )
                        2 -> SettingsSystemTab(
                            autoBrightness = autoBrightness,
                            onAutoBrightnessChange = {
                                autoBrightness = it
                                sharedPrefs.edit().putBoolean("auto_brightness_key", it).apply()
                            },
                            screenBrightness = screenBrightness,
                            onScreenBrightnessChange = {
                                screenBrightness = it
                                sharedPrefs.edit().putFloat("screen_brightness_key", it).apply()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Alarm Dialog
    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("Set Alarm") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = alarmTitle,
                        onValueChange = { alarmTitle = it },
                        label = { Text("Alarm Label") },
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = alarmHour.toString(),
                            onValueChange = {
                                alarmHour = it.toIntOrNull()?.coerceIn(0, 23) ?: 0
                            },
                            label = { Text("Hour (0-23)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = alarmMinute.toString(),
                            onValueChange = {
                                alarmMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: 0
                            },
                            label = { Text("Minute (0-59)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (savedAlarmTime != null) {
                        TextButton(
                            onClick = {
                                cancelAlarm(context)
                                savedAlarmTime = null
                            }
                        ) {
                            Text("Cancel Current Alarm", color = Color.Red)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    setAlarm(context, alarmHour, alarmMinute, alarmTitle)
                    savedAlarmTime = System.currentTimeMillis()
                    showAlarmDialog = false
                }) {
                    Text("Set Alarm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DigitalClockFace(
    currentTime: LocalDateTime,
    is24HourFormat: Boolean,
    showSeconds: Boolean,
    textColor: Color,
    fontFamily: FontFamily
) {
    val pattern = buildString {
        append(if (is24HourFormat) "HH" else "hh")
        append(":mm")
        if (showSeconds) append(":ss")
    }
    val formattedTime = currentTime.format(DateTimeFormatter.ofPattern(pattern))

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formattedTime,
            color = textColor,
            fontSize = 90.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
            letterSpacing = 4.sp
        )

        if (!is24HourFormat && !showSeconds) {
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("a")),
                color = textColor.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AnalogClockFace(
    currentTime: LocalDateTime,
    textColor: Color,
    backgroundColor: Color
) {
    val hour = currentTime.hour % 12
    val minute = currentTime.minute
    val second = currentTime.second

    val hourAngle = (hour * 30) + (minute * 0.5)
    val minuteAngle = minute * 6
    val secondAngle = second * 6

    Box(
        modifier = Modifier.size(250.dp),
        contentAlignment = Alignment.Center
    ) {
        // Clock face background
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = backgroundColor.copy(alpha = 0.3f),
                radius = size.minDimension / 2,
                center = Offset(size.width / 2, size.height / 2)
            )

            drawCircle(
                color = textColor,
                radius = 5f,
                center = Offset(size.width / 2, size.height / 2)
            )

            // Hour markers
            for (i in 1..12) {
                val angle = i * 30 - 90
                val radian = angle * PI / 180
                val startX = size.width / 2 + (size.width / 2 - 30) * cos(radian).toFloat()
                val startY = size.height / 2 + (size.height / 2 - 30) * sin(radian).toFloat()
                val endX = size.width / 2 + (size.width / 2 - 15) * cos(radian).toFloat()
                val endY = size.height / 2 + (size.height / 2 - 15) * sin(radian).toFloat()

                drawLine(
                    color = textColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3f
                )
            }
        }

        // Hour hand
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val angle = (hourAngle - 90) * PI / 180
            val radius = size.width / 3
            val x = size.width / 2 + radius * cos(angle).toFloat()
            val y = size.height / 2 + radius * sin(angle).toFloat()

            drawLine(
                color = textColor,
                start = Offset(size.width / 2, size.height / 2),
                end = Offset(x, y),
                strokeWidth = 8f
            )
        }

        // Minute hand
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val angle = (minuteAngle - 90) * PI / 180
            val radius = size.width / 2.2f
            val x = size.width / 2 + radius * cos(angle).toFloat()
            val y = size.height / 2 + radius * sin(angle).toFloat()

            drawLine(
                color = textColor,
                start = Offset(size.width / 2, size.height / 2),
                end = Offset(x, y),
                strokeWidth = 5f
            )
        }

        // Second hand
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val angle = (secondAngle - 90) * PI / 180
            val radius = size.width / 1.8f
            val x = size.width / 2 + radius * cos(angle).toFloat()
            val y = size.height / 2 + radius * sin(angle).toFloat()

            drawLine(
                color = Color.Red,
                start = Offset(size.width / 2, size.height / 2),
                end = Offset(x, y),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun SplitClockFace(
    currentTime: LocalDateTime,
    is24HourFormat: Boolean,
    showSeconds: Boolean,
    textColor: Color,
    fontFamily: FontFamily
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val hourPattern = if (is24HourFormat) "HH" else "hh"
        val hour = currentTime.format(DateTimeFormatter.ofPattern(hourPattern))
        Text(
            text = hour,
            color = textColor,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("mm")),
                color = textColor,
                fontSize = 50.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = fontFamily
            )
            if (showSeconds) {
                Text(
                    text = currentTime.format(DateTimeFormatter.ofPattern("ss")),
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 24.sp,
                    fontFamily = fontFamily
                )
            }
            if (!is24HourFormat) {
                Text(
                    text = currentTime.format(DateTimeFormatter.ofPattern("a")),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MinimalistClockFace(
    currentTime: LocalDateTime,
    is24HourFormat: Boolean,
    textColor: Color,
    fontFamily: FontFamily
) {
    val pattern = if (is24HourFormat) "HH:mm" else "h:mm"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern(pattern)),
            color = textColor,
            fontSize = 70.sp,
            fontWeight = FontWeight.Light,
            fontFamily = fontFamily,
            letterSpacing = 2.sp
        )
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
            color = textColor.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun SettingsDisplayTab(
    is24HourFormat: Boolean,
    on24HourFormatChange: (Boolean) -> Unit,
    showSeconds: Boolean,
    onShowSecondsChange: (Boolean) -> Unit,
    clockFace: ClockFace,
    onClockFaceChange: (ClockFace) -> Unit,
    selectedFontFamily: FontFamily?,
    onFontChange: (FontOption?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Time Format", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("24-hour format")
            Switch(
                checked = is24HourFormat,
                onCheckedChange = on24HourFormatChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Show seconds")
            Switch(
                checked = showSeconds,
                onCheckedChange = onShowSecondsChange
            )
        }

        HorizontalDivider()

        Text("Clock Face", fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ClockFace.values()) { face ->
                FilterChip(
                    selected = clockFace == face,
                    onClick = { onClockFaceChange(face) },
                    label = { Text(face.name, fontSize = 12.sp) }
                )
            }
        }

        HorizontalDivider()

        Text("Font Style", fontWeight = FontWeight.SemiBold)
        Text(
            text = "System fonts available",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SettingsAppearanceTab(
    backgroundColorInt: Int,
    onBackgroundChange: (Int) -> Unit,
    textColorInt: Int,
    onTextColorChange: (Int) -> Unit,
    burnInProtection: Boolean,
    onBurnInProtectionChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Background Color", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val backgroundPresets = listOf(0xFF0A0A0F.toInt(), 0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt())
            backgroundPresets.forEach { colorHex ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color(colorHex),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (backgroundColorInt == colorHex) 3.dp else 0.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onBackgroundChange(colorHex) }
                )
            }
        }

        Text("Text Color", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val neonPresets = listOf(0xFF00FFCC.toInt(), 0xFFFF3366.toInt(), 0xFFFFCC00.toInt(), 0xFF00FF66.toInt(), 0xFFFFFFFF.toInt())
            neonPresets.forEach { colorHex ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(colorHex), RoundedCornerShape(8.dp))
                        .border(
                            width = if (textColorInt == colorHex) 3.dp else 0.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTextColorChange(colorHex) }
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Burn-in Protection (Pixel Shift)")
            Switch(
                checked = burnInProtection,
                onCheckedChange = onBurnInProtectionChange
            )
        }
        Text(
            text = "Gently moves clock position every minute to prevent OLED burn-in",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SettingsSystemTab(
    autoBrightness: Boolean,
    onAutoBrightnessChange: (Boolean) -> Unit,
    screenBrightness: Float,
    onScreenBrightnessChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Auto Brightness")
            Switch(
                checked = autoBrightness,
                onCheckedChange = onAutoBrightnessChange
            )
        }

        if (!autoBrightness) {
            Text("Manual Brightness", fontWeight = FontWeight.SemiBold)
            Slider(
                value = screenBrightness,
                onValueChange = onScreenBrightnessChange,
                valueRange = 0.1f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${(screenBrightness * 100).toInt()}%",
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        HorizontalDivider()

        Text("Battery Optimization", fontWeight = FontWeight.SemiBold)
        Text(
            text = "This app can keep screen on while running. For best experience, disable battery optimization for this app in system settings.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// Alarm helper functions
fun setAlarm(context: Context, hour: Int, minute: Int, title: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)

        if (timeInMillis <= System.currentTimeMillis()) {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
    }

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("alarm_title", title)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}

fun cancelAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}