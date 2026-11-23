package com.frankov_dev.khmlight

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.notifications.BackgroundWorkScheduler
import com.frankov_dev.khmlight.notifications.NotificationHelper
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

class KHMlightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = PrefsManager(this)
        AppCompatDelegate.setDefaultNightMode(prefs.getThemeMode())

        // Apply Material You dynamic colors on Android 12+ when enabled;
        // older devices automatically fall back to our Material 3 tonal palette.
        val options = DynamicColorsOptions.Builder()
            .setPrecondition { _, _ -> PrefsManager(this).isDynamicColorsEnabled() }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, options)

        // Ensure Android 8+ notification channels & background WorkManager tasks are ready
        NotificationHelper.ensureChannels(this)
        BackgroundWorkScheduler.ensureScheduled(this)
    }
}
