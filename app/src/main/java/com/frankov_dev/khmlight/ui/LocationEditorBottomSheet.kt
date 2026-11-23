package com.frankov_dev.khmlight.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.LocationIcon
import com.frankov_dev.khmlight.data.QueueConstants
import com.frankov_dev.khmlight.data.SavedLocation
import com.frankov_dev.khmlight.data.SavedLocationCodec
import com.frankov_dev.khmlight.databinding.BottomSheetLocationEditorBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import java.util.UUID

class LocationEditorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLocationEditorBinding? = null
    private val binding get() = _binding!!

    private var editingId: String? = null
    private var selectedIcon: LocationIcon = LocationIcon.HOME
    private var selectedQueue: String? = null
    private var notifyEnabled: Boolean = true

    private val iconCards = mutableMapOf<LocationIcon, Pair<MaterialCardView, ImageView>>()
    private val queueButtons = mutableMapOf<String, MaterialButton>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLocationEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        editingId = args?.getString(ARG_ID)
        val initialName = args?.getString(ARG_NAME).orEmpty()
        selectedIcon = LocationIcon.fromName(
            savedInstanceState?.getString(ARG_ICON) ?: args?.getString(ARG_ICON)
        )
        selectedQueue = savedInstanceState?.getString(ARG_QUEUE) ?: args?.getString(ARG_QUEUE)
        notifyEnabled = savedInstanceState?.getBoolean(
            ARG_NOTIFY,
            args?.getBoolean(ARG_NOTIFY, true) ?: true
        ) ?: (args?.getBoolean(ARG_NOTIFY, true) ?: true)

        val isEdit = !editingId.isNullOrBlank()
        binding.tvEditorTitle.text = getString(
            if (isEdit) R.string.edit_location else R.string.new_location_title
        )
        if (savedInstanceState == null) {
            binding.etLocationName.setText(initialName)
        }
        binding.switchLocationNotify.isChecked = notifyEnabled
        binding.btnDeleteLocation.visibility = if (isEdit) View.VISIBLE else View.GONE

        buildPresetChips()
        buildIconGrid()
        buildQueueGrid()

        binding.switchLocationNotify.setOnCheckedChangeListener { _, isChecked ->
            notifyEnabled = isChecked
        }

        M3Motion.attachPressSpring(binding.btnSaveLocation)
        binding.btnSaveLocation.setOnClickListener {
            val queue = selectedQueue
            if (queue.isNullOrBlank()) {
                Snackbar.make(binding.root, R.string.location_select_queue_error, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val enteredName = binding.etLocationName.text?.toString()?.trim().orEmpty()
            val finalName = enteredName.ifBlank { selectedIcon.titleUk }
                .take(SavedLocationCodec.MAX_NAME_LENGTH)

            val saved = SavedLocation(
                id = editingId ?: UUID.randomUUID().toString(),
                name = finalName,
                queueId = queue,
                icon = selectedIcon,
                notifyEnabled = notifyEnabled
            )
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_ACTION to ACTION_SAVE,
                    ARG_ID to saved.id,
                    ARG_NAME to saved.name,
                    ARG_QUEUE to saved.queueId,
                    ARG_ICON to saved.icon.name,
                    ARG_NOTIFY to saved.notifyEnabled
                )
            )
            dismiss()
        }

        binding.btnDeleteLocation.setOnClickListener {
            val id = editingId ?: return@setOnClickListener
            val currentName = binding.etLocationName.text?.toString()?.trim()
                ?.ifBlank { selectedIcon.titleUk } ?: selectedIcon.titleUk
            val toDelete = SavedLocation(
                id = id,
                name = currentName,
                queueId = selectedQueue.orEmpty(),
                icon = selectedIcon,
                notifyEnabled = notifyEnabled
            )
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_ACTION to ACTION_DELETE,
                    ARG_ID to toDelete.id,
                    ARG_NAME to toDelete.name,
                    ARG_QUEUE to toDelete.queueId,
                    ARG_ICON to toDelete.icon.name,
                    ARG_NOTIFY to toDelete.notifyEnabled
                )
            )
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_ICON, selectedIcon.name)
        outState.putString(ARG_QUEUE, selectedQueue)
        outState.putBoolean(ARG_NOTIFY, notifyEnabled)
    }

    private fun buildPresetChips() {
        val presets = listOf(
            LocationIcon.HOME,
            LocationIcon.WORK,
            LocationIcon.FAMILY,
            LocationIcon.COTTAGE,
            LocationIcon.SCHOOL,
            LocationIcon.GARAGE
        )
        binding.chipGroupPresets.removeAllViews()
        for (preset in presets) {
            val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                text = preset.titleUk
                setChipIconResource(preset.iconRes)
                isChipIconVisible = true
                setOnClickListener {
                    binding.etLocationName.setText(preset.titleUk)
                    binding.etLocationName.setSelection(preset.titleUk.length)
                    selectedIcon = preset
                    updateIconGridState(animate = true)
                }
            }
            binding.chipGroupPresets.addView(chip)
        }
    }

    private fun buildIconGrid() {
        binding.iconGrid.removeAllViews()
        iconCards.clear()
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        for (icon in LocationIcon.entries) {
            val card = MaterialCardView(ctx).apply {
                radius = 14f * density
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                contentDescription = icon.titleUk
            }
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = (44 * density).toInt()
                setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
            }
            val iv = ImageView(ctx).apply {
                setImageResource(icon.iconRes)
                contentDescription = icon.titleUk
                layoutParams = LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt())
            }
            inner.addView(iv)
            card.addView(inner)

            M3Motion.attachPressSpring(card, pressedScale = 0.93f)
            card.setOnClickListener {
                if (selectedIcon != icon) {
                    selectedIcon = icon
                    updateIconGridState(animate = true)
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            binding.iconGrid.addView(card, params)
            iconCards[icon] = card to iv
        }
        updateIconGridState(animate = false)
    }

    private fun updateIconGridState(animate: Boolean) {
        val density = resources.displayMetrics.density
        val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val primaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        val onPrimaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnPrimaryContainer
        )
        val surfaceLow = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorSurfaceContainerLow
        )
        val onSurfaceVariant = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        for ((icon, pair) in iconCards) {
            val (card, iv) = pair
            val isSelected = icon == selectedIcon
            card.setCardBackgroundColor(if (isSelected) primaryContainer else surfaceLow)
            card.strokeWidth = if (isSelected) (2 * density).toInt() else 0
            card.strokeColor = primary
            iv.imageTintList = ColorStateList.valueOf(if (isSelected) onPrimaryContainer else onSurfaceVariant)

            val targetScale = if (isSelected) 1.04f else 1.0f
            if (animate) {
                card.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(190L)
                    .setInterpolator(M3Motion.SPRING_OVERSHOOT)
                    .start()
            } else {
                card.scaleX = targetScale
                card.scaleY = targetScale
            }
        }
    }

    private fun buildQueueGrid() {
        binding.editorQueueGrid.removeAllViews()
        queueButtons.clear()
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        for (queueId in QueueConstants.ALL_QUEUES) {
            val btn = MaterialButton(
                ctx,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = queueId
                minHeight = (48 * density).toInt()
                insetTop = 0
                insetBottom = 0
                setOnClickListener {
                    selectedQueue = queueId
                    updateQueueButtonsState()
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            binding.editorQueueGrid.addView(btn, params)
            queueButtons[queueId] = btn
        }
        updateQueueButtonsState()
    }

    private fun updateQueueButtonsState() {
        val primary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
        val onSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val outline = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)

        for ((queueId, btn) in queueButtons) {
            val isSelected = queueId == selectedQueue
            btn.backgroundTintList = ColorStateList.valueOf(
                if (isSelected) primary else android.graphics.Color.TRANSPARENT
            )
            btn.setTextColor(if (isSelected) onPrimary else onSurface)
            btn.strokeColor = ColorStateList.valueOf(if (isSelected) primary else outline)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "req_location_editor"
        const val RESULT_ACTION = "result_action"
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        private const val ARG_ID = "id"
        private const val ARG_NAME = "name"
        private const val ARG_ICON = "icon"
        private const val ARG_QUEUE = "queue"
        private const val ARG_NOTIFY = "notify"

        fun parseResultLocation(bundle: Bundle): Pair<String, SavedLocation>? {
            val action = bundle.getString(RESULT_ACTION) ?: return null
            val id = bundle.getString(ARG_ID) ?: return null
            val name = bundle.getString(ARG_NAME).orEmpty()
            val queue = bundle.getString(ARG_QUEUE).orEmpty()
            val icon = LocationIcon.fromName(bundle.getString(ARG_ICON))
            val notify = bundle.getBoolean(ARG_NOTIFY, true)
            return action to SavedLocation(
                id = id,
                name = name,
                queueId = queue,
                icon = icon,
                notifyEnabled = notify
            )
        }

        fun newForCreate(preselectedQueue: String? = null): LocationEditorBottomSheet {
            return LocationEditorBottomSheet().apply {
                arguments = bundleOf(
                    ARG_QUEUE to preselectedQueue,
                    ARG_ICON to LocationIcon.HOME.name,
                    ARG_NOTIFY to true
                )
            }
        }

        fun newForEdit(location: SavedLocation): LocationEditorBottomSheet {
            return LocationEditorBottomSheet().apply {
                arguments = bundleOf(
                    ARG_ID to location.id,
                    ARG_NAME to location.name,
                    ARG_ICON to location.icon.name,
                    ARG_QUEUE to location.queueId,
                    ARG_NOTIFY to location.notifyEnabled
                )
            }
        }
    }
}
