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
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
            MaterialTheme(colorScheme = darkColorScheme(background = CyberDark)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GeneratorDutyScreen(
                        isMonitoring = isMonitoring.value,
                        isFullCharge = isFullChargeAlarm.value,
                        isUnplug = isUnplugAlarm.value,
                        isCharging = isCharging.value,
                        onToggleMonitoring = { toggleMonitoring() },
                        onToggleFullCharge = { isFullChargeAlarm.value = !isFullChargeAlarm.value; updateService() },
                        onToggleUnplug = { isUnplugAlarm.value = !isUnplugAlarm.value; updateService() },
                        onStopAlarm = { stopAlarm() }
                    )
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
fun GeneratorDutyScreen(
    isMonitoring: Boolean,
    isFullCharge: Boolean,
    isUnplug: Boolean,
    isCharging: Boolean,
    onToggleMonitoring: () -> Unit,
    onToggleFullCharge: () -> Unit,
    onToggleUnplug: () -> Unit,
    onStopAlarm: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var blastAlpha by remember { mutableStateOf(0f) }
    var blastScale by remember { mutableStateOf(1f) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        // High-Quality Portrait Background
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.app_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        
        // Darkened Overlay for Contrast
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(40.dp))
            Text("GEN-DUTY", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("SYSTEM OVERWATCH", fontSize = 12.sp, color = NeonBlue, letterSpacing = 4.sp)

            Spacer(modifier = Modifier.weight(0.8f))

            // TOP BUTTONS (Full Charge & Unplug)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                FeatureButton(label = "FULL CHARGE", active = isFullCharge, color = NeonGreen, onClick = onToggleFullCharge)
                FeatureButton(label = "UNPLUG ALARM", active = isUnplug, color = WarningRed, onClick = onToggleUnplug)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // CENTER ACTIVATE BUTTON with BLAST EFFECT
            Box(contentAlignment = Alignment.Center) {
                // Blast Animation Ring
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(blastScale)
                        .alpha(blastAlpha)
                        .border(10.dp, statusColor, CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(if (isMonitoring) glowScale else 1f)
                        .shadow(if (isMonitoring) 50.dp else 0.dp, CircleShape, spotColor = statusColor)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(statusColor.copy(alpha = 0.3f), Color.Transparent)
                            )
                        )
                        .border(3.dp, statusColor, CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                coroutineScope.launch {
                                    blastAlpha = 0.8f
                                    blastScale = 1f
                                    onToggleMonitoring()
                                    launch {
                                        animate(1f, 2.5f, animationSpec = tween(500)) { v, _ -> blastScale = v }
                                    }
                                    launch {
                                        animate(0.8f, 0f, animationSpec = tween(500)) { v, _ -> blastAlpha = v }
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
