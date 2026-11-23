package com.frankov_dev.khmlight.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.widget.WidgetUpdater

class SyncScheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repo = OutageRepository(applicationContext)
        val prefs = PrefsManager(applicationContext)

        val oldFeed = repo.loadCachedFeed().first
        val fetchResult = repo.fetchFeed()
        val newFeed = fetchResult.feed ?: return Result.retry()

        val enabledLocations = prefs.getSavedLocations().filter { it.notifyEnabled }
        if (enabledLocations.isNotEmpty() && prefs.isNotifyScheduleChangeEnabled()) {
            val notifyQueues = enabledLocations.map { it.queueId }.distinct()
            for (queueId in notifyQueues) {
                val label = SavedLocationCodec.formatQuotedLabel(enabledLocations, queueId)
                val changes = ScheduleChangeDetector.detectChanges(
                    oldFeed = oldFeed,
                    newFeed = newFeed,
                    queueId = queueId,
                    lastNotifiedTodaySig = prefs.getLastNotifiedTodaySignature(queueId),
                    lastNotifiedTomorrowSig = prefs.getLastNotifiedTomorrowSignature(queueId)
                )
                for (change in changes) {
                    when (change) {
                        is ScheduleChangeEvent.TodayChanged -> {
                            val sig = ScheduleChangeDetector.buildSignature(
                                change.date,
                                change.queueId,
                                change.intervals
                            )
                            prefs.setLastNotifiedTodaySignature(queueId, sig)
                            val deltaLabel = newFeed.schedules[change.date]
                                ?.computeQueueDiff(change.queueId)
                                ?.formatDeltaShort()
                                ?.takeIf { it.isNotBlank() }
                            NotificationHelper.showTodayScheduleChanged(
                                context = applicationContext,
                                queueId = change.queueId,
                                locationLabel = label,
                                intervals = change.intervals,
                                totalHoursOff = change.totalHoursOff,
                                deltaLabel = deltaLabel
                            )
                        }
                        is ScheduleChangeEvent.TomorrowPublished -> {
                            val sig = ScheduleChangeDetector.buildSignature(
                                change.date,
                                change.queueId,
                                change.intervals
                            )
                            prefs.setLastNotifiedTomorrowSignature(queueId, sig)
                            NotificationHelper.showTomorrowSchedulePublished(
                                context = applicationContext,
                                queueId = change.queueId,
                                locationLabel = label,
                                intervals = change.intervals,
                                totalHoursOff = change.totalHoursOff
                            )
                        }
                    }
                }
            }
        }

        WidgetUpdater.updateAllWidgetsSuspend(applicationContext, newFeed)
        OutageAlarmScheduler.schedule(applicationContext, newFeed)

        return Result.success()
    }
}
