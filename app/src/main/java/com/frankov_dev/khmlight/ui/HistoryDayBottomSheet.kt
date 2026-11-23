package com.frankov_dev.khmlight.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.DaySchedule
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.ui.adapters.QueueCardAdapter
import com.frankov_dev.khmlight.util.TimeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HistoryDayBottomSheet : BottomSheetDialogFragment() {

    var daySchedule: DaySchedule? = null
    var savedLocations: List<SavedLocation> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_history_day, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val vm = runCatching {
            ViewModelProvider(requireActivity())[MainViewModel::class.java]
        }.getOrNull()
        val argDate = arguments?.getString(ARG_DATE)
        val day = daySchedule
            ?: (if (argDate != null) vm?.uiState?.value?.feed?.schedules?.get(argDate) else null)
            ?: run {
                view.post { if (isAdded) dismissAllowingStateLoss() }
                return
            }
        val locations = savedLocations.ifEmpty {
            vm?.uiState?.value?.savedLocations.orEmpty()
        }

        val tvTitle = view.findViewById<TextView>(R.id.tvSheetDateTitle)
        val tvMsg = view.findViewById<TextView>(R.id.tvSheetMessage)
        val rv = view.findViewById<RecyclerView>(R.id.rvSheetQueues)

        val screenH = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.density
        rv.layoutParams = rv.layoutParams.apply {
            height = (screenH * 0.64f).toInt().coerceAtLeast((200 * density).toInt())
        }

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
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_DATE_KEY to date,
                        RESULT_QUEUE_ID to diff.queueId
                    )
                )
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val isToday = TimeUtils.isToday(day.date)
        val nowMin = if (isToday) TimeUtils.currentMinuteOfDay() else null
        adapter.submitData(
            queues = day.queues.values.toList(),
            savedLocations = locations,
            isToday = isToday,
            currentMinute = nowMin,
            daySchedule = day
        )
    }

    companion object {
        const val REQUEST_KEY = "req_history_day_sheet"
        const val RESULT_DATE_KEY = "result_date_key"
        const val RESULT_QUEUE_ID = "result_queue_id"
        private const val ARG_DATE = "arg_date"

        fun newInstance(day: DaySchedule, savedLocations: List<SavedLocation>): HistoryDayBottomSheet {
            return HistoryDayBottomSheet().apply {
                this.daySchedule = day
                this.savedLocations = savedLocations
                arguments = bundleOf(ARG_DATE to day.date)
            }
        }
    }
}
