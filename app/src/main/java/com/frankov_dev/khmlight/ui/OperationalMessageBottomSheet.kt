package com.frankov_dev.khmlight.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import com.frankov_dev.khmlight.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class OperationalMessageBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_operational_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dateLabel = arguments?.getString(ARG_DATE_LABEL).orEmpty()
        val message = arguments?.getString(ARG_MESSAGE).orEmpty()

        val tvDate = view.findViewById<TextView>(R.id.tvMessageDate)
        val tvBody = view.findViewById<TextView>(R.id.tvMessageBody)
        val btnOk = view.findViewById<MaterialButton>(R.id.btnMessageOk)

        tvDate.text = dateLabel
        tvDate.visibility = if (dateLabel.isNotBlank()) View.VISIBLE else View.GONE
        tvBody.text = message

        btnOk.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        private const val ARG_DATE_LABEL = "date_label"
        private const val ARG_MESSAGE = "message"

        fun newInstance(dateLabel: String, message: String): OperationalMessageBottomSheet {
            return OperationalMessageBottomSheet().apply {
                arguments = bundleOf(
                    ARG_DATE_LABEL to dateLabel,
                    ARG_MESSAGE to message
                )
            }
        }
    }
}
