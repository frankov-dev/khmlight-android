package com.frankov_dev.khmlight.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            val appCtx = context.applicationContext
            BackgroundWorkScheduler.ensureScheduled(appCtx)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val feed = OutageRepository(appCtx).loadCachedFeed().first
                    OutageAlarmScheduler.schedule(appCtx, feed)
                    WidgetUpdater.updateAllWidgetsSuspend(appCtx, feed)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
