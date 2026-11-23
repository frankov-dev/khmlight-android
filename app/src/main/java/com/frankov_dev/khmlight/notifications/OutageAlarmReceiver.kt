package com.frankov_dev.khmlight.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OutageAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appCtx = context.applicationContext
        val type = intent.getStringExtra(OutageAlarmScheduler.EXTRA_ALARM_TYPE)
        val queueIds = intent.getStringArrayListExtra(OutageAlarmScheduler.EXTRA_QUEUE_IDS).orEmpty()
        val eventTimes = intent.getStringArrayListExtra(OutageAlarmScheduler.EXTRA_EVENT_TIMES).orEmpty()
        val intervalEnds = intent.getStringArrayListExtra(OutageAlarmScheduler.EXTRA_INTERVAL_ENDS).orEmpty()
        val leadMinutes = intent.getIntExtra(OutageAlarmScheduler.EXTRA_LEAD_MINUTES, 15)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = PrefsManager(appCtx)
                val enabledLocations = prefs.getSavedLocations().filter { it.notifyEnabled }
                val enabledQueues = enabledLocations.map { it.queueId }.toSet()

                for (i in queueIds.indices) {
                    val queueId = queueIds[i]
                    if (queueId !in enabledQueues) continue
                    val eventTime = eventTimes.getOrNull(i).orEmpty()
                    val intervalEnd = intervalEnds.getOrNull(i).orEmpty()
                    val label = SavedLocationCodec.formatQuotedLabel(enabledLocations, queueId)

                    when (type) {
                        OutageAlarmScheduler.TYPE_OUTAGE_WARNING -> {
                            if (prefs.isNotifyOutageEnabled()) {
                                NotificationHelper.showOutageWarning(
                                    context = appCtx,
                                    queueId = queueId,
                                    locationLabel = label,
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
                                    queueId = queueId,
                                    locationLabel = label,
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
