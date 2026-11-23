package com.frankov_dev.khmlight.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.QueueSchedule
import com.frankov_dev.khmlight.databinding.BottomSheetScheduleRevisionsBinding
import com.frankov_dev.khmlight.ui.view.TimelineBarView
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ScheduleRevisionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetScheduleRevisionsBinding? = null
    private val binding get() = _binding!!

    var dateKey: String = ""
    var diff: QueueRevisionDiff? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetScheduleRevisionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val d = diff ?: run {
            dismiss()
            return
        }

        val ctx = requireContext()
        binding.tvRevisionSheetTitle.text =
            "${getString(R.string.queue_format, d.queueId)} • Зміни графіка"
        binding.tvRevisionSheetDate.text = TimeUtils.formatDateHeading(dateKey)

        // Delta badge styling
        val deltaText = d.formatDeltaShort().ifBlank { "Змінено" }
        binding.tvRevisionDeltaPill.text = deltaText
        val (deltaBg, deltaFg) = resolveDiffColors(d.deltaHours)
        binding.cardRevisionDeltaPill.setCardBackgroundColor(deltaBg)
        binding.cardRevisionDeltaPill.strokeColor = ColorUtils.setAlphaComponent(deltaFg, 90)
        binding.tvRevisionDeltaPill.setTextColor(deltaFg)

        binding.llRevisionsContainer.removeAllViews()

        // 1. Current active schedule card
        val currentCard = buildVersionCard(
            badgeLabel = "Актуальний графік",
            isCurrent = true,
            schedule = d.current,
            message = d.currentMessage,
            index = 0
        )
        binding.llRevisionsContainer.addView(currentCard)

        // 2. Previous revisions for the same day
        d.previousVersions.forEachIndexed { idx, prev ->
            val label = if (d.previousVersions.size == 1) {
                "Попередня версія"
            } else {
                "Попередня версія #${idx + 1}"
            }
            val prevCard = buildVersionCard(
                badgeLabel = label,
                isCurrent = false,
                schedule = prev.schedule,
                message = prev.operationalMessage,
                index = idx + 1
            )
            binding.llRevisionsContainer.addView(prevCard)
        }

        M3Motion.attachPressSpring(binding.btnCloseRevisions)
        binding.btnCloseRevisions.setOnClickListener { dismiss() }
    }

    private fun buildVersionCard(
        badgeLabel: String,
        isCurrent: Boolean,
        schedule: QueueSchedule,
        message: String?,
        index: Int
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density

        val surfaceLow = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerLow
        )
        val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val primaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        val onPrimaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val surfaceHigh = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerHigh
        )
        val onSurface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val outlineVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOutlineVariant
        )

        val cardBg = if (isCurrent) {
            ColorUtils.blendARGB(surfaceLow, primaryContainer, 0.24f)
        } else {
            surfaceLow
        }

        val card = MaterialCardView(ctx).apply {
            radius = 22f * density
            cardElevation = 0f
            setCardBackgroundColor(cardBg)
            strokeWidth = if (isCurrent) (1.5f * density).toInt().coerceAtLeast(2) else (1 * density).toInt()
            strokeColor = if (isCurrent) primary else outlineVariant
            alpha = if (isCurrent) 1f else 0.88f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = (12 * density).toInt()
            }
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * density).toInt()
            setPadding(p, p, p, p)
        }

        // Top row: Version badge + Total hours
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badgePill = MaterialCardView(ctx).apply {
            radius = 12f * density
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(if (isCurrent) primaryContainer else surfaceHigh)
        }
        val tvBadge = TextView(ctx).apply {
            text = badgeLabel
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isCurrent) onPrimaryContainer else onSurfaceVariant)
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
        }
        badgePill.addView(tvBadge)

        val tvHours = TextView(ctx).apply {
            text = getString(R.string.total_hours_off, TimeUtils.formatHours(schedule.totalHoursOff))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isCurrent) primary else onSurfaceVariant)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(badgePill)
        header.addView(tvHours)
        content.addView(header)

        // 24h mini timeline bar
        val timeline = TimelineBarView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (38 * density).toInt()
            ).apply {
                topMargin = (12 * density).toInt()
            }
            setData(
                intervals = schedule.intervals,
                currentMinute = if (isCurrent && TimeUtils.isToday(dateKey)) TimeUtils.currentMinuteOfDay() else null,
                showHourLabels = true,
                trackBaseColor = cardBg,
                animateReveal = true
            )
        }
        content.addView(timeline)

        // Exact intervals text
        val intervalsText = if (schedule.intervals.isEmpty()) {
            getString(R.string.no_outages_day)
        } else {
            schedule.intervals.joinToString("  •  ") { "${it.start}–${it.end}" }
        }
        val tvIntervals = TextView(ctx).apply {
            text = intervalsText
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface)
            fontFeatureSettings = "tnum"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (10 * density).toInt()
            }
        }
        content.addView(tvIntervals)

        // Operational message snippet if present
        if (!message.isNullOrBlank()) {
            val tvMsg = TextView(ctx).apply {
                text = message
                textSize = 12.5f
                setTextColor(onSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * density).toInt()
                }
            }
            content.addView(tvMsg)
        }

        card.addView(content)
        M3Motion.animateCascadeItem(card, index, baseDelayMs = 30L)
        return card
    }

    private fun resolveDiffColors(deltaHours: Double): Pair<Int, Int> {
        val ctx = requireContext()
        return when {
            deltaHours > 0.01 -> {
                val bg = MaterialColors.harmonizeWithPrimary(
                    ctx,
                    ContextCompat.getColor(ctx, R.color.status_power_off_bg)
                )
                val fg = MaterialColors.harmonizeWithPrimary(
                    ctx,
                    ContextCompat.getColor(ctx, R.color.status_power_off_fg)
                )
                bg to fg
            }
            deltaHours < -0.01 -> {
                val bg = MaterialColors.harmonizeWithPrimary(
                    ctx,
                    ContextCompat.getColor(ctx, R.color.status_power_on_bg)
                )
                val fg = MaterialColors.harmonizeWithPrimary(
                    ctx,
                    ContextCompat.getColor(ctx, R.color.status_power_on_fg)
                )
                bg to fg
            }
            else -> {
                val bg = MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorTertiaryContainer
                )
                val fg = MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorOnTertiaryContainer
                )
                bg to fg
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
