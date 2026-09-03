package com.example.widget

import android.content.Intent
import android.content.Context
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale

/**
 * Contract between home-screen widgets and [MainActivity].
 * Keep these names in sync with the extras read in MainActivity.handleWidgetIntent().
 */
object WidgetIntents {
    const val EXTRA_CREATE_EVENT = "create_event"
    const val EXTRA_CREATE_TITLE = "create_title"
    const val EXTRA_CREATE_CATEGORY = "create_category"
    const val EXTRA_CREATE_MINUTES = "create_minutes"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_JUMP_DATE = "jump_date"
    const val EXTRA_VIEW_MODE = "view_mode"
}

/** Typed [ActionParameters] keys carrying the [WidgetIntents] extras. */
object WidgetActionKeys {
    val CreateEvent = ActionParameters.Key<Boolean>(WidgetIntents.EXTRA_CREATE_EVENT)
    val CreateTitle = ActionParameters.Key<String>(WidgetIntents.EXTRA_CREATE_TITLE)
    val CreateCategory = ActionParameters.Key<String>(WidgetIntents.EXTRA_CREATE_CATEGORY)
    val CreateMinutes = ActionParameters.Key<Int>(WidgetIntents.EXTRA_CREATE_MINUTES)
    val EventId = ActionParameters.Key<Long>(WidgetIntents.EXTRA_EVENT_ID)
    val JumpDate = ActionParameters.Key<Long>(WidgetIntents.EXTRA_JUMP_DATE)
    val ViewMode = ActionParameters.Key<String>(WidgetIntents.EXTRA_VIEW_MODE)
}

/** Shared intent actions every widget can trigger. */
object WidgetActions {

    /** Opens the app without any special navigation. */
    fun openApp(context: Context): Action =
        actionStartActivity(Intent(context, MainActivity::class.java))

    /** Opens the event editor pre-filled with an optional template. */
    fun createEvent(
        context: Context,
        title: String? = null,
        category: String? = null,
        durationMinutes: Int = 60
    ): Action {
        val params = when {
            title != null && category != null -> actionParametersOf(
                WidgetActionKeys.CreateEvent to true,
                WidgetActionKeys.CreateTitle to title,
                WidgetActionKeys.CreateCategory to category,
                WidgetActionKeys.CreateMinutes to durationMinutes
            )
            title != null -> actionParametersOf(
                WidgetActionKeys.CreateEvent to true,
                WidgetActionKeys.CreateTitle to title,
                WidgetActionKeys.CreateMinutes to durationMinutes
            )
            else -> actionParametersOf(
                WidgetActionKeys.CreateEvent to true,
                WidgetActionKeys.CreateMinutes to durationMinutes
            )
        }
        return actionStartActivity(Intent(context, MainActivity::class.java), params)
    }

    /** Opens the Day view for the given date. */
    fun jumpToDate(context: Context, dateMillis: Long): Action =
        actionStartActivity(
            Intent(context, MainActivity::class.java),
            actionParametersOf(WidgetActionKeys.JumpDate to dateMillis)
        )

    /** Jumps straight to a named app section (e.g. "agenda", "month"). */
    fun openView(context: Context, viewMode: String): Action =
        actionStartActivity(
            Intent(context, MainActivity::class.java),
            actionParametersOf(WidgetActionKeys.ViewMode to viewMode)
        )

    /** Opens the details sheet for a specific event. */
    fun openEvent(context: Context, eventId: Long): Action =
        actionStartActivity(
            Intent(context, MainActivity::class.java),
            actionParametersOf(WidgetActionKeys.EventId to eventId)
        )
}

/** Safe, single-shot data loading used by all widgets. */
internal object WidgetData {

    /** Loads every locally stored event; returns an empty list if the DB is unavailable. */
    suspend fun loadAllEvents(context: Context): List<EventEntity> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context.applicationContext, null)
        runCatching { db.eventDao().getAllEvents().first() }.getOrDefault(emptyList())
    }
}

/** Small formatting helpers shared by widget layouts. */
internal object WidgetFormat {

    /** Narrow single-glyph weekday initials, Sunday-first to match the month grid. */
    fun dayInitials(): List<String> {
        val narrow = SimpleDateFormat("EEEEE", Locale.getDefault())
        // 2023-01-01 is a Sunday; walk 7 days to label the grid columns.
        val cursor = GregorianCalendar(2023, Calendar.JANUARY, 1)
        return List(7) {
            val label = narrow.format(cursor.time)
            cursor.add(Calendar.DAY_OF_MONTH, 1)
            if (label.isEmpty()) "?" else label.substring(0, 1).uppercase(Locale.getDefault())
        }
    }

    /** "Today" / "Tomorrow" / "Mon, Sep 8" style label for a timestamp. */
    fun friendlyDay(millis: Long): String {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        if (DateTimeUtils.isSameDay(today, target)) return "Today"
        today.add(Calendar.DAY_OF_MONTH, 1)
        if (DateTimeUtils.isSameDay(today, target)) return "Tomorrow"
        return SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(millis))
    }

    /** "All day" or a locale time such as "2:30 PM". */
    fun timeLabel(event: EventEntity): String =
        if (event.isAllDay) "All day"
        else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.startMillis))

    /** "2h 30m" style duration, rounded down to minutes. */
    fun duration(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /** Locale month + year heading, e.g. "September 2026". */
    fun monthYear(calendar: Calendar): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
}
