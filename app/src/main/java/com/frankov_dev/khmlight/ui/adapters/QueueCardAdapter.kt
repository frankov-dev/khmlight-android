package com.frankov_dev.khmlight.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.QueueSchedule
import com.frankov_dev.khmlight.databinding.ItemQueueCardBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.color.MaterialColors

class QueueCardAdapter(
    private val onQueueClicked: (String) -> Unit
) : RecyclerView.Adapter<QueueCardAdapter.ViewHolder>() {

    private var items: List<QueueSchedule> = emptyList()
    private var myQueueId: String? = null
    private var isToday: Boolean = false
    private var currentMinute: Int? = null
    private var lastDatasetSignature: String? = null
    private val animatedPositions = HashSet<Int>()

    fun submitData(
        queues: List<QueueSchedule>,
        myQueueId: String?,
        isToday: Boolean,
        currentMinute: Int?
    ) {
        val sorted = queues.sortedBy { it.queueId }
        val signature = "$isToday|" + sorted.joinToString("|") { q ->
            "${q.queueId}:${q.intervals.joinToString(",") { "${it.start}-${it.end}" }}"
        }
        val dataChanged = signature != lastDatasetSignature
        val queueSelectionChanged = myQueueId != this.myQueueId
        lastDatasetSignature = signature

        this.items = sorted
        this.myQueueId = myQueueId
        this.isToday = isToday
        this.currentMinute = currentMinute

        if (dataChanged) {
            animatedPositions.clear()
            notifyDataSetChanged()
        } else if (queueSelectionChanged || isToday) {
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

        init {
            M3Motion.attachPressSpring(binding.queueCard)
        }

        fun bind(item: QueueSchedule, position: Int, shouldAnimate: Boolean) {
            val ctx = binding.root.context
            val isMy = item.queueId == myQueueId

            binding.tvQueueTitle.text = ctx.getString(R.string.queue_format, item.queueId)
            binding.tvMyQueueTag.visibility = if (isMy) View.VISIBLE else View.GONE
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

            val cardBg: Int
            if (isMy) {
                cardBg = ColorUtils.blendARGB(surfaceLow, primaryContainer, 0.28f)
                binding.queueCard.strokeWidth = (1.5f * density).toInt().coerceAtLeast(2)
                binding.queueCard.strokeColor = primary
                binding.queueCard.setCardBackgroundColor(cardBg)
            } else {
                cardBg = surfaceLow
                binding.queueCard.strokeWidth = 0
                binding.queueCard.setCardBackgroundColor(cardBg)
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

            binding.queueCard.setOnClickListener {
                onQueueClicked(item.queueId)
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
