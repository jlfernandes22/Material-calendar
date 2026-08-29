package com.example.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.database.AppDatabase
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val TAG = "event_reminder"

    fun schedule(context: Context, event: EventEntity) {
        if (event.reminderMinutes < 0) {
            cancel(context, event.id)
            return
        }
        val now = System.currentTimeMillis()
        val occurrenceStart: Long? = DateTimeUtils.nextOccurrenceAfter(event, now)
            ?: if (event.startMillis > now) event.startMillis else null
        if (occurrenceStart == null) {
            cancel(context, event.id)
            return
        }
        scheduleForOccurrence(context, event, occurrenceStart)
    }

    fun scheduleForOccurrence(context: Context, event: EventEntity, occurrenceStart: Long) {
        if (event.reminderMinutes < 0) {
            cancel(context, event.id)
            return
        }
        val now = System.currentTimeMillis()
        val triggerAt = occurrenceStart - event.reminderMinutes * 60_000L
        if (triggerAt <= now) {
            cancel(context, event.id)
            return
        }
        enqueue(context, event, occurrenceStart, triggerAt - now)
    }

    private fun enqueue(context: Context, event: EventEntity, occurrenceStart: Long, delayMillis: Long) {
        val data = workDataOf(
            ReminderWorker.KEY_EVENT_ID to event.id,
            ReminderWorker.KEY_OCCURRENCE_START to occurrenceStart,
            ReminderWorker.KEY_TITLE to event.title,
            ReminderWorker.KEY_DESCRIPTION to event.description,
            ReminderWorker.KEY_CALENDAR_ID to event.calendarId
        )
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(event.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, eventId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(eventId))
    }

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context.applicationContext, null)
        val events = try {
            db.eventDao().getAllEvents().first()
        } catch (_: Exception) {
            emptyList()
        }
        events.forEach { schedule(context, it) }
    }

    private fun uniqueName(eventId: Long) = "reminder_$eventId"
}
