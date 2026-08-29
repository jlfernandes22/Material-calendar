package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refreshes all home-screen widgets after calendar data changes.
 */
object WidgetUpdater {
    suspend fun update(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            NextEventsWidget().updateAll(context)
            MonthWidget().updateAll(context)
        }
    }
}
