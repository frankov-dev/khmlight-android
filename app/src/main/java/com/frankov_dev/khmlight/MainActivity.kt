package com.frankov_dev.khmlight

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.databinding.ActivityMainBinding
import com.frankov_dev.khmlight.notifications.NotificationHelper
import com.frankov_dev.khmlight.notifications.OutageAlarmScheduler
import com.frankov_dev.khmlight.ui.DaySelection
import com.frankov_dev.khmlight.ui.HistoryDayBottomSheet
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.ui.MainTab
import com.frankov_dev.khmlight.ui.MainUiState
import com.frankov_dev.khmlight.ui.MainViewModel
import com.frankov_dev.khmlight.ui.OperationalMessageBottomSheet
import com.frankov_dev.khmlight.ui.QueuePickerBottomSheet
import com.frankov_dev.khmlight.ui.SettingsActivity
import com.frankov_dev.khmlight.ui.adapters.AllQueuesPagerAdapter
import com.frankov_dev.khmlight.ui.adapters.HistoryAdapter
import com.frankov_dev.khmlight.ui.adapters.MyQueuePagerAdapter
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var myQueuePagerAdapter: MyQueuePagerAdapter
    private lateinit var allQueuesPagerAdapter: AllQueuesPagerAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private var appliedDynamicColors: Boolean = true
    private var isHorizontalDragging: Boolean = false
    private var lastRenderedTab: MainTab? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            OutageAlarmScheduler.schedule(this, viewModel.uiState.value.feed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appliedDynamicColors = viewModel.isDynamicColorsEnabled()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDynamicChrome()
        setupWindowInsets()
        setupTopBar()
        setupBottomNav()
        setupPagersAndLists()
        setupListeners()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.isDynamicColorsEnabled() != appliedDynamicColors) {
            recreate()
            return
        }
        viewModel.syncPrefsFromStorage()
    }

    private fun setupDynamicChrome() {
        val primary = MaterialColors.getColor(
            binding.root,
            androidx.appcompat.R.attr.colorPrimary
        )
        val surfaceContainerHigh = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerHigh
        )
        binding.swipeRefresh.setColorSchemeColors(primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(surfaceContainerHigh)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = sysBars.top)
            binding.bottomNav.updatePadding(bottom = sysBars.bottom)
            insets
        }
    }

    private fun setupTopBar() {
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    @Suppress("DEPRECATION")
                    overridePendingTransition(
                        R.anim.m3_shared_axis_x_enter,
                        R.anim.m3_shared_axis_x_exit
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_my_queue -> {
                    viewModel.selectTab(MainTab.MY_QUEUE)
                    true
                }
                R.id.nav_all_queues -> {
                    viewModel.selectTab(MainTab.ALL_QUEUES)
                    true
                }
                R.id.nav_history -> {
                    viewModel.selectTab(MainTab.HISTORY)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPagersAndLists() {
        // Tab 1: My Queue ViewPager2 (Today / Tomorrow)
        myQueuePagerAdapter = MyQueuePagerAdapter()
        binding.vpMyQueue.adapter = myQueuePagerAdapter
        binding.vpMyQueue.offscreenPageLimit = 1
        binding.vpMyQueue.setPageTransformer(M3Motion.dayPagerTransformer())
        binding.vpMyQueue.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selection = if (position == 1) DaySelection.TOMORROW else DaySelection.TODAY
                if (viewModel.uiState.value.selectedDayMyQueue != selection) {
                    viewModel.selectDayForMyQueue(selection)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                isHorizontalDragging = state == ViewPager2.SCROLL_STATE_DRAGGING
            }
        })

        // Tab 2: All Queues ViewPager2 (Today / Tomorrow)
        allQueuesPagerAdapter = AllQueuesPagerAdapter { queueId ->
            if (queueId != viewModel.uiState.value.selectedQueue) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.queue_format, queueId))
                    .setMessage(getString(R.string.set_as_my_queue) + "?")
                    .setPositiveButton(R.string.set_as_my_queue) { _, _ ->
                        viewModel.selectQueue(queueId)
                        Snackbar.make(
                            binding.root,
                            getString(R.string.my_queue_updated, queueId),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
            }
        }
        binding.vpAllQueues.adapter = allQueuesPagerAdapter
        binding.vpAllQueues.offscreenPageLimit = 1
        binding.vpAllQueues.setPageTransformer(M3Motion.dayPagerTransformer())
        binding.vpAllQueues.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val selection = if (position == 1) DaySelection.TOMORROW else DaySelection.TODAY
                if (viewModel.uiState.value.selectedDayAllQueues != selection) {
                    viewModel.selectDayForAllQueues(selection)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                isHorizontalDragging = state == ViewPager2.SCROLL_STATE_DRAGGING
            }
        })

        // Tab 3: History RecyclerView
        historyAdapter = HistoryAdapter { day ->
            openHistoryDaySheet(day)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter

        // Prevent pull-to-refresh while scrolled down inside ViewPager2 pages or dragging horizontally
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            if (isHorizontalDragging) return@setOnChildScrollUpCallback true
            when (viewModel.uiState.value.activeTab) {
                MainTab.MY_QUEUE -> myQueuePagerAdapter.canScrollUp(binding.vpMyQueue.currentItem)
                MainTab.ALL_QUEUES -> allQueuesPagerAdapter.canScrollUp(binding.vpAllQueues.currentItem)
                MainTab.HISTORY -> binding.rvHistory.canScrollVertically(-1)
            }
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.btnRetry.setOnClickListener {
            viewModel.refresh(showFullLoader = true)
        }
        M3Motion.attachPressSpring(binding.chipMyQueueSelector)
        M3Motion.attachPressSpring(binding.btnOperationalMessage, pressedScale = 0.92f)
        M3Motion.attachPressSpring(binding.btnOnboardingSelectQueue)
        binding.btnOnboardingSelectQueue.setOnClickListener {
            openQueuePicker()
        }
        binding.chipMyQueueSelector.setOnClickListener {
            openQueuePicker()
        }
        binding.btnOperationalMessage.setOnClickListener {
            openActiveOperationalMessage()
        }
        binding.toggleMyQueueDay.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selection = if (checkedId == R.id.btnMyQueueTomorrow) {
                DaySelection.TOMORROW
            } else {
                DaySelection.TODAY
            }
            viewModel.selectDayForMyQueue(selection)
            val targetPage = if (selection == DaySelection.TOMORROW) 1 else 0
            if (binding.vpMyQueue.currentItem != targetPage) {
                binding.vpMyQueue.setCurrentItem(targetPage, true)
            }
        }
        binding.toggleAllQueuesDay.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selection = if (checkedId == R.id.btnAllQueuesTomorrow) {
                DaySelection.TOMORROW
            } else {
                DaySelection.TODAY
            }
            viewModel.selectDayForAllQueues(selection)
            val targetPage = if (selection == DaySelection.TOMORROW) 1 else 0
            if (binding.vpAllQueues.currentItem != targetPage) {
                binding.vpAllQueues.setCurrentItem(targetPage, true)
            }
        }
        binding.historyFilterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val onlyMy = checkedIds.contains(R.id.chipHistoryMyQueue)
            viewModel.setHistoryOnlyMyQueue(onlyMy)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MainUiState) {
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.offlineBanner.visibility =
            if (state.isFromCache && state.feed != null) View.VISIBLE else View.GONE

        if (state.isLoading && state.feed == null) {
            binding.loadingContainer.visibility = View.VISIBLE
            binding.errorContainer.visibility = View.GONE
            return
        }
        binding.loadingContainer.visibility = View.GONE

        if (state.feed == null) {
            binding.errorContainer.visibility = View.VISIBLE
            binding.tvErrorMessage.text = state.errorMessage ?: getString(R.string.error_loading)
            return
        }
        binding.errorContainer.visibility = View.GONE

        if (lastRenderedTab != state.activeTab) {
            val fromView = when (lastRenderedTab) {
                MainTab.MY_QUEUE -> binding.myQueueContainer
                MainTab.ALL_QUEUES -> binding.allQueuesContainer
                MainTab.HISTORY -> binding.historyContainer
                null -> null
            }
            val toView = when (state.activeTab) {
                MainTab.MY_QUEUE -> binding.myQueueContainer
                MainTab.ALL_QUEUES -> binding.allQueuesContainer
                MainTab.HISTORY -> binding.historyContainer
            }
            val movingRight = (lastRenderedTab?.ordinal ?: 0) <= state.activeTab.ordinal
            if (fromView == null) {
                binding.myQueueContainer.visibility =
                    if (state.activeTab == MainTab.MY_QUEUE) View.VISIBLE else View.GONE
                binding.allQueuesContainer.visibility =
                    if (state.activeTab == MainTab.ALL_QUEUES) View.VISIBLE else View.GONE
                binding.historyContainer.visibility =
                    if (state.activeTab == MainTab.HISTORY) View.VISIBLE else View.GONE
            } else {
                M3Motion.switchTab(fromView, toView, movingRight)
            }
            lastRenderedTab = state.activeTab
        }

        renderMyQueueTab(state, state.feed)
        renderAllQueuesTab(state, state.feed)
        renderHistoryTab(state, state.feed)
    }

    private fun renderMyQueueTab(state: MainUiState, feed: OutagesFeed) {
        val myQueue = state.selectedQueue
        if (myQueue == null) {
            binding.onboardingCard.visibility = View.VISIBLE
            binding.myQueueContent.visibility = View.GONE
            return
        }

        binding.onboardingCard.visibility = View.GONE
        binding.myQueueContent.visibility = View.VISIBLE

        binding.chipMyQueueSelector.text = getString(R.string.queue_format, myQueue)
        binding.tvLocalUpdated.text = getString(
            R.string.local_updated_short,
            TimeUtils.formatEpochMs(state.lastAppSyncMs)
        )

        val todayStr = TimeUtils.todayDateString()
        val tomorrowStr = TimeUtils.tomorrowDateString()
        binding.btnMyQueueToday.text = TimeUtils.formatShortDayButton(todayStr)
        binding.btnMyQueueTomorrow.text = TimeUtils.formatShortDayButton(tomorrowStr)

        val activeDaySchedule = resolveMyQueueDaySchedule(state, feed)
        binding.btnOperationalMessage.visibility =
            if (!activeDaySchedule?.operationalMessage.isNullOrBlank()) View.VISIBLE else View.GONE

        val targetToggleId = if (state.selectedDayMyQueue == DaySelection.TOMORROW) {
            R.id.btnMyQueueTomorrow
        } else {
            R.id.btnMyQueueToday
        }
        if (binding.toggleMyQueueDay.checkedButtonId != targetToggleId) {
            binding.toggleMyQueueDay.check(targetToggleId)
        }
        val targetPage = if (state.selectedDayMyQueue == DaySelection.TOMORROW) 1 else 0
        if (binding.vpMyQueue.currentItem != targetPage) {
            binding.vpMyQueue.setCurrentItem(targetPage, true)
        }

        myQueuePagerAdapter.submitData(
            feed = feed,
            selectedQueue = myQueue
        )
    }

    private fun resolveMyQueueDaySchedule(state: MainUiState, feed: OutagesFeed): DaySchedule? {
        val todayStr = TimeUtils.todayDateString()
        val tomorrowStr = TimeUtils.tomorrowDateString()
        return when (state.selectedDayMyQueue) {
            DaySelection.TODAY -> feed.schedules[todayStr]
                ?: feed.latestDate?.let { feed.schedules[it] }
            DaySelection.TOMORROW -> feed.schedules[tomorrowStr]
        }
    }

    private fun openActiveOperationalMessage() {
        val state = viewModel.uiState.value
        val feed = state.feed ?: return
        val day = resolveMyQueueDaySchedule(state, feed) ?: return
        val msg = day.operationalMessage?.takeIf { it.isNotBlank() } ?: return
        val dateLabel = TimeUtils.formatDateHeading(day.date)
        OperationalMessageBottomSheet.newInstance(dateLabel, msg)
            .show(supportFragmentManager, "operational_message")
    }

    private fun renderAllQueuesTab(state: MainUiState, feed: OutagesFeed) {
        val todayStr = TimeUtils.todayDateString()
        val tomorrowStr = TimeUtils.tomorrowDateString()
        binding.btnAllQueuesToday.text = TimeUtils.formatShortDayButton(todayStr)
        binding.btnAllQueuesTomorrow.text = TimeUtils.formatShortDayButton(tomorrowStr)

        val targetToggleId = if (state.selectedDayAllQueues == DaySelection.TOMORROW) {
            R.id.btnAllQueuesTomorrow
        } else {
            R.id.btnAllQueuesToday
        }
        if (binding.toggleAllQueuesDay.checkedButtonId != targetToggleId) {
            binding.toggleAllQueuesDay.check(targetToggleId)
        }
        val targetPage = if (state.selectedDayAllQueues == DaySelection.TOMORROW) 1 else 0
        if (binding.vpAllQueues.currentItem != targetPage) {
            binding.vpAllQueues.setCurrentItem(targetPage, true)
        }

        allQueuesPagerAdapter.submitData(
            feed = feed,
            selectedQueue = state.selectedQueue
        )
    }

    private fun renderHistoryTab(state: MainUiState, feed: OutagesFeed) {
        val targetChipId = if (state.historyOnlyMyQueue && state.selectedQueue != null) {
            R.id.chipHistoryMyQueue
        } else {
            R.id.chipHistoryAllQueues
        }
        if (binding.historyFilterChipGroup.checkedChipId != targetChipId) {
            binding.historyFilterChipGroup.check(targetChipId)
        }
        binding.chipHistoryMyQueue.isEnabled = state.selectedQueue != null

        val days = feed.availableDates.mapNotNull { feed.schedules[it] }
        historyAdapter.submitData(
            days = days,
            myQueueId = state.selectedQueue,
            onlyMyQueue = state.historyOnlyMyQueue && state.selectedQueue != null
        )
    }

    private fun openQueuePicker() {
        val sheet = QueuePickerBottomSheet.newInstance(viewModel.uiState.value.selectedQueue)
        sheet.onQueueSelected = { queueId ->
            viewModel.selectQueue(queueId)
            maybeRequestNotificationPermission()
            Snackbar.make(
                binding.root,
                getString(R.string.my_queue_updated, queueId),
                Snackbar.LENGTH_SHORT
            ).show()
        }
        sheet.show(supportFragmentManager, "queue_picker")
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = PrefsManager(this)
        if (!prefs.hasRequestedNotificationPermission() && !NotificationHelper.hasNotificationPermission(this)) {
            prefs.markNotificationPermissionRequested()
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openHistoryDaySheet(day: DaySchedule) {
        val sheet = HistoryDayBottomSheet().apply {
            daySchedule = day
            myQueueId = viewModel.uiState.value.selectedQueue
            onSelectAsMyQueue = { queueId ->
                viewModel.selectQueue(queueId)
                Snackbar.make(
                    binding.root,
                    getString(R.string.my_queue_updated, queueId),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        sheet.show(supportFragmentManager, "history_day")
    }
}
