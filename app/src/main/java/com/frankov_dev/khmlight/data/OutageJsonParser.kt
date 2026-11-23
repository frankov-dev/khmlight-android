package com.frankov_dev.khmlight.data

import org.json.JSONArray
import org.json.JSONObject

object OutageJsonParser {

    fun parseStatusJson(rawJson: String): FeedStatus {
        val root = JSONObject(rawJson)
        val datesArr = root.optJSONArray("available_dates") ?: JSONArray()
        val dates = mutableListOf<String>()
        for (i in 0 until datesArr.length()) {
            val d = datesArr.optString(i, "").trim()
            if (d.isNotEmpty()) dates.add(d)
        }
        return FeedStatus(
            source = root.optString("source", ""),
            lastUpdated = root.optString("last_updated", ""),
            scheduleHash = root.optString("schedule_hash", "").trim(),
            latestDate = optNullableString(root, "latest_date"),
            todayDate = optNullableString(root, "today_date"),
            tomorrowDate = optNullableString(root, "tomorrow_date"),
            hasTomorrow = root.optBoolean("has_tomorrow", false),
            availableDates = dates
        )
    }

    /**
     * Parses single-day convenience files (`today.json`, `tomorrow.json`, `latest.json`, `schedules/YYYY-MM-DD.json`).
     * Returns null if `available == false` (e.g., `tomorrow.json` before publication) or if queues are missing.
     */
    fun parseSingleDayJson(rawJson: String): Pair<DaySchedule?, String?> {
        val root = JSONObject(rawJson)
        val hash = optNullableString(root, "schedule_hash")
        if (root.has("available") && !root.optBoolean("available", true)) {
            return null to hash
        }
        if (!root.has("date") || !root.has("queues")) {
            return null to hash
        }
        val day = parseDayObject(root)
        return (if (day.date.isNotBlank() && day.queues.isNotEmpty()) day else null) to hash
    }

    fun parseOutagesJson(rawJson: String): OutagesFeed {
        val root = JSONObject(rawJson)
        val source = root.optString("source", "")
        val lastUpdated = root.optString("last_updated", "")
        val scheduleHash = optNullableString(root, "schedule_hash")

        // Support both outages.json (multi-day) and latest.json/today.json (single-day)
        if (!root.has("schedules") && root.has("date") && root.has("queues")) {
            val day = parseDayObject(root)
            return OutagesFeed(
                source = source,
                lastUpdated = lastUpdated,
                availableDates = if (day.date.isNotBlank()) listOf(day.date) else emptyList(),
                schedules = if (day.date.isNotBlank()) mapOf(day.date to day) else emptyMap(),
                scheduleHash = scheduleHash
            )
        }

        val datesArray = root.optJSONArray("available_dates") ?: JSONArray()
        val parsedDates = mutableListOf<String>()
        for (i in 0 until datesArray.length()) {
            val d = datesArray.optString(i)
            if (d.isNotBlank()) parsedDates.add(d)
        }

        val schedulesObj = root.optJSONObject("schedules") ?: JSONObject()
        val schedulesMap = linkedMapOf<String, DaySchedule>()
        val keys = schedulesObj.keys()
        while (keys.hasNext()) {
            val dateKey = keys.next()
            val dayObj = schedulesObj.optJSONObject(dateKey) ?: continue
            val day = parseDayObject(dayObj, fallbackDate = dateKey)
            schedulesMap[day.date] = day
        }

        val availableDates = if (parsedDates.isNotEmpty()) {
            parsedDates.filter { schedulesMap.containsKey(it) }
        } else {
            schedulesMap.keys.sortedDescending()
        }

        return OutagesFeed(
            source = source,
            lastUpdated = lastUpdated,
            availableDates = availableDates,
            schedules = schedulesMap,
            scheduleHash = scheduleHash
        )
    }

    private fun parseDayObject(obj: JSONObject, fallbackDate: String = ""): DaySchedule {
        val date = obj.optString("date", fallbackDate).ifBlank { fallbackDate }
        val message = optNullableString(obj, "operational_message")
        val queuesMap = parseQueuesObject(obj.optJSONObject("queues"))

        val revisions = mutableListOf<ScheduleRevision>()
        val prevArr = obj.optJSONArray("previous_schedules")
        if (prevArr != null) {
            for (i in 0 until prevArr.length()) {
                val revObj = prevArr.optJSONObject(i) ?: continue
                val revMsg = optNullableString(revObj, "operational_message")
                val revQueues = parseQueuesObject(revObj.optJSONObject("queues"))
                if (revQueues.isNotEmpty()) {
                    revisions.add(
                        ScheduleRevision(
                            operationalMessage = revMsg,
                            queues = revQueues
                        )
                    )
                }
            }
        }

        return DaySchedule(
            date = date,
            operationalMessage = message,
            queues = queuesMap,
            previousSchedules = revisions
        )
    }

    private fun parseQueuesObject(queuesObj: JSONObject?): Map<String, QueueSchedule> {
        if (queuesObj == null) return emptyMap()
        val queuesMap = linkedMapOf<String, QueueSchedule>()
        val queueIds = mutableListOf<String>()
        val qKeys = queuesObj.keys()
        while (qKeys.hasNext()) {
            queueIds.add(qKeys.next())
        }
        queueIds.sort()

        for (queueId in queueIds) {
            val qObj = queuesObj.optJSONObject(queueId) ?: continue
            val intervalsArr = qObj.optJSONArray("intervals") ?: JSONArray()
            val intervals = mutableListOf<OutageInterval>()
            for (i in 0 until intervalsArr.length()) {
                val iObj = intervalsArr.optJSONObject(i) ?: continue
                val start = iObj.optString("start", "")
                val end = iObj.optString("end", "")
                val type = iObj.optString("type", "base")
                if (start.isNotBlank() && end.isNotBlank()) {
                    intervals.add(OutageInterval(start = start, end = end, type = type))
                }
            }
            val totalHours = qObj.optDouble("total_hours_off", 0.0)
            queuesMap[queueId] = QueueSchedule(
                queueId = queueId,
                intervals = intervals.sortedBy { it.startMinutes },
                totalHoursOff = totalHours
            )
        }
        return queuesMap
    }

    private fun optNullableString(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key, "").takeIf { it.isNotBlank() }
    }
}
