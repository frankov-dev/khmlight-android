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
import com.frankov_dev.khmlight.data.AppUpdateChecker
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.LocationQueueGroup
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.data.UpdateCheckResult
import com.frankov_dev.khmlight.data.groupByQueuePreservingOrder
import com.frankov_dev.khmlight.databinding.ActivityMainBinding
import com.frankov_dev.khmlight.notifications.NotificationHelper
import com.frankov_dev.khmlight.notifications.OutageAlarmScheduler
import com.frankov_dev.khmlight.ui.DaySelection
import com.frankov_dev.khmlight.ui.HistoryDayBottomSheet
import com.frankov_dev.khmlight.ui.LocationEditorBottomSheet
import com.frankov_dev.khmlight.ui.LocationGroupBottomSheet
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.ui.MainTab
import com.frankov_dev.khmlight.ui.MainUiState
import com.frankov_dev.khmlight.ui.MainViewModel
import com.frankov_dev.khmlight.ui.OperationalMessageBottomSheet
import com.frankov_dev.khmlight.ui.ScheduleRevisionsBottomSheet
import com.frankov_dev.khmlight.ui.SettingsActivity
import com.frankov_dev.khmlight.ui.UpdateAvailableBottomSheet
import com.frankov_dev.khmlight.ui.adapters.AllQueuesPagerAdapter
import com.frankov_dev.khmlight.ui.adapters.HistoryAdapter
import com.frankov_dev.khmlight.ui.adapters.MyQueuePagerAdapter
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var myQueuePagerAdapter: MyQueuePagerAdapter
    private lateinit var allQueuesPagerAdapter: AllQueuesPagerAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private var appliedDynamicColors: Boolean = true
    private var isHorizontalDragging: Boolean = false
    private var isLocationDragging: Boolean = false
    private var lastRenderedTab: MainTab? = null
    private var lastHistoryChipsStructureSig: String? = null
    private val historyChipsByQueue = mutableMapOf<String?, Chip>()

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
        setupFragmentResultListeners()
        setupListeners()
        observeState()
        if (savedInstanceState == null) {
            checkAppUpdateSilently()
        }
    }

    private fun checkAppUpdateSilently() {
        lifecycleScope.launch {
            delay(1_500L)
            val result = AppUpdateChecker.checkForUpdate(this@MainActivity, force = false)
            if (result is UpdateCheckResult.UpdateAvailable) {
                val tag = "update_available_sheet"
                if (supportFragmentManager.findFragmentByTag(tag) == null && !supportFragmentManager.isStateSaved) {
                    val currentVersion = AppUpdateChecker.getCurrentVersionName(this@MainActivity)
                    UpdateAvailableBottomSheet.newInstance(currentVersion, result.release)
                        .show(supportFragmentManager, tag)
                }
            }
        }
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
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.rootLayout.updatePadding(left = sysBars.left, right = sysBars.right)
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
        // Tab 1: My Queues ViewPager2 (Today / Tomorrow) with Drag-and-Drop grouped location blocks
        myQueuePagerAdapter = MyQueuePagerAdapter(
            onEditLocation = { location -> openLocationEditorForEdit(location) },
            onEditGroup = { group -> openGroupEditChooser(group) },
            onShowRevisions = { dateKey, diff -> openScheduleRevisionsSheet(dateKey, diff) },
            onLocationsReordered = { reordered -> viewModel.reorderLocations(reordered) },
            onDraggingChanged = { dragging ->
                isLocationDragging = dragging
                binding.swipeRefresh.isEnabled = !dragging
                binding.vpMyQueue.isUserInputEnabled = !dragging
            }
        )
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

        // Tab 2: All Queues ViewPager2 (Today / Tomorrow) — display only (revision badges remain clickable)
        allQueuesPagerAdapter = AllQueuesPagerAdapter(
            onRevisionBadgeClicked = { dateKey, diff -> openScheduleRevisionsSheet(dateKey, diff) }
        )
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

        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            if (isHorizontalDragging || isLocationDragging) return@setOnChildScrollUpCallback true
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
        M3Motion.attachPressSpring(binding.chipAddLocation)
        M3Motion.attachPressSpring(binding.btnOperationalMessage, pressedScale = 0.92f)
        M3Motion.attachPressSpring(binding.btnOnboardingSelectQueue)
        binding.btnOnboardingSelectQueue.setOnClickListener {
            openLocationEditorForCreate()
        }
        binding.chipAddLocation.setOnClickListener {
            openLocationEditorForCreate()
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
    }

    private fun setupFragmentResultListeners() {
        supportFragmentManager.setFragmentResultListener(
            LocationEditorBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            val (action, loc) = LocationEditorBottomSheet.parseResultLocation(bundle) ?: return@setFragmentResultListener
            when (action) {
                LocationEditorBottomSheet.ACTION_SAVE -> {
                    viewModel.saveLocation(loc)
                    if (loc.notifyEnabled) {
                        maybeRequestNotificationPermission()
                    }
                    Snackbar.make(
                        binding.root,
                        getString(R.string.location_saved_msg, loc.name, loc.queueId),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                LocationEditorBottomSheet.ACTION_DELETE -> {
                    viewModel.deleteLocation(loc.id)
                    Snackbar.make(
                        binding.root,
                        getString(R.string.location_deleted_msg, loc.name),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        supportFragmentManager.setFragmentResultListener(
            LocationGroupBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            when (bundle.getString(LocationGroupBottomSheet.RESULT_ACTION)) {
                LocationGroupBottomSheet.ACTION_EDIT_LOCATION -> {
                    val locId = bundle.getString(LocationGroupBottomSheet.RESULT_LOCATION_ID) ?: return@setFragmentResultListener
                    val loc = viewModel.uiState.value.savedLocations.firstOrNull { it.id == locId } ?: return@setFragmentResultListener
                    openLocationEditorForEdit(loc)
                }
                LocationGroupBottomSheet.ACTION_ADD_ON_QUEUE -> {
                    val queueId = bundle.getString(LocationGroupBottomSheet.RESULT_QUEUE_ID)
                    openLocationEditorForCreate(preselectedQueue = queueId)
                }
            }
        }

        supportFragmentManager.setFragmentResultListener(
            HistoryDayBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            val dateKey = bundle.getString(HistoryDayBottomSheet.RESULT_DATE_KEY) ?: return@setFragmentResultListener
            val queueId = bundle.getString(HistoryDayBottomSheet.RESULT_QUEUE_ID) ?: return@setFragmentResultListener
            val diff = viewModel.uiState.value.feed?.schedules?.get(dateKey)?.computeQueueDiff(queueId) ?: return@setFragmentResultListener
            openScheduleRevisionsSheet(dateKey, diff)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
                    while (isActive) {
                        viewModel.onClockTick()
                        delay(30_000L)
                    }
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
        val locations = state.savedLocations
        if (locations.isEmpty()) {
            binding.onboardingCard.visibility = View.VISIBLE
            binding.myQueueContent.visibility = View.GONE
            return
        }

        binding.onboardingCard.visibility = View.GONE
        binding.myQueueContent.visibility = View.VISIBLE

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
            locations = locations
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
        val tag = "operational_message"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        val state = viewModel.uiState.value
        val feed = state.feed ?: return
        val day = resolveMyQueueDaySchedule(state, feed) ?: return
        val msg = day.operationalMessage?.takeIf { it.isNotBlank() } ?: return
        val prevMsgs = day.previousSchedules
            .mapNotNull { it.operationalMessage?.takeIf { m -> m.isNotBlank() && m != msg } }
            .distinct()
        val dateLabel = TimeUtils.formatDateHeading(day.date)
        OperationalMessageBottomSheet.newInstance(dateLabel, msg, prevMsgs)
            .show(supportFragmentManager, tag)
    }

    private fun openScheduleRevisionsSheet(dateKey: String, diff: QueueRevisionDiff) {
        val tag = "schedule_revisions"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        ScheduleRevisionsBottomSheet.newInstance(dateKey, diff)
            .show(supportFragmentManager, tag)
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
            savedLocations = state.savedLocations
        )
    }

    private fun renderHistoryTab(state: MainUiState, feed: OutagesFeed) {
        val groups = state.savedLocations.groupByQueuePreservingOrder()
        val selectedGroup = state.historyFilterLocationId?.let { filterId ->
            groups.firstOrNull { g -> g.locations.any { it.id == filterId } }
        }

        val structureSig = groups.joinToString("|") { g ->
            g.queueId + ":" + g.locations.joinToString(",") { "${it.id}:${it.name}:${it.icon.name}" }
        }
        if (structureSig != lastHistoryChipsStructureSig) {
            lastHistoryChipsStructureSig = structureSig
            val groupView = binding.historyFilterChipGroup
            groupView.removeAllViews()
            historyChipsByQueue.clear()

            for (g in groups) {
                val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                    text = g.displayTitleWithQueue
                    setChipIconResource(g.primary.icon.iconRes)
                    isChipIconVisible = true
                    isCheckable = true
                    isChecked = selectedGroup?.queueId == g.queueId
                    setOnClickListener {
                        viewModel.setHistoryFilterLocation(g.primary.id)
                    }
                }
                groupView.addView(chip)
                historyChipsByQueue[g.queueId] = chip
            }

            val allChip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = getString(R.string.history_filter_all)
                isCheckable = true
                isChecked = selectedGroup == null
                setOnClickListener {
                    viewModel.setHistoryFilterLocation(null)
                }
            }
            groupView.addView(allChip)
            historyChipsByQueue[null] = allChip
        } else {
            val activeKey = selectedGroup?.queueId
            for ((key, chip) in historyChipsByQueue) {
                val shouldCheck = key == activeKey
                if (chip.isChecked != shouldCheck) {
                    chip.isChecked = shouldCheck
                }
            }
        }

        val days = feed.availableDates.mapNotNull { feed.schedules[it] }
        historyAdapter.submitData(
            days = days,
            selectedGroup = selectedGroup
        )
    }

    private fun openGroupEditChooser(group: LocationQueueGroup) {
        val tag = "location_group_sheet"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        val canAdd = viewModel.uiState.value.savedLocations.size < SavedLocationCodec.MAX_LOCATIONS
        LocationGroupBottomSheet.newInstance(group, canAdd)
            .show(supportFragmentManager, tag)
    }

    private fun openLocationEditorForCreate(preselectedQueue: String? = null) {
        if (viewModel.uiState.value.savedLocations.size >= SavedLocationCodec.MAX_LOCATIONS) {
            Snackbar.make(binding.root, R.string.location_limit_reached, Snackbar.LENGTH_SHORT).show()
            return
        }
        val tag = "location_editor"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        LocationEditorBottomSheet.newForCreate(preselectedQueue)
            .show(supportFragmentManager, tag)
    }

    private fun openLocationEditorForEdit(location: SavedLocation) {
        val tag = "location_editor"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        LocationEditorBottomSheet.newForEdit(location)
            .show(supportFragmentManager, tag)
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
        val tag = "history_day"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        HistoryDayBottomSheet.newInstance(day, viewModel.uiState.value.savedLocations)
            .show(supportFragmentManager, tag)
    }
}
