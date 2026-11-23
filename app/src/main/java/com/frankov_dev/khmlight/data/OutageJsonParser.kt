package com.frankov_dev.khmlight.data

import org.json.JSONArray
import org.json.JSONObject

object OutageJsonParser {

    fun parseOutagesJson(rawJson: String): OutagesFeed {
        val root = JSONObject(rawJson)
        val source = root.optString("source", "")
        val lastUpdated = root.optString("last_updated", "")

        // Support both outages.json (multi-day) and latest.json (single-day)
        if (!root.has("schedules") && root.has("date") && root.has("queues")) {
            val day = parseDayObject(root)
            return OutagesFeed(
                source = source,
                lastUpdated = lastUpdated,
                availableDates = listOf(day.date),
                schedules = mapOf(day.date to day)
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
            schedules = schedulesMap
        )
    }

    private fun parseDayObject(obj: JSONObject, fallbackDate: String = ""): DaySchedule {
        val date = obj.optString("date", fallbackDate).ifBlank { fallbackDate }
        val message = if (obj.isNull("operational_message")) {
            null
        } else {
            obj.optString("operational_message", "").takeIf { it.isNotBlank() }
        }

        val queuesObj = obj.optJSONObject("queues") ?: JSONObject()
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

        return DaySchedule(
            date = date,
            operationalMessage = message,
            queues = queuesMap
        )
    }
}
