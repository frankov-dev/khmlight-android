package com.frankov_dev.khmlight.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences("khmlight_prefs", Context.MODE_PRIVATE)

    fun getSelectedQueue(): String? = prefs.getString(KEY_SELECTED_QUEUE, null)

    fun setSelectedQueue(queueId: String) {
        val prev = getSelectedQueue()
        prefs.edit().apply {
            putString(KEY_SELECTED_QUEUE, queueId)
            if (prev != queueId) {
                remove(KEY_LAST_NOTIFIED_TODAY_SIG)
                remove(KEY_LAST_NOTIFIED_TOMORROW_SIG)
            }
        }.apply()
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

    fun getLastServerUpdatedIso(): String? = prefs.getString(KEY_LAST_SERVER_UPDATED, null)

    fun setLastServerUpdatedIso(iso: String?) {
        prefs.edit().apply {
            if (iso.isNullOrBlank()) remove(KEY_LAST_SERVER_UPDATED)
            else putString(KEY_LAST_SERVER_UPDATED, iso)
        }.apply()
    }

    // Notification preferences
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

    fun getLastNotifiedTodaySignature(): String? =
        prefs.getString(KEY_LAST_NOTIFIED_TODAY_SIG, null)

    fun setLastNotifiedTodaySignature(sig: String) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_TODAY_SIG, sig).apply()
    }

    fun getLastNotifiedTomorrowSignature(): String? =
        prefs.getString(KEY_LAST_NOTIFIED_TOMORROW_SIG, null)

    fun setLastNotifiedTomorrowSignature(sig: String) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_TOMORROW_SIG, sig).apply()
    }

    companion object {
        private const val KEY_SELECTED_QUEUE = "selected_queue"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors_enabled"
        private const val KEY_HISTORY_ONLY_MY = "history_only_my"
        private const val KEY_LAST_APP_SYNC_MS = "last_app_sync_ms"
        private const val KEY_LAST_ETAG = "last_outages_etag"
        private const val KEY_LAST_SERVER_UPDATED = "last_server_updated_iso"

        private const val KEY_NOTIFY_OUTAGE = "notify_outage_enabled"
        private const val KEY_NOTIFY_POWER_ON = "notify_power_on_enabled"
        private const val KEY_NOTIFY_SCHEDULE_CHANGE = "notify_schedule_change_enabled"
        private const val KEY_LEAD_TIME_MINUTES = "notify_lead_time_minutes"
        private const val KEY_QUIET_HOURS = "notify_quiet_hours_enabled"
        private const val KEY_NOTIF_PERM_REQUESTED = "notif_perm_requested"
        private const val KEY_LAST_NOTIFIED_TODAY_SIG = "last_notified_today_sig"
        private const val KEY_LAST_NOTIFIED_TOMORROW_SIG = "last_notified_tomorrow_sig"
    }
}
