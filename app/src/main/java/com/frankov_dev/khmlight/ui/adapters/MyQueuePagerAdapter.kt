package com.frankov_dev.khmlight.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.databinding.ItemIntervalRowBinding
import com.frankov_dev.khmlight.databinding.PageMyQueueDayBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.color.MaterialColors

class MyQueuePagerAdapter : RecyclerView.Adapter<MyQueuePagerAdapter.PageViewHolder>() {

    private var feed: OutagesFeed? = null
    private var selectedQueue: String? = null
    private val scrollViews = arrayOfNulls<NestedScrollView>(2)

    fun submitData(feed: OutagesFeed?, selectedQueue: String?) {
        this.feed = feed
        this.selectedQueue = selectedQueue
        notifyItemRangeChanged(0, itemCount, PAYLOAD_LIVE_UPDATE)
    }

    fun canScrollUp(position: Int): Boolean {
        val sv = scrollViews.getOrNull(position) ?: return false
        return sv.canScrollVertically(-1)
    }

    override fun getItemCount(): Int = 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = PageMyQueueDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        scrollViews[position] = holder.binding.pageScrollView
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        scrollViews[position] = holder.binding.pageScrollView
        holder.bind(position)
    }

    inner class PageViewHolder(
        val binding: PageMyQueueDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastHeroBgColor: Int? = null
        private var lastIntervalsSignature: String? = null
        private val rowBindings = mutableListOf<ItemIntervalRowBinding>()

        fun bind(position: Int) {
            val ctx = binding.root.context
            val currentFeed = feed ?: return
            val myQueue = selectedQueue ?: return

            val todayStr = TimeUtils.todayDateString()
            val tomorrowStr = TimeUtils.tomorrowDateString()
            val daySchedule: DaySchedule? = if (position == 0) {
                currentFeed.schedules[todayStr]
                    ?: currentFeed.latestDate?.let { currentFeed.schedules[it] }
            } else {
                currentFeed.schedules[tomorrowStr]
            }

            if (daySchedule == null) {
                if (binding.myQueueTomorrowEmptyCard.visibility != View.VISIBLE) {
                    binding.myQueueTomorrowEmptyCard.visibility = View.VISIBLE
                    M3Motion.animateCascadeItem(binding.myQueueTomorrowEmptyCard, 0)
                }
                binding.myQueueScheduleGroup.visibility = View.GONE
                lastIntervalsSignature = null
                return
            }

            binding.myQueueTomorrowEmptyCard.visibility = View.GONE
            binding.myQueueScheduleGroup.visibility = View.VISIBLE

            val dateKey = daySchedule.date
            val queueSchedule = daySchedule.queues[myQueue]
            val intervals = queueSchedule?.intervals.orEmpty()
            val totalHours = queueSchedule?.totalHoursOff ?: 0.0
            val isToday = TimeUtils.isToday(dateKey)
            val nowMin = TimeUtils.currentMinuteOfDay()

            val signature = "$position|$myQueue|$dateKey|" +
                intervals.joinToString(",") { "${it.start}-${it.end}" }
            val scheduleChanged = signature != lastIntervalsSignature
            lastIntervalsSignature = signature

            binding.tvTotalHoursBadge.text =
                ctx.getString(R.string.total_hours_off, TimeUtils.formatHours(totalHours))
            binding.tvIntervalsCountBadge.text = formatIntervalsCount(intervals.size)

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
                        binding.statusHeroCard,
                        com.google.android.material.R.attr.colorPrimaryContainer
                    )
                    heroFgColor = MaterialColors.getColor(
                        binding.statusHeroCard,
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
                    binding.statusHeroCard,
                    com.google.android.material.R.attr.colorSecondaryContainer
                )
                heroFgColor = MaterialColors.getColor(
                    binding.statusHeroCard,
                    com.google.android.material.R.attr.colorOnSecondaryContainer
                )
                binding.tvStatusTitle.text = TimeUtils.formatDateHeading(dateKey)
                binding.tvStatusSubtitle.text = if (intervals.isEmpty()) {
                    ctx.getString(R.string.no_outages_day)
                } else {
                    ctx.getString(R.string.status_scheduled_day)
                }
            }

            M3Motion.animateCardBackgroundColor(binding.statusHeroCard, lastHeroBgColor, heroBgColor)
            lastHeroBgColor = heroBgColor
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
                        M3Motion.attachPressSpring(rowBinding.intervalContent, pressedScale = 0.985f)
                        binding.intervalsContainer.addView(rowBinding.root)
                        rowBindings.add(rowBinding)
                        M3Motion.animateCascadeItem(rowBinding.root, index, baseDelayMs = 45L)
                    }
                }
            }

            // Palette colors for interval rows (matching the uploaded Material 3 card design)
            val offFg = MaterialColors.harmonizeWithPrimary(
                ctx,
                ContextCompat.getColor(ctx, R.color.status_power_off_fg)
            )
            val offBg = MaterialColors.harmonizeWithPrimary(
                ctx,
                ContextCompat.getColor(ctx, R.color.status_power_off_bg)
            )
            val primary = MaterialColors.getColor(
                binding.root,
                androidx.appcompat.R.attr.colorPrimary
            )
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

            // Update interval status badges, vertical accent bars, and subtitles
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

        private fun formatIntervalsCount(count: Int): String {
            if (count <= 0) return "0 інтервалів"
            val mod10 = count % 10
            val mod100 = count % 100
            return when {
                mod10 == 1 && mod100 != 11 -> "$count інтервал"
                mod10 in 2..4 && mod100 !in 12..14 -> "$count інтервали"
                else -> "$count інтервалів"
            }
        }
    }

    companion object {
        private const val PAYLOAD_LIVE_UPDATE = "live_update"
    }
}
