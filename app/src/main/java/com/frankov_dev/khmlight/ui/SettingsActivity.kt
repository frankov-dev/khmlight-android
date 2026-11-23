package com.frankov_dev.khmlight.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.databinding.ActivitySettingsBinding
import com.frankov_dev.khmlight.notifications.NotificationHelper
import com.frankov_dev.khmlight.notifications.OutageAlarmScheduler
import com.frankov_dev.khmlight.util.TimeUtils
import com.frankov_dev.khmlight.widget.WidgetUpdater
import com.google.android.material.color.DynamicColors
import com.google.android.material.snackbar.Snackbar

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
        setupNotificationsSection()
        setupAppearanceSection()
        setupQueueSection()
        setupAboutSection()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionBanner()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.settingsAppBar.updatePadding(top = sysBars.top)
            binding.settingsScrollView.updatePadding(bottom = sysBars.bottom + dp(24))
            insets
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

    private fun setupQueueSection() {
        updateQueueLabel()
        M3Motion.attachPressSpring(binding.cardMyQueueSetting)
        binding.cardMyQueueSetting.setOnClickListener {
            val sheet = QueuePickerBottomSheet.newInstance(prefs.getSelectedQueue())
            sheet.onQueueSelected = { queueId ->
                prefs.setSelectedQueue(queueId)
                updateQueueLabel()
                WidgetUpdater.updateAllWidgets(applicationContext)
                OutageAlarmScheduler.scheduleFromCacheAsync(applicationContext)
                Snackbar.make(
                    binding.root,
                    getString(R.string.my_queue_updated, queueId),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            sheet.show(supportFragmentManager, "settings_queue_picker")
        }
    }

    private fun updateQueueLabel() {
        val q = prefs.getSelectedQueue()
        binding.tvSelectedQueueValue.text = if (q != null) {
            getString(R.string.queue_format, q)
        } else {
            getString(R.string.settings_queue_not_selected)
        }
    }

    private fun setupAboutSection() {
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        binding.tvAboutAppName.text = "${getString(R.string.app_name)} $versionName"

        val serverFormatted = TimeUtils.formatLastUpdated(prefs.getLastServerUpdatedIso().orEmpty())
        val appFormatted = TimeUtils.formatEpochMs(prefs.getLastAppSyncMs())
        binding.tvServerUpdated.text = getString(R.string.settings_server_updated, serverFormatted)
        binding.tvAppUpdated.text = getString(R.string.settings_app_updated, appFormatted)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
