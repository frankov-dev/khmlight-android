package com.frankov_dev.khmlight.data

import kotlin.math.abs
import kotlin.math.round

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
) {
    fun intervalsSignature(): String = intervals
        .sortedBy { it.startMinutes }
        .joinToString(",") { "${it.start}-${it.end}" }
}

data class ScheduleRevision(
    val operationalMessage: String?,
    val queues: Map<String, QueueSchedule>
)

data class QueueRevisionEntry(
    val operationalMessage: String?,
    val schedule: QueueSchedule
)

data class QueueRevisionDiff(
    val queueId: String,
    val currentMessage: String?,
    val current: QueueSchedule,
    val previousVersions: List<QueueRevisionEntry>,
    val deltaHours: Double
) {
    fun formatDeltaShort(): String {
        if (abs(deltaHours) < 0.01) return ""
        val sign = if (deltaHours > 0) "+" else "−"
        val absVal = abs(deltaHours)
        val formatted = if (absVal == absVal.toLong().toDouble()) {
            "${absVal.toLong()} год"
        } else {
            val rounded = round(absVal * 10.0) / 10.0
            "$rounded год"
        }
        return "$sign$formatted"
    }

    fun formatBadgeText(): String {
        val delta = formatDeltaShort()
        return if (delta.isNotEmpty()) "Оновлено · $delta" else "Оновлено"
    }
}

data class DaySchedule(
    val date: String,
    val operationalMessage: String?,
    val queues: Map<String, QueueSchedule>,
    val previousSchedules: List<ScheduleRevision> = emptyList()
) {
    /**
     * Computes a revision diff for [queueId] ONLY if at least one previous revision for this day
     * had different intervals for this queue compared to the current active schedule.
     */
    fun computeQueueDiff(queueId: String): QueueRevisionDiff? {
        val current = queues[queueId] ?: return null
        if (previousSchedules.isEmpty()) return null

        val currentSig = current.intervalsSignature()
        val seenSigs = mutableSetOf(currentSig)
        val distinctPrev = mutableListOf<QueueRevisionEntry>()

        for (rev in previousSchedules) {
            val prevQ = rev.queues[queueId] ?: continue
            val sig = prevQ.intervalsSignature()
            if (seenSigs.add(sig)) {
                distinctPrev.add(
                    QueueRevisionEntry(
                        operationalMessage = rev.operationalMessage,
                        schedule = prevQ
                    )
                )
            }
        }
        if (distinctPrev.isEmpty()) return null

        val delta = round((current.totalHoursOff - distinctPrev.first().schedule.totalHoursOff) * 100.0) / 100.0
        return QueueRevisionDiff(
            queueId = queueId,
            currentMessage = operationalMessage,
            current = current,
            previousVersions = distinctPrev,
            deltaHours = delta
        )
    }
}

data class OutagesFeed(
    val source: String,
    val lastUpdated: String,
    val availableDates: List<String>,
    val schedules: Map<String, DaySchedule>,
    val scheduleHash: String? = null
) {
    val latestDate: String? get() = availableDates.firstOrNull()
}

data class FeedStatus(
    val source: String,
    val lastUpdated: String,
    val scheduleHash: String,
    val latestDate: String?,
    val todayDate: String?,
    val tomorrowDate: String?,
    val hasTomorrow: Boolean,
    val availableDates: List<String>
)

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
