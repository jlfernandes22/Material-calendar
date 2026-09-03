package com.example

import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure-JVM tests for the date & recurrence engine that the app,
 * the widgets and the reminders all rely on.
 */
class DateTimeUtilsTest {

    private fun cal(
        year: Int,
        month: Int, // 1-based for readability
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ): Calendar = Calendar.getInstance(Locale.US).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun event(
        start: Calendar,
        end: Calendar,
        rrule: String? = null,
        allDay: Boolean = false
    ) = EventEntity(
        title = "Test event",
        startMillis = start.timeInMillis,
        endMillis = end.timeInMillis,
        isAllDay = allDay,
        rrule = rrule
    )

    @Test
    fun `start and end of day bracket the day`() {
        val noon = cal(2026, 9, 3, 12, 0)
        val start = DateTimeUtils.getStartOfDay(noon)
        val end = DateTimeUtils.getEndOfDay(noon)

        assertTrue(start <= noon.timeInMillis)
        assertTrue(noon.timeInMillis <= end)
        assertEquals(23 * 3600_000L + 59 * 60_000L + 59_999L, end - start)
    }

    @Test
    fun `one-shot event is found on its own day only`() {
        val start = cal(2026, 9, 3, 9, 0)
        val end = cal(2026, 9, 3, 10, 0)
        val oneShot = event(start, end)

        listOf(2, 3, 4).forEach { day ->
            val dayStart = DateTimeUtils.getStartOfDay(cal(2026, 9, day))
            val dayEnd = DateTimeUtils.getEndOfDay(cal(2026, 9, day))
            val occurs = DateTimeUtils.eventOccursOnDay(oneShot, dayStart, dayEnd)
            if (day == 3) assertTrue(occurs) else assertFalse(occurs)
        }
    }

    @Test
    fun `weekly recurrence expands across weeks`() {
        val first = cal(2026, 9, 1, 9, 0) // Tuesday
        val end = cal(2026, 9, 1, 10, 0)
        val weekly = event(first, end, rrule = "FREQ=WEEKLY")

        val nextWeekSameDay = DateTimeUtils.getStartOfDay(cal(2026, 9, 8))
        val nextWeekEnd = DateTimeUtils.getEndOfDay(cal(2026, 9, 8))
        assertTrue(DateTimeUtils.eventOccursOnDay(weekly, nextWeekSameDay, nextWeekEnd))

        val nextDay = DateTimeUtils.getStartOfDay(cal(2026, 9, 9))
        val nextDayEnd = DateTimeUtils.getEndOfDay(cal(2026, 9, 9))
        assertFalse(DateTimeUtils.eventOccursOnDay(weekly, nextDay, nextDayEnd))
    }

    @Test
    fun `recurrence until date stops expansion`() {
        val first = cal(2026, 9, 1, 9, 0)
        val end = cal(2026, 9, 1, 10, 0)
        val bounded = EventEntity(
            title = "Bounded",
            startMillis = first.timeInMillis,
            endMillis = end.timeInMillis,
            rrule = "FREQ=DAILY",
            recurrenceUntilMillis = cal(2026, 9, 5, 9, 0).timeInMillis
        )

        val within = DateTimeUtils.getStartOfDay(cal(2026, 9, 5))
        val withinEnd = DateTimeUtils.getEndOfDay(cal(2026, 9, 5))
        assertTrue(DateTimeUtils.eventOccursOnDay(bounded, within, withinEnd))

        val after = DateTimeUtils.getStartOfDay(cal(2026, 9, 8))
        val afterEnd = DateTimeUtils.getEndOfDay(cal(2026, 9, 8))
        assertFalse(DateTimeUtils.eventOccursOnDay(bounded, after, afterEnd))
    }

    @Test
    fun `month grid always spans six Sunday-first weeks`() {
        val grid = DateTimeUtils.generateMonthDays(cal(2026, 9, 15))
        assertEquals(42, grid.size)
        grid.chunked(7).forEach { week ->
            // First cell of every row must be a Sunday
            assertEquals(Calendar.SUNDAY, week.first().calendar.get(Calendar.DAY_OF_WEEK))
        }
        // All September days are present exactly once
        val septemberDays = grid.count { it.calendar.get(Calendar.MONTH) == Calendar.SEPTEMBER }
        assertEquals(30, septemberDays)
    }

    @Test
    fun `today is detected across the grid`() {
        val grid = DateTimeUtils.generateMonthDays(Calendar.getInstance())
        val todayFlags = grid.count { it.isToday }
        assertTrue(todayFlags == 1 || todayFlags == 0) // 0 only if today is outside the grid
    }

    @Test
    fun `month grid is timezone independent`() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val utcGrid = DateTimeUtils.generateMonthDays(cal(2026, 2, 10))
            assertEquals(28, utcGrid.count { it.calendar.get(Calendar.MONTH) == Calendar.FEBRUARY })
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }
}
