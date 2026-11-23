package com.frankov_dev.khmlight.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.LocationQueueGroup
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.data.groupByQueuePreservingOrder
import com.frankov_dev.khmlight.databinding.BottomSheetLocationGroupBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class LocationGroupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLocationGroupBinding? = null
    private val binding get() = _binding!!

    var group: LocationQueueGroup? = null
    var canAddMore: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLocationGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val savedLocations = PrefsManager(requireContext()).getSavedLocations()
        val argQueueId = arguments?.getString(ARG_QUEUE_ID)
        val g = group
            ?: argQueueId?.let { qId ->
                savedLocations.groupByQueuePreservingOrder().firstOrNull { it.queueId == qId }
            }
            ?: run {
                view.post { if (isAdded) dismissAllowingStateLoss() }
                return
            }
        val allowAdd = arguments?.getBoolean(
            ARG_CAN_ADD_MORE,
            savedLocations.size < SavedLocationCodec.MAX_LOCATIONS
        ) ?: canAddMore

        val ctx = requireContext()
        val density = resources.displayMetrics.density

        binding.tvGroupSheetTitle.text = getString(R.string.queue_format, g.queueId)
        binding.tvGroupSheetCount.text = formatLocationsCount(g.locations.size)

        val surfaceLow = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerLow
        )
        val secondaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSecondaryContainer
        )
        val onSecondaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSecondaryContainer
        )
        val primary = MaterialColors.getColor(
            binding.root,
            androidx.appcompat.R.attr.colorPrimary
        )
        val onSurface = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val outlineVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOutlineVariant
        )

        binding.llGroupLocationsList.removeAllViews()
        g.locations.forEachIndexed { index, loc ->
            val card = MaterialCardView(ctx).apply {
                radius = 20f * density
                cardElevation = 0f
                setCardBackgroundColor(surfaceLow)
                strokeWidth = (1 * density).toInt()
                strokeColor = outlineVariant
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = (8 * density).toInt()
                }
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
            }

            // Icon squircle badge
            val iconBox = MaterialCardView(ctx).apply {
                radius = 14f * density
                cardElevation = 0f
                strokeWidth = 0
                setCardBackgroundColor(secondaryContainer)
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply {
                    marginEnd = (12 * density).toInt()
                }
            }
            val ivIcon = ImageView(ctx).apply {
                setImageResource(loc.icon.iconRes)
                imageTintList = ColorStateList.valueOf(onSecondaryContainer)
                val p = (9 * density).toInt()
                setPadding(p, p, p, p)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            iconBox.addView(ivIcon)

            // Name + notification status subtitle
            val textCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvName = TextView(ctx).apply {
                text = loc.name
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onSurface)
                maxLines = 1
            }
            val tvSub = TextView(ctx).apply {
                text = if (loc.notifyEnabled) "Сповіщення увімкнено" else "Без сповіщень"
                textSize = 12.5f
                setTextColor(onSurfaceVariant)
            }
            textCol.addView(tvName)
            textCol.addView(tvSub)

            // Edit icon
            val ivEdit = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_edit)
                imageTintList = ColorStateList.valueOf(primary)
                layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()).apply {
                    marginStart = (10 * density).toInt()
                }
            }

            row.addView(iconBox)
            row.addView(textCol)
            row.addView(ivEdit)
            card.addView(row)

            M3Motion.attachPressSpring(card)
            card.setOnClickListener {
                dismiss()
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(
                        RESULT_ACTION to ACTION_EDIT_LOCATION,
                        RESULT_LOCATION_ID to loc.id
                    )
                )
            }

            binding.llGroupLocationsList.addView(card)
            M3Motion.animateCascadeItem(card, index, baseDelayMs = 30L)
        }

        binding.btnAddAnotherOnQueue.visibility = if (allowAdd) View.VISIBLE else View.GONE
        M3Motion.attachPressSpring(binding.btnAddAnotherOnQueue)
        binding.btnAddAnotherOnQueue.setOnClickListener {
            dismiss()
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_ACTION to ACTION_ADD_ON_QUEUE,
                    RESULT_QUEUE_ID to g.queueId
                )
            )
        }
    }

    private fun formatLocationsCount(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        return when {
            mod10 == 1 && mod100 != 11 -> "$count локація"
            mod10 in 2..4 && mod100 !in 12..14 -> "$count локації"
            else -> "$count локацій"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "req_location_group"
        const val RESULT_ACTION = "result_action"
        const val ACTION_EDIT_LOCATION = "edit_location"
        const val ACTION_ADD_ON_QUEUE = "add_on_queue"
        const val RESULT_LOCATION_ID = "location_id"
        const val RESULT_QUEUE_ID = "queue_id"

        private const val ARG_QUEUE_ID = "arg_queue_id"
        private const val ARG_CAN_ADD_MORE = "arg_can_add_more"

        fun newInstance(group: LocationQueueGroup, canAddMore: Boolean): LocationGroupBottomSheet {
            return LocationGroupBottomSheet().apply {
                this.group = group
                this.canAddMore = canAddMore
                arguments = bundleOf(
                    ARG_QUEUE_ID to group.queueId,
                    ARG_CAN_ADD_MORE to canAddMore
                )
            }
        }
    }
}
