package com.frankov_dev.khmlight.ui.adapters

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.QueueSchedule
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.databinding.ItemQueueCardBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class QueueCardAdapter(
    private val onRevisionBadgeClicked: ((String, QueueRevisionDiff) -> Unit)? = null
) : RecyclerView.Adapter<QueueCardAdapter.ViewHolder>() {

    private var items: List<QueueSchedule> = emptyList()
    private var daySchedule: DaySchedule? = null
    private var savedLocations: List<SavedLocation> = emptyList()
    private var isToday: Boolean = false
    private var currentMinute: Int? = null
    private var lastDatasetSignature: String? = null
    private var lastLocationsSignature: String? = null
    private val animatedPositions = HashSet<Int>()

    fun submitData(
        queues: List<QueueSchedule>,
        savedLocations: List<SavedLocation>,
        isToday: Boolean,
        currentMinute: Int?,
        daySchedule: DaySchedule? = null
    ) {
        val sorted = queues.sortedBy { it.queueId }
        val signature = "$isToday|${daySchedule?.date}|${daySchedule?.previousSchedules?.size}|" +
            sorted.joinToString("|") { q ->
                "${q.queueId}:${q.intervals.joinToString(",") { "${it.start}-${it.end}" }}"
            }
        val locSig = savedLocations.joinToString("|") { "${it.id}:${it.name}:${it.queueId}:${it.icon.name}" }
        val dataChanged = signature != lastDatasetSignature
        val locationsChanged = locSig != lastLocationsSignature
        lastDatasetSignature = signature
        lastLocationsSignature = locSig

        this.items = sorted
        this.daySchedule = daySchedule
        this.savedLocations = savedLocations
        this.isToday = isToday
        this.currentMinute = currentMinute

        if (dataChanged) {
            animatedPositions.clear()
            notifyDataSetChanged()
        } else if (locationsChanged || isToday) {
            notifyItemRangeChanged(0, items.size, PAYLOAD_TICK)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shouldAnimate = animatedPositions.add(position)
        holder.bind(items[position], position, shouldAnimate)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            holder.bind(items[position], position, shouldAnimate = false)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemQueueCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QueueSchedule, position: Int, shouldAnimate: Boolean) {
            val ctx = binding.root.context
            val matchingLocations = savedLocations.filter { it.queueId == item.queueId }
            val isMy = matchingLocations.isNotEmpty()

            binding.tvQueueTitle.text = ctx.getString(R.string.queue_format, item.queueId)
            binding.tvQueueHours.text = TimeUtils.formatHours(item.totalHoursOff)

            val density = ctx.resources.displayMetrics.density
            val surfaceLow = MaterialColors.getColor(
                binding.queueCard,
                com.google.android.material.R.attr.colorSurfaceContainerLow
            )
            val primary = MaterialColors.getColor(
                binding.queueCard,
                androidx.appcompat.R.attr.colorPrimary
            )
            val onPrimary = MaterialColors.getColor(
                binding.queueCard,
                com.google.android.material.R.attr.colorOnPrimary
            )
            val primaryContainer = MaterialColors.getColor(
                binding.queueCard,
                com.google.android.material.R.attr.colorPrimaryContainer
            )
            val onSurface = MaterialColors.getColor(
                binding.queueCard,
                com.google.android.material.R.attr.colorOnSurface
            )
            val onSurfaceVariant = MaterialColors.getColor(
                binding.queueCard,
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )

            binding.tvQueueTitle.setTextColor(if (isMy) primary else onSurface)
            binding.tvQueueHours.setTextColor(onSurfaceVariant)
            binding.tvQueueIntervals.setTextColor(onSurface)

            if (isMy) {
                val cardBg = ColorUtils.blendARGB(surfaceLow, primaryContainer, 0.28f)
                binding.queueCard.strokeWidth = (1.5f * density).toInt().coerceAtLeast(2)
                binding.queueCard.strokeColor = primary
                binding.queueCard.setCardBackgroundColor(cardBg)
            } else {
                binding.queueCard.strokeWidth = 0
                binding.queueCard.setCardBackgroundColor(surfaceLow)
            }

            val revDiff = daySchedule?.computeQueueDiff(item.queueId)
            if (isMy || revDiff != null) {
                binding.hsvQueueLocationTags.visibility = View.VISIBLE
                binding.llQueueLocationTags.removeAllViews()

                for (loc in matchingLocations) {
                    val pill = MaterialCardView(ctx).apply {
                        radius = 12f * density
                        cardElevation = 0f
                        strokeWidth = 0
                        setCardBackgroundColor(primary)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = (6 * density).toInt()
                        }
                    }
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(
                            (8 * density).toInt(),
                            (3 * density).toInt(),
                            (9 * density).toInt(),
                            (3 * density).toInt()
                        )
                    }
                    val iv = ImageView(ctx).apply {
                        setImageResource(loc.icon.iconRes)
                        imageTintList = ColorStateList.valueOf(onPrimary)
                        layoutParams = LinearLayout.LayoutParams(
                            (13 * density).toInt(),
                            (13 * density).toInt()
                        ).apply {
                            marginEnd = (4 * density).toInt()
                        }
                    }
                    val tv = TextView(ctx).apply {
                        text = loc.name
                        textSize = 11.5f
                        setTextColor(onPrimary)
                        maxLines = 1
                    }
                    row.addView(iv)
                    row.addView(tv)
                    pill.addView(row)
                    binding.llQueueLocationTags.addView(pill)
                }

                if (revDiff != null) {
                    val (diffBg, diffFg) = when {
                        revDiff.deltaHours > 0.01 -> {
                            MaterialColors.harmonizeWithPrimary(ctx, ContextCompat.getColor(ctx, R.color.status_power_off_bg)) to
                                MaterialColors.harmonizeWithPrimary(ctx, ContextCompat.getColor(ctx, R.color.status_power_off_fg))
                        }
                        revDiff.deltaHours < -0.01 -> {
                            MaterialColors.harmonizeWithPrimary(ctx, ContextCompat.getColor(ctx, R.color.status_power_on_bg)) to
                                MaterialColors.harmonizeWithPrimary(ctx, ContextCompat.getColor(ctx, R.color.status_power_on_fg))
                        }
                        else -> {
                            MaterialColors.getColor(binding.queueCard, com.google.android.material.R.attr.colorTertiaryContainer) to
                                MaterialColors.getColor(binding.queueCard, com.google.android.material.R.attr.colorOnTertiaryContainer)
                        }
                    }
                    val diffPill = MaterialCardView(ctx).apply {
                        radius = 12f * density
                        cardElevation = 0f
                        setCardBackgroundColor(diffBg)
                        strokeWidth = (1 * density).toInt()
                        strokeColor = ColorUtils.setAlphaComponent(diffFg, 95)
                        isClickable = true
                        isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = (6 * density).toInt()
                        }
                    }
                    val tvDiff = TextView(ctx).apply {
                        text = "${revDiff.formatBadgeText()} ›"
                        textSize = 11.5f
                        setTextColor(diffFg)
                        setPadding(
                            (9 * density).toInt(),
                            (3 * density).toInt(),
                            (9 * density).toInt(),
                            (3 * density).toInt()
                        )
                    }
                    diffPill.addView(tvDiff)
                    M3Motion.attachPressSpring(diffPill, pressedScale = 0.92f)
                    diffPill.setOnClickListener {
                        val date = daySchedule?.date ?: return@setOnClickListener
                        onRevisionBadgeClicked?.invoke(date, revDiff)
                    }
                    binding.llQueueLocationTags.addView(diffPill)
                }
            } else {
                binding.hsvQueueLocationTags.visibility = View.GONE
            }

            if (isToday && currentMinute != null) {
                val status = TimeUtils.computeLiveStatus(item.intervals, currentMinute!!)
                binding.tvQueueStatusDot.visibility = View.VISIBLE
                if (status.isPowerOff) {
                    binding.tvQueueStatusDot.text = "● ${ctx.getString(R.string.status_power_off)}"
                    val offColor = MaterialColors.harmonizeWithPrimary(
                        ctx,
                        ContextCompat.getColor(ctx, R.color.status_power_off_fg)
                    )
                    binding.tvQueueStatusDot.setTextColor(offColor)
                } else {
                    binding.tvQueueStatusDot.text = "● ${ctx.getString(R.string.status_power_on)}"
                    val onColor = MaterialColors.harmonizeWithPrimary(
                        ctx,
                        ContextCompat.getColor(ctx, R.color.status_power_on_fg)
                    )
                    binding.tvQueueStatusDot.setTextColor(onColor)
                }
            } else {
                binding.tvQueueStatusDot.visibility = View.GONE
            }

            binding.miniTimelineBar.setData(
                intervals = item.intervals,
                currentMinute = if (isToday) currentMinute else null,
                showHourLabels = false,
                trackBaseColor = surfaceLow,
                animateReveal = shouldAnimate
            )

            binding.tvQueueIntervals.text = if (item.intervals.isEmpty()) {
                ctx.getString(R.string.no_outages_day)
            } else {
                item.intervals.joinToString("  •  ") { "${it.start}–${it.end}" }
            }

            if (shouldAnimate) {
                M3Motion.animateCascadeItem(binding.queueCard, position)
            }
        }
    }

    companion object {
        private const val PAYLOAD_TICK = "tick"
    }
}
