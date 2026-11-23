package com.frankov_dev.khmlight.notifications

import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.util.TimeUtils
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduledEvent(
    val triggerEpochMs: Long,
    val eventTimeLabel: String,
    val intervalEndLabel: String,
    val leadMinutes: Int,
    val queueId: String = ""
)

data class UpcomingAlarms(
    val nextOutageWarning: ScheduledEvent?,
    val nextPowerOnWarning: ScheduledEvent?,
    val nextWidgetTransitionEpochMs: Long?
)

data class MultiUpcomingAlarms(
    val nextOutageTriggerMs: Long?,
    val nextOutageEvents: List<ScheduledEvent>,
    val nextPowerOnTriggerMs: Long?,
    val nextPowerOnEvents: List<ScheduledEvent>,
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
     * Detect real changes ONLY for the given queue on today, or first appearance of tomorrow's schedule.
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

        // 1. Check today's intervals
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
     * across today and tomorrow for a single queue.
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
        val singleLoc = listOf(SavedLocation(id = "single", name = queueId, queueId = queueId, notifyEnabled = true))
        val multi = computeNextAlarmsForLocations(feed, singleLoc, leadMinutes, now)
        return UpcomingAlarms(
            nextOutageWarning = multi.nextOutageEvents.firstOrNull(),
            nextPowerOnWarning = multi.nextPowerOnEvents.firstOrNull(),
            nextWidgetTransitionEpochMs = multi.nextWidgetTransitionEpochMs
        )
    }

    /**
     * Compute the earliest upcoming outage warning(s), power-on warning(s), and exact widget transition
     * across all saved locations. Warnings are filtered to locations with `notifyEnabled == true`;
     * simultaneous events for different queues are grouped together so none are skipped.
     */
    fun computeNextAlarmsForLocations(
        feed: OutagesFeed?,
        locations: List<SavedLocation>,
        leadMinutes: Int,
        now: ZonedDateTime = TimeUtils.nowKyiv()
    ): MultiUpcomingAlarms {
        if (feed == null || locations.isEmpty()) {
            return MultiUpcomingAlarms(null, emptyList(), null, emptyList(), null)
        }

        val zone = now.zone
        val nowMs = now.toInstant().toEpochMilli()
        val today = now.toLocalDate()
        val candidateDates = listOf(today, today.plusDays(1))

        val notifyQueueIds = locations.filter { it.notifyEnabled }.map { it.queueId }.distinct()
        val allQueueIds = locations.map { it.queueId }.distinct()

        var bestOutageMs: Long? = null
        val bestOutageEvents = mutableListOf<ScheduledEvent>()

        var bestPowerOnMs: Long? = null
        val bestPowerOnEvents = mutableListOf<ScheduledEvent>()

        var bestTransitionMs: Long? = null

        for (date in candidateDates) {
            val daySchedule = feed.schedules[date.toString()] ?: continue

            for (queueId in allQueueIds) {
                val intervals = daySchedule.queues[queueId]?.intervals.orEmpty()
                val shouldNotify = queueId in notifyQueueIds

                for (interval in intervals) {
                    val startMs = minuteToEpochMs(date, interval.startMinutes, zone)
                    val endMs = minuteToEpochMs(date, interval.endMinutes, zone)

                    if (startMs > nowMs) {
                        val t = startMs + 5_000L
                        if (bestTransitionMs == null || t < bestTransitionMs) bestTransitionMs = t
                    }
                    if (endMs > nowMs) {
                        val t = endMs + 5_000L
                        if (bestTransitionMs == null || t < bestTransitionMs) bestTransitionMs = t
                    }

                    if (!shouldNotify) continue

                    val outageTriggerMs = startMs - leadMinutes * 60_000L
                    if (outageTriggerMs > nowMs) {
                        val ev = ScheduledEvent(
                            triggerEpochMs = outageTriggerMs,
                            eventTimeLabel = interval.start,
                            intervalEndLabel = interval.end,
                            leadMinutes = leadMinutes,
                            queueId = queueId
                        )
                        if (bestOutageMs == null || outageTriggerMs < bestOutageMs) {
                            bestOutageMs = outageTriggerMs
                            bestOutageEvents.clear()
                            bestOutageEvents.add(ev)
                        } else if (outageTriggerMs == bestOutageMs && bestOutageEvents.none { it.queueId == queueId }) {
                            bestOutageEvents.add(ev)
                        }
                    }

                    val powerOnTriggerMs = endMs - leadMinutes * 60_000L
                    if (powerOnTriggerMs > nowMs) {
                        val ev = ScheduledEvent(
                            triggerEpochMs = powerOnTriggerMs,
                            eventTimeLabel = interval.end,
                            intervalEndLabel = interval.end,
                            leadMinutes = leadMinutes,
                            queueId = queueId
                        )
                        if (bestPowerOnMs == null || powerOnTriggerMs < bestPowerOnMs) {
                            bestPowerOnMs = powerOnTriggerMs
                            bestPowerOnEvents.clear()
                            bestPowerOnEvents.add(ev)
                        } else if (powerOnTriggerMs == bestPowerOnMs && bestPowerOnEvents.none { it.queueId == queueId }) {
                            bestPowerOnEvents.add(ev)
                        }
                    }
                }
            }
        }

        return MultiUpcomingAlarms(
            nextOutageTriggerMs = bestOutageMs,
            nextOutageEvents = bestOutageEvents,
            nextPowerOnTriggerMs = bestPowerOnMs,
            nextPowerOnEvents = bestPowerOnEvents,
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
