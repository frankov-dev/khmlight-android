package com.frankov_dev.khmlight.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frankov_dev.khmlight.widget.WidgetUpdater

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val appCtx = context.applicationContext
            BackgroundWorkScheduler.ensureScheduled(appCtx)
            OutageAlarmScheduler.scheduleFromCacheAsync(appCtx)
            WidgetUpdater.updateAllWidgets(appCtx)
        }
    }
}
