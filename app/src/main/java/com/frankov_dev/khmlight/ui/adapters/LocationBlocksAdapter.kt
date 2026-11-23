package com.frankov_dev.khmlight.ui.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.LocationQueueGroup
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.data.groupByQueuePreservingOrder
import com.frankov_dev.khmlight.databinding.ItemIntervalRowBinding
import com.frankov_dev.khmlight.databinding.ItemLocationBlockBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.color.MaterialColors
import java.util.Collections

class LocationBlocksAdapter(
    private val onEditLocation: (SavedLocation) -> Unit,
    private val onEditGroup: (LocationQueueGroup) -> Unit,
    private val onShowRevisions: (String, QueueRevisionDiff) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onOrderCommitted: (List<SavedLocation>) -> Unit
) : RecyclerView.Adapter<LocationBlocksAdapter.BlockViewHolder>() {

    private val groups = mutableListOf<LocationQueueGroup>()
    private var daySchedule: DaySchedule? = null
    private var lastStructureKey: String? = null
    private var isDragging = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = groups[position].queueId.hashCode().toLong()

    fun submitData(newLocations: List<SavedLocation>, schedule: DaySchedule?) {
        if (isDragging) return
        val newGroups = newLocations.groupByQueuePreservingOrder()
        val newKey = schedule?.date + "|" + newGroups.joinToString("|") { g ->
            g.queueId + ":" + g.locations.joinToString(",") { "${it.id}:${it.name}:${it.icon.name}" }
        }
        val structureChanged = newKey != lastStructureKey
        lastStructureKey = newKey

        this.daySchedule = schedule
        if (structureChanged) {
            groups.clear()
            groups.addAll(newGroups)
            notifyDataSetChanged()
        } else {
            notifyItemRangeChanged(0, groups.size, PAYLOAD_LIVE_TICK)
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in groups.indices || toPosition !in groups.indices) return false
        Collections.swap(groups, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun onDragStateChanged(dragging: Boolean) {
        val wasDragging = isDragging
        isDragging = dragging
        if (wasDragging && !dragging) {
            lastStructureKey = daySchedule?.date + "|" + groups.joinToString("|") { g ->
                g.queueId + ":" + g.locations.joinToString(",") { "${it.id}:${it.name}:${it.icon.name}" }
            }
            onOrderCommitted(groups.flatMap { it.locations })
        }
    }

    override fun getItemCount(): Int = groups.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockViewHolder {
        val binding = ItemLocationBlockBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BlockViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlockViewHolder, position: Int) {
        holder.bind(groups[position], groups.size > 1)
    }

    override fun onBindViewHolder(
        holder: BlockViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bind(groups[position], groups.size > 1)
    }

    inner class BlockViewHolder(
        val binding: ItemLocationBlockBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastHeroBgColor: Int? = null
        private var lastIntervalsSignature: String? = null
        private val rowBindings = mutableListOf<ItemIntervalRowBinding>()

        @SuppressLint("ClickableViewAccessibility")
        fun bind(group: LocationQueueGroup, showDragHandle: Boolean) {
            val ctx = binding.root.context
            val schedule = daySchedule ?: return
            val density = ctx.resources.displayMetrics.density
            val primaryLoc = group.primary
            val extraCount = group.locations.size - 1

            val dateKey = schedule.date
            val queueSchedule = schedule.queues[group.queueId]
            val intervals = queueSchedule?.intervals.orEmpty()
            val totalHours = queueSchedule?.totalHoursOff ?: 0.0
            val isToday = TimeUtils.isToday(dateKey)
            val nowMin = TimeUtils.currentMinuteOfDay()

            val signature = "${group.queueId}|$dateKey|" +
                intervals.joinToString(",") { "${it.start}-${it.end}" }
            val scheduleChanged = signature != lastIntervalsSignature
            lastIntervalsSignature = signature

            binding.ivLocationIcon.setImageResource(primaryLoc.icon.iconRes)
            binding.tvLocationName.text = primaryLoc.name
            binding.tvLocationName.maxWidth = ((if (extraCount > 0) 118 else 160) * density).toInt()

            if (extraCount > 0) {
                binding.cardExtraLocationsCount.visibility = View.VISIBLE
                binding.tvExtraLocationsCount.text = "+$extraCount"
                M3Motion.attachPressSpring(binding.cardExtraLocationsCount, pressedScale = 0.92f)
                binding.cardExtraLocationsCount.setOnClickListener {
                    onEditGroup(group)
                }
            } else {
                binding.cardExtraLocationsCount.visibility = View.GONE
                binding.cardExtraLocationsCount.setOnClickListener(null)
            }

            binding.tvLocationQueueBadge.text = ctx.getString(R.string.queue_format, group.queueId)
            binding.tvTotalHoursBadge.text =
                ctx.getString(R.string.total_hours_off, TimeUtils.formatHours(totalHours))
            binding.tvScheduleStripTitle.text = ctx.getString(R.string.intervals_header)

            M3Motion.attachPressSpring(binding.btnEditLocation, pressedScale = 0.88f)
            binding.btnEditLocation.setOnClickListener {
                if (extraCount == 0) {
                    onEditLocation(primaryLoc)
                } else {
                    onEditGroup(group)
                }
            }

            // Compact clickable revision diff pill (shown ONLY if this queue's intervals changed vs previous_schedules)
            val revDiff = schedule.computeQueueDiff(group.queueId)
            if (revDiff != null) {
                binding.cardRevisionDiffBanner.visibility = View.VISIBLE
                binding.tvRevisionDiffText.text = "${revDiff.formatBadgeText()} ›"
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
                        MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorTertiaryContainer) to
                            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnTertiaryContainer)
                    }
                }
                binding.cardRevisionDiffBanner.setCardBackgroundColor(diffBg)
                binding.cardRevisionDiffBanner.strokeColor = ColorUtils.setAlphaComponent(diffFg, 95)
                binding.tvRevisionDiffText.setTextColor(diffFg)
                M3Motion.attachPressSpring(binding.cardRevisionDiffBanner, pressedScale = 0.92f)
                binding.cardRevisionDiffBanner.setOnClickListener {
                    onShowRevisions(dateKey, revDiff)
                }
            } else {
                binding.cardRevisionDiffBanner.visibility = View.GONE
                binding.cardRevisionDiffBanner.setOnClickListener(null)
            }

            binding.ivDragHandle.visibility = if (showDragHandle) View.VISIBLE else View.GONE
            if (showDragHandle) {
                binding.ivDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            } else {
                binding.ivDragHandle.setOnTouchListener(null)
            }

            // Hero status colors & text
            val heroBgColor: Int
            val heroFgColor: Int
            if (isToday) {
                val liveStatus = TimeUtils.computeLiveStatus(intervals, nowMin)
                if (liveStatus.isPowerOff) {
                    heroBgColor = MaterialColors.harmonizeWithPrimary(
                        ctx,
                        ContextCompat.getColor(ctx, R.color.status_power_off_bg)
                    )
                    heroFgColor = MaterialColors.harmonizeWithPrimary(
                        ctx,
                        ContextCompat.getColor(ctx, R.color.status_power_off_fg)
                    )
                    binding.tvStatusTitle.text = ctx.getString(R.string.status_power_off)
                    val mins = liveStatus.minutesUntilChange ?: 0
                    val endStr = liveStatus.nextChangeTime ?: ""
                    binding.tvStatusSubtitle.text = ctx.getString(
                        R.string.until_power_on,
                        TimeUtils.formatDurationMinutes(mins),
                        endStr
                    )
                } else {
                    heroBgColor = MaterialColors.getColor(
                        binding.heroSection,
                        com.google.android.material.R.attr.colorPrimaryContainer
                    )
                    heroFgColor = MaterialColors.getColor(
                        binding.heroSection,
                        com.google.android.material.R.attr.colorOnPrimaryContainer
                    )
                    binding.tvStatusTitle.text = ctx.getString(R.string.status_power_on)
                    binding.tvStatusSubtitle.text =
                        if (liveStatus.nextChangeTime != null && liveStatus.minutesUntilChange != null) {
                            ctx.getString(
                                R.string.until_outage,
                                TimeUtils.formatDurationMinutes(liveStatus.minutesUntilChange),
                                liveStatus.nextChangeTime
                            )
                        } else {
                            ctx.getString(R.string.no_more_outages_today)
                        }
                }
            } else {
                heroBgColor = MaterialColors.getColor(
                    binding.heroSection,
                    com.google.android.material.R.attr.colorSecondaryContainer
                )
                heroFgColor = MaterialColors.getColor(
                    binding.heroSection,
                    com.google.android.material.R.attr.colorOnSecondaryContainer
                )
                binding.tvStatusTitle.text = TimeUtils.formatDateHeading(dateKey)
                binding.tvStatusSubtitle.text = if (intervals.isEmpty()) {
                    ctx.getString(R.string.no_outages_day)
                } else {
                    ctx.getString(R.string.status_scheduled_day)
                }
            }

            M3Motion.animateViewBackgroundColor(binding.heroSection, lastHeroBgColor, heroBgColor)
            lastHeroBgColor = heroBgColor

            val fgTint = ColorStateList.valueOf(heroFgColor)
            val pillBgColor = ColorUtils.blendARGB(heroBgColor, heroFgColor, 0.14f)
            binding.ivLocationIcon.imageTintList = fgTint
            binding.tvLocationName.setTextColor(heroFgColor)
            binding.cardExtraLocationsCount.setCardBackgroundColor(
                ColorUtils.blendARGB(heroBgColor, heroFgColor, 0.22f)
            )
            binding.tvExtraLocationsCount.setTextColor(heroFgColor)

            binding.btnEditLocation.imageTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(heroFgColor, 200)
            )
            binding.ivDragHandle.imageTintList = ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(heroFgColor, 185)
            )
            binding.cardLocationQueueBadge.setCardBackgroundColor(pillBgColor)
            binding.tvLocationQueueBadge.setTextColor(heroFgColor)

            binding.tvStatusTitle.setTextColor(heroFgColor)
            binding.tvStatusSubtitle.setTextColor(heroFgColor)
            binding.tvTotalHoursBadge.setTextColor(heroFgColor)

            binding.myQueueTimelineBar.setData(
                intervals = intervals,
                currentMinute = if (isToday) nowMin else null,
                showHourLabels = true,
                accentColor = heroFgColor,
                trackBaseColor = heroBgColor,
                animateReveal = scheduleChanged
            )

            // Build interval rows only when schedule changes
            if (scheduleChanged) {
                binding.intervalsContainer.removeAllViews()
                rowBindings.clear()
                val inflater = LayoutInflater.from(ctx)
                if (intervals.isEmpty()) {
                    val rowBinding =
                        ItemIntervalRowBinding.inflate(inflater, binding.intervalsContainer, false)
                    rowBinding.intervalDivider.visibility = View.GONE
                    rowBinding.intervalAccentBar.visibility = View.GONE
                    rowBinding.cardIntervalStatusPill.visibility = View.GONE
                    rowBinding.tvIntervalTime.text = ctx.getString(R.string.no_outages_day)
                    rowBinding.tvIntervalDuration.visibility = View.GONE
                    binding.intervalsContainer.addView(rowBinding.root)
                    M3Motion.animateCascadeItem(rowBinding.root, 0, baseDelayMs = 45L)
                } else {
                    intervals.forEachIndexed { index, interval ->
                        val rowBinding =
                            ItemIntervalRowBinding.inflate(inflater, binding.intervalsContainer, false)
                        rowBinding.intervalDivider.visibility =
                            if (index == 0) View.GONE else View.VISIBLE
                        rowBinding.tvIntervalTime.text = "${interval.start} – ${interval.end}"
                        binding.intervalsContainer.addView(rowBinding.root)
                        rowBindings.add(rowBinding)
                        M3Motion.animateCascadeItem(rowBinding.root, index, baseDelayMs = 45L)
                    }
                }
            }

            // Palette colors for interval rows
            val offFg = MaterialColors.harmonizeWithPrimary(
                ctx,
                ContextCompat.getColor(ctx, R.color.status_power_off_fg)
            )
            val offBg = MaterialColors.harmonizeWithPrimary(
                ctx,
                ContextCompat.getColor(ctx, R.color.status_power_off_bg)
            )
            val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
            val primaryContainer = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorPrimaryContainer
            )
            val surfaceHigh = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorSurfaceContainerHigh
            )
            val onSurfaceVariant = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorOnSurfaceVariant
            )
            val outlineVariant = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorOutlineVariant
            )
            val upcomingBarColor = ColorUtils.blendARGB(outlineVariant, onSurfaceVariant, 0.45f)
            val upcomingPillBg = ColorUtils.blendARGB(surfaceHigh, primaryContainer, 0.42f)
            val upcomingPillText = ColorUtils.blendARGB(onSurfaceVariant, primary, 0.72f)

            intervals.forEachIndexed { index, interval ->
                val rowBinding = rowBindings.getOrNull(index) ?: return@forEachIndexed
                val durationLabel = TimeUtils.formatDurationMinutes(interval.durationMinutes)

                when {
                    isToday && interval.containsMinute(nowMin) -> {
                        rowBinding.intervalContent.alpha = 1f
                        rowBinding.intervalAccentBar.setCardBackgroundColor(offFg)
                        rowBinding.tvIntervalDuration.text =
                            ctx.getString(R.string.interval_sub_now, durationLabel)
                        rowBinding.tvIntervalStatus.text = ctx.getString(R.string.interval_now)
                        rowBinding.tvIntervalStatus.setTextColor(offFg)
                        rowBinding.cardIntervalStatusPill.setCardBackgroundColor(offBg)
                        rowBinding.cardIntervalStatusPill.strokeColor =
                            ColorUtils.setAlphaComponent(offFg, 95)
                    }
                    isToday && interval.endMinutes <= nowMin -> {
                        rowBinding.intervalContent.alpha = 0.58f
                        rowBinding.intervalAccentBar.setCardBackgroundColor(outlineVariant)
                        rowBinding.tvIntervalDuration.text =
                            ctx.getString(R.string.interval_sub_done, durationLabel)
                        rowBinding.tvIntervalStatus.text = ctx.getString(R.string.interval_done)
                        rowBinding.tvIntervalStatus.setTextColor(onSurfaceVariant)
                        rowBinding.cardIntervalStatusPill.setCardBackgroundColor(surfaceHigh)
                        rowBinding.cardIntervalStatusPill.strokeColor =
                            ColorUtils.setAlphaComponent(outlineVariant, 90)
                    }
                    else -> {
                        rowBinding.intervalContent.alpha = 1f
                        rowBinding.intervalAccentBar.setCardBackgroundColor(upcomingBarColor)
                        rowBinding.tvIntervalDuration.text =
                            ctx.getString(R.string.interval_sub_upcoming, durationLabel)
                        rowBinding.tvIntervalStatus.text = ctx.getString(R.string.interval_upcoming)
                        rowBinding.tvIntervalStatus.setTextColor(upcomingPillText)
                        rowBinding.cardIntervalStatusPill.setCardBackgroundColor(upcomingPillBg)
                        rowBinding.cardIntervalStatusPill.strokeColor =
                            ColorUtils.setAlphaComponent(upcomingPillText, 70)
                    }
                }
            }
        }
    }

    companion object {
        private const val PAYLOAD_LIVE_TICK = "live_tick"
    }
}
