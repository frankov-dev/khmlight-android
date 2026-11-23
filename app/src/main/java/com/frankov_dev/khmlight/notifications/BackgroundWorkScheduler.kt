package com.frankov_dev.khmlight.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundWorkScheduler {

    private const val LEGACY_WORK_SYNC_SCHEDULE = "khmlight_sync_schedule_30m"
    private const val WORK_SYNC_SCHEDULE = "khmlight_sync_schedule_15m"
    private const val WORK_WIDGET_TICK = "khmlight_widget_tick_15m"

    fun ensureScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        // Cancel legacy 30m periodic work if upgrading from v1.0
        workManager.cancelUniqueWork(LEGACY_WORK_SYNC_SCHEDULE)

        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Fast 15m polling backed by lightweight ~250B status.json + schedule_hash
        val syncRequest = PeriodicWorkRequestBuilder<SyncScheduleWorker>(15, TimeUnit.MINUTES)
            .setConstraints(syncConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_SYNC_SCHEDULE,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        val tickRequest = PeriodicWorkRequestBuilder<WidgetTickWorker>(15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_WIDGET_TICK,
            ExistingPeriodicWorkPolicy.KEEP,
            tickRequest
        )
    }
}
