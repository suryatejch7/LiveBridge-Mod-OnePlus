package com.appsfolder.livebridge.liveupdate

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appsfolder.livebridge.R

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = ConverterPrefs(context)
        if (!prefs.getEventsBluetoothEnabled()) return

        val action = intent.action ?: return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try {
                    device?.name?.takeIf { it.isNotBlank() }
                } catch (_: SecurityException) { null } ?: "Bluetooth Device"

                LiveUpdateNotifier.triggerSyntheticSystemEvent(
                    context, "Connected", deviceName, R.drawable.ic_bluetooth
                )
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = try {
                    device?.name?.takeIf { it.isNotBlank() }
                } catch (_: SecurityException) { null } ?: "Bluetooth Device"

                LiveUpdateNotifier.triggerSyntheticSystemEvent(
                    context, "Disconnected", deviceName, R.drawable.ic_bluetooth
                )
            }
        }
    }
}