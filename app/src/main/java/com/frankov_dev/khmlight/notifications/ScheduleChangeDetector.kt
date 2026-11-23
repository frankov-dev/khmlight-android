package com.frankov_dev.khmlight.notifications

import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.util.TimeUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduledEvent(
    val triggerEpochMs: Long,
    val eventTimeLabel: String,
    val intervalEndLabel: String,
    val leadMinutes: Int
)

data class UpcomingAlarms(
    val nextOutageWarning: ScheduledEvent?,
    val nextPowerOnWarning: ScheduledEvent?,
    val nextWidgetTransitionEpochMs: Long?
)

sealed class ScheduleChangeEvent {
    data class TodayChanged(
        val date: String,
        val queueId: String,
        val intervals: List<OutageInterval>,
        val totalHoursOff: Double
    ) : ScheduleChangeEvent()

    data class TomorrowPublished(
        val date: String,
        val queueId: String,
        val intervals: List<OutageInterval>,
        val totalHoursOff: Double
    ) : ScheduleChangeEvent()
}

object ScheduleChangeDetector {

    fun buildSignature(date: String, queueId: String, intervals: List<OutageInterval>): String {
        val normalized = intervals
            .sortedBy { it.startMinutes }
            .joinToString(",") { "${it.start}-${it.end}" }
        return "$date|$queueId|$normalized"
    }

    /**
     * Quiet hours: 23:00 .. 06:59 (local Kyiv hour).
     */
    fun isInQuietHours(hourOfDay: Int): Boolean = hourOfDay >= 23 || hourOfDay < 7

    /**
     * Detect real changes ONLY for my queue on today, or first appearance of tomorrow's schedule.
     */
    fun detectChanges(
        oldFeed: OutagesFeed?,
        newFeed: OutagesFeed,
        queueId: String,
        lastNotifiedTodaySig: String?,
        lastNotifiedTomorrowSig: String?,
        today: LocalDate = TimeUtils.nowKyiv().toLocalDate()
    ): List<ScheduleChangeEvent> {
        val events = mutableListOf<ScheduleChangeEvent>()
        val todayStr = today.toString()
        val tomorrowStr = today.plusDays(1).toString()

        // 1. Check today's intervals for my queue
        val newTodayQ = newFeed.schedules[todayStr]?.queues?.get(queueId)
        val oldTodayQ = oldFeed?.schedules?.get(todayStr)?.queues?.get(queueId)
        if (newTodayQ != null && oldTodayQ != null) {
            val newSig = buildSignature(todayStr, queueId, newTodayQ.intervals)
            val oldSig = buildSignature(todayStr, queueId, oldTodayQ.intervals)
            if (newSig != oldSig && newSig != lastNotifiedTodaySig) {
                events.add(
                    ScheduleChangeEvent.TodayChanged(
                        date = todayStr,
                        queueId = queueId,
                        intervals = newTodayQ.intervals,
                        totalHoursOff = newTodayQ.totalHoursOff
                    )
                )
            }
        }

        // 2. Check tomorrow's schedule first appearance (or update after appearing)
        val newTomorrowQ = newFeed.schedules[tomorrowStr]?.queues?.get(queueId)
        if (newTomorrowQ != null) {
            val newSig = buildSignature(tomorrowStr, queueId, newTomorrowQ.intervals)
            val oldTomorrowQ = oldFeed?.schedules?.get(tomorrowStr)?.queues?.get(queueId)
            val oldSig = oldTomorrowQ?.let { buildSignature(tomorrowStr, queueId, it.intervals) }
            if (newSig != oldSig && newSig != lastNotifiedTomorrowSig) {
                events.add(
                    ScheduleChangeEvent.TomorrowPublished(
                        date = tomorrowStr,
                        queueId = queueId,
                        intervals = newTomorrowQ.intervals,
                        totalHoursOff = newTomorrowQ.totalHoursOff
                    )
                )
            }
        }

        return events
    }

    /**
     * Compute the next upcoming outage warning, power-on warning, and exact widget transition
     * across today and tomorrow. Handles 24:00 end-of-day cleanly.
     */
    fun computeNextAlarms(
        feed: OutagesFeed?,
        queueId: String?,
        leadMinutes: Int,
        now: ZonedDateTime = TimeUtils.nowKyiv()
    ): UpcomingAlarms {
        if (feed == null || queueId.isNullOrBlank()) {
            return UpcomingAlarms(null, null, null)
        }

        val zone = now.zone
        val nowMs = now.toInstant().toEpochMilli()
        val today = now.toLocalDate()
        val candidateDates = listOf(today, today.plusDays(1))

        var bestOutage: ScheduledEvent? = null
        var bestPowerOn: ScheduledEvent? = null
        var bestTransitionMs: Long? = null

        for (date in candidateDates) {
            val daySchedule = feed.schedules[date.toString()] ?: continue
            val intervals = daySchedule.queues[queueId]?.intervals.orEmpty()

            for (interval in intervals) {
                val startMs = minuteToEpochMs(date, interval.startMinutes, zone)
                val endMs = minuteToEpochMs(date, interval.endMinutes, zone)

                // Exact status transition for widgets (add 5s cushion so minute has rolled over)
                if (startMs > nowMs) {
                    val t = startMs + 5_000L
                    if (bestTransitionMs == null || t < bestTransitionMs) bestTransitionMs = t
                }
                if (endMs > nowMs) {
                    val t = endMs + 5_000L
                    if (bestTransitionMs == null || t < bestTransitionMs) bestTransitionMs = t
                }

                // Outage warning: leadMinutes before start
                val outageTriggerMs = startMs - leadMinutes * 60_000L
                if (outageTriggerMs > nowMs) {
                    if (bestOutage == null || outageTriggerMs < bestOutage.triggerEpochMs) {
                        bestOutage = ScheduledEvent(
                            triggerEpochMs = outageTriggerMs,
                            eventTimeLabel = interval.start,
                            intervalEndLabel = interval.end,
                            leadMinutes = leadMinutes
                        )
                    }
                }

                // Power-on warning: leadMinutes before end
                val powerOnTriggerMs = endMs - leadMinutes * 60_000L
                if (powerOnTriggerMs > nowMs) {
                    if (bestPowerOn == null || powerOnTriggerMs < bestPowerOn.triggerEpochMs) {
                        bestPowerOn = ScheduledEvent(
                            triggerEpochMs = powerOnTriggerMs,
                            eventTimeLabel = interval.end,
                            intervalEndLabel = interval.end,
                            leadMinutes = leadMinutes
                        )
                    }
                }
            }
        }

        return UpcomingAlarms(
            nextOutageWarning = bestOutage,
            nextPowerOnWarning = bestPowerOn,
            nextWidgetTransitionEpochMs = bestTransitionMs
        )
    }

    private fun minuteToEpochMs(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long {
        val zdt = if (minuteOfDay >= 1440) {
            date.plusDays(1).atStartOfDay(zone)
        } else {
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            ZonedDateTime.of(date, time, zone)
        }
        return zdt.toInstant().toEpochMilli()
    }
}
