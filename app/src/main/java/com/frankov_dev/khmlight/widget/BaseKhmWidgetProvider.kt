package com.frankov_dev.khmlight.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseKhmWidgetProvider(
    private val kind: WidgetKind
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateWidgetsForKind(
                    context = context,
                    kind = kind,
                    widgetIds = appWidgetIds,
                    fetchFreshIfNoCache = true
                )
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateWidgetsForKind(
                    context = context,
                    kind = kind,
                    widgetIds = intArrayOf(appWidgetId),
                    fetchFreshIfNoCache = false
                )
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = WidgetPrefs(context)
        for (id in appWidgetIds) {
            prefs.deleteConfig(id)
        }
    }
}
