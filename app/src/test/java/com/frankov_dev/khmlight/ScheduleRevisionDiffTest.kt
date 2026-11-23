package com.frankov_dev.khmlight

import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.QueueSchedule
import com.frankov_dev.khmlight.data.ScheduleRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleRevisionDiffTest {

    @Test
    fun returnsNullWhenQueueDidNotChangeBetweenRevisions() {
        val q11 = QueueSchedule(
            queueId = "1.1",
            intervals = listOf(OutageInterval("10:00", "15:00")),
            totalHoursOff = 5.0
        )
        val day = DaySchedule(
            date = "2026-11-23",
            operationalMessage = "Оновлено з 11:00",
            queues = mapOf("1.1" to q11),
            previousSchedules = listOf(
                ScheduleRevision(
                    operationalMessage = "Початковий графік",
                    queues = mapOf("1.1" to q11)
                )
            )
        )
        assertNull(day.computeQueueDiff("1.1"))
    }

    @Test
    fun computesPositiveAndNegativeHourDeltaAccurately() {
        val day = DaySchedule(
            date = "2026-11-23",
            operationalMessage = "Оновлено з 11:00",
            queues = mapOf(
                "3.1" to QueueSchedule(
                    "3.1",
                    listOf(OutageInterval("11:00", "13:00"), OutageInterval("15:00", "20:00")),
                    7.0
                ),
                "5.2" to QueueSchedule(
                    "5.2",
                    listOf(OutageInterval("07:00", "10:00"), OutageInterval("13:00", "17:00")),
                    7.0
                )
            ),
            previousSchedules = listOf(
                ScheduleRevision(
                    operationalMessage = "Початковий графік",
                    queues = mapOf(
                        "3.1" to QueueSchedule("3.1", listOf(OutageInterval("15:00", "20:00")), 5.0),
                        "5.2" to QueueSchedule(
                            "5.2",
                            listOf(OutageInterval("06:00", "10:00"), OutageInterval("13:00", "17:00")),
                            8.0
                        )
                    )
                )
            )
        )

        val diff31 = day.computeQueueDiff("3.1")
        assertNotNull(diff31)
        assertEquals(2.0, diff31!!.deltaHours, 0.01)
        assertEquals("+2 год", diff31.formatDeltaShort())
        assertEquals("Оновлено · +2 год", diff31.formatBadgeText())
        assertEquals(1, diff31.previousVersions.size)

        val diff52 = day.computeQueueDiff("5.2")
        assertNotNull(diff52)
        assertEquals(-1.0, diff52!!.deltaHours, 0.01)
        assertEquals("−1 год", diff52.formatDeltaShort())
        assertEquals("Оновлено · −1 год", diff52.formatBadgeText())
    }

    @Test
    fun formatDeltaShortRoundsNearIntegerWithoutTrailingZero() {
        val qCurr = QueueSchedule("1.1", listOf(OutageInterval("10:00", "14:00")), 3.98)
        val qPrev = QueueSchedule("1.1", listOf(OutageInterval("10:00", "12:00")), 2.0)
        val day = DaySchedule(
            date = "2026-11-23",
            operationalMessage = null,
            queues = mapOf("1.1" to qCurr),
            previousSchedules = listOf(ScheduleRevision(null, mapOf("1.1" to qPrev)))
        )
        val diff = day.computeQueueDiff("1.1")
        assertNotNull(diff)
        assertEquals("+2 год", diff!!.formatDeltaShort())
    }
}
