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
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class PowerMonitorService : Service() {

    private var toneGenerator: ToneGenerator? = null
    private var isAlarmRinging = false
    private var vibrator: Vibrator? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                Log.d("PowerMonitor", "Power DISCONNECTED! Triggering alarm.")
                triggerAlarm()
                
                // Notify MainActivity to update UI
                val broadcast = Intent("com.cyber.generatorduty.POWER_UPDATE")
                broadcast.putExtra("isCharging", false)
                sendBroadcast(broadcast)
            } else if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                Log.d("PowerMonitor", "Power CONNECTED.")
                val broadcast = Intent("com.cyber.generatorduty.POWER_UPDATE")
                broadcast.putExtra("isCharging", true)
                sendBroadcast(broadcast)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") {
            stopAlarm()
        }
        return START_STICKY
    }

    private fun triggerAlarm() {
        if (isAlarmRinging) return
        isAlarmRinging = true

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Maximize volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Generate an extremely irritating high-frequency beep
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        
        Thread {
            while (isAlarmRinging) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
                try { Thread.sleep(1000) } catch (e: InterruptedException) {}
            }
        }.start()

        // Continuous strong vibration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
        }
    }

    private fun stopAlarm() {
        isAlarmRinging = false
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
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
