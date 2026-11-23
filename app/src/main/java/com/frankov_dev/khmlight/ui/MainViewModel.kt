package com.frankov_dev.khmlight.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.notifications.OutageAlarmScheduler
import com.frankov_dev.khmlight.notifications.ScheduleChangeDetector
import com.frankov_dev.khmlight.util.TimeUtils
import com.frankov_dev.khmlight.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MainTab {
    MY_QUEUE,
    ALL_QUEUES,
    HISTORY
}

enum class DaySelection {
    TODAY,
    TOMORROW
}

data class MainUiState(
    val feed: OutagesFeed? = null,
    val savedLocations: List<SavedLocation> = emptyList(),
    val activeTab: MainTab = MainTab.MY_QUEUE,
    val selectedDayMyQueue: DaySelection = DaySelection.TODAY,
    val selectedDayAllQueues: DaySelection = DaySelection.TODAY,
    val historyFilterLocationId: String? = null,
    val lastAppSyncMs: Long? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFromCache: Boolean = false,
    val errorMessage: String? = null,
    val clockTick: Long = 0L
) {
    val primaryQueue: String? get() = savedLocations.firstOrNull()?.queueId
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OutageRepository(application)
    private val prefs = PrefsManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            savedLocations = prefs.getSavedLocations(),
            historyFilterLocationId = prefs.getHistoryFilterLocationId(),
            lastAppSyncMs = prefs.getLastAppSyncMs()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadInitial()
        startClockTicker()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                _uiState.update { it.copy(clockTick = it.clockTick + 1) }
            }
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val (cached, syncedMs) = repository.loadCachedFeed()
            if (cached != null) {
                _uiState.update {
                    it.copy(
                        feed = cached,
                        lastAppSyncMs = syncedMs ?: it.lastAppSyncMs,
                        isLoading = false,
                        isFromCache = true
                    )
                }
            }
            refresh(showFullLoader = cached == null)
        }
    }

    fun refresh(showFullLoader: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = showFullLoader && it.feed == null,
                    isRefreshing = !showFullLoader,
                    errorMessage = null
                )
            }
            val result = repository.fetchFeed()
            _uiState.update { current ->
                val newFeed = result.feed ?: current.feed
                current.copy(
                    feed = newFeed,
                    lastAppSyncMs = result.syncedAtMs ?: current.lastAppSyncMs,
                    isLoading = false,
                    isRefreshing = false,
                    isFromCache = result.isFromCache,
                    errorMessage = if (newFeed == null) result.error else null
                )
            }
            if (result.feed != null) {
                seedSeenSignatures(result.feed)
                WidgetUpdater.updateAllWidgets(getApplication(), result.feed)
                OutageAlarmScheduler.schedule(getApplication(), result.feed)
            }
        }
    }

    fun saveLocation(location: SavedLocation) {
        val hadNone = _uiState.value.savedLocations.isEmpty()
        val updated = prefs.upsertLocation(location)
        val filterId = if (hadNone) {
            prefs.setHistoryFilterLocationId(location.id)
            location.id
        } else {
            _uiState.value.historyFilterLocationId
        }
        _uiState.update { it.copy(savedLocations = updated, historyFilterLocationId = filterId) }
        val feed = _uiState.value.feed
        if (feed != null) {
            seedSeenSignatures(feed)
        }
        WidgetUpdater.updateAllWidgets(getApplication(), feed)
        OutageAlarmScheduler.schedule(getApplication(), feed)
    }

    fun deleteLocation(locationId: String) {
        val updated = prefs.deleteLocation(locationId)
        val currentFilter = _uiState.value.historyFilterLocationId
        val nextFilter = if (currentFilter == locationId) {
            val fallback = updated.firstOrNull()?.id
            prefs.setHistoryFilterLocationId(fallback)
            fallback
        } else {
            currentFilter
        }
        _uiState.update { it.copy(savedLocations = updated, historyFilterLocationId = nextFilter) }
        val feed = _uiState.value.feed
        WidgetUpdater.updateAllWidgets(getApplication(), feed)
        OutageAlarmScheduler.schedule(getApplication(), feed)
    }

    fun reorderLocations(reordered: List<SavedLocation>) {
        prefs.saveLocations(reordered)
        _uiState.update { it.copy(savedLocations = reordered) }
        val feed = _uiState.value.feed
        WidgetUpdater.updateAllWidgets(getApplication(), feed)
        OutageAlarmScheduler.schedule(getApplication(), feed)
    }

    private fun seedSeenSignatures(feed: OutagesFeed) {
        val notifyQueues = prefs.getSavedLocations()
            .filter { it.notifyEnabled }
            .map { it.queueId }
            .distinct()
        val today = TimeUtils.todayDateString()
        val tomorrow = TimeUtils.tomorrowDateString()
        for (queueId in notifyQueues) {
            feed.schedules[today]?.queues?.get(queueId)?.let { q ->
                prefs.setLastNotifiedTodaySignature(
                    queueId,
                    ScheduleChangeDetector.buildSignature(today, queueId, q.intervals)
                )
            }
            feed.schedules[tomorrow]?.queues?.get(queueId)?.let { q ->
                prefs.setLastNotifiedTomorrowSignature(
                    queueId,
                    ScheduleChangeDetector.buildSignature(tomorrow, queueId, q.intervals)
                )
            }
        }
    }

    fun syncPrefsFromStorage() {
        val stored = prefs.getSavedLocations()
        val storedFilter = prefs.getHistoryFilterLocationId()
        if (stored != _uiState.value.savedLocations || storedFilter != _uiState.value.historyFilterLocationId) {
            _uiState.update {
                it.copy(savedLocations = stored, historyFilterLocationId = storedFilter)
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectDayForMyQueue(day: DaySelection) {
        _uiState.update { it.copy(selectedDayMyQueue = day) }
    }

    fun selectDayForAllQueues(day: DaySelection) {
        _uiState.update { it.copy(selectedDayAllQueues = day) }
    }

    fun setHistoryFilterLocation(locationId: String?) {
        prefs.setHistoryFilterLocationId(locationId)
        _uiState.update { it.copy(historyFilterLocationId = locationId) }
    }

    fun isDynamicColorsEnabled(): Boolean = prefs.isDynamicColorsEnabled()
}
