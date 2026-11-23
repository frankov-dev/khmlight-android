package com.frankov_dev.khmlight.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.widget.WidgetUpdater

class WidgetTickWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val feed = OutageRepository(applicationContext).loadCachedFeed().first
        WidgetUpdater.updateAllWidgets(applicationContext, feed)
        OutageAlarmScheduler.schedule(applicationContext, feed)
        return Result.success()
    }
}
