package com.frankov_dev.khmlight.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OutageAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appCtx = context.applicationContext
        val type = intent.getStringExtra(OutageAlarmScheduler.EXTRA_ALARM_TYPE)
        val queueId = intent.getStringExtra(OutageAlarmScheduler.EXTRA_QUEUE_ID).orEmpty()
        val eventTime = intent.getStringExtra(OutageAlarmScheduler.EXTRA_EVENT_TIME).orEmpty()
        val intervalEnd = intent.getStringExtra(OutageAlarmScheduler.EXTRA_INTERVAL_END).orEmpty()
        val leadMinutes = intent.getIntExtra(OutageAlarmScheduler.EXTRA_LEAD_MINUTES, 15)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = PrefsManager(appCtx)
                val currentQueue = prefs.getSelectedQueue()
                if (currentQueue != null && (queueId.isBlank() || queueId == currentQueue)) {
                    when (type) {
                        OutageAlarmScheduler.TYPE_OUTAGE_WARNING -> {
                            if (prefs.isNotifyOutageEnabled()) {
                                NotificationHelper.showOutageWarning(
                                    context = appCtx,
                                    queueId = currentQueue,
                                    leadMinutes = leadMinutes,
                                    startTime = eventTime,
                                    endTime = intervalEnd
                                )
                            }
                        }
                        OutageAlarmScheduler.TYPE_POWER_ON_WARNING -> {
                            if (prefs.isNotifyPowerOnEnabled()) {
                                NotificationHelper.showPowerOnWarning(
                                    context = appCtx,
                                    queueId = currentQueue,
                                    leadMinutes = leadMinutes,
                                    endTime = eventTime
                                )
                            }
                        }
                    }
                }

                val feed = OutageRepository(appCtx).loadCachedFeed().first
                WidgetUpdater.updateAllWidgets(appCtx, feed)
                OutageAlarmScheduler.schedule(appCtx, feed)
            } finally {
                pending.finish()
            }
        }
    }
}
