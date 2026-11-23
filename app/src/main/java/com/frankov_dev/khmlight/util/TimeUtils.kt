package com.frankov_dev.khmlight.util

import com.frankov_dev.khmlight.data.LivePowerStatus
import com.frankov_dev.khmlight.data.OutageInterval
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {

    val kyivZone: ZoneId by lazy {
        try {
            ZoneId.of("Europe/Kyiv")
        } catch (_: Exception) {
            try {
                ZoneId.of("Europe/Kiev")
            } catch (_: Exception) {
                ZoneId.systemDefault()
            }
        }
    }

    private val UK_MONTHS_GENITIVE = arrayOf(
        "",
        "січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
    )

    private val UK_MONTHS_SHORT = arrayOf(
        "",
        "січ.", "лют.", "бер.", "квіт.", "трав.", "черв.",
        "лип.", "серп.", "вер.", "жовт.", "лист.", "груд."
    )

    private val UK_DAYS_SHORT = arrayOf(
        "", "пн", "вт", "ср", "чт", "пт", "сб", "нд"
    )

    fun nowKyiv(): ZonedDateTime = ZonedDateTime.now(kyivZone)

    fun todayDateString(): String = nowKyiv().toLocalDate().toString()

    fun tomorrowDateString(): String = nowKyiv().toLocalDate().plusDays(1).toString()

    fun currentMinuteOfDay(): Int {
        val now = nowKyiv()
        return now.hour * 60 + now.minute
    }

    fun isToday(dateStr: String): Boolean = dateStr == todayDateString()

    /**
     * Returns only today and future dates (e.g. today & tomorrow), sorted ascending (today first).
     * Falls back to the single most recent date only if all dates are in the past.
     */
    fun filterCurrentAndFutureDates(
        availableDates: List<String>,
        today: LocalDate = nowKyiv().toLocalDate()
    ): List<String> {
        val upcoming = availableDates
            .mapNotNull { str -> parseLocalDate(str)?.let { str to it } }
            .filter { (_, date) -> !date.isBefore(today) }
            .sortedBy { (_, date) -> date }
            .map { (str, _) -> str }

        if (upcoming.isNotEmpty()) return upcoming
        return availableDates.firstOrNull()?.let { listOf(it) } ?: emptyList()
    }

    fun formatShortDayButton(
        dateStr: String,
        today: LocalDate = nowKyiv().toLocalDate()
    ): String {
        val date = parseLocalDate(dateStr) ?: return dateStr
        val shortDate = "${date.dayOfMonth} ${UK_MONTHS_SHORT[date.monthValue]}"
        return when (date) {
            today -> "Сьогодні, $shortDate"
            today.plusDays(1) -> "Завтра, $shortDate"
            today.minusDays(1) -> "Вчора, $shortDate"
            else -> shortDate
        }
    }

    fun formatDateChip(dateStr: String): String {
        val date = parseLocalDate(dateStr) ?: return dateStr
        val today = nowKyiv().toLocalDate()
        return when (date) {
            today -> "Сьогодні"
            today.plusDays(1) -> "Завтра"
            today.minusDays(1) -> "Вчора"
            else -> "${date.dayOfMonth} ${UK_MONTHS_GENITIVE[date.monthValue]}"
        }
    }

    fun formatDateHeading(dateStr: String): String {
        val date = parseLocalDate(dateStr) ?: return dateStr
        val today = nowKyiv().toLocalDate()
        val dayMonth = "${date.dayOfMonth} ${UK_MONTHS_GENITIVE[date.monthValue]}"
        val dow = UK_DAYS_SHORT[date.dayOfWeek.value]
        return when (date) {
            today -> "Сьогодні, $dayMonth"
            today.plusDays(1) -> "Завтра, $dayMonth"
            today.minusDays(1) -> "Вчора, $dayMonth"
            else -> "$dayMonth ($dow)"
        }
    }

    fun formatLastUpdated(isoString: String): String {
        if (isoString.isBlank()) return "—"
        return try {
            val zdt = OffsetDateTime.parse(isoString).atZoneSameInstant(kyivZone)
            formatZonedTimestamp(zdt)
        } catch (_: Exception) {
            isoString.take(16).replace("T", " ")
        }
    }

    fun formatEpochMs(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "—"
        return try {
            val zdt = Instant.ofEpochMilli(epochMs).atZone(kyivZone)
            formatZonedTimestamp(zdt)
        } catch (_: Exception) {
            "—"
        }
    }

    private fun formatZonedTimestamp(zdt: ZonedDateTime): String {
        val today = nowKyiv().toLocalDate()
        return if (zdt.toLocalDate() == today) {
            zdt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        } else {
            zdt.format(DateTimeFormatter.ofPattern("dd.MM о HH:mm", Locale.US))
        }
    }

    fun formatHours(hours: Double): String {
        val rounded = Math.round(hours * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            "${rounded.toInt()} год"
        } else {
            String.format(Locale.US, "%.1f год", rounded)
        }
    }

    fun formatDurationMinutes(minutes: Int): String {
        if (minutes <= 0) return "0 хв"
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h год $m хв"
            h > 0 -> "$h год"
            else -> "$m хв"
        }
    }

    fun computeLiveStatus(intervals: List<OutageInterval>, nowMinute: Int = currentMinuteOfDay()): LivePowerStatus {
        val sorted = intervals.sortedBy { it.startMinutes }
        val active = sorted.firstOrNull { it.containsMinute(nowMinute) }
        if (active != null) {
            val remaining = (active.endMinutes - nowMinute).coerceAtLeast(0)
            return LivePowerStatus(
                isPowerOff = true,
                currentInterval = active,
                nextChangeTime = active.end,
                minutesUntilChange = remaining
            )
        }

        val next = sorted.firstOrNull { it.startMinutes > nowMinute }
        return if (next != null) {
            LivePowerStatus(
                isPowerOff = false,
                currentInterval = null,
                nextChangeTime = next.start,
                minutesUntilChange = (next.startMinutes - nowMinute).coerceAtLeast(0)
            )
        } else {
            LivePowerStatus(
                isPowerOff = false,
                currentInterval = null,
                nextChangeTime = null,
                minutesUntilChange = null
            )
        }
    }

    private fun parseLocalDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }
}
