package com.frankov_dev.khmlight.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val releaseUrl: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long?
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object AppUpdateChecker {

    const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/frankov-dev/khmlight-android/releases/latest"
    const val GITHUB_RELEASES_PAGE_URL =
        "https://github.com/frankov-dev/khmlight-android/releases"

    private const val TIMEOUT_MS = 12_000
    private const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun getCurrentVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.1.1"
    }

    /**
     * Checks GitHub Releases for a newer app version.
     * When [force] is false, respects the 24h cooldown and skipped version preference,
     * returning null if no check or prompt is needed.
     */
    suspend fun checkForUpdate(
        context: Context,
        force: Boolean = false
    ): UpdateCheckResult? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefs = PrefsManager(appCtx)
        val nowMs = System.currentTimeMillis()

        if (!force) {
            val lastCheck = prefs.getLastUpdateCheckMs()
            if (lastCheck != null && (nowMs - lastCheck) < AUTO_CHECK_INTERVAL_MS) {
                return@withContext null
            }
        }

        val currentVersion = getCurrentVersionName(appCtx)
        try {
            val release = fetchLatestRelease(currentVersion)
            prefs.setLastUpdateCheckMs(nowMs)

            if (release == null) {
                return@withContext if (force) UpdateCheckResult.UpToDate(currentVersion) else null
            }

            if (isNewerVersion(release.versionName, currentVersion)) {
                if (!force && prefs.getSkippedUpdateVersion() == release.versionName) {
                    return@withContext null
                }
                UpdateCheckResult.UpdateAvailable(release)
            } else {
                UpdateCheckResult.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            if (force) {
                UpdateCheckResult.Error(e.localizedMessage ?: e.javaClass.simpleName)
            } else {
                null
            }
        }
    }

    private fun fetchLatestRelease(currentVersion: String): AppReleaseInfo? {
        val connection = (URL(GITHUB_LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "KHMlight-Android/$currentVersion")
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                // No published releases on GitHub yet
                return null
            }
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseReleaseJson(body)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseReleaseJson(rawJson: String): AppReleaseInfo? {
        val root = JSONObject(rawJson)
        if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
            return null
        }
        val tagName = root.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null

        val versionName = normalizeVersion(tagName)
        val title = root.optString("name", "").trim().ifBlank { tagName }
        val changelog = root.optString("body", "").trim()
        val htmlUrl = root.optString("html_url", "").trim().ifBlank { GITHUB_RELEASES_PAGE_URL }

        var apkUrl: String? = null
        var apkSize: Long? = null
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    val url = asset.optString("browser_download_url", "").trim()
                    if (url.isNotEmpty()) {
                        apkUrl = url
                        val size = asset.optLong("size", -1L)
                        apkSize = if (size > 0L) size else null
                        break
                    }
                }
            }
        }

        return AppReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            changelog = changelog,
            releaseUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkSizeBytes = apkSize
        )
    }

    fun normalizeVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
    }

    /**
     * Compares two semantic-like version strings (e.g. "1.2" vs "1.1.1", "v1.10.0" vs "1.2.3").
     * Returns > 0 if [latest] is newer than [current], 0 if equal, < 0 if older.
     */
    fun compareVersions(latest: String, current: String): Int {
        val a = parseVersionParts(latest)
        val b = parseVersionParts(current)
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isNewerVersion(latest: String, current: String): Boolean {
        return compareVersions(latest, current) > 0
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f МБ", mb)
        } else {
            val kb = (bytes / 1024L).coerceAtLeast(1L)
            "$kb КБ"
        }
    }

    private fun parseVersionParts(raw: String): List<Int> {
        val cleaned = normalizeVersion(raw)
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').map { part ->
            part.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }
}
