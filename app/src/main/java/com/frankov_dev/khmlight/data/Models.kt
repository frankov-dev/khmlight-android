package com.frankov_dev.khmlight.data

data class OutageInterval(
    val start: String,
    val end: String,
    val type: String = "base"
) {
    val startMinutes: Int get() = toMinutes(start)
    val endMinutes: Int get() = toMinutes(end)
    val durationMinutes: Int get() = (endMinutes - startMinutes).coerceAtLeast(0)

    fun containsMinute(minuteOfDay: Int): Boolean {
        return minuteOfDay in startMinutes until endMinutes
    }

    companion object {
        fun toMinutes(time: String): Int {
            val parts = time.trim().split(":")
            if (parts.size != 2) return 0
            val h = parts[0].toIntOrNull() ?: return 0
            val m = parts[1].toIntOrNull() ?: return 0
            return (h * 60 + m).coerceIn(0, 1440)
        }
    }
}

data class QueueSchedule(
    val queueId: String,
    val intervals: List<OutageInterval>,
    val totalHoursOff: Double
)

data class DaySchedule(
    val date: String,
    val operationalMessage: String?,
    val queues: Map<String, QueueSchedule>
)

data class OutagesFeed(
    val source: String,
    val lastUpdated: String,
    val availableDates: List<String>,
    val schedules: Map<String, DaySchedule>
) {
    val latestDate: String? get() = availableDates.firstOrNull()
}

data class LivePowerStatus(
    val isPowerOff: Boolean,
    val currentInterval: OutageInterval?,
    val nextChangeTime: String?,
    val minutesUntilChange: Int?
)

object QueueConstants {
    val ALL_QUEUES = listOf(
        "1.1", "1.2",
        "2.1", "2.2",
        "3.1", "3.2",
        "4.1", "4.2",
        "5.1", "5.2",
        "6.1", "6.2"
    )
}
