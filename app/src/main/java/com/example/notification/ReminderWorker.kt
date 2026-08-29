package com.example.notification

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import kotlinx.coroutines.runBlocking

class ReminderWorker(
    appContext: Context,
    private val params: WorkerParameters
) : Worker(appContext, params) {

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_OCCURRENCE_START = "occurrence_start"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CALENDAR_ID = "calendar_id"
    }

    override fun doWork(): Result {
        val context = applicationContext
        val eventId = inputData.getLong(KEY_EVENT_ID, -1L)
        val occurrenceStart = inputData.getLong(KEY_OCCURRENCE_START, -1L)
        val title = inputData.getString(KEY_TITLE) ?: "Event"
        val description = inputData.getString(KEY_DESCRIPTION) ?: ""
        val calendarId = inputData.getLong(KEY_CALENDAR_ID, 1L)

        val event = runCatching {
            runBlocking {
                AppDatabase.getDatabase(context.applicationContext, null)
                    .eventDao()
                    .getEventById(eventId)
            }
        }.getOrNull()

        NotificationHelper.showReminder(
            context,
            event ?: EventEntity(
                id = eventId,
                calendarId = calendarId,
                title = title,
                description = description,
                startMillis = occurrenceStart,
                endMillis = occurrenceStart + 3600_000L
            )
        )

        // Recurring events: schedule the following occurrence.
        if (event != null && occurrenceStart > 0L) {
            val next = DateTimeUtils.nextOccurrenceAfter(event, occurrenceStart)
            if (next != null && next > occurrenceStart) {
                ReminderScheduler.scheduleForOccurrence(context, event, next)
            }
        }

        return Result.success()
    }
}
