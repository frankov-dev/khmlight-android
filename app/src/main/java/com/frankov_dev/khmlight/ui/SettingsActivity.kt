package com.frankov_dev.khmlight.ui

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.AppUpdateChecker
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.data.UpdateCheckResult
import com.frankov_dev.khmlight.databinding.ActivitySettingsBinding
import com.frankov_dev.khmlight.notifications.NotificationHelper
import com.frankov_dev.khmlight.notifications.OutageAlarmScheduler
import com.frankov_dev.khmlight.util.TimeUtils
import com.frankov_dev.khmlight.widget.WidgetUpdater
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateNotificationPermissionBanner()
        OutageAlarmScheduler.scheduleFromCacheAsync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupToolbar()
        setupFragmentResultListener()
        setupNotificationsSection()
        setupAppearanceSection()
        setupLocationsSection()
        setupAboutSection()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionBanner()
        renderLocationLists()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.settingsRoot.updatePadding(left = sysBars.left, right = sysBars.right)
            binding.settingsAppBar.updatePadding(top = sysBars.top)
            binding.settingsScrollView.updatePadding(bottom = sysBars.bottom + dp(24))
            insets
        }
    }

    private fun setupFragmentResultListener() {
        supportFragmentManager.setFragmentResultListener(
            LocationEditorBottomSheet.REQUEST_KEY,
            this
        ) { _, bundle ->
            val (action, loc) = LocationEditorBottomSheet.parseResultLocation(bundle) ?: return@setFragmentResultListener
            when (action) {
                LocationEditorBottomSheet.ACTION_SAVE -> {
                    prefs.upsertLocation(loc)
                    renderLocationLists()
                    WidgetUpdater.updateAllWidgets(applicationContext)
                    OutageAlarmScheduler.scheduleFromCacheAsync(applicationContext)
                    if (loc.notifyEnabled) requestPermissionIfNeeded()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.location_saved_msg, loc.name, loc.queueId),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                LocationEditorBottomSheet.ACTION_DELETE -> {
                    prefs.deleteLocation(loc.id)
                    renderLocationLists()
                    WidgetUpdater.updateAllWidgets(applicationContext)
                    OutageAlarmScheduler.scheduleFromCacheAsync(applicationContext)
                    Snackbar.make(
                        binding.root,
                        getString(R.string.location_deleted_msg, loc.name),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            R.anim.m3_shared_axis_x_pop_enter,
            R.anim.m3_shared_axis_x_pop_exit
        )
    }

    private fun setupNotificationsSection() {
        updateNotificationPermissionBanner()

        binding.btnEnableNotifPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !prefs.hasRequestedNotificationPermission()
            ) {
                prefs.markNotificationPermissionRequested()
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openSystemNotificationSettings()
            }
        }

        binding.switchNotifyOutage.isChecked = prefs.isNotifyOutageEnabled()
        binding.switchNotifyPowerOn.isChecked = prefs.isNotifyPowerOnEnabled()
        binding.switchNotifySchedule.isChecked = prefs.isNotifyScheduleChangeEnabled()
        binding.switchQuietHours.isChecked = prefs.isQuietHoursEnabled()

        val chipId = when (prefs.getLeadTimeMinutes()) {
            30 -> R.id.chipLead30
            60 -> R.id.chipLead60
            else -> R.id.chipLead15
        }
        binding.chipGroupLeadTime.check(chipId)

        binding.switchNotifyOutage.setOnCheckedChangeListener { _, isChecked ->
            prefs.setNotifyOutageEnabled(isChecked)
            if (isChecked) requestPermissionIfNeeded()
            OutageAlarmScheduler.scheduleFromCacheAsync(this)
        }
        binding.switchNotifyPowerOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.setNotifyPowerOnEnabled(isChecked)
            if (isChecked) requestPermissionIfNeeded()
            OutageAlarmScheduler.scheduleFromCacheAsync(this)
        }
        binding.switchNotifySchedule.setOnCheckedChangeListener { _, isChecked ->
            prefs.setNotifyScheduleChangeEnabled(isChecked)
            if (isChecked) requestPermissionIfNeeded()
        }
        binding.chipGroupLeadTime.setOnCheckedStateChangeListener { _, checkedIds ->
            val minutes = when {
                checkedIds.contains(R.id.chipLead30) -> 30
                checkedIds.contains(R.id.chipLead60) -> 60
                else -> 15
            }
            if (minutes != prefs.getLeadTimeMinutes()) {
                prefs.setLeadTimeMinutes(minutes)
                OutageAlarmScheduler.scheduleFromCacheAsync(this)
            }
        }
        binding.switchQuietHours.setOnCheckedChangeListener { _, isChecked ->
            prefs.setQuietHoursEnabled(isChecked)
        }
    }

    private fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasNotificationPermission(this)
        ) {
            prefs.markNotificationPermissionRequested()
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateNotificationPermissionBanner() {
        val granted = NotificationHelper.hasNotificationPermission(this)
        binding.notifPermissionBanner.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun openSystemNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
        }
        runCatching { startActivity(intent) }
    }

    private fun setupAppearanceSection() {
        when (prefs.getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rgThemeMode.check(R.id.rbThemeLight)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rgThemeMode.check(R.id.rbThemeDark)
            else -> binding.rgThemeMode.check(R.id.rbThemeSystem)
        }

        binding.rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rbThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rbThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (newMode != prefs.getThemeMode()) {
                prefs.setThemeMode(newMode)
                AppCompatDelegate.setDefaultNightMode(newMode)
            }
        }

        if (DynamicColors.isDynamicColorAvailable()) {
            binding.dynamicColorSection.visibility = View.VISIBLE
            binding.switchDynamicColors.isChecked = prefs.isDynamicColorsEnabled()
            binding.switchDynamicColors.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != prefs.isDynamicColorsEnabled()) {
                    prefs.setDynamicColorsEnabled(isChecked)
                    recreate()
                }
            }
        } else {
            binding.dynamicColorSection.visibility = View.GONE
        }
    }

    private fun setupLocationsSection() {
        M3Motion.attachPressSpring(binding.btnSettingsAddLocation)
        binding.btnSettingsAddLocation.setOnClickListener {
            if (prefs.getSavedLocations().size >= SavedLocationCodec.MAX_LOCATIONS) {
                Snackbar.make(binding.root, R.string.location_limit_reached, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val tag = "settings_loc_editor"
            if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) {
                return@setOnClickListener
            }
            LocationEditorBottomSheet.newForCreate()
                .show(supportFragmentManager, tag)
        }
        renderLocationLists()
    }

    private fun renderLocationLists() {
        val locations = prefs.getSavedLocations()
        val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val outlineVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOutlineVariant
        )

        // 1. Per-location notification switches inside Notifications section
        binding.llNotifLocations.removeAllViews()
        if (locations.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = getString(R.string.settings_notif_locations_empty)
                setTextColor(onSurfaceVariant)
                textSize = 13f
            }
            binding.llNotifLocations.addView(tvEmpty)
        } else {
            for (loc in locations) {
                val sw = MaterialSwitch(this).apply {
                    text = "${loc.name} (${getString(R.string.queue_format, loc.queueId)})"
                    isChecked = loc.notifyEnabled
                    setOnCheckedChangeListener { _, isChecked ->
                        prefs.setLocationNotifyEnabled(loc.id, isChecked)
                        if (isChecked) requestPermissionIfNeeded()
                        OutageAlarmScheduler.scheduleFromCacheAsync(applicationContext)
                    }
                }
                binding.llNotifLocations.addView(sw)
            }
        }

        // 2. Saved locations management list
        binding.llSettingsLocations.removeAllViews()
        if (locations.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = getString(R.string.settings_queue_not_selected)
                setTextColor(onSurfaceVariant)
                textSize = 14f
                setPadding(0, dp(4), 0, dp(8))
            }
            binding.llSettingsLocations.addView(tvEmpty)
        } else {
            locations.forEachIndexed { index, loc ->
                if (index > 0) {
                    val div = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(1)
                        ).apply {
                            topMargin = dp(6)
                            bottomMargin = dp(6)
                        }
                        setBackgroundColor(outlineVariant)
                        alpha = 0.45f
                    }
                    binding.llSettingsLocations.addView(div)
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(2), dp(8), dp(2), dp(8))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openLocationEditor(loc) }
                }

                val iv = ImageView(this).apply {
                    setImageResource(loc.icon.iconRes)
                    imageTintList = ColorStateList.valueOf(primary)
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                        marginEnd = dp(12)
                    }
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvName = TextView(this).apply {
                    text = loc.name
                    textSize = 15.5f
                    setTextColor(onSurface)
                }
                val tvSub = TextView(this).apply {
                    text = getString(R.string.queue_format, loc.queueId)
                    textSize = 13f
                    setTextColor(onSurfaceVariant)
                }
                textCol.addView(tvName)
                textCol.addView(tvSub)

                val tvEdit = TextView(this).apply {
                    text = getString(R.string.change_queue)
                    textSize = 13.5f
                    setTextColor(primary)
                }

                row.addView(iv)
                row.addView(textCol)
                row.addView(tvEdit)
                binding.llSettingsLocations.addView(row)
            }
        }
    }

    private fun openLocationEditor(location: SavedLocation) {
        val tag = "settings_loc_editor"
        if (supportFragmentManager.findFragmentByTag(tag) != null || supportFragmentManager.isStateSaved) return
        LocationEditorBottomSheet.newForEdit(location)
            .show(supportFragmentManager, tag)
    }

    private fun setupAboutSection() {
        val versionName = AppUpdateChecker.getCurrentVersionName(this)
        binding.tvAboutAppName.text = "${getString(R.string.app_name)} $versionName"

        val serverFormatted = TimeUtils.formatLastUpdated(prefs.getLastServerUpdatedIso().orEmpty())
        val appFormatted = TimeUtils.formatEpochMs(prefs.getLastAppSyncMs())
        binding.tvServerUpdated.text = getString(R.string.settings_server_updated, serverFormatted)
        binding.tvAppUpdated.text = getString(R.string.settings_app_updated, appFormatted)

        M3Motion.attachPressSpring(binding.btnCheckUpdates)
        binding.btnCheckUpdates.setOnClickListener {
            checkForAppUpdates()
        }
    }

    private fun checkForAppUpdates() {
        binding.btnCheckUpdates.isEnabled = false
        binding.tvUpdateStatus.visibility = View.VISIBLE
        binding.tvUpdateStatus.text = getString(R.string.settings_checking_updates)

        lifecycleScope.launch {
            val currentVersion = AppUpdateChecker.getCurrentVersionName(this@SettingsActivity)
            when (val result = AppUpdateChecker.checkForUpdate(this@SettingsActivity, force = true)) {
                is UpdateCheckResult.UpdateAvailable -> {
                    binding.tvUpdateStatus.text = getString(
                        R.string.update_available_short,
                        result.release.versionName
                    )
                    val tag = "update_available_sheet"
                    if (supportFragmentManager.findFragmentByTag(tag) == null && !supportFragmentManager.isStateSaved) {
                        UpdateAvailableBottomSheet.newInstance(currentVersion, result.release)
                            .show(supportFragmentManager, tag)
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    val msg = getString(R.string.update_up_to_date, result.currentVersion)
                    binding.tvUpdateStatus.text = msg
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                }
                is UpdateCheckResult.Error -> {
                    binding.tvUpdateStatus.text = getString(R.string.update_check_error)
                    Snackbar.make(binding.root, R.string.update_check_error, Snackbar.LENGTH_SHORT).show()
                }
                null -> {
                    binding.tvUpdateStatus.visibility = View.GONE
                }
            }
            binding.btnCheckUpdates.isEnabled = true
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
