package com.frankov_dev.khmlight.widget

import android.content.Context

enum class WidgetKind {
    W1X1,
    W1X4,
    W2X2,
    W4X2
}

enum class BadgeShape(val code: String, val title: String) {
    B1_SQUIRCLE("B1", "Squircle"),
    B2_WAVE("B2", "Wave"),
    B3_BLOB("B3", "Blob"),
    B4_CLOUD("B4", "Cloud"),
    B5_ORGANIC("B5", "Organic")
}

enum class WidgetColorPreset(val titleUk: String, val previewHex: Int) {
    DYNAMIC("Material You", 0xFFD6E3FF.toInt()),
    LAVENDER("Лаванда", 0xFFE6E0F5.toInt()),
    OCEAN("Океан", 0xFFD6E4FF.toInt()),
    MINT("М'ята", 0xFFD2EFE0.toInt()),
    AMBER("Бурштин", 0xFFF7E2C8.toInt()),
    ROSE("Кварц", 0xFFF7D9E4.toInt()),
    SLATE("Графіт", 0xFFE2E4EC.toInt())
}

enum class WidgetThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class WidgetInstanceConfig(
    val badgeShape: BadgeShape = BadgeShape.B1_SQUIRCLE,
    val colorPreset: WidgetColorPreset = WidgetColorPreset.DYNAMIC,
    val themeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    val queueOverride: String? = null,
    val locationId: String? = null
)

class WidgetPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("khmlight_widget_v2_prefs", Context.MODE_PRIVATE)

    fun saveConfig(widgetId: Int, config: WidgetInstanceConfig) {
        prefs.edit()
            .putString(keyShape(widgetId), config.badgeShape.name)
            .putString(keyColor(widgetId), config.colorPreset.name)
            .putString(keyTheme(widgetId), config.themeMode.name)
            .apply {
                if (config.queueOverride != null) {
                    putString(keyQueue(widgetId), config.queueOverride)
                } else {
                    remove(keyQueue(widgetId))
                }
                if (config.locationId != null) {
                    putString(keyLocation(widgetId), config.locationId)
                } else {
                    remove(keyLocation(widgetId))
                }
            }
            .apply()
    }

    fun getConfig(widgetId: Int, isDynamicAvailable: Boolean = true): WidgetInstanceConfig {
        val defaultPreset = if (isDynamicAvailable) {
            WidgetColorPreset.DYNAMIC
        } else {
            WidgetColorPreset.LAVENDER
        }

        val shapeName = prefs.getString(keyShape(widgetId), BadgeShape.B1_SQUIRCLE.name)
        val shape = runCatching { BadgeShape.valueOf(shapeName ?: "") }
            .getOrDefault(BadgeShape.B1_SQUIRCLE)

        val colorName = prefs.getString(keyColor(widgetId), defaultPreset.name)
        var colorPreset = runCatching { WidgetColorPreset.valueOf(colorName ?: "") }
            .getOrDefault(defaultPreset)
        if (colorPreset == WidgetColorPreset.DYNAMIC && !isDynamicAvailable) {
            colorPreset = WidgetColorPreset.LAVENDER
        }

        val themeName = prefs.getString(keyTheme(widgetId), WidgetThemeMode.SYSTEM.name)
        val themeMode = runCatching { WidgetThemeMode.valueOf(themeName ?: "") }
            .getOrDefault(WidgetThemeMode.SYSTEM)

        val queue = prefs.getString(keyQueue(widgetId), null)
        val locationId = prefs.getString(keyLocation(widgetId), null)

        return WidgetInstanceConfig(
            badgeShape = shape,
            colorPreset = colorPreset,
            themeMode = themeMode,
            queueOverride = queue,
            locationId = locationId
        )
    }

    fun deleteConfig(widgetId: Int) {
        prefs.edit()
            .remove(keyShape(widgetId))
            .remove(keyColor(widgetId))
            .remove(keyTheme(widgetId))
            .remove(keyQueue(widgetId))
            .remove(keyLocation(widgetId))
            .apply()
    }

    private fun keyShape(id: Int) = "w_${id}_shape"
    private fun keyColor(id: Int) = "w_${id}_color"
    private fun keyTheme(id: Int) = "w_${id}_theme"
    private fun keyQueue(id: Int) = "w_${id}_queue"
    private fun keyLocation(id: Int) = "w_${id}_location_id"
}
