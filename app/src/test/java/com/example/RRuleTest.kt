package com.example

import com.example.data.recurrence.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * JVM unit tests for the RRULE recurrence engine.
 *
 * All expectations are computed in UTC so the tests are timezone-independent
 * (the engine expands occurrences in the device default zone; using UTC as the
 * device zone keeps day boundaries deterministic on CI).
 */
class RRuleTest {

    private fun utc(millis: Long): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }

    private fun utcMillis(
        year: Int, month: Int, day: Int,
        hour: Int = 0, minute: Int = 0, second: Int = 0
    ): Long = utc(0).apply {
        clear()
        set(year, month, day, hour, minute, second)
    }.timeInMillis

    private fun dayStart(millis: Long): Long =
        utc(millis).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // --- DAILY ---

    @Test
    fun `daily event expands every day in range`() {
        val start = utcMillis(2025, Calendar.JULY, 1, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=DAILY",
            recurrenceUntilMillis = null,
            rangeStart = utcMillis(2025, Calendar.JULY, 10),
            rangeEnd = utcMillis(2025, Calendar.JULY, 12, 23, 59)
        )
        assertEquals(
            listOf(
                utcMillis(2025, Calendar.JULY, 10, 9, 0),
                utcMillis(2025, Calendar.JULY, 11, 9, 0),
                utcMillis(2025, Calendar.JULY, 12, 9, 0)
            ),
            occs
        )
    }

    @Test
    fun `daily with interval 3 skips days`() {
        val start = utcMillis(2025, Calendar.JULY, 1, 8, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=DAILY;INTERVAL=3",
            recurrenceUntilMillis = null,
            rangeStart = start,
            rangeEnd = utcMillis(2025, Calendar.JULY, 11, 23, 59)
        )
        // Jul 1, 4, 7, 10
        assertEquals(
            listOf(1, 4, 7, 10),
            occs.map { utc(it).get(Calendar.DAY_OF_MONTH) }
        )
    }

    // --- WEEKLY ---

    @Test
    fun `weekly without byday repeats on start weekday`() {
        // 2025-09-01 is a Monday.
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=WEEKLY",
            recurrenceUntilMillis = null,
            rangeStart = utcMillis(2025, Calendar.SEPTEMBER, 8),
            rangeEnd = utcMillis(2025, Calendar.SEPTEMBER, 30, 23, 59)
        )
        assertEquals(
            listOf(8, 15, 22, 29),
            occs.map { utc(it).get(Calendar.DAY_OF_MONTH) }
        )
    }

    @Test
    fun `weekly byday with multiple days yields every matching weekday`() {
        // Regression: FREQ=WEEKLY;BYDAY=MO,WE previously only produced Mondays.
        // 2025-09-01 is a Monday.
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 10, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE",
            recurrenceUntilMillis = null,
            rangeStart = start,
            rangeEnd = utcMillis(2025, Calendar.SEPTEMBER, 15, 23, 59)
        )
        val expected = listOf(
            utcMillis(2025, Calendar.SEPTEMBER, 1, 10, 0),
            utcMillis(2025, Calendar.SEPTEMBER, 3, 10, 0),
            utcMillis(2025, Calendar.SEPTEMBER, 8, 10, 0),
            utcMillis(2025, Calendar.SEPTEMBER, 10, 10, 0),
            utcMillis(2025, Calendar.SEPTEMBER, 15, 10, 0)
        )
        assertEquals(expected, occs)
    }

    @Test
    fun `weekly byday respects interval weeks`() {
        // 2025-09-01 is a Monday. Every 2nd week on Monday + Friday.
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR",
            recurrenceUntilMillis = null,
            rangeStart = start,
            rangeEnd = utcMillis(2025, Calendar.SEPTEMBER, 30, 23, 59)
        )
        // Week of Sep 1: Mon 1, Fri 5. Week of Sep 15: Mon 15, Fri 19. Week of Sep 29: Mon 29.
        assertEquals(
            listOf(1, 5, 15, 19, 29),
            occs.map { utc(it).get(Calendar.DAY_OF_MONTH) }
        )
    }

    // --- MONTHLY ---

    @Test
    fun `monthly clamps to end of shorter months`() {
        // Jan 31 -> Jan 31, Feb 28, Mar 31, Apr 30 ...
        val start = utcMillis(2025, Calendar.JANUARY, 31, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=MONTHLY",
            recurrenceUntilMillis = null,
            rangeStart = start,
            rangeEnd = utcMillis(2025, Calendar.MAY, 31, 23, 59)
        )
        assertEquals(
            listOf(
                31 to Calendar.JANUARY,
                28 to Calendar.FEBRUARY,
                31 to Calendar.MARCH,
                30 to Calendar.APRIL,
                31 to Calendar.MAY
            ),
            occs.map { utc(it).get(Calendar.DAY_OF_MONTH) to utc(it).get(Calendar.MONTH) }
        )
    }

    // --- UNTIL / COUNT ---

    @Test
    fun `until is inclusive`() {
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0)
        val until = utcMillis(2025, Calendar.SEPTEMBER, 15, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=WEEKLY;UNTIL=20250915T090000Z",
            recurrenceUntilMillis = null,
            rangeStart = start,
            rangeEnd = utcMillis(2025, Calendar.OCTOBER, 31, 23, 59)
        )
        assertEquals(3, occs.size) // Sep 1, 8, 15
        assertEquals(15, occs.last().let { utc(it).get(Calendar.DAY_OF_MONTH) })
    }

    @Test
    fun `count limits total occurrences from dtstart`() {
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=DAILY;COUNT=5",
            recurrenceUntilMillis = null,
            rangeStart = utcMillis(2025, Calendar.SEPTEMBER, 1),
            rangeEnd = utcMillis(2025, Calendar.SEPTEMBER, 30, 23, 59)
        )
        assertEquals(5, occs.size)
        assertEquals(5, occs.last().let { utc(it).get(Calendar.DAY_OF_MONTH) })
    }

    @Test
    fun `recurrenceUntilMillis bounds the expansion`() {
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=DAILY",
            recurrenceUntilMillis = utcMillis(2025, Calendar.SEPTEMBER, 3, 9, 0),
            rangeStart = start,
            rangeEnd = utcMillis(2025, Calendar.SEPTEMBER, 30, 23, 59)
        )
        assertEquals(3, occs.size) // Sep 1, 2, 3
    }

    // --- nextOccurrenceAfter ---

    @Test
    fun `next occurrence is strictly after given time`() {
        val start = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0)
        val next = RRule.nextOccurrenceAfter(
            startMillis = start,
            rrule = "FREQ=DAILY",
            recurrenceUntilMillis = null,
            afterMillis = utcMillis(2025, Calendar.SEPTEMBER, 3, 9, 0)
        )
        assertEquals(utcMillis(2025, Calendar.SEPTEMBER, 4, 9, 0), next)
    }

    @Test
    fun `next occurrence returns null for blank rule`() {
        assertNull(
            RRule.nextOccurrenceAfter(
                startMillis = utcMillis(2025, Calendar.SEPTEMBER, 1, 9, 0),
                rrule = null,
                recurrenceUntilMillis = null,
                afterMillis = utcMillis(2025, Calendar.SEPTEMBER, 2, 9, 0)
            )
        )
    }

    @Test
    fun `far future ranges are reached without runaway iteration`() {
        // 2020-01-06 is a Monday; 313 weeks later is Monday 2026-01-05.
        val start = utcMillis(2020, Calendar.JANUARY, 6, 9, 0)
        val target = utcMillis(2026, Calendar.JANUARY, 5, 9, 0)
        val occs = RRule.occurrences(
            startMillis = start,
            rrule = "FREQ=WEEKLY",
            recurrenceUntilMillis = null,
            rangeStart = target,
            rangeEnd = utcMillis(2026, Calendar.JANUARY, 12, 23, 59)
        )
        assertTrue(occs.isNotEmpty())
        assertEquals(target, occs.first())
    }
}
