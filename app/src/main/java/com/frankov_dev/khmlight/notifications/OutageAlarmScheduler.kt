package com.frankov_dev.khmlight.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OutageAlarmScheduler {

    const val EXTRA_ALARM_TYPE = "alarm_type"
    const val EXTRA_QUEUE_IDS = "queue_ids"
    const val EXTRA_EVENT_TIMES = "event_times"
    const val EXTRA_INTERVAL_ENDS = "interval_ends"
    const val EXTRA_LEAD_MINUTES = "lead_minutes"

    const val TYPE_OUTAGE_WARNING = "outage_warning"
    const val TYPE_POWER_ON_WARNING = "power_on_warning"
    const val TYPE_WIDGET_TRANSITION = "widget_transition"

    private const val REQ_OUTAGE = 3001
    private const val REQ_POWER_ON = 3002
    private const val REQ_WIDGET_TRANSITION = 3003

    fun scheduleFromCacheAsync(context: Context) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val feed = OutageRepository(appCtx).loadCachedFeed().first
            schedule(appCtx, feed)
        }
    }

    fun schedule(context: Context, feed: OutagesFeed?) {
        val appCtx = context.applicationContext
        val prefs = PrefsManager(appCtx)
        val locations = prefs.getSavedLocations()
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (feed == null || locations.isEmpty()) {
            cancelAll(appCtx, alarmManager)
            return
        }

        val leadMinutes = prefs.getLeadTimeMinutes()
        val alarms = ScheduleChangeDetector.computeNextAlarmsForLocations(
            feed = feed,
            locations = locations,
            leadMinutes = leadMinutes
        )

        // 1. Outage warning alarm(s) for all enabled locations sharing the earliest trigger
        if (prefs.isNotifyOutageEnabled() && alarms.nextOutageTriggerMs != null && alarms.nextOutageEvents.isNotEmpty()) {
            setAlarm(
                alarmManager,
                alarms.nextOutageTriggerMs,
                buildPendingIntent(
                    context = appCtx,
                    requestCode = REQ_OUTAGE,
                    type = TYPE_OUTAGE_WARNING,
                    events = alarms.nextOutageEvents,
                    leadMinutes = leadMinutes
                )
            )
        } else {
            cancelAlarm(appCtx, alarmManager, REQ_OUTAGE, TYPE_OUTAGE_WARNING)
        }

        // 2. Power-on warning alarm(s)
        if (prefs.isNotifyPowerOnEnabled() && alarms.nextPowerOnTriggerMs != null && alarms.nextPowerOnEvents.isNotEmpty()) {
            setAlarm(
                alarmManager,
                alarms.nextPowerOnTriggerMs,
                buildPendingIntent(
                    context = appCtx,
                    requestCode = REQ_POWER_ON,
                    type = TYPE_POWER_ON_WARNING,
                    events = alarms.nextPowerOnEvents,
                    leadMinutes = leadMinutes
                )
            )
        } else {
            cancelAlarm(appCtx, alarmManager, REQ_POWER_ON, TYPE_POWER_ON_WARNING)
        }

        // 3. Exact transition alarm so widgets immediately flip ON <-> OFF
        if (alarms.nextWidgetTransitionEpochMs != null) {
            setAlarm(
                alarmManager,
                alarms.nextWidgetTransitionEpochMs,
                buildPendingIntent(
                    context = appCtx,
                    requestCode = REQ_WIDGET_TRANSITION,
                    type = TYPE_WIDGET_TRANSITION
                )
            )
        } else {
            cancelAlarm(appCtx, alarmManager, REQ_WIDGET_TRANSITION, TYPE_WIDGET_TRANSITION)
        }
    }

    private fun setAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAll(context: Context, alarmManager: AlarmManager) {
        cancelAlarm(context, alarmManager, REQ_OUTAGE, TYPE_OUTAGE_WARNING)
        cancelAlarm(context, alarmManager, REQ_POWER_ON, TYPE_POWER_ON_WARNING)
        cancelAlarm(context, alarmManager, REQ_WIDGET_TRANSITION, TYPE_WIDGET_TRANSITION)
    }

    private fun cancelAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        type: String
    ) {
        val pi = buildPendingIntent(context, requestCode, type)
        alarmManager.cancel(pi)
    }

    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        type: String,
        events: List<ScheduledEvent> = emptyList(),
        leadMinutes: Int = 15
    ): PendingIntent {
        val intent = Intent(context, OutageAlarmReceiver::class.java).apply {
            action = "com.frankov_dev.khmlight.ALARM_$type"
            putExtra(EXTRA_ALARM_TYPE, type)
            putStringArrayListExtra(EXTRA_QUEUE_IDS, ArrayList(events.map { it.queueId }))
            putStringArrayListExtra(EXTRA_EVENT_TIMES, ArrayList(events.map { it.eventTimeLabel }))
            putStringArrayListExtra(EXTRA_INTERVAL_ENDS, ArrayList(events.map { it.intervalEndLabel }))
            putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
