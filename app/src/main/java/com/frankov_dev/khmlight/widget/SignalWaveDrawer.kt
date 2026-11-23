package com.frankov_dev.khmlight.widget

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.frankov_dev.khmlight.data.OutageInterval
import kotlin.math.abs

data class SignalWaveSegment(
    val startMinute: Int,
    val endMinute: Int,
    val isOutage: Boolean
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
}

/**
 * Draws the orthogonal rounded-corner digital Signal Wave matching the reference design:
 * - Upper level (yHigh) = power ON (neutral gray)
 * - Lower level (yLow) = scheduled outage (soft pink)
 * - Orthogonal vertical step transitions with smooth 90-degree rounded corners (quadTo)
 * - Optional vertical dashed current-time hairline + solid dot on the wave
 */
object SignalWaveDrawer {

    fun buildSegments(intervals: List<OutageInterval>): List<SignalWaveSegment> {
        val merged = mergeIntervals(intervals)
        if (merged.isEmpty()) {
            return listOf(SignalWaveSegment(0, 1440, isOutage = false))
        }

        val result = mutableListOf<SignalWaveSegment>()
        var cursor = 0
        for ((s, e) in merged) {
            if (s > cursor) {
                result.add(SignalWaveSegment(cursor, s, isOutage = false))
            }
            if (e > s) {
                result.add(SignalWaveSegment(s, e, isOutage = true))
            }
            cursor = maxOf(cursor, e)
        }
        if (cursor < 1440) {
            result.add(SignalWaveSegment(cursor, 1440, isOutage = false))
        }
        return result
    }

    private fun mergeIntervals(intervals: List<OutageInterval>): List<Pair<Int, Int>> {
        val valid = intervals
            .map { it.startMinutes.coerceIn(0, 1440) to it.endMinutes.coerceIn(0, 1440) }
            .filter { (s, e) -> e > s }
            .sortedBy { it.first }
        if (valid.isEmpty()) return emptyList()

        val merged = mutableListOf<Pair<Int, Int>>()
        var (curS, curE) = valid[0]
        for (i in 1 until valid.size) {
            val (s, e) = valid[i]
            if (s <= curE) {
                curE = maxOf(curE, e)
            } else {
                merged.add(curS to curE)
                curS = s
                curE = e
            }
        }
        merged.add(curS to curE)
        return merged
    }

    fun draw(
        canvas: Canvas,
        rect: RectF,
        intervals: List<OutageInterval>,
        hasKnownSchedule: Boolean,
        onColor: Int,
        offColor: Int,
        markerColor: Int,
        currentMinute: Int?,
        showCurrentTimeMarker: Boolean,
        scale: Float
    ) {
        val strokePx = 2.2f * scale
        val padX = strokePx + 1.5f * scale
        val yHigh = rect.top + strokePx + 2.5f * scale
        val yLow = (rect.bottom - strokePx - 2.5f * scale).coerceAtLeast(yHigh + 6f * scale)
        val yMid = (yHigh + yLow) / 2f
        val startX = rect.left + padX
        val usableW = (rect.width() - 2f * padX).coerceAtLeast(10f)

        if (!hasKnownSchedule) {
            val unknownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                color = onColor
                alpha = 140
                pathEffect = DashPathEffect(floatArrayOf(4.5f * scale, 5.5f * scale), 0f)
            }
            canvas.drawLine(startX, yMid, startX + usableW, yMid, unknownPaint)
            return
        }

        val segments = buildSegments(intervals)
        val verticalSpan = abs(yLow - yHigh)
        val maxCornerR = (4.8f * scale).coerceAtMost(verticalSpan * 0.46f)

        val wavePath = Path()
        for (i in segments.indices) {
            val seg = segments[i]
            val segStartX = startX + (seg.startMinute / 1440f) * usableW
            val segEndX = startX + (seg.endMinute / 1440f) * usableW
            val segY = if (seg.isOutage) yLow else yHigh

            if (i == 0) {
                wavePath.moveTo(segStartX, segY)
            }

            if (i < segments.lastIndex) {
                val next = segments[i + 1]
                val nextEndX = startX + (next.endMinute / 1440f) * usableW
                val nextY = if (next.isOutage) yLow else yHigh
                val prevW = (segEndX - segStartX).coerceAtLeast(0f)
                val nextW = (nextEndX - segEndX).coerceAtLeast(0f)

                val r = minOf(maxCornerR, prevW * 0.45f, nextW * 0.45f)
                val dir = if (nextY > segY) 1f else -1f

                // Horizontal approach -> 90-deg rounded corner -> vertical step -> 90-deg rounded corner
                wavePath.lineTo(segEndX - r, segY)
                wavePath.quadTo(segEndX, segY, segEndX, segY + dir * r)
                wavePath.lineTo(segEndX, nextY - dir * r)
                wavePath.quadTo(segEndX, nextY, segEndX + r, nextY)
            } else {
                wavePath.lineTo(segEndX, segY)
            }
        }

        val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = onColor
        }
        val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = offColor
        }

        // Top half (power ON level + upper corner) -> neutral gray
        val splitY = yHigh + verticalSpan * 0.34f
        canvas.save()
        canvas.clipRect(rect.left, rect.top - 4f * scale, rect.right, splitY)
        canvas.drawPath(wavePath, onPaint)
        canvas.restore()

        // Lower 2/3 (vertical drop/rise + bottom outage trough) -> soft pink
        canvas.save()
        canvas.clipRect(rect.left, splitY, rect.right, rect.bottom + 4f * scale)
        canvas.drawPath(wavePath, offPaint)
        canvas.restore()

        // Vertical dashed current-time hairline + solid dot on the wave
        if (showCurrentTimeMarker && currentMinute != null) {
            val clamped = currentMinute.coerceIn(0, 1440)
            val activeSeg = segments.firstOrNull { clamped in it.startMinute until it.endMinute }
                ?: segments.last()
            val dotX = startX + (clamped / 1440f) * usableW
            val dotY = if (activeSeg.isOutage) yLow else yHigh

            val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.1f * scale
                color = onColor
                alpha = 145
                pathEffect = DashPathEffect(floatArrayOf(2.6f * scale, 2.6f * scale), 0f)
            }
            canvas.drawLine(dotX, rect.top, dotX, rect.bottom, hairlinePaint)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = markerColor
            }
            canvas.drawCircle(dotX, dotY, 3.1f * scale, dotPaint)
        }
    }
}
