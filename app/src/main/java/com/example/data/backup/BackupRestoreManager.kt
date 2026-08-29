package com.example.data.backup

import com.example.data.dao.CalendarDao
import com.example.data.dao.EventDao
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class BackupData(
    val exportTimestamp: Long,
    val appVersion: String,
    val calendars: List<CalendarEntity>,
    val events: List<EventEntity>
)

data class RestoreResult(
    val success: Boolean,
    val calendarsRestored: Int,
    val eventsRestored: Int,
    val message: String
)

class BackupRestoreManager(
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao
) {
    private val utcDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val calendars = calendarDao.getAllCalendars().first()
        val events = eventDao.getAllEvents().first()

        val rootObj = JSONObject()
        rootObj.put("appName", "Local Calendar")
        rootObj.put("version", "1.0")
        rootObj.put("exportTimestamp", System.currentTimeMillis())
        rootObj.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // Calendars
        val calArray = JSONArray()
        for (cal in calendars) {
            val calObj = JSONObject()
            calObj.put("id", cal.id)
            calObj.put("name", cal.name)
            calObj.put("accountName", cal.accountName)
            calObj.put("color", cal.color)
            calObj.put("isVisible", cal.isVisible)
            calObj.put("isLocal", cal.isLocal)
            calObj.put("systemCalendarId", cal.systemCalendarId ?: JSONObject.NULL)
            calArray.put(calObj)
        }
        rootObj.put("calendars", calArray)

        // Events
        val eventArray = JSONArray()
        for (ev in events) {
            val evObj = JSONObject()
            evObj.put("id", ev.id)
            evObj.put("calendarId", ev.calendarId)
            evObj.put("title", ev.title)
            evObj.put("description", ev.description)
            evObj.put("location", ev.location)
            evObj.put("startMillis", ev.startMillis)
            evObj.put("endMillis", ev.endMillis)
            evObj.put("isAllDay", ev.isAllDay)
            evObj.put("color", ev.color)
            evObj.put("category", ev.category)
            evObj.put("recurrence", ev.recurrence)
            evObj.put("rrule", ev.rrule ?: JSONObject.NULL)
            evObj.put("recurrenceUntilMillis", ev.recurrenceUntilMillis ?: JSONObject.NULL)
            evObj.put("timezone", ev.timezone ?: JSONObject.NULL)
            evObj.put("reminderMinutes", ev.reminderMinutes)
            evObj.put("isCompleted", ev.isCompleted)
            evObj.put("createdAt", ev.createdAt)
            evObj.put("updatedAt", ev.updatedAt)
            eventArray.put(evObj)
        }
        rootObj.put("events", eventArray)

        return@withContext rootObj.toString(2)
    }

    suspend fun restoreFromJson(jsonString: String, clearExisting: Boolean = false): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (clearExisting) {
                eventDao.deleteAllEvents()
                calendarDao.deleteAllCalendars()
            }

            val calArray = root.optJSONArray("calendars") ?: JSONArray()
            val oldToNewCalIdMap = mutableMapOf<Long, Long>()
            var calendarsRestored = 0

            for (i in 0 until calArray.length()) {
                val obj = calArray.getJSONObject(i)
                val oldId = obj.getLong("id")
                val name = obj.getString("name")
                val account = obj.optString("accountName", "Local Storage")
                val color = obj.optInt("color", 0xFF039BE5.toInt())
                val isVisible = obj.optBoolean("isVisible", true)
                val isLocal = obj.optBoolean("isLocal", true)

                val newCal = CalendarEntity(
                    name = name,
                    accountName = account,
                    color = color,
                    isVisible = isVisible,
                    isLocal = isLocal
                )
                val newId = calendarDao.insertCalendar(newCal)
                oldToNewCalIdMap[oldId] = newId
                calendarsRestored++
            }

            val eventArray = root.optJSONArray("events") ?: JSONArray()
            val restoredEvents = mutableListOf<EventEntity>()

            for (i in 0 until eventArray.length()) {
                val obj = eventArray.getJSONObject(i)
                val oldCalId = obj.optLong("calendarId", 1L)
                val targetCalId = oldToNewCalIdMap[oldCalId] ?: (if (calendarsRestored > 0) oldToNewCalIdMap.values.first() else 1L)

                val title = obj.getString("title")
                val desc = obj.optString("description", "")
                val loc = obj.optString("location", "")
                val start = obj.getLong("startMillis")
                val end = obj.optLong("endMillis", start + 3600_000L)
                val isAllDay = obj.optBoolean("isAllDay", false)
                val color = obj.optInt("color", 0)
                val category = obj.optString("category", "General")
                val recurrence = obj.optString("recurrence", "NONE")
                val rrule = obj.optString("rrule", "").takeIf { it.isNotBlank() }
                val recurrenceUntilMillis = if (obj.has("recurrenceUntilMillis") && !obj.isNull("recurrenceUntilMillis")) {
                    obj.optLong("recurrenceUntilMillis")
                } else {
                    null
                }
                val timezone = obj.optString("timezone", "").takeIf { it.isNotBlank() }
                val reminder = obj.optInt("reminderMinutes", 10)
                val completed = obj.optBoolean("isCompleted", false)

                restoredEvents.add(
                    EventEntity(
                        calendarId = targetCalId,
                        title = title,
                        description = desc,
                        location = loc,
                        startMillis = start,
                        endMillis = end,
                        isAllDay = isAllDay,
                        color = color,
                        category = category,
                        recurrence = recurrence,
                        rrule = rrule,
                        recurrenceUntilMillis = recurrenceUntilMillis,
                        timezone = timezone,
                        reminderMinutes = reminder,
                        isCompleted = completed
                    )
                )
            }

            if (restoredEvents.isNotEmpty()) {
                eventDao.insertEvents(restoredEvents)
            }

            return@withContext RestoreResult(
                success = true,
                calendarsRestored = calendarsRestored,
                eventsRestored = restoredEvents.size,
                message = "Successfully restored $calendarsRestored calendars and ${restoredEvents.size} events."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext RestoreResult(
                success = false,
                calendarsRestored = 0,
                eventsRestored = 0,
                message = "Failed to parse JSON backup: ${e.message}"
            )
        }
    }

    suspend fun exportToIcs(): String = withContext(Dispatchers.IO) {
        val events = eventDao.getAllEvents().first()
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Local Calendar App//Google MD3//EN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")

        for (ev in events) {
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:local-event-${ev.id}-${ev.createdAt}@localcalendar.app\r\n")
            sb.append("DTSTAMP:${utcDateFormat.format(Date(ev.createdAt))}\r\n")
            sb.append("SUMMARY:${escapeIcs(ev.title)}\r\n")
            if (ev.description.isNotEmpty()) {
                sb.append("DESCRIPTION:${escapeIcs(ev.description)}\r\n")
            }
            if (ev.location.isNotEmpty()) {
                sb.append("LOCATION:${escapeIcs(ev.location)}\r\n")
            }
            if (ev.isAllDay) {
                val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
                sb.append("DTSTART;VALUE=DATE:${dayFormat.format(Date(ev.startMillis))}\r\n")
                sb.append("DTEND;VALUE=DATE:${dayFormat.format(Date(ev.endMillis))}\r\n")
            } else if (ev.timezone != null) {
                sb.append("DTSTART;TZID=${ev.timezone}:${utcDateFormat.format(Date(ev.startMillis))}\r\n")
                sb.append("DTEND;TZID=${ev.timezone}:${utcDateFormat.format(Date(ev.endMillis))}\r\n")
            } else {
                sb.append("DTSTART:${utcDateFormat.format(Date(ev.startMillis))}\r\n")
                sb.append("DTEND:${utcDateFormat.format(Date(ev.endMillis))}\r\n")
            }
            if (ev.rrule != null) {
                sb.append("RRULE:${ev.rrule}\r\n")
            } else if (ev.recurrence != "NONE") {
                sb.append("RRULE:FREQ=${ev.recurrence}\r\n")
            }
            sb.append("CATEGORIES:${escapeIcs(ev.category)}\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return@withContext sb.toString()
    }

    suspend fun importFromIcs(icsContent: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val rawLines = icsContent.lines()
            val logicalLines = mutableListOf<String>()
            for (line in rawLines) {
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    if (logicalLines.isNotEmpty()) {
                        logicalLines[logicalLines.lastIndex] += line.drop(1)
                    }
                } else {
                    logicalLines.add(line.trim())
                }
            }
            val lines = logicalLines

            var importedEvents = 0
            val newEvents = mutableListOf<EventEntity>()

            // Get or create import calendar
            val defaultCal = calendarDao.getAllCalendars().first().firstOrNull()
            val calId = defaultCal?.id ?: calendarDao.insertCalendar(
                CalendarEntity(
                    name = "Imported ICS Calendar",
                    accountName = "ICS Import",
                    color = 0xFF8E24AA.toInt(),
                    isLocal = true
                )
            )

            var inEvent = false
            var title = ""
            var description = ""
            var location = ""
            var startMillis = 0L
            var endMillis = 0L
            var isAllDay = false
            var recurrence = "NONE"
            var rrule: String? = null
            var timezone: String? = null
            var category = "General"

            val icsUtcFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val icsLocalFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
            val icsDayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

            for (line in lines) {
                when {
                    line.startsWith("BEGIN:VEVENT") -> {
                        inEvent = true
                        title = ""
                        description = ""
                        location = ""
                        startMillis = 0L
                        endMillis = 0L
                        isAllDay = false
                        recurrence = "NONE"
                        rrule = null
                        timezone = null
                        category = "General"
                    }
                    line.startsWith("END:VEVENT") -> {
                        if (inEvent && title.isNotEmpty()) {
                            if (startMillis == 0L) startMillis = System.currentTimeMillis()
                            if (endMillis <= startMillis) endMillis = startMillis + 3600_000L

                            newEvents.add(
                                EventEntity(
                                    calendarId = calId,
                                    title = unescapeIcs(title),
                                    description = unescapeIcs(description),
                                    location = unescapeIcs(location),
                                    startMillis = startMillis,
                                    endMillis = endMillis,
                                    isAllDay = isAllDay,
                                    category = category,
                                    recurrence = recurrence,
                                    rrule = rrule,
                                    timezone = timezone
                                )
                            )
                            importedEvents++
                        }
                        inEvent = false
                    }
                    inEvent && line.startsWith("SUMMARY:") -> {
                        title = line.substringAfter("SUMMARY:")
                    }
                    inEvent && line.startsWith("DESCRIPTION:") -> {
                        description = line.substringAfter("DESCRIPTION:")
                    }
                    inEvent && line.startsWith("LOCATION:") -> {
                        location = line.substringAfter("LOCATION:")
                    }
                    inEvent && line.startsWith("CATEGORIES:") -> {
                        category = line.substringAfter("CATEGORIES:")
                    }
                    inEvent && line.startsWith("RRULE:") -> {
                        val rule = line.substringAfter("RRULE:")
                        rrule = rule
                        recurrence = when {
                            rule.contains("FREQ=DAILY") -> "DAILY"
                            rule.contains("FREQ=WEEKLY") -> "WEEKLY"
                            rule.contains("FREQ=MONTHLY") -> "MONTHLY"
                            rule.contains("FREQ=YEARLY") -> "YEARLY"
                            else -> "NONE"
                        }
                    }
                    inEvent && (line.startsWith("DTSTART") || line.startsWith("DTSTART;")) -> {
                        val head = line.substringBefore(":")
                        val value = line.substringAfter(":")
                        val tzid = head.split(";").firstOrNull { it.startsWith("TZID=") }?.substringAfter("=")
                        if (line.contains("VALUE=DATE") || value.length == 8) {
                            isAllDay = true
                            startMillis = icsDayFormat.parse(value)?.time ?: System.currentTimeMillis()
                            timezone = null
                        } else if (value.endsWith("Z")) {
                            startMillis = icsUtcFormat.parse(value)?.time ?: System.currentTimeMillis()
                            timezone = null
                        } else if (tzid != null) {
                            val zoneFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone(tzid)
                            }
                            startMillis = zoneFormat.parse(value)?.time ?: System.currentTimeMillis()
                            timezone = tzid
                        } else {
                            startMillis = icsLocalFormat.parse(value)?.time ?: System.currentTimeMillis()
                            timezone = null
                        }
                    }
                    inEvent && (line.startsWith("DTEND") || line.startsWith("DTEND;")) -> {
                        val head = line.substringBefore(":")
                        val value = line.substringAfter(":")
                        val tzid = head.split(";").firstOrNull { it.startsWith("TZID=") }?.substringAfter("=")
                        if (line.contains("VALUE=DATE") || value.length == 8) {
                            endMillis = icsDayFormat.parse(value)?.time ?: (startMillis + 86400_000L)
                        } else if (value.endsWith("Z")) {
                            endMillis = icsUtcFormat.parse(value)?.time ?: (startMillis + 3600_000L)
                        } else if (tzid != null) {
                            val zoneFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone(tzid)
                            }
                            endMillis = zoneFormat.parse(value)?.time ?: (startMillis + 3600_000L)
                        } else {
                            endMillis = icsLocalFormat.parse(value)?.time ?: (startMillis + 3600_000L)
                        }
                    }
                }
            }

            if (newEvents.isNotEmpty()) {
                eventDao.insertEvents(newEvents)
            }

            return@withContext RestoreResult(
                success = true,
                calendarsRestored = 1,
                eventsRestored = importedEvents,
                message = "Imported $importedEvents events from iCalendar (.ics) file."
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext RestoreResult(
                success = false,
                calendarsRestored = 0,
                eventsRestored = 0,
                message = "Failed to parse iCalendar file: ${e.message}"
            )
        }
    }

    private fun escapeIcs(str: String): String {
        return str.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }

    private fun unescapeIcs(str: String): String {
        return str.replace("\\n", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }
}
