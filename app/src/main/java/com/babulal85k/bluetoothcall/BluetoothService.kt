package com.babulal85k.bluetoothcall

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class BluetoothService : Service() {

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "bluetooth_call_channel"
        createNotificationChannel(channelId)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bluetooth Call Active")
            .setContentText("Connected to a device")
            .setSmallIcon(R.drawable.ic_notification) // Ensure this icon exists
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(1, notification) // Start as a foreground service
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bluetooth Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Keep service running
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
