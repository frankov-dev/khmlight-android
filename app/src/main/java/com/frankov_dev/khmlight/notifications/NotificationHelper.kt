package com.frankov_dev.khmlight.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.frankov_dev.khmlight.MainActivity
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.util.TimeUtils

object NotificationHelper {

    const val CHANNEL_OUTAGE = "khmlight_outage_warning"
    const val CHANNEL_POWER_ON = "khmlight_power_on"
    const val CHANNEL_SCHEDULE = "khmlight_schedule_changes"

    private const val ID_OUTAGE_BASE = 2100
    private const val ID_POWER_ON_BASE = 2300
    private const val ID_SCHEDULE_TODAY_BASE = 2500
    private const val ID_SCHEDULE_TOMORROW_BASE = 2700

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val outageChannel = NotificationChannel(
            CHANNEL_OUTAGE,
            context.getString(R.string.notif_channel_outage_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_outage_desc)
        }

        val powerOnChannel = NotificationChannel(
            CHANNEL_POWER_ON,
            context.getString(R.string.notif_channel_power_on_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_power_on_desc)
        }

        val scheduleChannel = NotificationChannel(
            CHANNEL_SCHEDULE,
            context.getString(R.string.notif_channel_schedule_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_schedule_desc)
        }

        nm.createNotificationChannels(listOf(outageChannel, powerOnChannel, scheduleChannel))
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showOutageWarning(
        context: Context,
        queueId: String,
        locationLabel: String? = null,
        leadMinutes: Int,
        startTime: String,
        endTime: String
    ) {
        if (!canPostNow(context)) return
        val label = locationLabel ?: resolveQuotedLabel(context, queueId)
        val title = "Відключення через ${TimeUtils.formatDurationMinutes(leadMinutes)}"
        val body = "$label • світло вимкнуть о $startTime (до $endTime)"
        post(context, CHANNEL_OUTAGE, idFor(ID_OUTAGE_BASE, queueId), title, body, NotificationCompat.PRIORITY_HIGH)
    }

    fun showPowerOnWarning(
        context: Context,
        queueId: String,
        locationLabel: String? = null,
        leadMinutes: Int,
        endTime: String
    ) {
        if (!canPostNow(context)) return
        val label = locationLabel ?: resolveQuotedLabel(context, queueId)
        val title = "Світло увімкнуть через ${TimeUtils.formatDurationMinutes(leadMinutes)}"
        val body = "$label • заживлення о $endTime"
        post(context, CHANNEL_POWER_ON, idFor(ID_POWER_ON_BASE, queueId), title, body, NotificationCompat.PRIORITY_DEFAULT)
    }

    fun showTodayScheduleChanged(
        context: Context,
        queueId: String,
        locationLabel: String? = null,
        intervals: List<OutageInterval>,
        totalHoursOff: Double,
        deltaLabel: String? = null
    ) {
        if (!canPostNow(context)) return
        val label = locationLabel ?: resolveQuotedLabel(context, queueId)
        val title = "Змінився графік на сьогодні"
        val summary = "$label • ${formatIntervalsSummary(intervals, totalHoursOff, deltaLabel)}"
        post(context, CHANNEL_SCHEDULE, idFor(ID_SCHEDULE_TODAY_BASE, queueId), title, summary, NotificationCompat.PRIORITY_DEFAULT)
    }

    fun showTomorrowSchedulePublished(
        context: Context,
        queueId: String,
        locationLabel: String? = null,
        intervals: List<OutageInterval>,
        totalHoursOff: Double
    ) {
        if (!canPostNow(context)) return
        val label = locationLabel ?: resolveQuotedLabel(context, queueId)
        val title = "Опубліковано графік на завтра"
        val summary = "$label • ${formatIntervalsSummary(intervals, totalHoursOff)}"
        post(context, CHANNEL_SCHEDULE, idFor(ID_SCHEDULE_TOMORROW_BASE, queueId), title, summary, NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun resolveQuotedLabel(context: Context, queueId: String): String {
        val enabledLocs = PrefsManager(context).getSavedLocations().filter { it.notifyEnabled }
        return SavedLocationCodec.formatQuotedLabel(enabledLocs, queueId)
    }

    private fun idFor(base: Int, queueId: String): Int = base + (queueId.hashCode() and 0x7F)

    private fun formatIntervalsSummary(
        intervals: List<OutageInterval>,
        totalHoursOff: Double,
        deltaLabel: String? = null
    ): String {
        if (intervals.isEmpty()) return "Відключень не заплановано"
        val hours = TimeUtils.formatHours(totalHoursOff)
        val deltaSuffix = if (!deltaLabel.isNullOrBlank()) " ($deltaLabel)" else ""
        val joined = intervals.joinToString(" • ") { "${it.start}–${it.end}" }
        return "$hours$deltaSuffix: $joined"
    }

    private fun canPostNow(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false
        val prefs = PrefsManager(context)
        if (prefs.isQuietHoursEnabled()) {
            val hour = TimeUtils.nowKyiv().hour
            if (ScheduleChangeDetector.isInQuietHours(hour)) return false
        }
        return true
    }

    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        priority: Int
    ) {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, notificationId, openIntent, flags)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
