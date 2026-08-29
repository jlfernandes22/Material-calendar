package com.example.data.sync

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.dao.CalendarDao
import com.example.data.dao.EventDao
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DeviceCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isSelected: Boolean = true
)

data class SyncResult(
    val success: Boolean,
    val calendarsImported: Int,
    val eventsImported: Int,
    val message: String
)

class DeviceCalendarSyncManager(
    private val context: Context,
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao
) {
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getDeviceCalendars(): List<DeviceCalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) {
            return@withContext emptyList()
        }

        val calendars = mutableListOf<DeviceCalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        try {
            val uri: Uri = CalendarContract.Calendars.CONTENT_URI
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val colorIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val name = it.getString(nameIdx) ?: "Unnamed Calendar"
                    val account = it.getString(accountIdx) ?: "Google Calendar"
                    val color = try {
                        it.getInt(colorIdx)
                    } catch (e: Exception) {
                        0xFF1A73E8.toInt()
                    }

                    calendars.add(
                        DeviceCalendarInfo(
                            id = id,
                            displayName = name,
                            accountName = account,
                            color = if (color != 0) color else 0xFF1A73E8.toInt()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext calendars
    }

    suspend fun importFromDevice(selectedCalendarIds: Set<Long>? = null): SyncResult = withContext(Dispatchers.IO) {
        if (!hasCalendarPermission()) {
            return@withContext SyncResult(
                success = false,
                calendarsImported = 0,
                eventsImported = 0,
                message = "Calendar permission is required to read Google Calendar data."
            )
        }

        val deviceCalendars = getDeviceCalendars()
        if (deviceCalendars.isEmpty()) {
            return@withContext SyncResult(
                success = false,
                calendarsImported = 0,
                eventsImported = 0,
                message = "No Google or device calendars found on this phone."
            )
        }

        var importedCalendarsCount = 0
        var importedEventsCount = 0

        val calendarMapping = mutableMapOf<Long, Long>() // System Cal ID -> Local DB Cal ID

        for (devCal in deviceCalendars) {
            if (selectedCalendarIds != null && !selectedCalendarIds.contains(devCal.id)) {
                continue
            }

            // Check if already in DB
            val existing = calendarDao.getCalendarBySystemId(devCal.id)
            val localCalId = if (existing != null) {
                existing.id
            } else {
                val newCal = CalendarEntity(
                    name = devCal.displayName,
                    accountName = devCal.accountName,
                    color = devCal.color,
                    isVisible = true,
                    isLocal = false,
                    systemCalendarId = devCal.id
                )
                val id = calendarDao.insertCalendar(newCal)
                importedCalendarsCount++
                id
            }
            calendarMapping[devCal.id] = localCalId
        }

        // Query events
        val eventProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EVENT_COLOR
        )

        try {
            val uri = CalendarContract.Events.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                eventProjection,
                null,
                null,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val calIdIdx = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val rruleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
                val colorIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_COLOR)

                val batchEvents = mutableListOf<EventEntity>()

                while (it.moveToNext()) {
                    val sysEventId = it.getLong(idIdx)
                    val sysCalId = it.getLong(calIdIdx)
                    val localCalId = calendarMapping[sysCalId] ?: continue

                    val title = it.getString(titleIdx) ?: "Untitled Event"
                    val desc = it.getString(descIdx) ?: ""
                    val loc = it.getString(locIdx) ?: ""
                    val start = it.getLong(startIdx)
                    var end = it.getLong(endIdx)
                    if (end <= 0 || end < start) {
                        end = start + 3600_000L // default 1 hr
                    }
                    val isAllDay = it.getInt(allDayIdx) == 1
                    val rrule = it.getString(rruleIdx) ?: "NONE"
                    val recurrence = when {
                        rrule.contains("DAILY") -> "DAILY"
                        rrule.contains("WEEKLY") -> "WEEKLY"
                        rrule.contains("MONTHLY") -> "MONTHLY"
                        rrule.contains("YEARLY") -> "YEARLY"
                        else -> "NONE"
                    }
                    val color = try { it.getInt(colorIdx) } catch (e: Exception) { 0 }

                    val existingEvent = eventDao.getEventBySystemId(sysEventId)
                    if (existingEvent == null) {
                        batchEvents.add(
                            EventEntity(
                                calendarId = localCalId,
                                title = title,
                                description = desc,
                                location = loc,
                                startMillis = start,
                                endMillis = end,
                                isAllDay = isAllDay,
                                color = color,
                                category = inferCategory(title, desc),
                                recurrence = recurrence,
                                systemEventId = sysEventId
                            )
                        )
                    }
                }

                if (batchEvents.isNotEmpty()) {
                    eventDao.insertEvents(batchEvents)
                    importedEventsCount = batchEvents.size
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext SyncResult(
                success = false,
                calendarsImported = importedCalendarsCount,
                eventsImported = importedEventsCount,
                message = "Error querying events: ${e.message}"
            )
        }

        return@withContext SyncResult(
            success = true,
            calendarsImported = importedCalendarsCount,
            eventsImported = importedEventsCount,
            message = "Imported $importedCalendarsCount calendars and $importedEventsCount events from device Google Calendar."
        )
    }

    suspend fun importSampleGoogleCalendar(): SyncResult = withContext(Dispatchers.IO) {
        val googleCal = CalendarEntity(
            name = "Google Calendar (Sample)",
            accountName = "user@gmail.com",
            color = 0xFF4285F4.toInt(), // Google Blue
            isVisible = true,
            isLocal = false
        )
        val calId = calendarDao.insertCalendar(googleCal)

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val today9am = cal.timeInMillis

        val sampleEvents = listOf(
            EventEntity(
                calendarId = calId,
                title = "Team Standup & Sync 💬",
                description = "Google Meet: https://meet.google.com/xyz-demo",
                location = "Google Meet",
                startMillis = today9am,
                endMillis = today9am + 1800_000L, // 30 min
                category = "Meeting",
                color = 0xFF4285F4.toInt(),
                recurrence = "DAILY"
            ),
            EventEntity(
                calendarId = calId,
                title = "Product Design Review 🎨",
                description = "Review Material 3 design tokens and responsive calendar widget layout.",
                location = "Design Lab / Conference A",
                startMillis = today9am + 7200_000L, // 11:00
                endMillis = today9am + 10800_000L, // 12:00
                category = "Work",
                color = 0xFF34A853.toInt()
            ),
            EventEntity(
                calendarId = calId,
                title = "Lunch with Alex & Jordan 🥗",
                description = "Healthy bowls at Green Deli",
                location = "Green Deli, 4th Street",
                startMillis = today9am + 14400_000L, // 13:00
                endMillis = today9am + 1800_000L * 3, // 14:00
                category = "Personal",
                color = 0xFFFBBC05.toInt()
            ),
            EventEntity(
                calendarId = calId,
                title = "Quarterly Strategy All-Hands 📊",
                description = "Company updates and Q3 milestones presentation.",
                location = "Main Auditorium & Live Stream",
                startMillis = today9am + 86400_000L + 3600_000L * 5, // Tomorrow 14:00
                endMillis = today9am + 86400_000L + 3600_000L * 7, // Tomorrow 16:00
                category = "Meeting",
                color = 0xFFEA4335.toInt()
            ),
            EventEntity(
                calendarId = calId,
                title = "Doctor Appointment 🩺",
                description = "Routine health checkup",
                location = "Downtown Medical Center",
                startMillis = today9am + 86400_000L * 2 + 3600_000L, // Day after tomorrow 10:00
                endMillis = today9am + 86400_000L * 2 + 3600_000L * 2,
                category = "Health",
                color = 0xFF0B8043.toInt()
            ),
            EventEntity(
                calendarId = calId,
                title = "Emma's Birthday Party 🎂",
                description = "Bring gift & card!",
                location = "Riverdale Club House",
                startMillis = today9am + 86400_000L * 4 + 3600_000L * 9, // Weekend evening
                endMillis = today9am + 86400_000L * 4 + 3600_000L * 13,
                category = "Celebration",
                color = 0xFFE67C73.toInt()
            )
        )

        eventDao.insertEvents(sampleEvents)

        return@withContext SyncResult(
            success = true,
            calendarsImported = 1,
            eventsImported = sampleEvents.size,
            message = "Imported 1 Google account calendar with ${sampleEvents.size} realistic sample events into local database."
        )
    }

    private fun inferCategory(title: String, desc: String): String {
        val combined = "$title $desc".lowercase()
        return when {
            combined.contains("meeting") || combined.contains("sync") || combined.contains("standup") || combined.contains("call") -> "Meeting"
            combined.contains("work") || combined.contains("project") || combined.contains("review") || combined.contains("sprint") -> "Work"
            combined.contains("doctor") || combined.contains("dentist") || combined.contains("workout") || combined.contains("gym") || combined.contains("run") -> "Health"
            combined.contains("birthday") || combined.contains("party") || combined.contains("anniversary") || combined.contains("celebrat") -> "Celebration"
            combined.contains("flight") || combined.contains("hotel") || combined.contains("trip") || combined.contains("travel") -> "Travel"
            combined.contains("exam") || combined.contains("study") || combined.contains("class") || combined.contains("homework") -> "Study"
            else -> "Personal"
        }
    }
}
