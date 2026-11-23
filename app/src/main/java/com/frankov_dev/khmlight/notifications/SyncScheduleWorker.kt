package com.frankov_dev.khmlight.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.PrefsManager
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

        val myQueue = prefs.getSelectedQueue()
        if (myQueue != null && prefs.isNotifyScheduleChangeEnabled()) {
            val changes = ScheduleChangeDetector.detectChanges(
                oldFeed = oldFeed,
                newFeed = newFeed,
                queueId = myQueue,
                lastNotifiedTodaySig = prefs.getLastNotifiedTodaySignature(),
                lastNotifiedTomorrowSig = prefs.getLastNotifiedTomorrowSignature()
            )
            for (change in changes) {
                when (change) {
                    is ScheduleChangeEvent.TodayChanged -> {
                        val sig = ScheduleChangeDetector.buildSignature(
                            change.date,
                            change.queueId,
                            change.intervals
                        )
                        prefs.setLastNotifiedTodaySignature(sig)
                        NotificationHelper.showTodayScheduleChanged(
                            context = applicationContext,
                            queueId = change.queueId,
                            intervals = change.intervals,
                            totalHoursOff = change.totalHoursOff
                        )
                    }
                    is ScheduleChangeEvent.TomorrowPublished -> {
                        val sig = ScheduleChangeDetector.buildSignature(
                            change.date,
                            change.queueId,
                            change.intervals
                        )
                        prefs.setLastNotifiedTomorrowSignature(sig)
                        NotificationHelper.showTomorrowSchedulePublished(
                            context = applicationContext,
                            queueId = change.queueId,
                            intervals = change.intervals,
                            totalHoursOff = change.totalHoursOff
                        )
                    }
                }
            }
        }

        WidgetUpdater.updateAllWidgets(applicationContext, newFeed)
        OutageAlarmScheduler.schedule(applicationContext, newFeed)

        return Result.success()
    }
}
