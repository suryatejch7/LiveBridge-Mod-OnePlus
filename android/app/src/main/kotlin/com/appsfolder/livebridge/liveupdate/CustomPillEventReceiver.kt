package com.appsfolder.livebridge.liveupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appsfolder.livebridge.R

class CustomPillEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CUSTOM_PILL_EVENT) return

        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: ""
        val iconName = intent.getStringExtra(EXTRA_ICON)?.trim()?.lowercase() ?: ""

        val iconResId = resolveIcon(iconName, context)

        LiveUpdateNotifier.ensureChannel(context)
        LiveUpdateNotifier.triggerSyntheticSystemEvent(context, title, text, iconResId)
    }

    private fun resolveIcon(iconName: String, context: Context): Int {
        if (iconName.isNotBlank()) {
            val resId = context.resources.getIdentifier(
                "ic_$iconName",
                "drawable",
                context.packageName
            )
            if (resId != 0) return resId

            val resIdDirect = context.resources.getIdentifier(
                iconName,
                "drawable",
                context.packageName
            )
            if (resIdDirect != 0) return resIdDirect
        }
        return R.drawable.ic_stat_liveupdate
    }

    companion object {
        const val ACTION_CUSTOM_PILL_EVENT = "com.appsfolder.livebridge.CUSTOM_PILL_EVENT"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ICON = "icon"
    }
}