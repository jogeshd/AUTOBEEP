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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

val CyberDark = Color(0xFF0A0A0A)
val NeonBlue = Color(0xFF00F0FF)
val NeonGreen = Color(0xFF39FF14)
val WarningRed = Color(0xFFFF003C)

class MainActivity : ComponentActivity() {

    private var isMonitoring = mutableStateOf(false)
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
                        isCharging = isCharging.value,
                        onToggleMonitoring = { toggleMonitoring() },
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
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun toggleMonitoring() {
        isMonitoring.value = !isMonitoring.value
        val intent = Intent(this, PowerMonitorService::class.java)
        if (isMonitoring.value) {
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
        val intent = Intent(this, PowerMonitorService::class.java).apply {
            action = "STOP_ALARM"
        }
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
    isCharging: Boolean,
    onToggleMonitoring: () -> Unit,
    onStopAlarm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (!isCharging && isMonitoring) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (!isCharging) 400 else 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val statusColor by animateColorAsState(
        targetValue = when {
            !isMonitoring -> NeonBlue
            isCharging -> NeonGreen
            else -> WarningRed
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image - Stretched correctly
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.app_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            alpha = 0.5f
        )

        // UI Overlay
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            
            Text(
                text = "DUTY EFS",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            Text(
                text = if (isCharging) "CONNECTED" else "UNPLUGGED",
                fontSize = 14.sp,
                color = statusColor,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // CENTER ACTIVATE BUTTON
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(scale)
                    .shadow(if (isMonitoring) 40.dp else 0.dp, CircleShape, spotColor = statusColor)
                    .clip(CircleShape)
                    .background(if (isMonitoring) statusColor.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.5f))
                    .border(4.dp, statusColor, CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleMonitoring() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        alpha = if (isMonitoring) 1f else 0.4f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isMonitoring) "ACTIVE" else "ACTIVATE",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // BOTTOM BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Section
                Column {
                    Text("STATUS", color = Color.Gray, fontSize = 10.sp)
                    Text(
                        if (isMonitoring) "SHIELD UP" else "SHIELD DOWN",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Settings Button (Bottom Right)
                IconButton(
                    onClick = { /* Settings not implemented yet */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }
        }

        // ALARM STOP OVERLAY
        if (!isCharging && isMonitoring) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                var isPressed by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("POWER LOSS DETECTED", color = WarningRed, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(40.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(80.dp)
                            .background(if (isPressed) WarningRed.copy(alpha = 0.8f) else WarningRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(2.dp, WarningRed, RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressed = true
                                        val startTime = System.currentTimeMillis()
                                        tryAwaitRelease()
                                        isPressed = false
                                        if (System.currentTimeMillis() - startTime > 2000) onStopAlarm()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("HOLD TO DISARM", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
