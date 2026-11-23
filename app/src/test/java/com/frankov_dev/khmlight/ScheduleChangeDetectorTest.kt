package com.frankov_dev.khmlight

import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.QueueSchedule
import com.frankov_dev.khmlight.notifications.ScheduleChangeDetector
import com.frankov_dev.khmlight.notifications.ScheduleChangeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleChangeDetectorTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val today = LocalDate.of(2026, 11, 22)

    private fun feedOf(vararg days: Pair<String, Map<String, List<OutageInterval>>>): OutagesFeed {
        val map = days.associate { (date, queues) ->
            date to DaySchedule(
                date = date,
                operationalMessage = null,
                queues = queues.mapValues { (qId, ints) ->
                    QueueSchedule(qId, ints, ints.sumOf { it.durationMinutes } / 60.0)
                }
            )
        }
        return OutagesFeed(
            source = "test",
            lastUpdated = "2026-11-22T12:00:00+02:00",
            availableDates = days.map { it.first },
            schedules = map
        )
    }

    @Test
    fun ignoresChangesToOtherQueuesOnly() {
        val oldFeed = feedOf(
            "2026-11-22" to mapOf(
                "1.1" to listOf(OutageInterval("06:00", "10:00")),
                "2.1" to listOf(OutageInterval("10:00", "14:00"))
            )
        )
        val newFeed = feedOf(
            "2026-11-22" to mapOf(
                "1.1" to listOf(OutageInterval("06:00", "10:00")),
                "2.1" to listOf(OutageInterval("11:00", "15:00"))
            )
        )
        val changes = ScheduleChangeDetector.detectChanges(
            oldFeed = oldFeed,
            newFeed = newFeed,
            queueId = "1.1",
            lastNotifiedTodaySig = null,
            lastNotifiedTomorrowSig = null,
            today = today
        )
        assertTrue(changes.isEmpty())
    }

    @Test
    fun detectsTodayIntervalChangeForMyQueue() {
        val oldFeed = feedOf(
            "2026-11-22" to mapOf("1.1" to listOf(OutageInterval("06:00", "10:00")))
        )
        val newFeed = feedOf(
            "2026-11-22" to mapOf(
                "1.1" to listOf(
                    OutageInterval("06:00", "10:00"),
                    OutageInterval("16:00", "20:00")
                )
            )
        )
        val changes = ScheduleChangeDetector.detectChanges(
            oldFeed = oldFeed,
            newFeed = newFeed,
            queueId = "1.1",
            lastNotifiedTodaySig = null,
            lastNotifiedTomorrowSig = null,
            today = today
        )
        assertEquals(1, changes.size)
        assertTrue(changes[0] is ScheduleChangeEvent.TodayChanged)
    }

    @Test
    fun detectsTomorrowFirstPublicationAndAvoidsDuplicateNotification() {
        val oldFeed = feedOf(
            "2026-11-22" to mapOf("1.1" to listOf(OutageInterval("06:00", "10:00")))
        )
        val tomorrowIntervals = listOf(OutageInterval("08:00", "12:00"))
        val newFeed = feedOf(
            "2026-11-22" to mapOf("1.1" to listOf(OutageInterval("06:00", "10:00"))),
            "2026-11-23" to mapOf("1.1" to tomorrowIntervals)
        )

        val firstRun = ScheduleChangeDetector.detectChanges(
            oldFeed = oldFeed,
            newFeed = newFeed,
            queueId = "1.1",
            lastNotifiedTodaySig = null,
            lastNotifiedTomorrowSig = null,
            today = today
        )
        assertEquals(1, firstRun.size)
        assertTrue(firstRun[0] is ScheduleChangeEvent.TomorrowPublished)

        val sig = ScheduleChangeDetector.buildSignature("2026-11-23", "1.1", tomorrowIntervals)
        val secondRun = ScheduleChangeDetector.detectChanges(
            oldFeed = oldFeed,
            newFeed = newFeed,
            queueId = "1.1",
            lastNotifiedTodaySig = null,
            lastNotifiedTomorrowSig = sig,
            today = today
        )
        assertTrue(secondRun.isEmpty())
    }

    @Test
    fun computeNextAlarmsFindsOutagePowerOnAndMidnightEnd() {
        val feed = feedOf(
            "2026-11-22" to mapOf(
                "1.1" to listOf(
                    OutageInterval("13:00", "16:00"),
                    OutageInterval("20:00", "24:00")
                )
            )
        )
        val now = ZonedDateTime.of(today, LocalTime.of(12, 0), kyiv)
        val alarms = ScheduleChangeDetector.computeNextAlarms(
            feed = feed,
            queueId = "1.1",
            leadMinutes = 15,
            now = now
        )
        assertNotNull(alarms.nextOutageWarning)
        assertEquals("13:00", alarms.nextOutageWarning?.eventTimeLabel)
        assertEquals(
            ZonedDateTime.of(today, LocalTime.of(12, 45), kyiv).toInstant().toEpochMilli(),
            alarms.nextOutageWarning?.triggerEpochMs
        )

        assertNotNull(alarms.nextPowerOnWarning)
        assertEquals("16:00", alarms.nextPowerOnWarning?.eventTimeLabel)
        assertEquals(
            ZonedDateTime.of(today, LocalTime.of(15, 45), kyiv).toInstant().toEpochMilli(),
            alarms.nextPowerOnWarning?.triggerEpochMs
        )

        assertNotNull(alarms.nextWidgetTransitionEpochMs)
        assertEquals(
            ZonedDateTime.of(today, LocalTime.of(13, 0), kyiv).toInstant().toEpochMilli() + 5_000L,
            alarms.nextWidgetTransitionEpochMs
        )
    }

    @Test
    fun quietHoursCovers23To07() {
        assertTrue(ScheduleChangeDetector.isInQuietHours(23))
        assertTrue(ScheduleChangeDetector.isInQuietHours(0))
        assertTrue(ScheduleChangeDetector.isInQuietHours(6))
        assertFalse(ScheduleChangeDetector.isInQuietHours(7))
        assertFalse(ScheduleChangeDetector.isInQuietHours(15))
        assertFalse(ScheduleChangeDetector.isInQuietHours(22))
    }
}
