package com.frankov_dev.khmlight.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.LocationQueueGroup
import com.frankov_dev.khmlight.databinding.ItemHistoryDayBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.util.TimeUtils

class HistoryAdapter(
    private val onDayClicked: (DaySchedule) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var days: List<DaySchedule> = emptyList()
    private var selectedGroup: LocationQueueGroup? = null
    private var lastSignature: String? = null
    private val animatedPositions = HashSet<Int>()

    fun submitData(
        days: List<DaySchedule>,
        selectedGroup: LocationQueueGroup?
    ) {
        val sig = "${selectedGroup?.queueId}|${selectedGroup?.joinedNames}|" +
            days.joinToString("|") { d ->
                val q = selectedGroup?.let { d.queues[it.queueId] }
                val qSig = q?.intervalsSignature()
                    ?: d.queues.values.sumOf { it.totalHoursOff }.toString()
                "${d.date}:${d.operationalMessage.orEmpty().hashCode()}:$qSig"
            }
        val changed = sig != lastSignature
        lastSignature = sig

        this.days = days
        this.selectedGroup = selectedGroup

        if (changed) {
            animatedPositions.clear()
            notifyDataSetChanged()
        } else if (days.isNotEmpty()) {
            notifyItemRangeChanged(0, days.size, PAYLOAD_TICK)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shouldAnimate = animatedPositions.add(position)
        holder.bind(days[position], position, shouldAnimate)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            holder.bind(days[position], position, shouldAnimate = false)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun getItemCount(): Int = days.size

    inner class ViewHolder(
        private val binding: ItemHistoryDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            M3Motion.attachPressSpring(binding.historyDayCard)
        }

        fun bind(day: DaySchedule, position: Int, shouldAnimate: Boolean) {
            val ctx = binding.root.context
            binding.tvHistoryDate.text = TimeUtils.formatDateHeading(day.date)

            val group = selectedGroup
            val locSchedule = group?.let { day.queues[it.queueId] }
            if (group != null && locSchedule != null) {
                binding.tvHistoryHours.text = TimeUtils.formatHours(locSchedule.totalHoursOff)
                binding.historyTimelineBar.visibility = View.VISIBLE
                binding.historyTimelineBar.setData(
                    intervals = locSchedule.intervals,
                    currentMinute = if (TimeUtils.isToday(day.date)) TimeUtils.currentMinuteOfDay() else null,
                    showHourLabels = false,
                    animateReveal = shouldAnimate
                )
                binding.tvHistoryIntervals.text = if (locSchedule.intervals.isEmpty()) {
                    "${group.displayTitleWithQueue}: ${ctx.getString(R.string.no_outages_day)}"
                } else {
                    "${group.displayTitleWithQueue}: " +
                        locSchedule.intervals.joinToString("  •  ") { "${it.start}–${it.end}" }
                }
            } else {
                val avgHours = if (day.queues.isNotEmpty()) {
                    day.queues.values.map { it.totalHoursOff }.average()
                } else {
                    0.0
                }
                binding.tvHistoryHours.text = "≈ ${TimeUtils.formatHours(avgHours)}"
                binding.historyTimelineBar.visibility = View.GONE
                binding.tvHistoryIntervals.text = ctx.getString(R.string.history_tap_details)
            }

            if (!day.operationalMessage.isNullOrBlank()) {
                binding.tvHistoryNote.visibility = View.VISIBLE
                binding.tvHistoryNote.text = day.operationalMessage
            } else {
                binding.tvHistoryNote.visibility = View.GONE
            }

            binding.historyDayCard.setOnClickListener {
                onDayClicked(day)
            }

            if (shouldAnimate) {
                M3Motion.animateCascadeItem(binding.historyDayCard, position)
            }
        }
    }

    companion object {
        private const val PAYLOAD_TICK = "tick"
    }
}
