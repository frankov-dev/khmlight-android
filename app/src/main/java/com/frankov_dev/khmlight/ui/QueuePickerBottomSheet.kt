package com.frankov_dev.khmlight.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.core.os.bundleOf
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.QueueConstants
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class QueuePickerBottomSheet : BottomSheetDialogFragment() {

    var onQueueSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_queue_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentQueue = arguments?.getString(ARG_CURRENT_QUEUE)
        val grid = view.findViewById<GridLayout>(R.id.queueGrid)
        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        for (queueId in QueueConstants.ALL_QUEUES) {
            val isSelected = queueId == currentQueue
            val styleAttr = if (isSelected) {
                com.google.android.material.R.attr.materialButtonStyle
            } else {
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            }
            val btn = MaterialButton(requireContext(), null, styleAttr).apply {
                text = queueId
                minHeight = (52 * density).toInt()
                setOnClickListener {
                    onQueueSelected?.invoke(queueId)
                    dismiss()
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            grid.addView(btn, params)
        }
    }

    companion object {
        private const val ARG_CURRENT_QUEUE = "current_queue"

        fun newInstance(currentQueue: String?): QueuePickerBottomSheet {
            return QueuePickerBottomSheet().apply {
                arguments = bundleOf(ARG_CURRENT_QUEUE to currentQueue)
            }
        }
    }
}
