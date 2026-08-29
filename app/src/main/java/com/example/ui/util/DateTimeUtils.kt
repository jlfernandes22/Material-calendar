package com.example.ui.util

import com.example.data.model.EventEntity
import com.example.data.recurrence.RRule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    fun formatMonthYear(calendar: Calendar): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun formatMonthShort(calendar: Calendar): String {
        val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun formatFullDate(millis: Long): String {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatDayOfWeek(calendar: Calendar): String {
        val sdf = SimpleDateFormat("EEE", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun formatDayOfMonth(calendar: Calendar): String {
        val sdf = SimpleDateFormat("d", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun formatTime(millis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun formatTimeRange(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
        if (isAllDay) return "All day"
        val startStr = formatTime(startMillis)
        val endStr = formatTime(endMillis)
        return "$startStr – $endStr"
    }

    fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
        return isSameDay(cal1, cal2)
    }

    fun isToday(millis: Long): Boolean {
        return isSameDay(millis, System.currentTimeMillis())
    }

    fun isToday(cal: Calendar): Boolean {
        return isSameDay(cal, Calendar.getInstance())
    }

    fun getStartOfDay(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getStartOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return getStartOfDay(cal)
    }

    fun getEndOfDay(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun getEndOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return getEndOfDay(cal)
    }

    fun getStartOfMonth(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return getStartOfDay(cal)
    }

    fun getEndOfMonth(calendar: Calendar): Long {
        val cal = calendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        return getEndOfDay(cal)
    }

    fun getStartOfWeek(calendar: Calendar): Calendar {
        val cal = calendar.clone() as Calendar
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    data class MonthDay(
        val dateMillis: Long,
        val dayNumber: Int,
        val isCurrentMonth: Boolean,
        val isToday: Boolean,
        val calendar: Calendar
    )

    /**
     * Generates a standard 6x7 = 42-day calendar grid for the specified month
     */
    fun generateMonthDays(viewCalendar: Calendar): List<MonthDay> {
        val days = mutableListOf<MonthDay>()
        val cal = viewCalendar.clone() as Calendar
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = Calendar.SUNDAY
        while (cal.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }

        val todayCal = Calendar.getInstance()

        for (i in 0 until 42) {
            val isCurrentMonth = cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear
            val isToday = isSameDay(cal, todayCal)

            days.add(
                MonthDay(
                    dateMillis = cal.timeInMillis,
                    dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = isCurrentMonth,
                    isToday = isToday,
                    calendar = cal.clone() as Calendar
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return days
    }

    /**
     * Returns 7 days for the week containing the given calendar
     */
    fun generateWeekDays(viewCalendar: Calendar): List<MonthDay> {
        val days = mutableListOf<MonthDay>()
        val cal = getStartOfWeek(viewCalendar)
        val todayCal = Calendar.getInstance()

        for (i in 0 until 7) {
            days.add(
                MonthDay(
                    dateMillis = cal.timeInMillis,
                    dayNumber = cal.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = true,
                    isToday = isSameDay(cal, todayCal),
                    calendar = cal.clone() as Calendar
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return days
    }

    /**
     * Checks if an event occurs on a specific day (accounting for recurrences)
     */
    fun eventOccursOnDay(event: EventEntity, dayStartMillis: Long, dayEndMillis: Long): Boolean {
        // Direct overlap
        if (event.startMillis <= dayEndMillis && event.endMillis >= dayStartMillis) {
            return true
        }

        // Recurrence expansion
        val rule = effectiveRrule(event)
        if (rule != null) {
            return RRule.occurrences(
                startMillis = event.startMillis,
                rrule = rule,
                recurrenceUntilMillis = event.recurrenceUntilMillis,
                rangeStart = dayStartMillis,
                rangeEnd = dayEndMillis
            ).isNotEmpty()
        }

        return false
    }

    /**
     * Returns all occurrence start times for an event within a range.
     */
    fun occurrencesBetween(event: EventEntity, rangeStartMillis: Long, rangeEndMillis: Long): List<Long> {
        val rule = effectiveRrule(event) ?: return emptyList()
        return RRule.occurrences(
            startMillis = event.startMillis,
            rrule = rule,
            recurrenceUntilMillis = event.recurrenceUntilMillis,
            rangeStart = rangeStartMillis,
            rangeEnd = rangeEndMillis
        )
    }

    /**
     * The next occurrence start strictly after [afterMillis], or null.
     */
    fun nextOccurrenceAfter(event: EventEntity, afterMillis: Long): Long? {
        val rule = effectiveRrule(event) ?: return null
        return RRule.nextOccurrenceAfter(
            startMillis = event.startMillis,
            rrule = rule,
            recurrenceUntilMillis = event.recurrenceUntilMillis,
            afterMillis = afterMillis
        )
    }

    /**
     * Prefer the preserved RRULE string; fall back to a simple derived rule so legacy
     * events (stored only as a recurrence enum) still expand correctly.
     */
    fun effectiveRrule(event: EventEntity): String? {
        event.rrule?.let { return it }
        return when (event.recurrence) {
            "NONE" -> null
            "DAILY" -> "FREQ=DAILY"
            "WEEKLY" -> "FREQ=WEEKLY"
            "MONTHLY" -> "FREQ=MONTHLY"
            "YEARLY" -> "FREQ=YEARLY"
            else -> null
        }
    }
}
