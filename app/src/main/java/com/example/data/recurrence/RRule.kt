package com.example.data.recurrence

import java.util.Calendar

/**
 * Minimal RFC 5545 RRULE reader/expander supporting FREQ (DAILY/WEEKLY/MONTHLY/YEARLY),
 * INTERVAL, BYDAY (for weekly), UNTIL and COUNT.
 *
 * Occurrences are computed in the device's local time zone (matching the way events are
 * stored as epoch millis and displayed). Recurring occurrences keep their local wall-clock
 * time across DST transitions because expansion walks [Calendar] fields instead of doing
 * fixed 24h millis arithmetic.
 *
 * Occurrence indices ("k") always count occurrences since DTSTART, so COUNT behaves the
 * same whether or not the queried range starts near DTSTART. When the query range begins
 * long after DTSTART, the index is jumped forward analytically; for WEEKLY+BYDAY the
 * count of skipped occurrences is then an approximation (documented below).
 */
object RRule {

    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 7L * DAY_MS
    private const val MAX_OCCURRENCES = 100_000L

    data class Parsed(
        val freq: String,             // DAILY | WEEKLY | MONTHLY | YEARLY
        val interval: Int,            // default 1
        val byDay: List<Int>,         // Java Calendar.DAY_OF_WEEK constants (1=Sunday .. 7=Saturday)
        val until: Long?,             // inclusive upper bound (millis)
        val count: Int                // -1 = unbounded
    )

    fun parse(rrule: String?): Parsed? {
        if (rrule.isNullOrBlank()) return null
        var freq = ""
        var interval = 1
        val byDay = mutableListOf<Int>()
        var until: Long? = null
        var count = -1

        for (part in rrule.split(";")) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val key = part.substring(0, idx).trim().uppercase()
            val value = part.substring(idx + 1).trim().uppercase()
            when (key) {
                "FREQ" -> freq = value
                "INTERVAL" -> interval = value.toIntOrNull()?.coerceAtLeast(1) ?: 1
                "COUNT" -> count = value.toIntOrNull()?.coerceAtLeast(0) ?: -1
                "UNTIL" -> until = parseUntil(value)
                "BYDAY" -> value.split(",").forEach { day ->
                    val d = mapDay(day.trim().takeLast(2))
                    if (d != 0) byDay.add(d)
                }
            }
        }

        return if (freq.isNotEmpty()) {
            Parsed(freq, interval, byDay.distinct(), until, count)
        } else {
            null
        }
    }

    private fun mapDay(s: String): Int = when (s.uppercase()) {
        "SU" -> Calendar.SUNDAY
        "MO" -> Calendar.MONDAY
        "TU" -> Calendar.TUESDAY
        "WE" -> Calendar.WEDNESDAY
        "TH" -> Calendar.THURSDAY
        "FR" -> Calendar.FRIDAY
        "SA" -> Calendar.SATURDAY
        else -> 0
    }

    private fun parseUntil(value: String): Long? {
        return runCatching {
            val formats = listOf("yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss", "yyyyMMdd")
            for (f in formats) {
                try {
                    val sdf = java.text.SimpleDateFormat(f, java.util.Locale.US)
                    if (f.endsWith("'Z'")) sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    return sdf.parse(value)?.time
                } catch (_: Exception) { }
            }
            null
        }.getOrNull()
    }

    /**
     * Returns occurrence start times in [rangeStart, rangeEnd], in chronological order.
     * UNTIL is treated as inclusive per RFC 5545.
     */
    fun occurrences(
        startMillis: Long,
        rrule: String?,
        recurrenceUntilMillis: Long?,
        rangeStart: Long,
        rangeEnd: Long
    ): List<Long> {
        val parsed = parse(rrule) ?: return emptyList()
        val upper = minOf(recurrenceUntilMillis ?: Long.MAX_VALUE, rangeEnd)
        if (upper < rangeStart) return emptyList()

        val result = mutableListOf<Long>()
        val countLimit = parsed.count

        fun accept(candidate: Long, index: Long): Boolean {
            // Returns false when expansion must stop entirely.
            if (countLimit >= 0 && index >= countLimit) return false
            if (candidate > upper) return false
            if (parsed.until != null && candidate > parsed.until) return false
            if (candidate >= rangeStart) result.add(candidate)
            return true
        }

        when (parsed.freq) {
            "DAILY" -> {
                val approxDays = Math.floorDiv(rangeStart - startMillis, DAY_MS)
                var k = maxOf(0L, Math.floorDiv(approxDays - parsed.interval, parsed.interval.toLong()))
                val cal = Calendar.getInstance().apply {
                    timeInMillis = startMillis
                    add(Calendar.DAY_OF_MONTH, (k * parsed.interval).toInt())
                }
                while (k < MAX_OCCURRENCES) {
                    if (!accept(cal.timeInMillis, k)) break
                    cal.add(Calendar.DAY_OF_MONTH, parsed.interval)
                    k++
                }
            }

            "WEEKLY" -> {
                if (parsed.byDay.isEmpty()) {
                    // Simple every-N-weeks on DTSTART's weekday.
                    val approxDays = Math.floorDiv(rangeStart - startMillis, DAY_MS)
                    val approxWeeks = Math.floorDiv(approxDays, 7L)
                    var k = maxOf(0L, Math.floorDiv(approxWeeks - parsed.interval, parsed.interval.toLong()))
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = startMillis
                        add(Calendar.DAY_OF_MONTH, (k * parsed.interval * 7L).toInt())
                    }
                    while (k < MAX_OCCURRENCES) {
                        if (!accept(cal.timeInMillis, k)) break
                        cal.add(Calendar.DAY_OF_MONTH, parsed.interval * 7)
                        k++
                    }
                } else {
                    // Walk day by day so EVERY BYDAY weekday inside each interval week is
                    // produced (e.g. FREQ=WEEKLY;BYDAY=MO,WE yields both Monday and Wednesday).
                    // Occurrences before the jumped-to day are approximated for COUNT.
                    val approxDays = Math.floorDiv(rangeStart - startMillis, DAY_MS)
                    val startDay = maxOf(0L, approxDays - 14L)
                    var index = Math.round(startDay / 7.0) * parsed.byDay.size
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = startMillis
                        add(Calendar.DAY_OF_MONTH, startDay.toInt())
                    }
                    var d = startDay
                    while (d < MAX_OCCURRENCES) {
                        val t = cal.timeInMillis
                        if (t > upper) break
                        val weekday = cal.get(Calendar.DAY_OF_WEEK)
                        if (parsed.byDay.contains(weekday) &&
                            Math.floorMod(weeksFromAnchorMonday(t, startMillis), parsed.interval.toLong()) == 0L
                        ) {
                            if (countLimit >= 0 && index >= countLimit) break
                            if (parsed.until != null && t > parsed.until) break
                            if (t >= rangeStart) result.add(t)
                            index++
                        }
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        d++
                    }
                }
            }

            "MONTHLY" -> {
                val approxMonths = ((rangeStart - startMillis) / (30.44 * DAY_MS)).toLong()
                var k = maxOf(0L, Math.floorDiv(approxMonths - 2, parsed.interval.toLong()))
                val dayOfMonth = Calendar.getInstance().apply { timeInMillis = startMillis }
                    .get(Calendar.DAY_OF_MONTH)
                // Always expand from the DTSTART anchor: deriving from an already-clamped
                // date would drift (Jan 31 -> Feb 28 -> Mar 28 instead of Mar 31).
                val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
                while (k < MAX_OCCURRENCES) {
                    cal.timeInMillis = startMillis
                    cal.add(Calendar.MONTH, (k * parsed.interval).toInt())
                    cal.clampDayOfMonth(dayOfMonth)
                    if (!accept(cal.timeInMillis, k)) break
                    k++
                }
            }

            "YEARLY" -> {
                val approxYears = ((rangeStart - startMillis) / (365.25 * DAY_MS)).toLong()
                var k = maxOf(0L, Math.floorDiv(approxYears - 1, parsed.interval.toLong()))
                // Anchor-based expansion keeps Feb 29 occurrences aligned in leap years.
                val cal = Calendar.getInstance().apply { timeInMillis = startMillis }
                while (k < MAX_OCCURRENCES) {
                    cal.timeInMillis = startMillis
                    cal.add(Calendar.YEAR, (k * parsed.interval).toInt())
                    if (!accept(cal.timeInMillis, k)) break
                    k++
                }
            }

            else -> return emptyList()
        }

        return result
    }

    fun nextOccurrenceAfter(
        startMillis: Long,
        rrule: String?,
        recurrenceUntilMillis: Long?,
        afterMillis: Long
    ): Long? {
        // Strictly after [afterMillis]; scan up to ~500 years ahead.
        val horizon = afterMillis + 500L * 365L * DAY_MS
        return occurrences(
            startMillis = startMillis,
            rrule = rrule,
            recurrenceUntilMillis = recurrenceUntilMillis,
            rangeStart = afterMillis + 1,
            rangeEnd = horizon
        ).firstOrNull()
    }

    /** Keeps the same day-of-month, clamping to the last day of the (new) month. */
    private fun Calendar.clampDayOfMonth(dayOfMonth: Int) {
        val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, if (dayOfMonth > maxDay) maxDay else dayOfMonth)
    }

    /**
     * Number of whole weeks between the Monday-based week of [millis] and the Monday-based
     * week of the DTSTART anchor [anchorMillis]. Used for WEEKLY interval alignment
     * (RFC default WKST=MO).
     */
    private fun weeksFromAnchorMonday(millis: Long, anchorMillis: Long): Long {
        fun startOfWeekMonday(time: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = time
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            return cal.timeInMillis
        }
        return Math.round((startOfWeekMonday(millis) - startOfWeekMonday(anchorMillis)).toDouble() / WEEK_MS)
    }
}
