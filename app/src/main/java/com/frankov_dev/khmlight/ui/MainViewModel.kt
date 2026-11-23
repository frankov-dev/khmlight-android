package com.frankov_dev.khmlight.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.PrefsManager
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
    val selectedQueue: String? = null,
    val activeTab: MainTab = MainTab.MY_QUEUE,
    val selectedDayMyQueue: DaySelection = DaySelection.TODAY,
    val selectedDayAllQueues: DaySelection = DaySelection.TODAY,
    val historyOnlyMyQueue: Boolean = true,
    val lastAppSyncMs: Long? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFromCache: Boolean = false,
    val errorMessage: String? = null,
    val clockTick: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OutageRepository(application)
    private val prefs = PrefsManager(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedQueue = prefs.getSelectedQueue(),
            historyOnlyMyQueue = prefs.isHistoryOnlyMyQueue(),
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

    fun selectQueue(queueId: String) {
        prefs.setSelectedQueue(queueId)
        _uiState.update { it.copy(selectedQueue = queueId) }
        val feed = _uiState.value.feed
        if (feed != null) {
            seedSeenSignatures(feed)
        }
        WidgetUpdater.updateAllWidgets(getApplication(), feed)
        OutageAlarmScheduler.schedule(getApplication(), feed)
    }

    private fun seedSeenSignatures(feed: OutagesFeed) {
        val myQueue = prefs.getSelectedQueue() ?: return
        val today = TimeUtils.todayDateString()
        val tomorrow = TimeUtils.tomorrowDateString()
        feed.schedules[today]?.queues?.get(myQueue)?.let { q ->
            prefs.setLastNotifiedTodaySignature(
                ScheduleChangeDetector.buildSignature(today, myQueue, q.intervals)
            )
        }
        feed.schedules[tomorrow]?.queues?.get(myQueue)?.let { q ->
            prefs.setLastNotifiedTomorrowSignature(
                ScheduleChangeDetector.buildSignature(tomorrow, myQueue, q.intervals)
            )
        }
    }

    fun syncPrefsFromStorage() {
        val storedQueue = prefs.getSelectedQueue()
        if (storedQueue != _uiState.value.selectedQueue) {
            _uiState.update { it.copy(selectedQueue = storedQueue) }
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

    fun setHistoryOnlyMyQueue(onlyMy: Boolean) {
        prefs.setHistoryOnlyMyQueue(onlyMy)
        _uiState.update { it.copy(historyOnlyMyQueue = onlyMy) }
    }

    fun isDynamicColorsEnabled(): Boolean = prefs.isDynamicColorsEnabled()
}
