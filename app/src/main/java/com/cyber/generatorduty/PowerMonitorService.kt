package com.cyber.generatorduty

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.pm.ServiceInfo

class PowerMonitorService : Service() {

    private var ringtone: Ringtone? = null
    private var isAlarmRinging = false
    private var vibrator: Vibrator? = null
    private var isFullChargeAlarmEnabled = false
    private var isUnplugAlarmEnabled = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (isUnplugAlarmEnabled) triggerAlarm()
                    sendPowerUpdate(false)
                }
                Intent.ACTION_POWER_CONNECTED -> sendPowerUpdate(true)
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (isFullChargeAlarmEnabled && level != -1 && scale != -1 && level == scale) {
                        triggerAlarm()
                    }
                }
            }
        }
    }

    private fun sendPowerUpdate(isCharging: Boolean) {
        val broadcast = Intent("com.cyber.generatorduty.POWER_UPDATE")
        broadcast.putExtra("isCharging", isCharging)
        sendBroadcast(broadcast)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, buildNotification())
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        
        ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_ALARM" -> stopAlarm()
            "UPDATE_CONFIG" -> {
                isFullChargeAlarmEnabled = intent.getBooleanExtra("fullCharge", false)
                isUnplugAlarmEnabled = intent.getBooleanExtra("unplug", false)
                speedDialNumber = intent.getStringExtra("speedDial")
                speedDialDelay = intent.getIntExtra("speedDialDelay", 3)
            }
        }
        return START_STICKY
    }

    private var speedDialNumber: String? = null
    private var speedDialDelay: Int = 3
    private var alarmStartTime: Long = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val callRunnable = Runnable { makeEmergencyCall() }

    private fun triggerAlarm() {
        if (isAlarmRinging) return
        isAlarmRinging = true
        alarmStartTime = System.currentTimeMillis()
        
        if (!speedDialNumber.isNullOrBlank()) {
            handler.postDelayed(callRunnable, speedDialDelay * 60 * 1000L)
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        
        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = true
        }
        ringtone?.play()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
        }
    }

    private fun makeEmergencyCall() {
        if (!isAlarmRinging || speedDialNumber.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$speedDialNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("PowerMonitor", "Call failed: ${e.message}")
        }
    }

    private fun stopAlarm() {
        isAlarmRinging = false
        handler.removeCallbacks(callRunnable)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerReceiver)
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("gen_duty_channel", "Generator Duty", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "gen_duty_channel")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Generator Duty Active")
            .setContentText("Monitoring power status...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
    }
}
