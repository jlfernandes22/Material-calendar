package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refreshes all home-screen widgets after calendar data changes.
 * Each widget also re-renders on the system's 30-minute update cycle,
 * so "today"-relative content stays fresh without extra work.
 */
object WidgetUpdater {
    suspend fun update(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            NextEventsWidget().updateAll(context)
            MonthWidget().updateAll(context)
            WeekHorizonWidget().updateAll(context)
            QuickActionsWidget().updateAll(context)
            TimeAllocationWidget().updateAll(context)
        }
    }
}
