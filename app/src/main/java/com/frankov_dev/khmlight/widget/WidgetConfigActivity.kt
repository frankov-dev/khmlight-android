package com.frankov_dev.khmlight.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.QueueConstants
import com.frankov_dev.khmlight.databinding.ActivityWidgetConfigBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetKind: WidgetKind = WidgetKind.W1X4

    private var selectedShape: BadgeShape = BadgeShape.B1_SQUIRCLE
    private var selectedColorPreset: WidgetColorPreset = WidgetColorPreset.DYNAMIC
    private var selectedThemeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM
    private var selectedQueueOverride: String? = null
    private var selectedLocationId: String? = null

    private val shapeCards = mutableMapOf<BadgeShape, Pair<MaterialCardView, ShapeThumbnailView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        detectWidgetKind()
        loadInitialConfig()
        buildShapePicker()
        buildColorPresetPicker()
        buildThemeModePicker()
        buildQueuePicker()
        updateLivePreview(animate = false)

        M3Motion.attachPressSpring(binding.btnSaveWidget)
        binding.btnSaveWidget.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.configRoot) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.configAppBar.updatePadding(top = sysBars.top)
            binding.configFooter.updatePadding(bottom = sysBars.bottom + dp(16))
            insets
        }
    }

    private fun detectWidgetKind() {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        val cls = info?.provider?.className.orEmpty()
        widgetKind = when {
            cls.endsWith("Widget1x1Provider") -> WidgetKind.W1X1
            cls.endsWith("Widget2x2Provider") -> WidgetKind.W2X2
            cls.endsWith("Widget4x2Provider") -> WidgetKind.W4X2
            else -> WidgetKind.W1X4
        }

        val previewHeightDp = when (widgetKind) {
            WidgetKind.W1X1 -> 96
            WidgetKind.W1X4 -> 92
            WidgetKind.W2X2 -> 172
            WidgetKind.W4X2 -> 168
        }
        binding.ivWidgetLivePreview.layoutParams =
            binding.ivWidgetLivePreview.layoutParams.apply {
                height = dp(previewHeightDp)
            }
    }

    private fun loadInitialConfig() {
        val dynamicAvailable = WidgetPaletteResolver.isDynamicColorAvailable()
        val existing = WidgetPrefs(this).getConfig(appWidgetId, dynamicAvailable)
        selectedShape = existing.badgeShape
        selectedColorPreset = existing.colorPreset
        selectedThemeMode = existing.themeMode
        selectedQueueOverride = existing.queueOverride
        selectedLocationId = existing.locationId
        if (selectedLocationId == null && selectedQueueOverride == null) {
            selectedLocationId = PrefsManager(this).getSavedLocations().firstOrNull()?.id
        }
    }

    private fun buildShapePicker() {
        binding.shapesRow.removeAllViews()
        shapeCards.clear()

        val surfaceLow = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerLow
        )
        val onSurface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        for (shape in BadgeShape.entries) {
            val card = MaterialCardView(this).apply {
                radius = dp(18).toFloat()
                setCardBackgroundColor(surfaceLow)
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(dp(92), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(8)
                }
            }

            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(12), dp(8), dp(10))
            }

            val thumb = ShapeThumbnailView(this).apply {
                this.shape = shape
                layoutParams = LinearLayout.LayoutParams(dp(68), dp(36))
            }

            val tvCode = TextView(this).apply {
                text = shape.code
                textSize = 11f
                setTextColor(onSurfaceVariant)
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }

            val tvTitle = TextView(this).apply {
                text = shape.title
                textSize = 12f
                setTextColor(onSurface)
                gravity = Gravity.CENTER
                maxLines = 1
            }

            inner.addView(thumb)
            inner.addView(tvCode)
            inner.addView(tvTitle)
            card.addView(inner)

            M3Motion.attachPressSpring(card, pressedScale = 0.94f)
            card.setOnClickListener {
                if (selectedShape != shape) {
                    selectedShape = shape
                    updateShapeCardsState(animate = true)
                    updateLivePreview(animate = true)
                }
            }

            binding.shapesRow.addView(card)
            shapeCards[shape] = card to thumb
        }

        updateShapeCardsState(animate = false)
    }

    private fun updateShapeCardsState(animate: Boolean) {
        val primary = MaterialColors.getColor(
            binding.root,
            androidx.appcompat.R.attr.colorPrimary
        )
        val surfaceLow = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerLow
        )
        val surfaceHigh = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerHigh
        )

        for ((shape, pair) in shapeCards) {
            val (card, thumb) = pair
            val isSelected = shape == selectedShape
            thumb.isShapeSelected = isSelected
            card.strokeWidth = if (isSelected) dp(2) else 0
            card.strokeColor = primary
            card.setCardBackgroundColor(if (isSelected) surfaceHigh else surfaceLow)
            val targetScale = if (isSelected) 1.04f else 1.0f
            if (animate) {
                card.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(200L)
                    .setInterpolator(M3Motion.SPRING_OVERSHOOT)
                    .start()
            } else {
                card.scaleX = targetScale
                card.scaleY = targetScale
            }
        }
    }

    private fun buildColorPresetPicker() {
        binding.chipGroupColorPreset.removeAllViews()
        val dynamicAvailable = WidgetPaletteResolver.isDynamicColorAvailable()

        for (preset in WidgetColorPreset.entries) {
            if (preset == WidgetColorPreset.DYNAMIC && !dynamicAvailable) continue
            val chip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = preset.titleUk
                isCheckable = true
                isChecked = preset == selectedColorPreset
                setOnClickListener {
                    selectedColorPreset = preset
                    updateLivePreview()
                }
            }
            binding.chipGroupColorPreset.addView(chip)
        }
    }

    private fun buildThemeModePicker() {
        when (selectedThemeMode) {
            WidgetThemeMode.LIGHT -> binding.chipGroupThemeMode.check(R.id.chipThemeLight)
            WidgetThemeMode.DARK -> binding.chipGroupThemeMode.check(R.id.chipThemeDark)
            WidgetThemeMode.SYSTEM -> binding.chipGroupThemeMode.check(R.id.chipThemeSystem)
        }
        binding.chipGroupThemeMode.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedThemeMode = when {
                checkedIds.contains(R.id.chipThemeLight) -> WidgetThemeMode.LIGHT
                checkedIds.contains(R.id.chipThemeDark) -> WidgetThemeMode.DARK
                else -> WidgetThemeMode.SYSTEM
            }
            updateLivePreview()
        }
    }

    private fun buildQueuePicker() {
        val chipGroup = binding.chipGroupWidgetQueue
        chipGroup.removeAllViews()

        val savedLocations = PrefsManager(this).getSavedLocations()
        var matchedSaved = false

        if (savedLocations.isEmpty()) {
            val autoChip = createQueueChip(getString(R.string.widget_queue_auto), null, null, null)
            chipGroup.addView(autoChip)
            if (selectedQueueOverride == null && selectedLocationId == null) {
                autoChip.isChecked = true
                matchedSaved = true
            }
        } else {
            savedLocations.forEachIndexed { index, loc ->
                val chipQueueValue = if (index == 0) null else loc.queueId
                val chip = createQueueChip(
                    label = "${loc.name} · ${loc.queueId}",
                    queueValue = chipQueueValue,
                    locationIdValue = loc.id,
                    iconRes = loc.icon.iconRes
                )
                chipGroup.addView(chip)
                val isMatch = when {
                    selectedLocationId != null -> selectedLocationId == loc.id
                    index == 0 && selectedQueueOverride == null -> true
                    else -> false
                }
                if (!matchedSaved && isMatch) {
                    chip.isChecked = true
                    matchedSaved = true
                }
            }
        }

        for (queueId in QueueConstants.ALL_QUEUES) {
            val chip = createQueueChip(queueId, queueId, null, null)
            chipGroup.addView(chip)
            if (!matchedSaved && selectedQueueOverride == queueId) {
                chip.isChecked = true
            }
        }
    }

    private fun createQueueChip(
        label: String,
        queueValue: String?,
        locationIdValue: String?,
        iconRes: Int?
    ): Chip {
        return Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            text = label
            isCheckable = true
            if (iconRes != null) {
                setChipIconResource(iconRes)
                isChipIconVisible = true
            }
            setOnClickListener {
                selectedQueueOverride = queueValue
                selectedLocationId = locationIdValue
                updateLivePreview()
            }
        }
    }

    private fun updateLivePreview(animate: Boolean = true) {
        binding.tvPreviewTitle.text =
            "${selectedShape.code} · ${selectedShape.title}  •  ${selectedColorPreset.titleUk}"

        val prefs = PrefsManager(this)
        val savedLocations = prefs.getSavedLocations()
        val matchedLoc = when {
            selectedLocationId != null -> savedLocations.firstOrNull { it.id == selectedLocationId }
            selectedQueueOverride == null -> savedLocations.firstOrNull()
            else -> null
        }
        val queueBadge = matchedLoc?.queueId ?: selectedQueueOverride ?: prefs.getSelectedQueue() ?: "2.1"
        val headerLabel = WidgetDataResolver.buildHeaderLabel(matchedLoc, queueBadge)
        val iconRes = matchedLoc?.icon?.iconRes ?: R.drawable.ic_bolt

        val sampleData = ResolvedWidgetData(
            queueBadge = queueBadge,
            headerLabel = headerLabel,
            iconRes = iconRes,
            statusTitle = "Є світло",
            badgeTimerText = "42 хв",
            badgeCompactTimerText = "42 хв",
            badgeSubtitle = "До відключення",
            nextChangeTime = "14:30",
            dailyHoursShort = "8 год",
            dailyHoursLong = "8 год без світла",
            intervals = listOf(
                OutageInterval("06:00", "10:00"),
                OutageInterval("14:30", "18:30")
            ),
            mood = WidgetPowerMood.POWER_ON,
            hasKnownSchedule = true,
            showLiveTimeMarker = true,
            currentMinute = 13 * 60 + 48,
            isStale = false
        )

        val (previewW, previewH) = when (widgetKind) {
            WidgetKind.W1X1 -> 92 to 92
            WidgetKind.W1X4 -> 348 to 90
            WidgetKind.W2X2 -> 172 to 172
            WidgetKind.W4X2 -> 348 to 164
        }

        val bmp = KhmWidgetCanvasRenderer.render(
            context = this,
            kind = widgetKind,
            data = sampleData,
            config = currentConfig(),
            widthDp = previewW,
            heightDp = previewH
        )
        binding.ivWidgetLivePreview.setImageBitmap(bmp)
        if (animate) {
            M3Motion.animatePreviewPop(binding.ivWidgetLivePreview)
        }
    }

    private fun currentConfig() = WidgetInstanceConfig(
        badgeShape = selectedShape,
        colorPreset = selectedColorPreset,
        themeMode = selectedThemeMode,
        queueOverride = selectedQueueOverride,
        locationId = selectedLocationId
    )

    private fun saveAndFinish() {
        WidgetPrefs(this).saveConfig(appWidgetId, currentConfig())
        binding.btnSaveWidget.isEnabled = false
        lifecycleScope.launch {
            WidgetUpdater.updateWidgetsForKind(
                context = this@WidgetConfigActivity,
                kind = widgetKind,
                widgetIds = intArrayOf(appWidgetId),
                fetchFreshIfNoCache = true
            )
            val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
