package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val accountName: String = "Local Account",
    val color: Int = 0xFF1A73E8.toInt(), // Default Google Blue
    val isVisible: Boolean = true,
    val isLocal: Boolean = true,
    val systemCalendarId: Long? = null
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val calendarId: Long = 1,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startMillis: Long,
    val endMillis: Long,
    val isAllDay: Boolean = false,
    val color: Int = 0, // 0 = inherit calendar color
    val category: String = "General",
    val recurrence: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY, YEARLY (derived, kept for back-compat)
    val rrule: String? = null, // Full RFC 5545 RRULE (e.g. "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")
    val recurrenceUntilMillis: Long? = null,
    val timezone: String? = null, // IANA TZID preserved from import, e.g. "America/New_York"
    val reminderMinutes: Int = 10, // -1 = None, 0 = at time, 10, 30, 60, 1440
    val systemEventId: Long? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RecurrenceType(val displayName: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

data class CalendarColor(
    val name: String,
    val colorInt: Int
)

object PresetColors {
    val GoogleColors = listOf(
        CalendarColor("Peacock (Default)", 0xFF039BE5.toInt()),
        CalendarColor("Blueberry", 0xFF3F51B5.toInt()),
        CalendarColor("Lavender", 0xFF7986CB.toInt()),
        CalendarColor("Sage", 0xFF33B679.toInt()),
        CalendarColor("Basil", 0xFF0B8043.toInt()),
        CalendarColor("Banana", 0xFFF6BF26.toInt()),
        CalendarColor("Tangerine", 0xFFF4511E.toInt()),
        CalendarColor("Tomato", 0xFFD50000.toInt()),
        CalendarColor("Flamingo", 0xFFE67C73.toInt()),
        CalendarColor("Grape", 0xFF8E24AA.toInt()),
        CalendarColor("Graphite", 0xFF616161.toInt())
    )

    val Categories = listOf(
        "General",
        "Work",
        "Personal",
        "Meeting",
        "Health",
        "Family",
        "Study",
        "Birthdays",
        "Reminders",
        "Travel"
    )
}
