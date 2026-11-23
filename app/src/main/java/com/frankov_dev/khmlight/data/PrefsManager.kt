package com.frankov_dev.khmlight.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONObject

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("khmlight_prefs", Context.MODE_PRIVATE)

    /**
     * Returns all user saved locations in display order.
     * Automatically migrates legacy single `selected_queue` on first read.
     */
    fun getSavedLocations(): List<SavedLocation> {
        val raw = prefs.getString(KEY_SAVED_LOCATIONS, null)
        if (raw != null) {
            return SavedLocationCodec.decode(raw)
        }

        // One-time seamless migration from v1.0 single queue
        val legacyQueue = prefs.getString(KEY_SELECTED_QUEUE, null)?.trim()
        if (!legacyQueue.isNullOrEmpty()) {
            val migrated = listOf(
                SavedLocation(
                    id = "loc_migrated",
                    name = LocationIcon.HOME.titleUk,
                    queueId = legacyQueue,
                    icon = LocationIcon.HOME,
                    notifyEnabled = true
                )
            )
            saveLocations(migrated)
            return migrated
        }
        return emptyList()
    }

    fun saveLocations(locations: List<SavedLocation>) {
        val trimmed = locations.take(SavedLocationCodec.MAX_LOCATIONS)
        val primaryQueue = trimmed.firstOrNull()?.queueId
        prefs.edit().apply {
            putString(KEY_SAVED_LOCATIONS, SavedLocationCodec.encode(trimmed))
            if (primaryQueue != null) {
                putString(KEY_SELECTED_QUEUE, primaryQueue)
            } else {
                remove(KEY_SELECTED_QUEUE)
            }
        }.apply()
    }

    fun upsertLocation(location: SavedLocation): List<SavedLocation> {
        val current = getSavedLocations().toMutableList()
        val idx = current.indexOfFirst { it.id == location.id }
        if (idx >= 0) {
            val prevQueue = current[idx].queueId
            current[idx] = location
            if (prevQueue != location.queueId) {
                clearSignaturesForQueue(location.queueId)
            }
        } else if (current.size < SavedLocationCodec.MAX_LOCATIONS) {
            current.add(location)
        }
        saveLocations(current)
        return current
    }

    fun deleteLocation(locationId: String): List<SavedLocation> {
        val updated = getSavedLocations().filterNot { it.id == locationId }
        saveLocations(updated)
        return updated
    }

    fun setLocationNotifyEnabled(locationId: String, enabled: Boolean): List<SavedLocation> {
        val updated = getSavedLocations().map { loc ->
            if (loc.id == locationId) loc.copy(notifyEnabled = enabled) else loc
        }
        saveLocations(updated)
        return updated
    }

    /**
     * Backward-compatible accessor: returns primary (first) saved location's queueId.
     */
    fun getSelectedQueue(): String? = getSavedLocations().firstOrNull()?.queueId

    /**
     * Backward-compatible setter: updates primary location or creates one if empty.
     */
    fun setSelectedQueue(queueId: String) {
        val current = getSavedLocations().toMutableList()
        if (current.isEmpty()) {
            current.add(
                SavedLocation(
                    name = LocationIcon.HOME.titleUk,
                    queueId = queueId,
                    icon = LocationIcon.HOME,
                    notifyEnabled = true
                )
            )
        } else {
            val first = current[0]
            if (first.queueId != queueId) {
                current[0] = first.copy(queueId = queueId)
                clearSignaturesForQueue(queueId)
            }
        }
        saveLocations(current)
    }

    fun getThemeMode(): Int =
        prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun isDynamicColorsEnabled(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLORS, true)

    fun setDynamicColorsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply()
    }

    fun isHistoryOnlyMyQueue(): Boolean = prefs.getBoolean(KEY_HISTORY_ONLY_MY, true)

    fun setHistoryOnlyMyQueue(onlyMy: Boolean) {
        prefs.edit().putBoolean(KEY_HISTORY_ONLY_MY, onlyMy).apply()
    }

    /**
     * Returns saved history filter location ID, or null if "All Queues" is selected.
     */
    fun getHistoryFilterLocationId(): String? {
        val raw = prefs.getString(KEY_HISTORY_FILTER_LOC_ID, null)
        if (raw == HISTORY_FILTER_ALL) return null
        val locations = getSavedLocations()
        if (raw != null && locations.any { it.id == raw }) return raw
        return if (isHistoryOnlyMyQueue()) locations.firstOrNull()?.id else null
    }

    fun setHistoryFilterLocationId(locationId: String?) {
        prefs.edit().apply {
            if (locationId == null) {
                putString(KEY_HISTORY_FILTER_LOC_ID, HISTORY_FILTER_ALL)
                putBoolean(KEY_HISTORY_ONLY_MY, false)
            } else {
                putString(KEY_HISTORY_FILTER_LOC_ID, locationId)
                putBoolean(KEY_HISTORY_ONLY_MY, true)
            }
        }.apply()
    }

    fun getLastAppSyncMs(): Long? {
        val v = prefs.getLong(KEY_LAST_APP_SYNC_MS, -1L)
        return if (v > 0L) v else null
    }

    fun setLastAppSyncMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_APP_SYNC_MS, epochMs).apply()
    }

    fun getLastEtag(): String? = prefs.getString(KEY_LAST_ETAG, null)

    fun setLastEtag(etag: String?) {
        prefs.edit().apply {
            if (etag.isNullOrBlank()) remove(KEY_LAST_ETAG) else putString(KEY_LAST_ETAG, etag)
        }.apply()
    }

    fun getLastStatusEtag(): String? = prefs.getString(KEY_LAST_STATUS_ETAG, null)

    fun setLastStatusEtag(etag: String?) {
        prefs.edit().apply {
            if (etag.isNullOrBlank()) remove(KEY_LAST_STATUS_ETAG) else putString(KEY_LAST_STATUS_ETAG, etag)
        }.apply()
    }

    fun getLastScheduleHash(): String? = prefs.getString(KEY_LAST_SCHEDULE_HASH, null)

    fun setLastScheduleHash(hash: String?) {
        prefs.edit().apply {
            if (hash.isNullOrBlank()) remove(KEY_LAST_SCHEDULE_HASH) else putString(KEY_LAST_SCHEDULE_HASH, hash)
        }.apply()
    }

    fun getLastServerUpdatedIso(): String? = prefs.getString(KEY_LAST_SERVER_UPDATED, null)

    fun setLastServerUpdatedIso(iso: String?) {
        prefs.edit().apply {
            if (iso.isNullOrBlank()) remove(KEY_LAST_SERVER_UPDATED)
            else putString(KEY_LAST_SERVER_UPDATED, iso)
        }.apply()
    }

    fun getLastUpdateCheckMs(): Long? {
        val v = prefs.getLong(KEY_LAST_UPDATE_CHECK_MS, -1L)
        return if (v > 0L) v else null
    }

    fun setLastUpdateCheckMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_MS, epochMs).apply()
    }

    fun getSkippedUpdateVersion(): String? = prefs.getString(KEY_SKIPPED_UPDATE_VERSION, null)

    fun setSkippedUpdateVersion(version: String?) {
        prefs.edit().apply {
            if (version.isNullOrBlank()) remove(KEY_SKIPPED_UPDATE_VERSION)
            else putString(KEY_SKIPPED_UPDATE_VERSION, version)
        }.apply()
    }

    // Global Notification preferences
    fun isNotifyOutageEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_OUTAGE, true)

    fun setNotifyOutageEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_OUTAGE, enabled).apply()
    }

    fun isNotifyPowerOnEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_POWER_ON, true)

    fun setNotifyPowerOnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_POWER_ON, enabled).apply()
    }

    fun isNotifyScheduleChangeEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_SCHEDULE_CHANGE, true)

    fun setNotifyScheduleChangeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_SCHEDULE_CHANGE, enabled).apply()
    }

    fun getLeadTimeMinutes(): Int = prefs.getInt(KEY_LEAD_TIME_MINUTES, 15)

    fun setLeadTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_LEAD_TIME_MINUTES, minutes).apply()
    }

    fun isQuietHoursEnabled(): Boolean = prefs.getBoolean(KEY_QUIET_HOURS, false)

    fun setQuietHoursEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUIET_HOURS, enabled).apply()
    }

    fun hasRequestedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_NOTIF_PERM_REQUESTED, false)

    fun markNotificationPermissionRequested() {
        prefs.edit().putBoolean(KEY_NOTIF_PERM_REQUESTED, true).apply()
    }

    // Per-queue change signatures for multi-location schedule change notifications
    fun getLastNotifiedTodaySignature(queueId: String): String? {
        val map = readSigMap(KEY_LAST_NOTIFIED_TODAY_MAP)
        return map[queueId] ?: prefs.getString(KEY_LAST_NOTIFIED_TODAY_SIG, null)
            ?.takeIf { it.contains("|$queueId|") }
    }

    fun setLastNotifiedTodaySignature(queueId: String, sig: String) {
        val map = readSigMap(KEY_LAST_NOTIFIED_TODAY_MAP)
        map[queueId] = sig
        writeSigMap(KEY_LAST_NOTIFIED_TODAY_MAP, map)
    }

    fun getLastNotifiedTomorrowSignature(queueId: String): String? {
        val map = readSigMap(KEY_LAST_NOTIFIED_TOMORROW_MAP)
        return map[queueId] ?: prefs.getString(KEY_LAST_NOTIFIED_TOMORROW_SIG, null)
            ?.takeIf { it.contains("|$queueId|") }
    }

    fun setLastNotifiedTomorrowSignature(queueId: String, sig: String) {
        val map = readSigMap(KEY_LAST_NOTIFIED_TOMORROW_MAP)
        map[queueId] = sig
        writeSigMap(KEY_LAST_NOTIFIED_TOMORROW_MAP, map)
    }

    private fun clearSignaturesForQueue(queueId: String) {
        val todayMap = readSigMap(KEY_LAST_NOTIFIED_TODAY_MAP)
        if (todayMap.remove(queueId) != null) writeSigMap(KEY_LAST_NOTIFIED_TODAY_MAP, todayMap)
        val tomorrowMap = readSigMap(KEY_LAST_NOTIFIED_TOMORROW_MAP)
        if (tomorrowMap.remove(queueId) != null) writeSigMap(KEY_LAST_NOTIFIED_TOMORROW_MAP, tomorrowMap)
    }

    private fun readSigMap(key: String): MutableMap<String, String> {
        val raw = prefs.getString(key, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optString(k, "")
            }
            result
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun writeSigMap(key: String, map: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v)
        prefs.edit().putString(key, obj.toString()).apply()
    }

    companion object {
        private const val KEY_SELECTED_QUEUE = "selected_queue"
        private const val KEY_SAVED_LOCATIONS = "saved_locations_v1"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors_enabled"
        private const val KEY_HISTORY_ONLY_MY = "history_only_my"
        private const val KEY_HISTORY_FILTER_LOC_ID = "history_filter_loc_id"
        private const val HISTORY_FILTER_ALL = "__ALL__"
        private const val KEY_LAST_APP_SYNC_MS = "last_app_sync_ms"
        private const val KEY_LAST_ETAG = "last_outages_etag"
        private const val KEY_LAST_STATUS_ETAG = "last_status_etag"
        private const val KEY_LAST_SCHEDULE_HASH = "last_schedule_hash"
        private const val KEY_LAST_SERVER_UPDATED = "last_server_updated_iso"
        private const val KEY_LAST_UPDATE_CHECK_MS = "last_update_check_ms"
        private const val KEY_SKIPPED_UPDATE_VERSION = "skipped_update_version"

        private const val KEY_NOTIFY_OUTAGE = "notify_outage_enabled"
        private const val KEY_NOTIFY_POWER_ON = "notify_power_on_enabled"
        private const val KEY_NOTIFY_SCHEDULE_CHANGE = "notify_schedule_change_enabled"
        private const val KEY_LEAD_TIME_MINUTES = "notify_lead_time_minutes"
        private const val KEY_QUIET_HOURS = "notify_quiet_hours_enabled"
        private const val KEY_NOTIF_PERM_REQUESTED = "notif_perm_requested"
        private const val KEY_LAST_NOTIFIED_TODAY_SIG = "last_notified_today_sig"
        private const val KEY_LAST_NOTIFIED_TOMORROW_SIG = "last_notified_tomorrow_sig"
        private const val KEY_LAST_NOTIFIED_TODAY_MAP = "last_notified_today_map_v1"
        private const val KEY_LAST_NOTIFIED_TOMORROW_MAP = "last_notified_tomorrow_map_v1"
    }
}
