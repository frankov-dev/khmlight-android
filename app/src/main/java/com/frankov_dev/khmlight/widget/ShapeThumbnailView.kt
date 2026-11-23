package com.frankov_dev.khmlight.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * Renders a live geometric preview of a BadgeShape (B1-B5) inside WidgetConfigActivity.
 */
class ShapeThumbnailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var shape: BadgeShape = BadgeShape.B1_SQUIRCLE
        set(value) {
            field = value
            invalidate()
        }

    var isShapeSelected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = 3f * resources.displayMetrics.density
        val target = RectF(pad, pad, w - pad, h - pad)
        val path = ExpressiveShapes.buildPath(shape, target)

        fillPaint.color = if (isShapeSelected) {
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorPrimaryContainer,
                0xFFD6E3FF.toInt()
            )
        } else {
            MaterialColors.getColor(
                this,
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                0xFFE4E0F2.toInt()
            )
        }
        canvas.drawPath(path, fillPaint)

        if (isShapeSelected) {
            strokePaint.color = MaterialColors.getColor(
                this,
                androidx.appcompat.R.attr.colorPrimary,
                0xFF1E5AA8.toInt()
            )
            canvas.drawPath(path, strokePaint)
        }
    }
}
