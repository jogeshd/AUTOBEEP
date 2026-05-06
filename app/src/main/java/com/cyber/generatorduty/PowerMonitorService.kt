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
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private var speedDialNumber: String? = null
    private var speedDialDelay: Int = 3
    private var selectedTone: String = "Siren"
    private var alarmStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val callRunnable = Runnable { makeEmergencyCall() }

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
        startForeground(1, buildNotification())

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
                selectedTone = intent.getStringExtra("ringtone") ?: "Siren"
            }
        }
        return START_STICKY
    }

    private fun triggerAlarm() {
        if (isAlarmRinging) return
        isAlarmRinging = true
        alarmStartTime = System.currentTimeMillis()
        
        if (!speedDialNumber.isNullOrBlank()) {
            handler.postDelayed(callRunnable, speedDialDelay * 60 * 1000L)
        }

        // POP-UP OVER OTHER APPS: Update notification with High Priority and Full Screen Intent
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, buildNotification(true))

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Select Irritating Pattern
        val (vibePattern, alarmUri) = when (selectedTone) {
            "Siren" -> longArrayOf(0, 1000, 500, 1000) to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            "Nuclear" -> longArrayOf(0, 200, 100, 200, 100, 1000) to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            "Air Horn" -> longArrayOf(0, 3000, 500) to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            "Jackhammer" -> longArrayOf(0, 50, 50, 50, 50) to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            else -> longArrayOf(0, 500, 200, 500) to RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        ringtone?.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = true
        }
        ringtone?.play()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(vibePattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(vibePattern, 0)
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
        
        // Reset notification to normal
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, buildNotification(false))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "power_monitor",
                "Safe-Charging Status",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitoring battery and power state"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isAlarm: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "power_monitor")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(if (isAlarm) "!!! SECURITY BREACH !!!" else "SAFE-CHARGING Active")
            .setContentText(if (isAlarm) "DISCONNECT DETECTED! Tap to Disarm." else "System is monitoring power state.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, isAlarm)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(if (isAlarm) Notification.PRIORITY_MAX else Notification.PRIORITY_LOW)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }
}
