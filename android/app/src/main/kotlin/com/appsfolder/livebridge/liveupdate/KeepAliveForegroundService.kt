package com.appsfolder.livebridge.liveupdate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.appsfolder.livebridge.MainActivity
import com.appsfolder.livebridge.R

class KeepAliveForegroundService : Service() {
    private var systemEventReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        registerSystemEventsReceiver()
    }

    private fun registerSystemEventsReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        systemEventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                val prefs = ConverterPrefs(context)
                
                var title = ""
                var text = ""
                var iconResId = R.drawable.ic_stat_liveupdate

                when (action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        if (!prefs.getEventsBluetoothEnabled()) return
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_ON) {
                            title = "ON"
                            text = "Bluetooth"
                            iconResId = R.drawable.ic_bluetooth
                        } else if (state == BluetoothAdapter.STATE_OFF) {
                            title = "OFF"
                            text = "Bluetooth"
                            iconResId = R.drawable.ic_bluetooth_off
                        } else {
                            return
                        }
                    }
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        if (!prefs.getEventsWifiEnabled()) return
                        val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                        if (state == WifiManager.WIFI_STATE_ENABLED) {
                            title = "ON"
                            text = "Wi-Fi"
                            iconResId = R.drawable.ic_wifi
                        } else if (state == WifiManager.WIFI_STATE_DISABLED) {
                            title = "OFF"
                            text = "Wi-Fi"
                            iconResId = R.drawable.ic_wifi_off
                        } else {
                            return
                        }
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                        if (!prefs.getEventsWifiEnabled()) return
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                        val ssid = try {
                            val wifiManager = context.applicationContext
                                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                            wifiManager?.connectionInfo?.ssid
                                ?.removeSurrounding("\"")
                                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                        } catch (_: Exception) { null } ?: "Wi-Fi"

                        if (networkInfo?.isConnected == true) {
                            title = "Connected"
                            text = ssid
                            iconResId = R.drawable.ic_wifi
                        } else if (networkInfo?.state == NetworkInfo.State.DISCONNECTED) {
                            title = "Disconnected"
                            text = ssid
                            iconResId = R.drawable.ic_wifi_off
                        } else {
                            return
                        }
                    }
                    Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                        if (!prefs.getEventsAirplaneModeEnabled()) return
                        val isAirplaneModeOn = intent.getBooleanExtra("state", false)
                        title = if (isAirplaneModeOn) "ON" else "OFF"
                        text = "Airplane Mode"
                        iconResId = if (isAirplaneModeOn) R.drawable.ic_airplane else R.drawable.ic_airplane_off
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        if (!prefs.getEventsUnlockedEnabled()) return
                        title = "Unlocked"
                        text = "Device Unlocked"
                        iconResId = R.drawable.ic_unlocked
                    }
                    else -> return
                }
                LiveUpdateNotifier.triggerSyntheticSystemEvent(context, title, text, iconResId)
            }
        }
        registerReceiver(systemEventReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        systemEventReceiver?.let { unregisterReceiver(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForegroundCompat(notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_liveupdate)
            .setContentTitle("background mode")
            .setContentText("Keep this notification here for LiveBridge stability")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "livebridge_keep_alive"
        private const val CHANNEL_NAME = "Background Mode"
        private const val NOTIFICATION_ID = 41130

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, KeepAliveForegroundService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveForegroundService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing != null) {
                return
            }
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keep this notification here to use LiveBridge"
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
            )
        }
    }
}
