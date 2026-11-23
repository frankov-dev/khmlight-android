package com.frankov_dev.khmlight.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

data class ResolvedWidgetPalette(
    val outerBg: Int,
    val innerSurfaceBg: Int,
    val fgPrimary: Int,
    val fgMuted: Int,
    val dividerColor: Int,
    val dotPowerOn: Int,
    val dotPowerOff: Int,
    val signalOnColor: Int,
    val signalOffColor: Int,
    val signalMarkerColor: Int,
    val badgeToOutageBg: Int,
    val badgeToPowerOnBg: Int,
    val badgeNeutralBg: Int,
    val badgeTextPrimary: Int,
    val badgeTextMuted: Int
)

object WidgetPaletteResolver {

    fun isDynamicColorAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun resolve(
        context: Context,
        preset: WidgetColorPreset,
        themeMode: WidgetThemeMode
    ): ResolvedWidgetPalette {
        val isNight = when (themeMode) {
            WidgetThemeMode.LIGHT -> false
            WidgetThemeMode.DARK -> true
            WidgetThemeMode.SYSTEM -> {
                val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mask == Configuration.UI_MODE_NIGHT_YES
            }
        }

        val effectivePreset = if (preset == WidgetColorPreset.DYNAMIC && !isDynamicColorAvailable()) {
            WidgetColorPreset.LAVENDER
        } else {
            preset
        }

        val (outerBg, innerBg, fgPrimary, fgMuted) = if (effectivePreset == WidgetColorPreset.DYNAMIC &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            if (!isNight) {
                val outer = ContextCompat.getColor(context, android.R.color.system_neutral1_10)
                val accent = ContextCompat.getColor(context, android.R.color.system_accent1_100)
                val tintedOuter = ColorUtils.blendARGB(outer, accent, 0.25f)
                val inner = ColorUtils.blendARGB(accent, outer, 0.20f)
                val fg = ContextCompat.getColor(context, android.R.color.system_neutral1_900)
                val muted = ContextCompat.getColor(context, android.R.color.system_neutral2_600)
                Quad(tintedOuter, inner, fg, muted)
            } else {
                val outer = ContextCompat.getColor(context, android.R.color.system_neutral1_900)
                val inner = ContextCompat.getColor(context, android.R.color.system_neutral2_800)
                val fg = ContextCompat.getColor(context, android.R.color.system_neutral1_50)
                val muted = ContextCompat.getColor(context, android.R.color.system_neutral2_300)
                Quad(outer, inner, fg, muted)
            }
        } else {
            resolveStaticPreset(effectivePreset, isNight)
        }

        val dividerColor = ColorUtils.setAlphaComponent(fgMuted, if (isNight) 55 else 45)

        return if (!isNight) {
            ResolvedWidgetPalette(
                outerBg = outerBg,
                innerSurfaceBg = innerBg,
                fgPrimary = fgPrimary,
                fgMuted = fgMuted,
                dividerColor = dividerColor,
                dotPowerOn = 0xFF52C46C.toInt(),
                dotPowerOff = 0xFFE56B93.toInt(),
                signalOnColor = 0xFF8C8E99.toInt(),
                signalOffColor = 0xFFE67399.toInt(),
                signalMarkerColor = 0xFF9E3B63.toInt(),
                badgeToOutageBg = 0xFFF7DBE5.toInt(),
                badgeToPowerOnBg = 0xFFD9F3C9.toInt(),
                badgeNeutralBg = innerBg,
                badgeTextPrimary = 0xFF191C20.toInt(),
                badgeTextMuted = 0xFF555863.toInt()
            )
        } else {
            ResolvedWidgetPalette(
                outerBg = outerBg,
                innerSurfaceBg = innerBg,
                fgPrimary = fgPrimary,
                fgMuted = fgMuted,
                dividerColor = dividerColor,
                dotPowerOn = 0xFF6EE08A.toInt(),
                dotPowerOff = 0xFFF48FB1.toInt(),
                signalOnColor = 0xFF9498A4.toInt(),
                signalOffColor = 0xFFF28AA8.toInt(),
                signalMarkerColor = 0xFFF8BBD0.toInt(),
                badgeToOutageBg = 0xFF4F2635.toInt(),
                badgeToPowerOnBg = 0xFF23422A.toInt(),
                badgeNeutralBg = innerBg,
                badgeTextPrimary = 0xFFF1EFF8.toInt(),
                badgeTextMuted = 0xFFB8B5C6.toInt()
            )
        }
    }

    private data class Quad(val outer: Int, val inner: Int, val fg: Int, val muted: Int)

    private fun resolveStaticPreset(preset: WidgetColorPreset, isNight: Boolean): Quad {
        return when (preset) {
            WidgetColorPreset.LAVENDER, WidgetColorPreset.DYNAMIC -> if (!isNight) {
                // Exact palette from reference mockup
                Quad(
                    outer = 0xFFF3F0FA.toInt(),
                    inner = 0xFFE5DFF5.toInt(),
                    fg = 0xFF1A1922.toInt(),
                    muted = 0xFF626070.toInt()
                )
            } else {
                Quad(
                    outer = 0xFF1C1A24.toInt(),
                    inner = 0xFF2B2838.toInt(),
                    fg = 0xFFF1EFFA.toInt(),
                    muted = 0xFFABA7BC.toInt()
                )
            }
            WidgetColorPreset.OCEAN -> if (!isNight) {
                Quad(
                    outer = 0xFFEEF3FC.toInt(),
                    inner = 0xFFD8E4FA.toInt(),
                    fg = 0xFF141C2B.toInt(),
                    muted = 0xFF535F73.toInt()
                )
            } else {
                Quad(
                    outer = 0xFF151C26.toInt(),
                    inner = 0xFF222D3D.toInt(),
                    fg = 0xFFEAF0FA.toInt(),
                    muted = 0xFFA2B0C4.toInt()
                )
            }
            WidgetColorPreset.MINT -> if (!isNight) {
                Quad(
                    outer = 0xFFEDF6F1.toInt(),
                    inner = 0xFFD4EAE0.toInt(),
                    fg = 0xFF13221B.toInt(),
                    muted = 0xFF4F665B.toInt()
                )
            } else {
                Quad(
                    outer = 0xFF14201B.toInt(),
                    inner = 0xFF21332B.toInt(),
                    fg = 0xFFE8F5EE.toInt(),
                    muted = 0xFFA0B8AC.toInt()
                )
            }
            WidgetColorPreset.AMBER -> if (!isNight) {
                Quad(
                    outer = 0xFFFBF3EB.toInt(),
                    inner = 0xFFF3E0CA.toInt(),
                    fg = 0xFF241B12.toInt(),
                    muted = 0xFF6E5E4F.toInt()
                )
            } else {
                Quad(
                    outer = 0xFF221B15.toInt(),
                    inner = 0xFF33291F.toInt(),
                    fg = 0xFFF8EFE6.toInt(),
                    muted = 0xFFBDB0A2.toInt()
                )
            }
            WidgetColorPreset.ROSE -> if (!isNight) {
                Quad(
                    outer = 0xFFFBF0F4.toInt(),
                    inner = 0xFFF3DCE5.toInt(),
                    fg = 0xFF24171C.toInt(),
                    muted = 0xFF6E5861.toInt()
                )
            } else {
                Quad(
                    outer = 0xFF23181D.toInt(),
                    inner = 0xFF36252C.toInt(),
                    fg = 0xFFF9ECF1.toInt(),
                    muted = 0xFFC2ABB3.toInt()
                )
            }
            WidgetColorPreset.SLATE -> if (!isNight) {
                Quad(
                    outer = 0xFFF2F3F7.toInt(),
                    inner = 0xFFE2E4EC.toInt(),
                    fg = 0xFF191C20.toInt(),
                    muted = 0xFF5B5E66.toInt()
                )
            } else {
                Quad(
                    outer = 0xFF181A1E.toInt(),
                    inner = 0xFF262930.toInt(),
                    fg = 0xFFECEEF4.toInt(),
                    muted = 0xFFA5A8B2.toInt()
                )
            }
        }
    }
}
