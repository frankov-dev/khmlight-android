package com.frankov_dev.khmlight.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.QueueRevisionDiff
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.ui.adapters.QueueCardAdapter
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HistoryDayBottomSheet : BottomSheetDialogFragment() {

    var daySchedule: DaySchedule? = null
    var savedLocations: List<SavedLocation> = emptyList()
    var onRevisionBadgeClicked: ((String, QueueRevisionDiff) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_history_day, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val day = daySchedule ?: run {
            dismiss()
            return
        }

        val tvTitle = view.findViewById<TextView>(R.id.tvSheetDateTitle)
        val tvMsg = view.findViewById<TextView>(R.id.tvSheetMessage)
        val rv = view.findViewById<RecyclerView>(R.id.rvSheetQueues)

        tvTitle.text = TimeUtils.formatDateHeading(day.date)
        if (!day.operationalMessage.isNullOrBlank()) {
            tvMsg.visibility = View.VISIBLE
            tvMsg.text = day.operationalMessage
        } else {
            tvMsg.visibility = View.GONE
        }

        val adapter = QueueCardAdapter(
            onRevisionBadgeClicked = { date, diff ->
                dismiss()
                onRevisionBadgeClicked?.invoke(date, diff)
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val isToday = TimeUtils.isToday(day.date)
        val nowMin = if (isToday) TimeUtils.currentMinuteOfDay() else null
        adapter.submitData(
            queues = day.queues.values.toList(),
            savedLocations = savedLocations,
            isToday = isToday,
            currentMinute = nowMin,
            daySchedule = day
        )
    }
}
