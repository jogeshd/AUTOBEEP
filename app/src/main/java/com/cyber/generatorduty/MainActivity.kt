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
        // Request Ignore Battery Optimization (Crucial for background apps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
        
        // Request System Alert Window (Overlay)
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
        targetValue = if (!isCharging && isMonitoring) 1.2f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (!isCharging) 300 else 1000, easing = LinearEasing),
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
        // Background Image
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.app_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DUTY EFS",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "GENERATOR OVERWATCH",
                fontSize = 14.sp,
                color = NeonBlue,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(bottom = 60.dp)
            )

            // Main Status Indicator
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .scale(scale)
                    .shadow(if (isMonitoring) 50.dp else 0.dp, CircleShape, spotColor = statusColor)
                    .border(4.dp, statusColor, CircleShape)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier.fillMaxSize().padding(30.dp),
                    alpha = if (isMonitoring) 1f else 0.3f
                )
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxSize().padding(bottom = 40.dp)
                ) {
                    Text(
                        text = if (!isMonitoring) "STANDBY" else if (isCharging) "CHARGING" else "POWER LOSS!",
                        color = statusColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 10f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))

            Button(
                onClick = onToggleMonitoring,
                colors = ButtonDefaults.buttonColors(containerColor = if (isMonitoring) Color.DarkGray.copy(alpha = 0.8f) else NeonBlue.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(2.dp, if (isMonitoring) Color.Gray else NeonBlue, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isMonitoring) "DISABLE MONITORING" else "ENABLE MONITORING",
                    color = if (isMonitoring) Color.LightGray else Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!isCharging && isMonitoring) {
                Spacer(modifier = Modifier.height(20.dp))
                var isPressed by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(if (isPressed) WarningRed.copy(alpha = 0.7f) else WarningRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .border(2.dp, WarningRed, RoundedCornerShape(8.dp))
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
                    Text(text = "HOLD TO STOP ALARM", color = WarningRed, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
