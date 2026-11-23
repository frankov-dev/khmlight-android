package com.frankov_dev.khmlight

import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.LocationIcon
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.QueueSchedule
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.widget.BadgeShape
import com.frankov_dev.khmlight.widget.SignalWaveDrawer
import com.frankov_dev.khmlight.widget.WidgetColorPreset
import com.frankov_dev.khmlight.widget.WidgetDataResolver
import com.frankov_dev.khmlight.widget.WidgetPowerMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetDataResolverTest {

    private val today = LocalDate.of(2026, 11, 22)
    private val nowMs = 1_795_300_000_000L

    private fun makeFeed(date: String, intervals: List<OutageInterval>, hours: Double): OutagesFeed {
        val day = DaySchedule(
            date = date,
            operationalMessage = null,
            queues = mapOf("2.1" to QueueSchedule("2.1", intervals, hours))
        )
        return OutagesFeed(
            source = "test",
            lastUpdated = "2026-11-22T12:00:00+02:00",
            availableDates = listOf(date),
            schedules = mapOf(date to day)
        )
    }

    @Test
    fun powerOnUpcomingOutageFormatsTimerAndNextChange() {
        val feed = makeFeed("2026-11-22", listOf(OutageInterval("14:30", "18:00")), 3.5)
        val data = WidgetDataResolver.resolve(
            feed = feed,
            queueId = "2.1",
            lastAppSyncMs = nowMs - 60_000L,
            nowEpochMs = nowMs,
            today = today,
            nowMinute = 13 * 60 + 48
        )
        assertEquals("2.1", data.queueBadge)
        assertEquals("Є світло", data.statusTitle)
        assertEquals("42 хв", data.badgeTimerText)
        assertEquals("До відключення", data.badgeSubtitle)
        assertEquals("14:30", data.nextChangeTime)
        assertEquals(WidgetPowerMood.POWER_ON, data.mood)
        assertTrue(data.hasKnownSchedule)
        assertTrue(data.showLiveTimeMarker)
    }

    @Test
    fun powerOffFormatsUntilPowerOn() {
        val feed = makeFeed("2026-11-22", listOf(OutageInterval("12:00", "16:15")), 4.25)
        val data = WidgetDataResolver.resolve(
            feed = feed,
            queueId = "2.1",
            lastAppSyncMs = nowMs - 60_000L,
            nowEpochMs = nowMs,
            today = today,
            nowMinute = 14 * 60
        )
        assertEquals("Немає світла", data.statusTitle)
        assertEquals("2 год 15 хв", data.badgeTimerText)
        assertEquals("2г 15хв", data.badgeCompactTimerText)
        assertEquals("До включення", data.badgeSubtitle)
        assertEquals("16:15", data.nextChangeTime)
        assertEquals(WidgetPowerMood.POWER_OFF, data.mood)
    }

    @Test
    fun missingFeedMarksUnknownScheduleWithoutFakeWave() {
        val data = WidgetDataResolver.resolve(
            feed = null,
            queueId = "2.1",
            lastAppSyncMs = null,
            nowEpochMs = nowMs,
            today = today,
            nowMinute = 12 * 60
        )
        assertEquals("Дані відсутні", data.statusTitle)
        assertEquals("—", data.badgeTimerText)
        assertEquals("Немає графіка", data.badgeSubtitle)
        assertNull(data.nextChangeTime)
        assertFalse(data.hasKnownSchedule)
    }

    @Test
    fun signalWaveSegmentsCoverFull24HoursProportionally() {
        val segments = SignalWaveDrawer.buildSegments(
            listOf(
                OutageInterval("06:00", "10:00"),
                OutageInterval("14:30", "18:30")
            )
        )
        assertEquals(5, segments.size)
        assertEquals(1440, segments.sumOf { it.durationMinutes })
        assertFalse(segments[0].isOutage)
        assertTrue(segments[1].isOutage)
        assertEquals(240, segments[1].durationMinutes)
        assertTrue(segments[3].isOutage)
        assertEquals(240, segments[3].durationMinutes)
    }

    @Test
    fun fiveShapesAndSevenColorPresetsExist() {
        assertEquals(5, BadgeShape.entries.size)
        assertEquals(listOf("B1", "B2", "B3", "B4", "B5"), BadgeShape.entries.map { it.code })
        assertEquals(7, WidgetColorPreset.entries.size)
    }

    @Test
    fun headerLabelAndIconReflectSavedLocationOrGenericQueue() {
        val loc = SavedLocation(id = "loc1", name = "Дім", queueId = "2.1", icon = LocationIcon.HOME)
        val feed = makeFeed("2026-11-22", listOf(OutageInterval("14:30", "18:00")), 3.5)

        val withLoc = WidgetDataResolver.resolve(
            feed = feed,
            queueId = "2.1",
            lastAppSyncMs = nowMs - 60_000L,
            nowEpochMs = nowMs,
            today = today,
            nowMinute = 13 * 60 + 48,
            location = loc
        )
        assertEquals("Дім (2.1)", withLoc.headerLabel)
        assertEquals(R.drawable.ic_loc_home, withLoc.iconRes)

        val generic = WidgetDataResolver.resolve(
            feed = feed,
            queueId = "2.1",
            lastAppSyncMs = nowMs - 60_000L,
            nowEpochMs = nowMs,
            today = today,
            nowMinute = 13 * 60 + 48,
            location = null
        )
        assertEquals("Черга 2.1", generic.headerLabel)
        assertEquals(R.drawable.ic_bolt, generic.iconRes)
    }
}
