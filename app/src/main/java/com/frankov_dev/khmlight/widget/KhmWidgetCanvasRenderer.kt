package com.frankov_dev.khmlight.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.frankov_dev.khmlight.R

object KhmWidgetCanvasRenderer {

    private data class Fonts(
        val bold: Typeface,
        val medium: Typeface,
        val regular: Typeface
    )

    private fun loadFonts(context: Context): Fonts {
        val bold = runCatching {
            ResourcesCompat.getFont(context, R.font.google_sans_bold)
        }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val medium = runCatching {
            ResourcesCompat.getFont(context, R.font.google_sans_medium)
        }.getOrNull() ?: Typeface.DEFAULT
        val regular = runCatching {
            ResourcesCompat.getFont(context, R.font.google_sans_regular)
        }.getOrNull() ?: Typeface.DEFAULT
        return Fonts(bold, medium, regular)
    }

    fun render(
        context: Context,
        kind: WidgetKind,
        data: ResolvedWidgetData,
        config: WidgetInstanceConfig,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        val palette = WidgetPaletteResolver.resolve(context, config.colorPreset, config.themeMode)
        val fonts = loadFonts(context)
        val density = context.resources.displayMetrics.density

        return when (kind) {
            WidgetKind.W1X1 -> render1x1(context, data, config, palette, fonts, density, widthDp, heightDp)
            WidgetKind.W1X4 -> render1x4(context, data, config, palette, fonts, density, widthDp, heightDp)
            WidgetKind.W2X2 -> render2x2(context, data, config, palette, fonts, density, widthDp, heightDp)
            WidgetKind.W4X2 -> render4x2(context, data, config, palette, fonts, density, widthDp, heightDp)
        }
    }

    /**
     * 1x4 Horizontal Signal Wave Widget — exact match to the reference mockup photo.
     */
    private fun render1x4(
        context: Context,
        data: ResolvedWidgetData,
        config: WidgetInstanceConfig,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        density: Float,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        val safeWdp = widthDp.coerceIn(220, 520)
        val safeHdp = heightDp.coerceIn(60, 150)
        val w = safeWdp * density
        val h = safeHdp * density
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Scale unit normalized to 90dp reference height
        val s = h / 90f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // 1. Outer Material 3 rounded container
        val outerRadius = minOf(h * 0.43f, 35f * s)
        fillPaint.color = palette.outerBg
        canvas.drawRoundRect(RectF(0f, 0f, w, h), outerRadius, outerRadius, fillPaint)

        val padX = 12f * s
        val padY = 9f * s

        // 2. Left Location Icon squircle (replaces queue number, tinted with chosen palette accent)
        val groupW = 48f * s
        val groupH = (h - padY * 2.2f).coerceIn(44f * s, 56f * s)
        val groupLeft = padX
        val groupTop = (h - groupH) / 2f
        val groupRect = RectF(groupLeft, groupTop, groupLeft + groupW, groupTop + groupH)
        fillPaint.color = palette.innerSurfaceBg
        canvas.drawRoundRect(groupRect, 16f * s, 16f * s, fillPaint)

        drawTintedVectorIcon(
            context = context,
            canvas = canvas,
            iconRes = data.iconRes,
            cx = groupRect.centerX(),
            cy = groupRect.centerY(),
            sizePx = 25f * s,
            tintColor = palette.iconColor
        )

        // 3. Subtle vertical hairline divider (as seen in reference photo)
        val dividerX = groupRect.right + 10f * s
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * s
            color = palette.dividerColor
        }
        canvas.drawLine(dividerX, h * 0.22f, dividerX, h * 0.78f, dividerPaint)

        // 4. Location Name (Queue) + "● Є світло"
        val statusLeft = dividerX + 10f * s
        val appNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.medium
            color = palette.fgMuted
            textSize = 11.2f * s
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
            textSize = 14.8f * s
        }

        val dotRadius = 4.3f * s
        val dotGap = 5.5f * s
        val dotColor = resolveDotColor(data.mood, palette)
        val rightGap = if (w < 335f * s) 10f * s else 12f * s
        val maxLeftColW = maxOf(
            76f * s,
            w - padX - statusLeft - rightGap - 128f * s
        )
        val maxStatusTextW = maxOf(62f * s, maxLeftColW - (dotRadius * 2f + dotGap))
        statusPaint.textSize = fitTextSize(statusPaint, data.statusTitle, maxStatusTextW, 14.8f * s, 11.2f * s)
        val statusW = dotRadius * 2f + dotGap + statusPaint.measureText(data.statusTitle)
        val headerText = ellipsizeToWidth(data.headerLabel, appNamePaint, maxOf(statusW, maxLeftColW))
        val headerW = appNamePaint.measureText(headerText)
        val leftColEnd = statusLeft + maxOf(statusW, headerW)

        val topLineY = h * 0.37f
        val bottomLineY = h * 0.63f
        canvas.drawText(
            headerText,
            statusLeft,
            topLineY - (appNamePaint.ascent() + appNamePaint.descent()) / 2f,
            appNamePaint
        )
        fillPaint.color = dotColor
        canvas.drawCircle(statusLeft + dotRadius, bottomLineY, dotRadius, fillPaint)
        canvas.drawText(
            data.statusTitle,
            statusLeft + dotRadius * 2f + dotGap,
            bottomLineY - (statusPaint.ascent() + statusPaint.descent()) / 2f,
            statusPaint
        )

        // 5. Right section: Top = Signal Wave | Bottom = Expressive Badge (B1-B5) + Next Change Pill
        val rightLeft = leftColEnd + rightGap
        val rightRight = w - padX
        val rightW = (rightRight - rightLeft).coerceAtLeast(70f * s)

        val waveRect = RectF(rightLeft, padY + 1f * s, rightRight, h * 0.38f)
        SignalWaveDrawer.draw(
            canvas = canvas,
            rect = waveRect,
            intervals = data.intervals,
            hasKnownSchedule = data.hasKnownSchedule,
            onColor = palette.signalOnColor,
            offColor = palette.signalOffColor,
            markerColor = palette.signalMarkerColor,
            currentMinute = data.currentMinute,
            showCurrentTimeMarker = data.showLiveTimeMarker,
            scale = s
        )

        val bottomTop = waveRect.bottom + 5f * s
        val bottomBottom = h - padY
        val bottomH = (bottomBottom - bottomTop).coerceAtLeast(28f * s)

        val showNextChange = !data.nextChangeTime.isNullOrBlank() && rightW >= 112f * s
        val gap = 6.5f * s

        val badgeRect: RectF
        val nextRect: RectF?
        if (showNextChange) {
            val nextW = (rightW * 0.37f).coerceIn(54f * s, 90f * s)
            val badgeW = (rightW - nextW - gap).coerceAtLeast(52f * s)
            badgeRect = RectF(rightLeft, bottomTop, rightLeft + badgeW, bottomBottom)
            nextRect = RectF(badgeRect.right + gap, bottomTop, rightRight, bottomBottom)
        } else {
            badgeRect = RectF(rightLeft, bottomTop, rightRight, bottomBottom)
            nextRect = null
        }

        drawExpressiveBadge(
            canvas = canvas,
            rect = badgeRect,
            shape = config.badgeShape,
            data = data,
            palette = palette,
            fonts = fonts,
            scale = s
        )

        if (nextRect != null && !data.nextChangeTime.isNullOrBlank()) {
            drawNextChangePill(
                canvas = canvas,
                rect = nextRect,
                nextTime = data.nextChangeTime,
                palette = palette,
                fonts = fonts,
                scale = s
            )
        }

        return bitmap
    }

    /**
     * 1x1 Compact Tile — outer shape itself uses the selected Expressive Shape (B1-B5).
     */
    private fun render1x1(
        context: Context,
        data: ResolvedWidgetData,
        config: WidgetInstanceConfig,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        density: Float,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        val sideDp = minOf(widthDp.coerceIn(64, 180), heightDp.coerceIn(64, 180))
        val w = widthDp.coerceIn(64, 180) * density
        val h = heightDp.coerceIn(64, 180) * density
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val s = (sideDp * density) / 88f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Outer shape = selected B1..B5 using the chosen color palette surface
        val outerPath = ExpressiveShapes.buildPath(config.badgeShape, RectF(1f, 1f, w - 1f, h - 1f))
        fillPaint.color = palette.innerSurfaceBg
        canvas.drawPath(outerPath, fillPaint)

        // Top: glowing location icon (tinted with power ON/OFF status) + queue badge ("🏠 2.1")
        val queuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
            textSize = 12.8f * s
            fontFeatureSettings = "tnum"
        }
        val iconSize = 15.5f * s
        val haloR = 10.5f * s
        val iconGap = 4.5f * s
        val qW = queuePaint.measureText(data.queueBadge)
        val totalTopW = iconSize + iconGap + qW
        val topStartX = (w - totalTopW) / 2f
        val topY = h * 0.27f

        val statusGlowColor = resolveDotColor(data.mood, palette)
        val iconCx = topStartX + iconSize / 2f
        fillPaint.color = ColorUtils.setAlphaComponent(statusGlowColor, 56)
        canvas.drawCircle(iconCx, topY, haloR, fillPaint)
        drawTintedVectorIcon(
            context = context,
            canvas = canvas,
            iconRes = data.iconRes,
            cx = iconCx,
            cy = topY,
            sizePx = iconSize,
            tintColor = statusGlowColor
        )
        canvas.drawText(
            data.queueBadge,
            topStartX + iconSize + iconGap,
            topY - (queuePaint.ascent() + queuePaint.descent()) / 2f,
            queuePaint
        )

        // Center: compact timer ("42 хв" / "2г 15хв")
        val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }
        timerPaint.textSize = fitTextSize(timerPaint, data.badgeCompactTimerText, w * 0.76f, 16.5f * s, 10.5f * s)
        drawCenteredText(canvas, data.badgeCompactTimerText, w / 2f, h * 0.54f, timerPaint)

        // Bottom: next change or short subtitle
        val subText = if (!data.nextChangeTime.isNullOrBlank()) {
            "О ${data.nextChangeTime}"
        } else {
            data.badgeSubtitle
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.medium
            color = palette.fgMuted
            textAlign = Paint.Align.CENTER
        }
        subPaint.textSize = fitTextSize(subPaint, subText, w * 0.74f, 10f * s, 7.5f * s)
        drawCenteredText(canvas, subText, w / 2f, h * 0.76f, subPaint)

        return bitmap
    }

    /**
     * 2x2 Square / Vertical Expressive Card — adapts to square and tall Pixel Launcher 2x2 grids.
     */
    private fun render2x2(
        context: Context,
        data: ResolvedWidgetData,
        config: WidgetInstanceConfig,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        density: Float,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        val safeWdp = widthDp.coerceIn(130, 320)
        val safeHdp = heightDp.coerceIn(130, 340)
        val w = safeWdp * density
        val h = safeHdp * density
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val s = minOf(w, h) / 165f
        // 0f on tall grids (e.g. Pixel Launcher), 1f on square 1:1 grids (config preview / Samsung / Nova)
        val compact = ((1.18f - (h / w)) / 0.18f).coerceIn(0f, 1f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Outer card
        val outerRadius = lerp(28f, 24f, compact) * s
        fillPaint.color = palette.outerBg
        canvas.drawRoundRect(RectF(0f, 0f, w, h), outerRadius, outerRadius, fillPaint)

        val pad = lerp(13f, 10f, compact) * s

        // 1. Header: Location icon squircle + "Дім (2.1)" & "● Є світло"
        val groupSize = lerp(40f, 33f, compact) * s
        val groupRect = RectF(pad, pad, pad + groupSize, pad + groupSize)
        fillPaint.color = palette.innerSurfaceBg
        canvas.drawRoundRect(groupRect, lerp(13f, 11f, compact) * s, lerp(13f, 11f, compact) * s, fillPaint)

        drawTintedVectorIcon(
            context = context,
            canvas = canvas,
            iconRes = data.iconRes,
            cx = groupRect.centerX(),
            cy = groupRect.centerY(),
            sizePx = groupSize * 0.56f,
            tintColor = palette.iconColor
        )

        val statusLeft = groupRect.right + lerp(10f, 8.5f, compact) * s
        val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.medium
            color = palette.fgMuted
            textSize = lerp(10.2f, 9.4f, compact) * s
        }
        val dotR = lerp(3.8f, 3.4f, compact) * s
        val dotGap = 5f * s
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
        }
        val maxStatusW = (w - pad) - (statusLeft + dotR * 2f + dotGap)
        statusPaint.textSize = fitTextSize(
            statusPaint,
            data.statusTitle,
            maxStatusW,
            lerp(13.5f, 12.2f, compact) * s,
            9.5f * s
        )
        val headerText = ellipsizeToWidth(data.headerLabel, appPaint, (w - pad) - statusLeft)
        canvas.drawText(
            headerText,
            statusLeft,
            groupRect.top + groupSize * 0.34f - (appPaint.ascent() + appPaint.descent()) / 2f,
            appPaint
        )
        val dotY = groupRect.top + groupSize * 0.72f
        fillPaint.color = resolveDotColor(data.mood, palette)
        canvas.drawCircle(statusLeft + dotR, dotY, dotR, fillPaint)
        canvas.drawText(
            data.statusTitle,
            statusLeft + dotR * 2f + dotGap,
            dotY - (statusPaint.ascent() + statusPaint.descent()) / 2f,
            statusPaint
        )

        // 2. Bottom row: Next Change pill + Daily total hours pill
        val footerH = lerp(34f, 29f, compact) * s
        val footerTop = h - pad - footerH
        val footerBottom = h - pad
        val gap = lerp(7f, 6f, compact) * s
        val footerScale = lerp(1f, 0.9f, compact) * s

        if (!data.nextChangeTime.isNullOrBlank()) {
            val halfW = (w - pad * 2f - gap) / 2f
            val nextRect = RectF(pad, footerTop, pad + halfW, footerBottom)
            val hoursRect = RectF(nextRect.right + gap, footerTop, w - pad, footerBottom)
            drawNextChangePill(canvas, nextRect, data.nextChangeTime, palette, fonts, footerScale)
            drawSummaryPill(canvas, hoursRect, data.dailyHoursShort.ifBlank { "—" }, "Без світла", palette, fonts, footerScale)
        } else {
            val hoursRect = RectF(pad, footerTop, w - pad, footerBottom)
            drawSummaryPill(
                canvas,
                hoursRect,
                data.dailyHoursShort.ifBlank { "—" },
                data.dailyHoursLong.ifBlank { "За добу" },
                palette,
                fonts,
                footerScale
            )
        }

        // 3. Signal Wave above footer
        val waveH = lerp(22f, 17f, compact) * s
        val gapBeforeFooter = lerp(7f, 5f, compact) * s
        val waveBottom = footerTop - gapBeforeFooter
        val waveTop = waveBottom - waveH
        val waveRect = RectF(pad, waveTop, w - pad, waveBottom)
        SignalWaveDrawer.draw(
            canvas = canvas,
            rect = waveRect,
            intervals = data.intervals,
            hasKnownSchedule = data.hasKnownSchedule,
            onColor = palette.signalOnColor,
            offColor = palette.signalOffColor,
            markerColor = palette.signalMarkerColor,
            currentMinute = data.currentMinute,
            showCurrentTimeMarker = data.showLiveTimeMarker,
            scale = lerp(1f, 0.9f, compact) * s
        )

        // 4. Center Hero: Expressive Time Badge (B1-B5) cleanly fitting between header and wave
        val gapAfterGroup = lerp(8f, 5f, compact) * s
        val gapBeforeWave = lerp(7f, 5f, compact) * s
        val badgeTop = groupRect.bottom + gapAfterGroup
        val badgeBottom = (waveTop - gapBeforeWave).coerceAtLeast(badgeTop + 16f * s)
        val badgeRect = RectF(pad, badgeTop, w - pad, badgeBottom)
        drawExpressiveBadge(
            canvas = canvas,
            rect = badgeRect,
            shape = config.badgeShape,
            data = data,
            palette = palette,
            fonts = fonts,
            scale = lerp(1.12f, 0.98f, compact) * s
        )

        return bitmap
    }

    /**
     * 4x2 Wide Dashboard — Top (2.1 + Status | Badge B1-B5 + Next Change),
     * Middle (Full-width Signal Wave with hour markers), Bottom (Exact interval capsules + total hours).
     */
    private fun render4x2(
        context: Context,
        data: ResolvedWidgetData,
        config: WidgetInstanceConfig,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        density: Float,
        widthDp: Int,
        heightDp: Int
    ): Bitmap {
        val safeWdp = widthDp.coerceIn(250, 540)
        val safeHdp = heightDp.coerceIn(120, 320)
        val w = safeWdp * density
        val h = safeHdp * density
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val s = minOf(w / 330f, h / 155f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // Outer container
        val outerRadius = 28f * s
        fillPaint.color = palette.outerBg
        canvas.drawRoundRect(RectF(0f, 0f, w, h), outerRadius, outerRadius, fillPaint)

        val pad = 14f * s

        // 1. TOP TIER: Left (Location Icon + divider + "Дім (2.1)" / ● Є світло) | Right (Badge B1-B5 + О 14:30)
        val topH = 46f * s
        val groupRect = RectF(pad, pad, pad + 46f * s, pad + topH)
        fillPaint.color = palette.innerSurfaceBg
        canvas.drawRoundRect(groupRect, 15f * s, 15f * s, fillPaint)

        drawTintedVectorIcon(
            context = context,
            canvas = canvas,
            iconRes = data.iconRes,
            cx = groupRect.centerX(),
            cy = groupRect.centerY(),
            sizePx = 25f * s,
            tintColor = palette.iconColor
        )

        val dividerX = groupRect.right + 9f * s
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * s
            color = palette.dividerColor
        }
        canvas.drawLine(dividerX, pad + 6f * s, dividerX, pad + topH - 6f * s, divPaint)

        val statusLeft = dividerX + 9f * s
        val appPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.medium
            color = palette.fgMuted
            textSize = 10.8f * s
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
            textSize = 14f * s
        }
        val dotR = 4f * s
        val dotY = pad + topH * 0.72f
        val rightGap = 10f * s
        val maxLeftW = maxOf(76f * s, w - pad - statusLeft - rightGap - 126f * s)
        val maxStatusTextW = maxOf(64f * s, maxLeftW - (dotR * 2f + 5f * s))
        statusPaint.textSize = fitTextSize(statusPaint, data.statusTitle, maxStatusTextW, 14f * s, 11.2f * s)
        val statusRowW = dotR * 2f + 5f * s + statusPaint.measureText(data.statusTitle)
        val headerText = ellipsizeToWidth(data.headerLabel, appPaint, maxOf(statusRowW, maxLeftW))

        canvas.drawText(
            headerText,
            statusLeft,
            pad + topH * 0.34f - (appPaint.ascent() + appPaint.descent()) / 2f,
            appPaint
        )
        fillPaint.color = resolveDotColor(data.mood, palette)
        canvas.drawCircle(statusLeft + dotR, dotY, dotR, fillPaint)
        canvas.drawText(
            data.statusTitle,
            statusLeft + dotR * 2f + 5f * s,
            dotY - (statusPaint.ascent() + statusPaint.descent()) / 2f,
            statusPaint
        )

        val leftEnd = statusLeft + maxOf(statusRowW, appPaint.measureText(headerText))
        val rightStart = leftEnd + rightGap
        val rightEnd = w - pad
        val rightW = (rightEnd - rightStart).coerceAtLeast(80f * s)

        if (!data.nextChangeTime.isNullOrBlank() && rightW >= 112f * s) {
            val nextW = (rightW * 0.38f).coerceIn(54f * s, 88f * s)
            val gap = 6.5f * s
            val badgeRect = RectF(rightStart, pad, rightEnd - nextW - gap, pad + topH)
            val nextRect = RectF(badgeRect.right + gap, pad, rightEnd, pad + topH)
            drawExpressiveBadge(canvas, badgeRect, config.badgeShape, data, palette, fonts, s)
            drawNextChangePill(canvas, nextRect, data.nextChangeTime, palette, fonts, s)
        } else {
            val badgeRect = RectF(rightStart, pad, rightEnd, pad + topH)
            drawExpressiveBadge(canvas, badgeRect, config.badgeShape, data, palette, fonts, s)
        }

        // 2. BOTTOM TIER: Exact Outage Interval chips + Total Hours
        val bottomH = 26f * s
        val bottomTop = h - pad - bottomH
        val bottomBottom = h - pad

        val hoursText = data.dailyHoursLong.ifBlank { data.dailyHoursShort }
        val hoursPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.medium
            color = palette.fgMuted
            textSize = 10.5f * s
            textAlign = Paint.Align.RIGHT
        }
        val hoursW = if (hoursText.isNotBlank()) hoursPaint.measureText(hoursText) + 10f * s else 0f
        if (hoursText.isNotBlank()) {
            drawCenteredText(canvas, hoursText, w - pad, (bottomTop + bottomBottom) / 2f, hoursPaint)
        }

        val chipsMaxRight = w - pad - hoursW
        drawIntervalChipsRow(
            canvas = canvas,
            left = pad,
            top = bottomTop,
            maxRight = chipsMaxRight,
            height = bottomH,
            data = data,
            palette = palette,
            fonts = fonts,
            scale = s
        )

        // 3. MIDDLE TIER: Full-width Signal Wave + Hour labels (00:00, 06:00, 12:00, 18:00, 24:00)
        val midTop = pad + topH + 10f * s
        val midBottom = bottomTop - 8f * s
        val labelSpace = 12f * s
        val waveRect = RectF(pad, midTop, w - pad, (midBottom - labelSpace).coerceAtLeast(midTop + 16f * s))

        SignalWaveDrawer.draw(
            canvas = canvas,
            rect = waveRect,
            intervals = data.intervals,
            hasKnownSchedule = data.hasKnownSchedule,
            onColor = palette.signalOnColor,
            offColor = palette.signalOffColor,
            markerColor = palette.signalMarkerColor,
            currentMinute = data.currentMinute,
            showCurrentTimeMarker = data.showLiveTimeMarker,
            scale = s
        )

        val hourLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.regular
            color = palette.fgMuted
            textSize = 8.8f * s
            fontFeatureSettings = "tnum"
        }
        val labelY = midBottom - 1f * s
        val marks = listOf(0 to "00:00", 6 to "06:00", 12 to "12:00", 18 to "18:00", 24 to "24:00")
        for ((hr, label) in marks) {
            val x = waveRect.left + (hr / 24f) * waveRect.width()
            hourLabelPaint.textAlign = when (hr) {
                0 -> Paint.Align.LEFT
                24 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            canvas.drawText(label, x, labelY, hourLabelPaint)
        }

        return bitmap
    }

    private fun drawExpressiveBadge(
        canvas: Canvas,
        rect: RectF,
        shape: BadgeShape,
        data: ResolvedWidgetData,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        scale: Float
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = resolveBadgeFillColor(data, palette)
        }
        val path = ExpressiveShapes.buildPath(shape, rect)
        canvas.drawPath(path, fillPaint)

        val timerStr = if (rect.width() < 98f * scale) {
            data.badgeCompactTimerText
        } else {
            data.badgeTimerText
        }
        val subStr = data.badgeSubtitle

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.badgeTextPrimary
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.regular
            color = palette.badgeTextMuted
            textAlign = Paint.Align.CENTER
        }

        val safeW = rect.width() * 0.78f
        timePaint.textSize = fitTextSize(timePaint, timerStr, safeW, 13.8f * scale, 9f * scale)
        subPaint.textSize = fitTextSize(subPaint, subStr, safeW, 8.6f * scale, 6f * scale)

        if (subStr.isNotBlank()) {
            drawCenteredText(canvas, timerStr, rect.centerX(), rect.top + rect.height() * 0.37f, timePaint)
            drawCenteredText(canvas, subStr, rect.centerX(), rect.top + rect.height() * 0.71f, subPaint)
        } else {
            drawCenteredText(canvas, timerStr, rect.centerX(), rect.centerY(), timePaint)
        }
    }

    private fun drawNextChangePill(
        canvas: Canvas,
        rect: RectF,
        nextTime: String,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        scale: Float
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.innerSurfaceBg
        }
        val r = minOf(rect.height() * 0.45f, 18f * scale)
        canvas.drawRoundRect(rect, r, r, fillPaint)

        val text = "О $nextTime"
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }
        val safeW = rect.width() * 0.84f
        timePaint.textSize = fitTextSize(timePaint, text, safeW, 13.5f * scale, 9.5f * scale)

        drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), timePaint)
    }

    private fun drawSummaryPill(
        canvas: Canvas,
        rect: RectF,
        mainText: String,
        subText: String,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        scale: Float
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.innerSurfaceBg
        }
        val r = minOf(rect.height() * 0.45f, 18f * scale)
        canvas.drawRoundRect(rect, r, r, fillPaint)

        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.bold
            color = palette.fgPrimary
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.regular
            color = palette.fgMuted
            textAlign = Paint.Align.CENTER
        }
        val safeW = rect.width() * 0.86f
        mainPaint.textSize = fitTextSize(mainPaint, mainText, safeW, 12.8f * scale, 9f * scale)
        subPaint.textSize = fitTextSize(subPaint, subText, safeW, 8.2f * scale, 6.4f * scale)

        drawCenteredText(canvas, mainText, rect.centerX(), rect.top + rect.height() * 0.37f, mainPaint)
        drawCenteredText(canvas, subText, rect.centerX(), rect.top + rect.height() * 0.71f, subPaint)
    }

    private fun drawIntervalChipsRow(
        canvas: Canvas,
        left: Float,
        top: Float,
        maxRight: Float,
        height: Float,
        data: ResolvedWidgetData,
        palette: ResolvedWidgetPalette,
        fonts: Fonts,
        scale: Float
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = palette.innerSurfaceBg
        }
        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.medium
            color = palette.fgPrimary
            textSize = 10f * scale
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
        }

        if (data.intervals.isEmpty()) {
            val text = if (data.hasKnownSchedule) "Сьогодні без відключень" else "Графік відсутній"
            chipTextPaint.textAlign = Paint.Align.LEFT
            drawCenteredText(canvas, text, left, top + height / 2f, chipTextPaint)
            return
        }

        var cursorX = left
        val gap = 6f * scale
        val padH = 9f * scale
        val radius = height / 2f

        for (interval in data.intervals) {
            val label = "${interval.start}–${interval.end}"
            val chipW = chipTextPaint.measureText(label) + padH * 2f
            if (cursorX + chipW > maxRight) break
            val rect = RectF(cursorX, top, cursorX + chipW, top + height)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            drawCenteredText(canvas, label, rect.centerX(), rect.centerY(), chipTextPaint)
            cursorX += chipW + gap
        }
    }

    private fun resolveDotColor(mood: WidgetPowerMood, palette: ResolvedWidgetPalette): Int {
        return when (mood) {
            WidgetPowerMood.POWER_ON -> palette.dotPowerOn
            WidgetPowerMood.POWER_OFF -> palette.dotPowerOff
            WidgetPowerMood.NEUTRAL -> palette.fgMuted
        }
    }

    private fun resolveBadgeFillColor(
        data: ResolvedWidgetData,
        palette: ResolvedWidgetPalette
    ): Int {
        return when {
            !data.hasKnownSchedule || data.nextChangeTime == null -> palette.badgeNeutralBg
            data.mood == WidgetPowerMood.POWER_OFF -> palette.badgeToPowerOnBg
            else -> palette.badgeToOutageBg
        }
    }

    private fun drawTintedVectorIcon(
        context: Context,
        canvas: Canvas,
        @DrawableRes iconRes: Int,
        cx: Float,
        cy: Float,
        sizePx: Float,
        tintColor: Int
    ) {
        val raw = ContextCompat.getDrawable(context, iconRes) ?: return
        val drawable = DrawableCompat.wrap(raw.mutate())
        DrawableCompat.setTint(drawable, tintColor)
        val half = (sizePx / 2f).toInt().coerceAtLeast(1)
        val left = cx.toInt() - half
        val top = cy.toInt() - half
        drawable.setBounds(left, top, left + half * 2, top + half * 2)
        drawable.draw(canvas)
    }

    private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + ellipsis
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val baseline = cy - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, cx, baseline, paint)
    }

    private fun fitTextSize(
        paint: Paint,
        text: String,
        maxWidth: Float,
        preferredSize: Float,
        minSize: Float
    ): Float {
        if (text.isEmpty() || maxWidth <= 0f) return preferredSize
        var size = preferredSize
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > minSize) {
            size -= 0.5f
            paint.textSize = size
        }
        return size
    }
}
