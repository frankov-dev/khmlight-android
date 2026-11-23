package com.frankov_dev.khmlight.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.AppReleaseInfo
import com.frankov_dev.khmlight.data.AppUpdateChecker
import com.frankov_dev.khmlight.data.PrefsManager
import com.frankov_dev.khmlight.databinding.BottomSheetUpdateAvailableBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class UpdateAvailableBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetUpdateAvailableBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetUpdateAvailableBinding.inflate(inflater, container, false)
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
        val args = arguments ?: run {
            view.post { if (isAdded) dismissAllowingStateLoss() }
            return
        }

        val currentVersion = args.getString(ARG_CURRENT_VERSION).orEmpty()
        val newVersion = args.getString(ARG_NEW_VERSION).orEmpty()
        val changelog = args.getString(ARG_CHANGELOG).orEmpty()
        val releaseUrl = args.getString(ARG_RELEASE_URL).orEmpty()
            .ifBlank { AppUpdateChecker.GITHUB_RELEASES_PAGE_URL }
        val apkUrl = args.getString(ARG_APK_URL)?.takeIf { it.isNotBlank() }
        val apkSize = args.getLong(ARG_APK_SIZE, -1L).takeIf { it > 0L }

        binding.tvUpdateVersions.text = getString(
            R.string.update_version_transition,
            currentVersion,
            newVersion
        )

        if (changelog.isNotBlank()) {
            binding.cardUpdateChangelog.visibility = View.VISIBLE
            binding.tvUpdateChangelog.text = changelog
        } else {
            binding.cardUpdateChangelog.visibility = View.GONE
        }

        val primaryUrl = apkUrl ?: releaseUrl
        val sizeStr = AppUpdateChecker.formatFileSize(apkSize)
        binding.btnUpdateDownload.text = when {
            apkUrl != null && sizeStr.isNotEmpty() ->
                getString(R.string.update_download_apk_size, sizeStr)
            apkUrl != null ->
                getString(R.string.update_download_apk)
            else ->
                getString(R.string.update_open_github)
        }

        M3Motion.attachPressSpring(binding.btnUpdateDownload)
        binding.btnUpdateDownload.setOnClickListener {
            openUrl(primaryUrl)
            dismiss()
        }

        if (apkUrl != null) {
            binding.btnUpdateOpenRelease.visibility = View.VISIBLE
            binding.btnUpdateOpenRelease.setOnClickListener {
                openUrl(releaseUrl)
                dismiss()
            }
        } else {
            binding.btnUpdateOpenRelease.visibility = View.GONE
        }

        binding.btnUpdateSkip.setOnClickListener {
            if (newVersion.isNotBlank()) {
                PrefsManager(requireContext()).setSkippedUpdateVersion(newVersion)
            }
            dismiss()
        }

        binding.btnUpdateLater.setOnClickListener {
            dismiss()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CURRENT_VERSION = "current_version"
        private const val ARG_NEW_VERSION = "new_version"
        private const val ARG_CHANGELOG = "changelog"
        private const val ARG_RELEASE_URL = "release_url"
        private const val ARG_APK_URL = "apk_url"
        private const val ARG_APK_SIZE = "apk_size"

        fun newInstance(
            currentVersion: String,
            release: AppReleaseInfo
        ): UpdateAvailableBottomSheet {
            return UpdateAvailableBottomSheet().apply {
                arguments = bundleOf(
                    ARG_CURRENT_VERSION to currentVersion,
                    ARG_NEW_VERSION to release.versionName,
                    ARG_CHANGELOG to release.changelog,
                    ARG_RELEASE_URL to release.releaseUrl,
                    ARG_APK_URL to release.apkDownloadUrl,
                    ARG_APK_SIZE to (release.apkSizeBytes ?: -1L)
                )
            }
        }
    }
}
