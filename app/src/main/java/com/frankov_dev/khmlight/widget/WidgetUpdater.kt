package com.frankov_dev.khmlight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import com.frankov_dev.khmlight.MainActivity
import com.frankov_dev.khmlight.R
import com.frankov_dev.khmlight.data.OutageRepository
import com.frankov_dev.khmlight.data.OutagesFeed
import com.frankov_dev.khmlight.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetUpdater {

    private val providerEntries = listOf(
        WidgetKind.W1X1 to Widget1x1Provider::class.java,
        WidgetKind.W1X4 to Widget1x4Provider::class.java,
        WidgetKind.W2X2 to Widget2x2Provider::class.java,
        WidgetKind.W4X2 to Widget4x2Provider::class.java
    )

    fun updateAllWidgets(context: Context, feedOverride: OutagesFeed? = null) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            updateAllWidgetsSuspend(appCtx, feedOverride)
        }
    }

    suspend fun updateAllWidgetsSuspend(
        context: Context,
        feedOverride: OutagesFeed? = null
    ) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val repo = OutageRepository(appCtx)
        val (cached, syncedMs) = repo.loadCachedFeed()
        val feed = feedOverride ?: cached
        updateAllProvidersSync(appCtx, feed, syncedMs)
    }

    fun getActiveWidgetQueues(context: Context): Set<String> {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx)
        val widgetPrefs = WidgetPrefs(appCtx)
        val appPrefs = PrefsManager(appCtx)
        val savedLocations = appPrefs.getSavedLocations()
        val dynamicAvail = WidgetPaletteResolver.isDynamicColorAvailable()
        val queues = mutableSetOf<String>()

        for ((_, cls) in providerEntries) {
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(appCtx, cls))
            }.getOrNull() ?: continue
            for (id in ids) {
                val config = widgetPrefs.getConfig(id, dynamicAvail)
                val matchedLocation = when {
                    config.locationId != null -> savedLocations.firstOrNull { it.id == config.locationId }
                    config.queueOverride == null -> savedLocations.firstOrNull()
                    else -> null
                }
                val qId = matchedLocation?.queueId ?: config.queueOverride ?: appPrefs.getSelectedQueue()
                if (!qId.isNullOrBlank()) {
                    queues.add(qId)
                }
            }
        }
        return queues
    }

    suspend fun updateWidgetsForKind(
        context: Context,
        kind: WidgetKind,
        widgetIds: IntArray,
        fetchFreshIfNoCache: Boolean = true
    ) {
        val appCtx = context.applicationContext
        val repo = OutageRepository(appCtx)
        var (feed, syncMs) = repo.loadCachedFeed()
        if (feed == null && fetchFreshIfNoCache) {
            val res = repo.fetchFeed()
            feed = res.feed
            syncMs = res.syncedAtMs ?: syncMs
        }
        val manager = AppWidgetManager.getInstance(appCtx)
        for (id in widgetIds) {
            val views = buildRemoteViews(appCtx, manager, kind, id, feed, syncMs)
            manager.updateAppWidget(id, views)
        }
    }

    private fun updateAllProvidersSync(
        context: Context,
        feed: OutagesFeed?,
        lastAppSyncMs: Long?
    ) {
        val manager = AppWidgetManager.getInstance(context)
        for ((kind, cls) in providerEntries) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            for (id in ids) {
                val views = buildRemoteViews(context, manager, kind, id, feed, lastAppSyncMs)
                manager.updateAppWidget(id, views)
            }
        }
    }

    private fun buildRemoteViews(
        context: Context,
        manager: AppWidgetManager,
        kind: WidgetKind,
        widgetId: Int,
        feed: OutagesFeed?,
        lastAppSyncMs: Long?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_canvas_host)

        val config = WidgetPrefs(context).getConfig(
            widgetId = widgetId,
            isDynamicAvailable = WidgetPaletteResolver.isDynamicColorAvailable()
        )
        val appPrefs = PrefsManager(context)
        val savedLocations = appPrefs.getSavedLocations()
        val matchedLocation = when {
            config.locationId != null -> savedLocations.firstOrNull { it.id == config.locationId }
            config.queueOverride == null -> savedLocations.firstOrNull()
            else -> null
        }
        val queueId = matchedLocation?.queueId ?: config.queueOverride ?: appPrefs.getSelectedQueue()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            widgetId,
            openIntent,
            pendingIntentFlags()
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPending)

        val data = WidgetDataResolver.resolve(
            feed = feed,
            queueId = queueId,
            lastAppSyncMs = lastAppSyncMs,
            location = matchedLocation
        )

        val options = manager.getAppWidgetOptions(widgetId)
        val (targetW, targetH) = resolveTargetSizeDp(context, kind, options)

        val bitmap = KhmWidgetCanvasRenderer.render(
            context = context,
            kind = kind,
            data = data,
            config = config,
            widthDp = targetW,
            heightDp = targetH
        )
        views.setImageViewBitmap(R.id.ivWidgetCanvas, bitmap)
        views.setContentDescription(
            R.id.ivWidgetCanvas,
            "${data.queueBadge} • ${data.statusTitle} • ${data.badgeTimerText} ${data.badgeSubtitle}"
        )

        return views
    }

    private fun resolveTargetSizeDp(
        context: Context,
        kind: WidgetKind,
        options: Bundle
    ): Pair<Int, Int> {
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<android.util.SizeF>(
                AppWidgetManager.OPTION_APPWIDGET_SIZES
            )?.filter { it.width > 0f && it.height > 0f }
            if (!sizes.isNullOrEmpty()) {
                val chosen = if (sizes.size >= 2) {
                    if (isLandscape) sizes.maxByOrNull { it.width } else sizes.minByOrNull { it.width }
                } else {
                    sizes.first()
                }
                if (chosen != null) {
                    return chosen.width.toInt() to chosen.height.toInt()
                }
            }
        }

        val minW = options.getInt(
            if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            0
        ).takeIf { it > 0 } ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = options.getInt(
            if (isLandscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            0
        ).takeIf { it > 0 } ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (minW > 0 && minH > 0) {
            return minW to minH
        }

        return when (kind) {
            WidgetKind.W1X1 -> 88 to 88
            WidgetKind.W1X4 -> 348 to 88
            WidgetKind.W2X2 -> 170 to 170
            WidgetKind.W4X2 -> 348 to 164
        }
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }
}
