package com.frankov_dev.khmlight.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.databinding.PageAllQueuesDayBinding
import com.frankov_dev.khmlight.util.TimeUtils

class AllQueuesPagerAdapter(
    private val onQueueClicked: (String) -> Unit
) : RecyclerView.Adapter<AllQueuesPagerAdapter.PageViewHolder>() {

    private var feed: OutagesFeed? = null
    private var selectedQueue: String? = null
    private val recyclerViews = arrayOfNulls<RecyclerView>(2)

    fun submitData(feed: OutagesFeed?, selectedQueue: String?) {
        this.feed = feed
        this.selectedQueue = selectedQueue
        notifyDataSetChanged()
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

    inner class PageViewHolder(
        val binding: PageAllQueuesDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val queueAdapter = QueueCardAdapter(onQueueClicked)

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
                myQueueId = selectedQueue,
                isToday = isToday,
                currentMinute = if (isToday) TimeUtils.currentMinuteOfDay() else null
            )
        }
    }
}
