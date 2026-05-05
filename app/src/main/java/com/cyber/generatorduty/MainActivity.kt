package com.cyber.generatorduty

import com.cyber.generatorduty.R

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val CyberDark = Color(0xFF0A0A0A)
val NeonBlue = Color(0xFF00F0FF)
val NeonGreen = Color(0xFF39FF14)
val WarningRed = Color(0xFFFF003C)

class MainActivity : ComponentActivity() {

    private var isMonitoring = mutableStateOf(false)
    private var isFullChargeAlarm = mutableStateOf(false)
    private var isUnplugAlarm = mutableStateOf(true)
    private var isCharging = mutableStateOf(true)
    private var showSettings = mutableStateOf(false)
    
    // Settings state
    private var speedDialNumber = mutableStateOf("")
    private var speedDialDelay = mutableStateOf(3) // minutes
    private var selectedLanguage = mutableStateOf("English")
    private var isDarkMode = mutableStateOf(true)
    private var selectedTheme = mutableStateOf(1) // 1 to 4

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isCharging.value = intent?.getBooleanExtra("isCharging", false) ?: false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkPermissions()

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        ContextCompat.registerReceiver(
            this,
            updateReceiver,
            IntentFilter("com.cyber.generatorduty.POWER_UPDATE"),
            ContextCompat.RECEIVER_EXPORTED
        )

        setContent {
            MaterialTheme(colorScheme = if (isDarkMode.value) darkColorScheme(background = CyberDark) else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSettings.value) {
                        SettingsScreen(
                            onBack = { showSettings.value = false },
                            speedDial = speedDialNumber.value,
                            onSpeedDialChange = { speedDialNumber.value = it },
                            delay = speedDialDelay.value,
                            onDelayChange = { speedDialDelay.value = it },
                            language = selectedLanguage.value,
                            onLanguageChange = { selectedLanguage.value = it },
                            isDark = isDarkMode.value,
                            onThemeToggle = { isDarkMode.value = !isDarkMode.value }
                        )
                    } else {
                        GeneratorDutyScreen(
                            isMonitoring = isMonitoring.value,
                            isFullCharge = isFullChargeAlarm.value,
                            isUnplug = isUnplugAlarm.value,
                            isCharging = isCharging.value,
                            theme = selectedTheme.value,
                            onToggleMonitoring = { toggleMonitoring() },
                            onToggleFullCharge = { isFullChargeAlarm.value = !isFullChargeAlarm.value; updateService() },
                            onToggleUnplug = { isUnplugAlarm.value = !isUnplugAlarm.value; updateService() },
                            onStopAlarm = { stopAlarm() },
                            onOpenSettings = { showSettings.value = true }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
    }

    private fun toggleMonitoring() {
        isMonitoring.value = !isMonitoring.value
        updateService()
    }

    private fun updateService() {
        val intent = Intent(this, PowerMonitorService::class.java)
        if (isMonitoring.value) {
            intent.action = "UPDATE_CONFIG"
            intent.putExtra("fullCharge", isFullChargeAlarm.value)
            intent.putExtra("unplug", isUnplugAlarm.value)
            intent.putExtra("speedDial", speedDialNumber.value)
            intent.putExtra("speedDialDelay", speedDialDelay.value)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }

    private fun stopAlarm() {
        val intent = Intent(this, PowerMonitorService::class.java).apply { action = "STOP_ALARM" }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (e: Exception) {}
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    speedDial: String,
    onSpeedDialChange: (String) -> Unit,
    delay: Int,
    onDelayChange: (Int) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    isDark: Boolean,
    onThemeToggle: () -> Unit
) {
    BackHandler { onBack() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            Text("SYSTEM CONFIG", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("EMERGENCY SPEED DIAL", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        TextField(
            value = speedDial,
            onValueChange = onSpeedDialChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter contact number...") },
            colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("CALL DELAY: $delay MIN", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = delay.toFloat(),
            onValueChange = { onDelayChange(it.toInt()) },
            valueRange = 1f..10f,
            steps = 9
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("INTERFACE THEME", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DARK MODE")
            Switch(checked = isDark, onCheckedChange = { onThemeToggle() })
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("SYSTEM LANGUAGE", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val languages = listOf("English", "Spanish", "French", "German", "Hindi", "Arabic", "Chinese", "Japanese")
        languages.forEach { lang ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onLanguageChange(lang) }) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = language == lang, onClick = { onLanguageChange(lang) })
                Text(lang)
            }
        }
    }
}

@Composable
fun GeneratorDutyScreen(
    isMonitoring: Boolean,
    isFullCharge: Boolean,
    isUnplug: Boolean,
    isCharging: Boolean,
    theme: Int,
    onToggleMonitoring: () -> Unit,
    onToggleFullCharge: () -> Unit,
    onToggleUnplug: () -> Unit,
    onStopAlarm: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val blastAlpha = remember { Animatable(0f) }
    val blastScale = remember { Animatable(1f) }

    val infiniteTransition = rememberInfiniteTransition()
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )

    val statusColor by animateColorAsState(
        targetValue = when {
            !isMonitoring -> NeonBlue
            isCharging -> NeonGreen
            else -> WarningRed
        }
    )

    val backgroundRes = when(theme) {
        1 -> R.drawable.theme_1
        2 -> R.drawable.theme_2
        3 -> R.drawable.theme_3
        4 -> R.drawable.theme_4
        else -> R.drawable.app_bg
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = backgroundRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(40.dp))
            Text("GEN-DUTY", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("SYSTEM OVERWATCH", fontSize = 12.sp, color = NeonBlue, letterSpacing = 4.sp)

            Spacer(modifier = Modifier.weight(0.8f))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                FeatureButton(label = "FULL CHARGE", active = isFullCharge, color = NeonGreen, onClick = onToggleFullCharge)
                FeatureButton(label = "UNPLUG ALARM", active = isUnplug, color = WarningRed, onClick = onToggleUnplug)
            }

            Spacer(modifier = Modifier.height(40.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(blastScale.value)
                        .alpha(blastAlpha.value)
                        .border(10.dp, statusColor, CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(if (isMonitoring) glowScale else 1f)
                        .shadow(if (isMonitoring) 50.dp else 0.dp, CircleShape, spotColor = statusColor)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(colors = listOf(statusColor.copy(alpha = 0.3f), Color.Transparent)))
                        .border(3.dp, statusColor, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                coroutineScope.launch {
                                    onToggleMonitoring()
                                    launch {
                                        blastScale.snapTo(1f)
                                        blastScale.animateTo(2.5f, tween(500))
                                    }
                                    launch {
                                        blastAlpha.snapTo(0.8f)
                                        blastAlpha.animateTo(0f, tween(500))
                                    }
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(70.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isMonitoring) "ACTIVE" else "ACTIVATE",
                            color = statusColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // BOTTOM SETTINGS
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
                IconButton(
                    onClick = { /* Settings */ },
                    modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.15f), CircleShape).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                }
            }
        }

        // ALARM OVERLAY (3D Impact)
        if (isMonitoring && !isCharging && isUnplug) {
            AlarmOverlay(onStop = onStopAlarm)
        }
    }
}

@Composable
fun FeatureButton(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) color.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f))
                .border(2.dp, if (active) color else Color.Gray, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
            contentAlignment = Alignment.Center
        ) {
            Text(if (active) "ON" else "OFF", color = if (active) color else Color.Gray, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AlarmOverlay(onStop: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        var isPressed by remember { mutableStateOf(false) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("!!! WARNING !!!", color = WarningRed, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f).height(100.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isPressed) WarningRed else WarningRed.copy(alpha = 0.3f))
                    .border(3.dp, WarningRed, RoundedCornerShape(20.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            isPressed = true
                            val start = System.currentTimeMillis()
                            tryAwaitRelease()
                            isPressed = false
                            if (System.currentTimeMillis() - start > 2000) onStop()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("HOLD TO DISARM SYSTEM", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
