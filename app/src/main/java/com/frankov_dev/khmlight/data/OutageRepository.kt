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
        private const val BASE_DOCS_URL =
            "https://raw.githubusercontent.com/frankov-dev/khm_outage_parser/master/docs"
        const val STATUS_URL = "$BASE_DOCS_URL/status.json"
        const val OUTAGES_URL = "$BASE_DOCS_URL/outages.json"
        const val TODAY_URL = "$BASE_DOCS_URL/today.json"
        const val TOMORROW_URL = "$BASE_DOCS_URL/tomorrow.json"
        const val LATEST_URL = "$BASE_DOCS_URL/latest.json"
        private const val TIMEOUT_MS = 15_000
    }

    suspend fun loadCachedFeed(): Pair<OutagesFeed?, Long?> = withContext(Dispatchers.IO) {
        val feed = readCache()
        if (feed != null && feed.lastUpdated.isNotBlank() && prefs.getLastServerUpdatedIso().isNullOrBlank()) {
            prefs.setLastServerUpdatedIso(feed.lastUpdated)
        }
        feed to prefs.getLastAppSyncMs()
    }

    /**
     * Two-step smart sync:
     * 1) Polls lightweight `status.json` (~250B) with ETag + `schedule_hash`.
     *    If unchanged and local cache already matches current dates, skips downloading `outages.json`.
     * 2) Otherwise downloads full `outages.json` (with ETag), with fallback to `today.json` + `tomorrow.json` -> `latest.json` -> cache.
     */
    suspend fun fetchFeed(): FetchResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        val cached = readCache()
        val savedHash = prefs.getLastScheduleHash() ?: cached?.scheduleHash
        var status: FeedStatus? = null

        // Step 1: Fast poll via status.json (~250B)
        if (cached != null) {
            try {
                when (val statusRes = downloadWithEtag(STATUS_URL, prefs.getLastStatusEtag())) {
                    is HttpResult.NotModified -> {
                        val nowMs = System.currentTimeMillis()
                        prefs.setLastAppSyncMs(nowMs)
                        return@withContext FetchResult(
                            feed = cached,
                            isFromCache = false,
                            syncedAtMs = nowMs
                        )
                    }
                    is HttpResult.Body -> {
                        val parsedStatus = OutageJsonParser.parseStatusJson(statusRes.text)
                        status = parsedStatus
                        prefs.setLastStatusEtag(statusRes.etag)
                        if (parsedStatus.lastUpdated.isNotBlank()) {
                            prefs.setLastServerUpdatedIso(parsedStatus.lastUpdated)
                        }

                        val hashMatches = parsedStatus.scheduleHash.isNotBlank() &&
                            parsedStatus.scheduleHash == savedHash
                        val hasTodayInCache = parsedStatus.todayDate == null ||
                            cached.schedules.containsKey(parsedStatus.todayDate)
                        val hasTomorrowInCache = !parsedStatus.hasTomorrow ||
                            parsedStatus.tomorrowDate == null ||
                            cached.schedules.containsKey(parsedStatus.tomorrowDate)

                        if (hashMatches && hasTodayInCache && hasTomorrowInCache) {
                            val nowMs = System.currentTimeMillis()
                            prefs.setLastAppSyncMs(nowMs)
                            return@withContext FetchResult(
                                feed = cached,
                                isFromCache = false,
                                syncedAtMs = nowMs
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Non-fatal: continue directly to outages.json
            }
        }

        // Step 2: Download full outages.json (14 days + previous_schedules + schedule_hash)
        val savedOutagesEtag = if (cached != null) prefs.getLastEtag() else null
        try {
            when (val response = downloadWithEtag(OUTAGES_URL, savedOutagesEtag)) {
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
                        prefs.setLastScheduleHash(parsed.scheduleHash ?: status?.scheduleHash)
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

        // Step 3: Fallback to today.json (+ tomorrow.json when available) merged with cached history
        try {
            val todayRes = downloadWithEtag(TODAY_URL, etag = null)
            if (todayRes is HttpResult.Body) {
                val (todayDay, hash) = OutageJsonParser.parseSingleDayJson(todayRes.text)
                if (todayDay != null) {
                    val merged = linkedMapOf<String, DaySchedule>()
                    cached?.schedules?.forEach { (k, v) -> merged[k] = v }
                    merged[todayDay.date] = todayDay

                    if (status == null || status.hasTomorrow) {
                        runCatching {
                            val tomRes = downloadWithEtag(TOMORROW_URL, etag = null)
                            if (tomRes is HttpResult.Body) {
                                val (tomDay, _) = OutageJsonParser.parseSingleDayJson(tomRes.text)
                                if (tomDay != null) merged[tomDay.date] = tomDay
                            }
                        }
                    }

                    val sortedDates = merged.keys.sortedDescending()
                    val nowMs = System.currentTimeMillis()
                    val feed = OutagesFeed(
                        source = cached?.source.orEmpty(),
                        lastUpdated = status?.lastUpdated ?: cached?.lastUpdated.orEmpty(),
                        availableDates = sortedDates,
                        schedules = merged,
                        scheduleHash = hash ?: status?.scheduleHash
                    )
                    prefs.setLastScheduleHash(feed.scheduleHash)
                    prefs.setLastAppSyncMs(nowMs)
                    return@withContext FetchResult(
                        feed = feed,
                        isFromCache = false,
                        syncedAtMs = nowMs
                    )
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: e.javaClass.simpleName
        }

        // Step 4: Fallback to legacy latest.json
        try {
            val response = downloadWithEtag(LATEST_URL, etag = null)
            if (response is HttpResult.Body) {
                val parsed = OutageJsonParser.parseOutagesJson(response.text)
                if (parsed.availableDates.isNotEmpty()) {
                    val nowMs = System.currentTimeMillis()
                    writeCache(response.text)
                    prefs.setLastEtag(null)
                    prefs.setLastScheduleHash(parsed.scheduleHash)
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
            setRequestProperty("User-Agent", "KHMlight-Android/1.1")
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
