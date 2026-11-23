package com.frankov_dev.khmlight.ui.adapters

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.LocationQueueGroup
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.databinding.PageMyQueueDayBinding
import com.frankov_dev.khmlight.ui.M3Motion
import com.frankov_dev.khmlight.util.TimeUtils

class MyQueuePagerAdapter(
    private val onEditLocation: (SavedLocation) -> Unit,
    private val onEditGroup: (LocationQueueGroup) -> Unit,
    private val onShowRevisions: (String, QueueRevisionDiff) -> Unit,
    private val onLocationsReordered: (List<SavedLocation>) -> Unit,
    private val onDraggingChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<MyQueuePagerAdapter.PageViewHolder>() {

    private var feed: OutagesFeed? = null
    private var savedLocations: List<SavedLocation> = emptyList()
    private val recyclerViews = arrayOfNulls<RecyclerView>(2)

    fun submitData(feed: OutagesFeed?, locations: List<SavedLocation>) {
        this.feed = feed
        this.savedLocations = locations
        notifyItemRangeChanged(0, itemCount, PAYLOAD_LIVE_UPDATE)
    }

    fun canScrollUp(position: Int): Boolean {
        val rv = recyclerViews.getOrNull(position) ?: return false
        return rv.visibility == View.VISIBLE && rv.canScrollVertically(-1)
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
        recyclerViews[position] = holder.binding.rvLocationBlocks
        holder.bind(position)
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        recyclerViews[position] = holder.binding.rvLocationBlocks
        holder.bind(position)
    }

    inner class PageViewHolder(
        val binding: PageMyQueueDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var itemTouchHelper: ItemTouchHelper
        private val blocksAdapter = LocationBlocksAdapter(
            onEditLocation = onEditLocation,
            onEditGroup = onEditGroup,
            onShowRevisions = onShowRevisions,
            onStartDrag = { vh -> itemTouchHelper.startDrag(vh) },
            onOrderCommitted = onLocationsReordered
        )

        init {
            binding.rvLocationBlocks.layoutManager = LinearLayoutManager(binding.root.context)
            binding.rvLocationBlocks.adapter = blocksAdapter

            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun isLongPressDragEnabled(): Boolean = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return blocksAdapter.onItemMove(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        blocksAdapter.onDragStateChanged(true)
                        onDraggingChanged(true)
                        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewHolder.itemView.animate()
                            .scaleX(1.02f)
                            .scaleY(1.02f)
                            .alpha(0.95f)
                            .setDuration(140L)
                            .start()
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(160L)
                        .start()
                    blocksAdapter.onDragStateChanged(false)
                    onDraggingChanged(false)
                }
            }
            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper.attachToRecyclerView(binding.rvLocationBlocks)
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
                if (binding.myQueueTomorrowEmptyCard.visibility != View.VISIBLE) {
                    binding.myQueueTomorrowEmptyCard.visibility = View.VISIBLE
                    M3Motion.animateCascadeItem(binding.myQueueTomorrowEmptyCard, 0)
                }
                binding.rvLocationBlocks.visibility = View.GONE
                return
            }

            binding.myQueueTomorrowEmptyCard.visibility = View.GONE
            binding.rvLocationBlocks.visibility = View.VISIBLE
            blocksAdapter.submitData(savedLocations, daySchedule)
        }
    }

    companion object {
        private const val PAYLOAD_LIVE_UPDATE = "live_update"
    }
}
