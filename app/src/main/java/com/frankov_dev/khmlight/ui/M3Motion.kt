package com.frankov_dev.khmlight.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs

object M3Motion {

    val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
    val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
    val STANDARD = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
    val SPRING_OVERSHOOT = OvershootInterpolator(1.35f)

    /**
     * Smooth Material 3 entrance transition between bottom navigation tabs without ghosting/overlap.
     */
    fun switchTab(fromView: View?, toView: View, movingRight: Boolean) {
        val density = toView.resources.displayMetrics.density
        val slideInPx = (if (movingRight) 14f else -14f) * density

        if (fromView != null && fromView !== toView) {
            fromView.animate().cancel()
            fromView.visibility = View.GONE
            fromView.alpha = 1f
            fromView.translationX = 0f
            fromView.scaleX = 1f
            fromView.scaleY = 1f
        }

        toView.animate().cancel()
        toView.visibility = View.VISIBLE
        toView.alpha = 0f
        toView.translationX = slideInPx
        toView.scaleX = 0.985f
        toView.scaleY = 0.985f
        toView.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(0L)
            .setDuration(210L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .start()
    }

    /**
     * Material 3 page transformer for Today <-> Tomorrow ViewPager2 swipe.
     */
    fun dayPagerTransformer(): ViewPager2.PageTransformer = ViewPager2.PageTransformer { page, position ->
        val absPos = abs(position).coerceIn(0f, 1f)
        val scale = 1f - (absPos * 0.045f)
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - (absPos * 0.35f)
    }

    /**
     * Staggered Material 3 cascade entrance for list items and interval rows.
     */
    fun animateCascadeItem(
        view: View,
        index: Int,
        baseDelayMs: Long = 0L,
        stepDelayMs: Long = 28L
    ) {
        val density = view.resources.displayMetrics.density
        val clampedIndex = index.coerceIn(0, 8)
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = 14f * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(baseDelayMs + clampedIndex * stepDelayMs)
            .setDuration(230L)
            .setInterpolator(EMPHASIZED_DECELERATE)
            .start()
    }

    /**
     * Non-intrusive spring press feedback for cards and buttons (preserves ripples and click listeners).
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachPressSpring(view: View, pressedScale: Float = 0.97f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(pressedScale)
                        .scaleY(pressedScale)
                        .setDuration(105L)
                        .setInterpolator(STANDARD)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(220L)
                        .setInterpolator(SPRING_OVERSHOOT)
                        .start()
                }
            }
            false
        }
    }

    /**
     * Expressive spring pop + crossfade for live widget preview and shape selection.
     */
    fun animatePreviewPop(view: View) {
        view.animate().cancel()
        view.scaleX = 0.95f
        view.scaleY = 0.95f
        view.alpha = 0.68f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(230L)
            .setInterpolator(SPRING_OVERSHOOT)
            .start()
    }

    /**
     * Smoothly morphs a MaterialCardView background color when it changes.
     */
    fun animateCardBackgroundColor(
        card: MaterialCardView,
        fromColor: Int?,
        toColor: Int,
        durationMs: Long = 260L
    ) {
        if (fromColor == null || fromColor == toColor) {
            card.setCardBackgroundColor(toColor)
            return
        }
        ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = durationMs
            interpolator = STANDARD
            addUpdateListener { anim ->
                card.setCardBackgroundColor(anim.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * Smoothly morphs any View's solid background color (e.g. top hero section inside a monolithic card).
     */
    fun animateViewBackgroundColor(
        view: View,
        fromColor: Int?,
        toColor: Int,
        durationMs: Long = 260L
    ) {
        if (fromColor == null || fromColor == toColor) {
            view.setBackgroundColor(toColor)
            return
        }
        ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
            duration = durationMs
            interpolator = STANDARD
            addUpdateListener { anim ->
                view.setBackgroundColor(anim.animatedValue as Int)
            }
            start()
        }
    }
}
