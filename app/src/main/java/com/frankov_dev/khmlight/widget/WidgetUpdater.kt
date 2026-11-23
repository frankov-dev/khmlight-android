package com.frankov_dev.khmlight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

object WidgetUpdater {

    fun updateAllWidgets(context: Context, feedOverride: OutagesFeed? = null) {
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val repo = OutageRepository(appCtx)
            val (cached, syncedMs) = repo.loadCachedFeed()
            val feed = feedOverride ?: cached
            updateAllProvidersSync(appCtx, feed, syncedMs)
        }
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
        val providers = listOf(
            WidgetKind.W1X1 to Widget1x1Provider::class.java,
            WidgetKind.W1X4 to Widget1x4Provider::class.java,
            WidgetKind.W2X2 to Widget2x2Provider::class.java,
            WidgetKind.W4X2 to Widget4x2Provider::class.java
        )
        for ((kind, cls) in providers) {
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
        val queueId = config.queueOverride ?: appPrefs.getSelectedQueue()

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
            lastAppSyncMs = lastAppSyncMs
        )

        val options = manager.getAppWidgetOptions(widgetId)
        val (targetW, targetH) = resolveTargetSizeDp(kind, options)

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

    private fun resolveTargetSizeDp(kind: WidgetKind, options: Bundle): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<android.util.SizeF>(
                AppWidgetManager.OPTION_APPWIDGET_SIZES
            )
            val s = sizes?.firstOrNull()
            if (s != null && s.width > 0f && s.height > 0f) {
                return s.width.toInt() to s.height.toInt()
            }
        }

        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
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
