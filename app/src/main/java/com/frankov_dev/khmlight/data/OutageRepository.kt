package com.frankov_dev.khmlight.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class FetchResult(
    val feed: OutagesFeed?,
    val isFromCache: Boolean,
    val syncedAtMs: Long? = null,
    val error: String? = null
)

class OutageRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "outages_cache.json")
    private val prefs = PrefsManager(context)

    companion object {
        const val OUTAGES_URL =
            "https://raw.githubusercontent.com/frankov-dev/khm_outage_parser/master/docs/outages.json"
        const val LATEST_URL =
            "https://raw.githubusercontent.com/frankov-dev/khm_outage_parser/master/docs/latest.json"
        private const val TIMEOUT_MS = 15_000
    }

    suspend fun loadCachedFeed(): Pair<OutagesFeed?, Long?> = withContext(Dispatchers.IO) {
        val feed = readCache()
        if (feed != null && feed.lastUpdated.isNotBlank() && prefs.getLastServerUpdatedIso().isNullOrBlank()) {
            prefs.setLastServerUpdatedIso(feed.lastUpdated)
        }
        feed to prefs.getLastAppSyncMs()
    }

    suspend fun fetchFeed(): FetchResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        val cached = readCache()
        val savedEtag = if (cached != null) prefs.getLastEtag() else null

        // Primary URL supports ETag / 304 Not Modified to skip redundant downloads & JSON parsing
        try {
            when (val response = downloadWithEtag(OUTAGES_URL, savedEtag)) {
                is HttpResult.NotModified -> {
                    val nowMs = System.currentTimeMillis()
                    prefs.setLastAppSyncMs(nowMs)
                    if (cached != null) {
                        return@withContext FetchResult(
                            feed = cached,
                            isFromCache = false,
                            syncedAtMs = nowMs
                        )
                    }
                }
                is HttpResult.Body -> {
                    val parsed = OutageJsonParser.parseOutagesJson(response.text)
                    if (parsed.availableDates.isNotEmpty()) {
                        val nowMs = System.currentTimeMillis()
                        writeCache(response.text)
                        prefs.setLastEtag(response.etag)
                        prefs.setLastServerUpdatedIso(parsed.lastUpdated)
                        prefs.setLastAppSyncMs(nowMs)
                        return@withContext FetchResult(
                            feed = parsed,
                            isFromCache = false,
                            syncedAtMs = nowMs
                        )
                    }
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: e.javaClass.simpleName
        }

        // Fallback to latest.json if primary failed
        try {
            val response = downloadWithEtag(LATEST_URL, etag = null)
            if (response is HttpResult.Body) {
                val parsed = OutageJsonParser.parseOutagesJson(response.text)
                if (parsed.availableDates.isNotEmpty()) {
                    val nowMs = System.currentTimeMillis()
                    writeCache(response.text)
                    prefs.setLastEtag(null)
                    prefs.setLastServerUpdatedIso(parsed.lastUpdated)
                    prefs.setLastAppSyncMs(nowMs)
                    return@withContext FetchResult(
                        feed = parsed,
                        isFromCache = false,
                        syncedAtMs = nowMs
                    )
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: e.javaClass.simpleName
        }

        if (cached != null) {
            FetchResult(
                feed = cached,
                isFromCache = true,
                syncedAtMs = prefs.getLastAppSyncMs(),
                error = lastError
            )
        } else {
            FetchResult(
                feed = null,
                isFromCache = false,
                syncedAtMs = prefs.getLastAppSyncMs(),
                error = lastError ?: "Помилка мережі"
            )
        }
    }

    private sealed class HttpResult {
        data object NotModified : HttpResult()
        data class Body(val text: String, val etag: String?) : HttpResult()
    }

    private fun downloadWithEtag(urlString: String, etag: String?): HttpResult {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KHMlight-Android/1.0")
            if (!etag.isNullOrBlank()) {
                setRequestProperty("If-None-Match", etag)
            }
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return HttpResult.NotModified
            }
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val newEtag = connection.getHeaderField("ETag")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return HttpResult.Body(text = body, etag = newEtag)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(): OutagesFeed? {
        if (!cacheFile.exists()) return null
        return try {
            val raw = cacheFile.readText(Charsets.UTF_8)
            OutageJsonParser.parseOutagesJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(rawJson: String) {
        try {
            cacheFile.writeText(rawJson, Charsets.UTF_8)
        } catch (_: Exception) {
            // Ignore cache write errors
        }
    }
}
