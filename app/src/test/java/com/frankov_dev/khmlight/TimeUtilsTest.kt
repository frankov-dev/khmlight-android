package com.frankov_dev.khmlight

import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeUtilsTest {

    @Test
    fun intervalParsesEndOfDay2400() {
        val interval = OutageInterval("20:00", "24:00")
        assertEquals(1200, interval.startMinutes)
        assertEquals(1440, interval.endMinutes)
        assertEquals(240, interval.durationMinutes)
        assertTrue(interval.containsMinute(1200))
        assertTrue(interval.containsMinute(1439))
        assertFalse(interval.containsMinute(1199))
    }

    @Test
    fun computeLiveStatusDuringOutage() {
        val intervals = listOf(
            OutageInterval("06:00", "10:00"),
            OutageInterval("16:00", "20:00")
        )
        val status = TimeUtils.computeLiveStatus(intervals, nowMinute = 7 * 60 + 30)
        assertTrue(status.isPowerOff)
        assertEquals("10:00", status.nextChangeTime)
        assertEquals(150, status.minutesUntilChange)
    }

    @Test
    fun computeLiveStatusBeforeNextOutage() {
        val intervals = listOf(
            OutageInterval("06:00", "10:00"),
            OutageInterval("16:00", "20:00")
        )
        val status = TimeUtils.computeLiveStatus(intervals, nowMinute = 14 * 60 + 15)
        assertFalse(status.isPowerOff)
        assertEquals("16:00", status.nextChangeTime)
        assertEquals(105, status.minutesUntilChange)
    }

    @Test
    fun computeLiveStatusAfterAllOutages() {
        val intervals = listOf(
            OutageInterval("06:00", "10:00")
        )
        val status = TimeUtils.computeLiveStatus(intervals, nowMinute = 21 * 60)
        assertFalse(status.isPowerOff)
        assertNull(status.nextChangeTime)
        assertNull(status.minutesUntilChange)
    }

    @Test
    fun filterCurrentAndFutureDatesExcludesPastDays() {
        val today = LocalDate.of(2026, 11, 21)
        val dates = listOf("2026-11-22", "2026-11-21", "2026-11-20", "2026-11-19")
        val result = TimeUtils.filterCurrentAndFutureDates(dates, today = today)
        assertEquals(listOf("2026-11-21", "2026-11-22"), result)
    }

    @Test
    fun filterCurrentAndFutureDatesFallsBackToLatestIfAllPast() {
        val today = LocalDate.of(2026, 11, 25)
        val dates = listOf("2026-11-21", "2026-11-20")
        val result = TimeUtils.filterCurrentAndFutureDates(dates, today = today)
        assertEquals(listOf("2026-11-21"), result)
    }

    @Test
    fun formatShortDayButtonFormatsTodayAndTomorrow() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals("Сьогодні, 21 лист.", TimeUtils.formatShortDayButton("2026-11-21", today))
        assertEquals("Завтра, 22 лист.", TimeUtils.formatShortDayButton("2026-11-22", today))
    }

    @Test
    fun formatHelpersWorkCorrectly() {
        assertEquals("8 год", TimeUtils.formatHours(8.0))
        assertEquals("8.5 год", TimeUtils.formatHours(8.5))
        assertEquals("2 год 30 хв", TimeUtils.formatDurationMinutes(150))
        assertEquals("45 хв", TimeUtils.formatDurationMinutes(45))
    }
}
