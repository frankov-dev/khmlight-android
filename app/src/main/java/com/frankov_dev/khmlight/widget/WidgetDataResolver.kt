package com.frankov_dev.khmlight.widget

import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.util.TimeUtils
import java.time.LocalDate

enum class WidgetPowerMood {
    POWER_ON,
    POWER_OFF,
    NEUTRAL
}

data class ResolvedWidgetData(
    val queueBadge: String,
    val statusTitle: String,
    val badgeTimerText: String,
    val badgeCompactTimerText: String,
    val badgeSubtitle: String,
    val nextChangeTime: String?,
    val dailyHoursShort: String,
    val dailyHoursLong: String,
    val intervals: List<OutageInterval>,
    val mood: WidgetPowerMood,
    val hasKnownSchedule: Boolean,
    val showLiveTimeMarker: Boolean,
    val currentMinute: Int?,
    val isStale: Boolean
)

object WidgetDataResolver {

    const val STALE_THRESHOLD_MS = 3L * 60L * 60L * 1000L

    fun formatExpressiveTimer(minutes: Int): String {
        if (minutes <= 0) return "0 хв"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h год $m хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }

    fun formatCompactTimer(minutes: Int): String {
        if (minutes <= 0) return "0 хв"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}г ${m}хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }

    fun resolve(
        feed: OutagesFeed?,
        queueId: String?,
        lastAppSyncMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis(),
        today: LocalDate = TimeUtils.nowKyiv().toLocalDate(),
        nowMinute: Int = TimeUtils.currentMinuteOfDay()
    ): ResolvedWidgetData {
        if (queueId.isNullOrBlank()) {
            return ResolvedWidgetData(
                queueBadge = "—",
                statusTitle = "Оберіть чергу",
                badgeTimerText = "—",
                badgeCompactTimerText = "—",
                badgeSubtitle = "Налаштуйте",
                nextChangeTime = null,
                dailyHoursShort = "",
                dailyHoursLong = "",
                intervals = emptyList(),
                mood = WidgetPowerMood.NEUTRAL,
                hasKnownSchedule = false,
                showLiveTimeMarker = false,
                currentMinute = null,
                isStale = false
            )
        }

        if (feed == null || feed.availableDates.isEmpty()) {
            return ResolvedWidgetData(
                queueBadge = queueId,
                statusTitle = "Дані відсутні",
                badgeTimerText = "—",
                badgeCompactTimerText = "—",
                badgeSubtitle = "Немає графіка",
                nextChangeTime = null,
                dailyHoursShort = "",
                dailyHoursLong = "",
                intervals = emptyList(),
                mood = WidgetPowerMood.NEUTRAL,
                hasKnownSchedule = false,
                showLiveTimeMarker = false,
                currentMinute = null,
                isStale = true
            )
        }

        val syncIsStale = lastAppSyncMs != null && (nowEpochMs - lastAppSyncMs) > STALE_THRESHOLD_MS
        val todayStr = today.toString()
        val todaySchedule = feed.schedules[todayStr]

        if (todaySchedule != null) {
            return resolveToday(
                queueId = queueId,
                daySchedule = todaySchedule,
                nowMinute = nowMinute,
                isStale = syncIsStale
            )
        }

        // Future schedule only (no today schedule)
        val futureDate = feed.availableDates
            .mapNotNull { str -> parseDate(str)?.let { str to it } }
            .filter { (_, d) -> d.isAfter(today) }
            .minByOrNull { (_, d) -> d }

        if (futureDate != null) {
            val (dateStr, _) = futureDate
            val day = feed.schedules[dateStr]
            val q = day?.queues?.get(queueId)
            val intervals = q?.intervals.orEmpty()
            val hoursStr = TimeUtils.formatHours(q?.totalHoursOff ?: 0.0)
            return ResolvedWidgetData(
                queueBadge = queueId,
                statusTitle = TimeUtils.formatShortDayButton(dateStr, today),
                badgeTimerText = hoursStr,
                badgeCompactTimerText = hoursStr,
                badgeSubtitle = "Без світла",
                nextChangeTime = intervals.firstOrNull()?.start,
                dailyHoursShort = hoursStr,
                dailyHoursLong = "$hoursStr без світла",
                intervals = intervals,
                mood = WidgetPowerMood.NEUTRAL,
                hasKnownSchedule = q != null,
                showLiveTimeMarker = false,
                currentMinute = null,
                isStale = syncIsStale
            )
        }

        // Only past dates exist -> stale
        return ResolvedWidgetData(
            queueBadge = queueId,
            statusTitle = "Дані застаріли",
            badgeTimerText = "—",
            badgeCompactTimerText = "—",
            badgeSubtitle = "Немає графіка",
            nextChangeTime = null,
            dailyHoursShort = "",
            dailyHoursLong = "",
            intervals = emptyList(),
            mood = WidgetPowerMood.NEUTRAL,
            hasKnownSchedule = false,
            showLiveTimeMarker = false,
            currentMinute = null,
            isStale = true
        )
    }

    private fun resolveToday(
        queueId: String,
        daySchedule: DaySchedule,
        nowMinute: Int,
        isStale: Boolean
    ): ResolvedWidgetData {
        val q = daySchedule.queues[queueId]
        if (q == null) {
            return ResolvedWidgetData(
                queueBadge = queueId,
                statusTitle = "Дані відсутні",
                badgeTimerText = "—",
                badgeCompactTimerText = "—",
                badgeSubtitle = "Немає графіка",
                nextChangeTime = null,
                dailyHoursShort = "",
                dailyHoursLong = "",
                intervals = emptyList(),
                mood = WidgetPowerMood.NEUTRAL,
                hasKnownSchedule = false,
                showLiveTimeMarker = false,
                currentMinute = null,
                isStale = isStale
            )
        }

        val intervals = q.intervals
        val hoursStr = TimeUtils.formatHours(q.totalHoursOff)

        if (intervals.isEmpty()) {
            return ResolvedWidgetData(
                queueBadge = queueId,
                statusTitle = "Є світло",
                badgeTimerText = "24 год",
                badgeCompactTimerText = "24 год",
                badgeSubtitle = "Без відключень",
                nextChangeTime = null,
                dailyHoursShort = "0 год",
                dailyHoursLong = "Сьогодні без відключень",
                intervals = emptyList(),
                mood = WidgetPowerMood.POWER_ON,
                hasKnownSchedule = true,
                showLiveTimeMarker = true,
                currentMinute = nowMinute,
                isStale = isStale
            )
        }

        val live = TimeUtils.computeLiveStatus(intervals, nowMinute)
        return when {
            live.isPowerOff -> {
                val mins = live.minutesUntilChange ?: 0
                val endTime = live.nextChangeTime?.takeIf { it.isNotBlank() }
                ResolvedWidgetData(
                    queueBadge = queueId,
                    statusTitle = "Немає світла",
                    badgeTimerText = formatExpressiveTimer(mins),
                    badgeCompactTimerText = formatCompactTimer(mins),
                    badgeSubtitle = "До включення",
                    nextChangeTime = endTime,
                    dailyHoursShort = hoursStr,
                    dailyHoursLong = "$hoursStr без світла",
                    intervals = intervals,
                    mood = WidgetPowerMood.POWER_OFF,
                    hasKnownSchedule = true,
                    showLiveTimeMarker = true,
                    currentMinute = nowMinute,
                    isStale = isStale
                )
            }
            live.nextChangeTime != null && live.minutesUntilChange != null -> {
                val mins = live.minutesUntilChange
                ResolvedWidgetData(
                    queueBadge = queueId,
                    statusTitle = "Є світло",
                    badgeTimerText = formatExpressiveTimer(mins),
                    badgeCompactTimerText = formatCompactTimer(mins),
                    badgeSubtitle = "До відключення",
                    nextChangeTime = live.nextChangeTime,
                    dailyHoursShort = hoursStr,
                    dailyHoursLong = "$hoursStr без світла",
                    intervals = intervals,
                    mood = WidgetPowerMood.POWER_ON,
                    hasKnownSchedule = true,
                    showLiveTimeMarker = true,
                    currentMinute = nowMinute,
                    isStale = isStale
                )
            }
            else -> {
                ResolvedWidgetData(
                    queueBadge = queueId,
                    statusTitle = "Є світло",
                    badgeTimerText = "✓",
                    badgeCompactTimerText = "✓",
                    badgeSubtitle = "Завершено",
                    nextChangeTime = null,
                    dailyHoursShort = hoursStr,
                    dailyHoursLong = "$hoursStr без світла",
                    intervals = intervals,
                    mood = WidgetPowerMood.POWER_ON,
                    hasKnownSchedule = true,
                    showLiveTimeMarker = true,
                    currentMinute = nowMinute,
                    isStale = isStale
                )
            }
        }
    }

    private fun parseDate(str: String): LocalDate? = try {
        LocalDate.parse(str)
    } catch (_: Exception) {
        null
    }
}
