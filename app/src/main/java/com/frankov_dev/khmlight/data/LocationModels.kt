package com.frankov_dev.khmlight.data

import androidx.annotation.DrawableRes
import com.frankov_dev.khmlight.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class LocationIcon(
    val titleUk: String,
    @param:DrawableRes val iconRes: Int
) {
    HOME("Дім", R.drawable.ic_loc_home),
    WORK("Робота", R.drawable.ic_loc_work),
    FAMILY("Батьки", R.drawable.ic_loc_family),
    SCHOOL("Навчання", R.drawable.ic_loc_school),
    COTTAGE("Дача", R.drawable.ic_loc_cottage),
    STORE("Офіс", R.drawable.ic_loc_store),
    GARAGE("Гараж", R.drawable.ic_loc_garage),
    STAR("Інше", R.drawable.ic_loc_star);

    companion object {
        fun fromName(name: String?): LocationIcon {
            return entries.firstOrNull { it.name == name } ?: HOME
        }
    }
}

data class SavedLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val queueId: String,
    val icon: LocationIcon = LocationIcon.HOME,
    val notifyEnabled: Boolean = true
)

data class LocationQueueGroup(
    val queueId: String,
    val locations: List<SavedLocation>
) {
    val primary: SavedLocation get() = locations.first()
    val joinedNames: String get() = locations.joinToString(", ") { it.name }
    val displayTitleWithQueue: String get() = "$joinedNames ($queueId)"
}

fun List<SavedLocation>.groupByQueuePreservingOrder(): List<LocationQueueGroup> {
    val grouped = LinkedHashMap<String, MutableList<SavedLocation>>()
    for (loc in this) {
        grouped.getOrPut(loc.queueId) { mutableListOf() }.add(loc)
    }
    return grouped.map { (queueId, locs) -> LocationQueueGroup(queueId, locs) }
}

object SavedLocationCodec {

    const val MAX_LOCATIONS = 10
    const val MAX_NAME_LENGTH = 20

    fun encode(locations: List<SavedLocation>): String {
        val arr = JSONArray()
        for (loc in locations) {
            val obj = JSONObject()
            obj.put("id", loc.id)
            obj.put("name", loc.name.trim().take(MAX_NAME_LENGTH))
            obj.put("queueId", loc.queueId)
            obj.put("icon", loc.icon.name)
            obj.put("notifyEnabled", loc.notifyEnabled)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<SavedLocation> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = ArrayList<SavedLocation>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val queueId = obj.optString("queueId", "").trim()
                if (queueId.isEmpty()) continue
                val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
                val icon = LocationIcon.fromName(obj.optString("icon", ""))
                val name = obj.optString("name", "").trim().ifBlank { icon.titleUk }.take(MAX_NAME_LENGTH)
                val notifyEnabled = obj.optBoolean("notifyEnabled", true)
                result.add(
                    SavedLocation(
                        id = id,
                        name = name,
                        queueId = queueId,
                        icon = icon,
                        notifyEnabled = notifyEnabled
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Format location names in quotes for notifications:
     * Single: «Дім» (черга 2.1)
     * Multiple on same queue: «Дім», «Гараж» (черга 2.1)
     */
    fun formatQuotedLabel(locations: List<SavedLocation>, queueId: String): String {
        val names = locations
            .filter { it.queueId == queueId }
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (names.isEmpty()) {
            "Черга $queueId"
        } else {
            val quoted = names.joinToString(", ") { "«$it»" }
            "$quoted (черга $queueId)"
        }
    }
}
