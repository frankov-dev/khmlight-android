package com.frankov_dev.khmlight.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.databinding.PageAllQueuesDayBinding
import com.frankov_dev.khmlight.util.TimeUtils

class AllQueuesPagerAdapter(
    private val onRevisionBadgeClicked: (String, QueueRevisionDiff) -> Unit
) : RecyclerView.Adapter<AllQueuesPagerAdapter.PageViewHolder>() {

    private var feed: OutagesFeed? = null
    private var savedLocations: List<SavedLocation> = emptyList()
    private val recyclerViews = arrayOfNulls<RecyclerView>(2)

    fun submitData(feed: OutagesFeed?, savedLocations: List<SavedLocation>) {
        this.feed = feed
        this.savedLocations = savedLocations
        notifyItemRangeChanged(0, itemCount, PAYLOAD_UPDATE)
    }

    fun canScrollUp(position: Int): Boolean {
        val rv = recyclerViews.getOrNull(position) ?: return false
        return rv.visibility == View.VISIBLE && rv.canScrollVertically(-1)
    }

    override fun getItemCount(): Int = 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = PageAllQueuesDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        recyclerViews[position] = holder.binding.rvAllQueuesPage
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        recyclerViews[position] = holder.binding.rvAllQueuesPage
        holder.bind(position)
    }

    inner class PageViewHolder(
        val binding: PageAllQueuesDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val queueAdapter = QueueCardAdapter(onRevisionBadgeClicked)

        init {
            binding.rvAllQueuesPage.layoutManager = LinearLayoutManager(binding.root.context)
            binding.rvAllQueuesPage.adapter = queueAdapter
        }

        fun bind(position: Int) {
            val currentFeed = feed ?: return
            val todayStr = TimeUtils.todayDateString()
            val tomorrowStr = TimeUtils.tomorrowDateString()

            val daySchedule: DaySchedule? = if (position == 0) {
                currentFeed.schedules[todayStr]
                    ?: currentFeed.latestDate?.let { currentFeed.schedules[it] }
            } else {
                currentFeed.schedules[tomorrowStr]
            }

            if (daySchedule == null) {
                binding.allQueuesTomorrowEmptyCard.visibility = View.VISIBLE
                binding.tvAllQueuesMessage.visibility = View.GONE
                binding.rvAllQueuesPage.visibility = View.GONE
                return
            }

            binding.allQueuesTomorrowEmptyCard.visibility = View.GONE
            binding.rvAllQueuesPage.visibility = View.VISIBLE

            if (!daySchedule.operationalMessage.isNullOrBlank()) {
                binding.tvAllQueuesMessage.visibility = View.VISIBLE
                binding.tvAllQueuesMessage.text = daySchedule.operationalMessage
            } else {
                binding.tvAllQueuesMessage.visibility = View.GONE
            }

            val isToday = TimeUtils.isToday(daySchedule.date)
            queueAdapter.submitData(
                queues = daySchedule.queues.values.toList(),
                savedLocations = savedLocations,
                isToday = isToday,
                currentMinute = if (isToday) TimeUtils.currentMinuteOfDay() else null,
                daySchedule = daySchedule
            )
        }
    }

    companion object {
        private const val PAYLOAD_UPDATE = "update"
    }
}
