package com.frankov_dev.khmlight.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.OutageInterval
import com.frankov_dev.khmlight.ui.M3Motion
import com.google.android.material.color.MaterialColors

class TimelineBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var intervals: List<OutageInterval> = emptyList()
    private var lastAnimatedIntervalsKey: String? = null
    private var currentMinute: Int? = null
    private var showHourLabels: Boolean = false

    private var revealProgress: Float = 1f
    private var pulsePhase: Float = 0f
    private var revealAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val trackRect = RectF()
    private val clipPath = Path()

    private val density = resources.displayMetrics.density

    init {
        val primary = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.md_theme_light_primary)
        )
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            0xFFFFFFFF.toInt()
        )
        trackPaint.color = ColorUtils.blendARGB(surface, primary, 0.22f)

        val rawOutage = ContextCompat.getColor(context, R.color.timeline_light_off)
        outagePaint.color = MaterialColors.harmonizeWithPrimary(context, rawOutage)

        markerPaint.color = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.timeline_now_marker)
        )
        markerPaint.strokeWidth = 2.5f * density
        markerPaint.strokeCap = Paint.Cap.ROUND

        val labelColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0xFF666666.toInt()
        )
        labelPaint.color = labelColor
        labelPaint.textSize = 11f * density
        if (!isInEditMode) {
            labelPaint.typeface = ResourcesCompat.getFont(context, R.font.google_sans)
        }

        tickPaint.color = labelColor
        tickPaint.alpha = 70
        tickPaint.strokeWidth = 1f * density
    }

    fun setData(
        intervals: List<OutageInterval>,
        currentMinute: Int? = null,
        showHourLabels: Boolean = false,
        accentColor: Int? = null,
        trackBaseColor: Int? = null,
        animateReveal: Boolean = true
    ) {
        val newKey = intervals.joinToString("|") { "${it.start}-${it.end}" } + "|$showHourLabels"
        val shouldReveal = animateReveal && newKey != lastAnimatedIntervalsKey
        lastAnimatedIntervalsKey = newKey

        this.intervals = intervals
        this.currentMinute = currentMinute
        this.showHourLabels = showHourLabels

        val primary = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.md_theme_light_primary)
        )
        val surface = trackBaseColor ?: MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurface,
            0xFFFFFFFF.toInt()
        )

        if (accentColor != null) {
            trackPaint.color = ColorUtils.blendARGB(surface, accentColor, 0.16f)
            labelPaint.color = accentColor
            labelPaint.alpha = 215
            markerPaint.color = accentColor
            tickPaint.color = accentColor
            tickPaint.alpha = 65
        } else {
            trackPaint.color = ColorUtils.blendARGB(surface, primary, 0.22f)
            val defaultLabelColor = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                0xFF666666.toInt()
            )
            labelPaint.color = defaultLabelColor
            labelPaint.alpha = 255
            markerPaint.color = MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorOnSurface,
                ContextCompat.getColor(context, R.color.timeline_now_marker)
            )
            tickPaint.color = defaultLabelColor
            tickPaint.alpha = 70
        }

        if (shouldReveal && !isInEditMode) {
            startRevealAnimation()
        } else if (revealAnimator?.isRunning != true) {
            revealProgress = 1f
            invalidate()
        }
        updatePulseState()
    }

    private fun startRevealAnimation() {
        revealAnimator?.cancel()
        revealProgress = 0f
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420L
            interpolator = M3Motion.EMPHASIZED_DECELERATE
            addUpdateListener { anim ->
                revealProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updatePulseState() {
        val shouldPulse = isAttachedToWindow && visibility == VISIBLE && currentMinute != null && showHourLabels && !isInEditMode
        if (shouldPulse) {
            if (pulseAnimator?.isRunning != true) {
                pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1500L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    interpolator = M3Motion.STANDARD
                    addUpdateListener { anim ->
                        pulsePhase = anim.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            pulsePhase = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulseState()
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updatePulseState()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val labelSpace = if (showHourLabels) 18f * density else 0f
        val barTop = if (currentMinute != null) 4f * density else 0f
        val barBottom = (h - labelSpace - (if (currentMinute != null) 4f * density else 0f))
            .coerceAtLeast(barTop + 8f * density)
        val barHeight = barBottom - barTop
        val radius = barHeight / 2f

        trackRect.set(0f, barTop, w, barBottom)

        // Draw clipped rounded bar with animated outage segments
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(trackRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        canvas.drawRect(trackRect, trackPaint)

        val sweepRight = w * revealProgress
        for (interval in intervals) {
            val startX = (interval.startMinutes / 1440f) * w
            val endX = (interval.endMinutes / 1440f) * w
            val animatedEndX = minOf(endX, sweepRight)
            if (animatedEndX > startX) {
                canvas.drawRect(
                    startX,
                    barTop,
                    animatedEndX,
                    barBottom,
                    outagePaint
                )
            }
        }

        // Subtle 6-hour grid lines inside bar
        for (hour in intArrayOf(6, 12, 18)) {
            val x = (hour / 24f) * w
            canvas.drawLine(x, barTop, x, barBottom, tickPaint)
        }
        canvas.restore()

        // Current time marker with smooth entrance & soft breathing beacon centered on the bar
        currentMinute?.let { min ->
            val clamped = min.coerceIn(0, 1440)
            val targetX = (clamped / 1440f) * w
            val x = targetX * revealProgress
            val pad = 2f * density
            val barCenterY = (barTop + barBottom) / 2f

            if (showHourLabels) {
                val haloRadius = (5.2f + 2.8f * pulsePhase) * density
                val haloAlpha = (82f - 46f * pulsePhase).toInt().coerceIn(20, 95)
                val dotX = x.coerceIn(haloRadius, (w - haloRadius).coerceAtLeast(haloRadius))
                markerHaloPaint.color = ColorUtils.setAlphaComponent(markerPaint.color, haloAlpha)
                canvas.drawCircle(dotX, barCenterY, haloRadius, markerHaloPaint)
            }

            canvas.drawLine(x, barTop - pad, x, barBottom + pad, markerPaint)

            if (showHourLabels) {
                val dotRadius = 3.2f * density
                val dotX = x.coerceIn(dotRadius, (w - dotRadius).coerceAtLeast(dotRadius))
                markerHaloPaint.color = markerPaint.color
                canvas.drawCircle(dotX, barCenterY, dotRadius, markerHaloPaint)
            }
        }

        // Hour labels below bar
        if (showHourLabels) {
            val textY = h - 2f * density
            val marks = listOf(0 to "00:00", 6 to "06:00", 12 to "12:00", 18 to "18:00", 24 to "24:00")
            for ((hour, label) in marks) {
                val x = (hour / 24f) * w
                labelPaint.textAlign = when (hour) {
                    0 -> Paint.Align.LEFT
                    24 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                canvas.drawText(label, x, textY, labelPaint)
            }
        }
    }
}
