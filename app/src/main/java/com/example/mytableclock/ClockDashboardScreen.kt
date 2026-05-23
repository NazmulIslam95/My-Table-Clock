package com.example.mytableclock

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val digitalFontFamily = FontFamily.Monospace

@Composable
fun ClockDashboardScreen() {
    val context = LocalContext.current

    val sharedPrefs = remember {
        context.getSharedPreferences("clock_configuration", Context.MODE_PRIVATE)
    }

    val initialBgColor = remember { sharedPrefs.getInt("bg_color_key", 0xFF121212.toInt()) }
    val initialTextColor = remember { sharedPrefs.getInt("text_color_key", 0xFF00FFCC.toInt()) }
    val initialTimeFormat = remember { sharedPrefs.getBoolean("format_24h_key", true) }

    var backgroundColorInt by rememberSaveable { mutableStateOf(initialBgColor) }
    var textColorInt by rememberSaveable { mutableStateOf(initialTextColor) }
    var is24HourFormat by rememberSaveable { mutableStateOf(initialTimeFormat) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var batteryPercentage by remember { mutableStateOf(100) }
    var isBatteryCharging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val batteryStatus: Intent? = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            batteryStatus?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                batteryPercentage = ((level / scale.toFloat()) * 100).toInt()

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(5000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(backgroundColorInt))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        isBatteryCharging -> Icons.Default.BatteryChargingFull
                        batteryPercentage <= 20 -> Icons.Default.BatteryAlert
                        else -> Icons.Default.BatteryStd
                    },
                    contentDescription = "Battery Status",
                    tint = if (batteryPercentage <= 20 && !isBatteryCharging) Color.Red else Color(textColorInt),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$batteryPercentage%",
                    color = Color(textColorInt),
                    fontSize = 16.sp,
                    fontFamily = digitalFontFamily
                )
            }

            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings Configuration",
                tint = Color(textColorInt).copy(alpha = 0.7f),
                modifier = Modifier
                    .size(28.dp)
                    .clickable { showSettingsDialog = true }
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pattern = if (is24HourFormat) "HH:mm:ss" else "hh:mm:ss"
            val formattedTime = currentTime.format(DateTimeFormatter.ofPattern(pattern))

            Text(
                text = formattedTime,
                color = Color(textColorInt),
                fontSize = 90.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = digitalFontFamily
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                if (!is24HourFormat) {
                    val amPmLabel = currentTime.format(DateTimeFormatter.ofPattern("a"))
                    Text(
                        text = amPmLabel,
                        color = Color(textColorInt).copy(alpha = 0.8f),
                        fontSize = 20.sp,
                        fontFamily = digitalFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                val dateLabel = currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))
                Text(
                    text = dateLabel,
                    color = Color(textColorInt).copy(alpha = 0.8f),
                    fontSize = 22.sp,
                    fontFamily = digitalFontFamily
                )
            }
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text(text = "Clock Settings") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "24-Hour Time Layout")
                            Switch(
                                checked = is24HourFormat,
                                onCheckedChange = { checked ->
                                    is24HourFormat = checked
                                    sharedPrefs.edit().putBoolean("format_24h_key", checked).apply()
                                }
                            )
                        }

                        HorizontalDivider()

                        // Text Color Picker Selection
                        Text(text = "Digital Neon Color Picker", fontWeight = FontWeight.SemiBold)

                        // Interactive Color Slider Layout
                        HsvColorPicker(
                            initialColor = Color(textColorInt),
                            onColorSelected = { selectedColor ->
                                textColorInt = selectedColor.toArgb()
                                sharedPrefs.edit().putInt("text_color_key", textColorInt).apply()
                            }
                        )

                        HorizontalDivider()

                        // Background Presets Selection
                        Text(text = "Canvas Background Color", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val backgroundPresets = listOf(0xFF121212, 0xFF0A192F, 0xFF1C1A27)
                            backgroundPresets.forEach { colorHex ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(colorHex), RoundedCornerShape(8.dp))
                                        .clickable {
                                            backgroundColorInt = colorHex.toInt()
                                            sharedPrefs.edit().putInt("bg_color_key", backgroundColorInt).apply()
                                        }
                                )
                            }
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
    }
}

/**
 * A native, lightweight HSV-based color picker bar component.
 */
@Composable
fun HsvColorPicker(
    initialColor: Color,
    onColorSelected: (Color) -> mutableStateOf<Color>
) {
    var selectedColor by remember { mutableStateOf(initialColor) }

    // Extract native Hue element from current layout configuration
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableStateOf(initialHsv[0]) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Active display showing the currently selected color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(selectedColor)
        )

        // Interactive Spectrum Canvas Slider track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(CircleShape)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val dragPositionX = change.position.x
                            val canvasWidth = size.width.toFloat()

                            // Constrain percentage between boundaries
                            val normalizedPosition = (dragPositionX / canvasWidth).coerceIn(0f, 1f)
                            hue = normalizedPosition * 360f

                            val updatedHsv = floatArrayOf(hue, 1f, 1f)
                            val colorInt = android.graphics.Color.HSVToColor(updatedHsv)

                            selectedColor = Color(colorInt)
                            onColorSelected(selectedColor)
                        }
                    }
            ) {
                // Generate a smooth gradient spectrum using full rainbow colors
                val colorSpectrum = listOf(
                    Color.Red, Color.Yellow, Color.Green,
                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                )

                drawRect(
                    brush = Brush.horizontalGradient(colors = colorSpectrum),
                    size = size
                )

                // Render pointer locator knob on top of spectrum layout track
                val currentKnobPositionX = (hue / 360f) * size.width
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(currentKnobPositionX, size.height / 2f)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 8.dp.toPx(),
                    center = Offset(currentKnobPositionX, size.height / 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}