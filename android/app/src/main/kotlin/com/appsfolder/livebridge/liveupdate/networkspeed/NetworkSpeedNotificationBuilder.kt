package com.appsfolder.livebridge.liveupdate.networkspeed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.appsfolder.livebridge.MainActivity
import com.appsfolder.livebridge.R

class NetworkSpeedNotificationBuilder(
    private val context: Context
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                localizedText().channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localizedText().channelDescription
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    fun build(sample: NetworkSpeedSample): Notification {
        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detailText = buildString {
            append("↓")
            append(NetworkSpeedFormatter.formatCompact(sample.downloadBytesPerSecond))
            append("  ↑")
            append(NetworkSpeedFormatter.formatCompact(sample.uploadBytesPerSecond))
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(localizedText().notificationTitle)
            .setContentText(detailText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShortCriticalText(NetworkSpeedFormatter.formatCompact(sample.totalBytesPerSecond))
            .setRequestPromotedOngoing(true)
            .build()
    }

    private fun localizedText(): LocalizedText {
        return if (isRussianLocale()) {
            LocalizedText(
                notificationTitle = "Скорость сети",
                channelName = "Скорость сети",
                channelDescription = "Показывает текущую скорость сети в статус-баре"
            )
        } else {
            LocalizedText(
                notificationTitle = "Network speed",
                channelName = "Network speed",
                channelDescription = "Shows current network speed in the status bar"
            )
        }
    }

    private fun isRussianLocale(): Boolean {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return locale?.language?.startsWith("ru", ignoreCase = true) == true
    }

    private data class LocalizedText(
        val notificationTitle: String,
        val channelName: String,
        val channelDescription: String
    )

    companion object {
        const val CHANNEL_ID = "livebridge_network_speed"
    }
}
