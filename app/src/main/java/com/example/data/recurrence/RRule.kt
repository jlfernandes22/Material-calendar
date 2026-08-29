package com.example.data.recurrence

import java.util.Calendar

/**
 * Minimal RFC 5545 RRULE reader/expander supporting FREQ (DAILY/WEEKLY/MONTHLY/YEARLY),
 * INTERVAL, BYDAY (for weekly), UNTIL and COUNT. Occurrences are computed in the device's
 * local time zone, matching the way events are stored (as epoch millis) and displayed.
 */
object RRule {

    data class Parsed(
        val freq: String,             // DAILY | WEEKLY | MONTHLY | YEARLY
        val interval: Int,            // default 1
        val byDay: List<Int>,         // Java Calendar.DAY_OF_WEEK constants (1=Sunday .. 7=Saturday)
        val until: Long?,             // exclusive upper bound (millis)
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
     * Returns occurrence start times in [rangeStart, rangeEnd]. Base start is included only
     * if it actually falls inside the range; later occurrences are expanded from [startMillis].
     */
    fun occurrences(
        startMillis: Long,
        rrule: String?,
        recurrenceUntilMillis: Long?,
        rangeStart: Long,
        rangeEnd: Long
    ): List<Long> {
        val parsed = parse(rrule) ?: return emptyList()
        val upper = minOf(recurrenceUntilMillis?.let { it } ?: Long.MAX_VALUE, rangeEnd)
        if (upper < rangeStart) return emptyList()

        val result = mutableListOf<Long>()
        val count = parsed.count
        var seen = 0

        var k = 0L
        step@ while (k < 100_000) {
            val candidate = candidateAt(parsed, startMillis, k)
            if (candidate > upper) break
            if (candidate >= rangeStart) {
                if (parsed.until != null && candidate > parsed.until!!) break
                if (count >= 0 && seen >= count) break
                result.add(candidate)
                seen++
                if (count >= 0 && seen >= count) break
            }
            k++

            // Guard against pathological schedules (e.g. yearly interval on Feb 29).
            if (candidate - startMillis > 500L * 365L * 24L * 3600_000L) break
        }
        return result
    }

    fun nextOccurrenceAfter(
        startMillis: Long,
        rrule: String?,
        recurrenceUntilMillis: Long?,
        afterMillis: Long
    ): Long? {
        val parsed = parse(rrule) ?: return null
        val upper = recurrenceUntilMillis ?: (afterMillis + 500L * 365L * 24L * 3600_000L)
        for (k in 0L until 100_000L) {
            val candidate = candidateAt(parsed, startMillis, k)
            if (candidate > upper) return null
            if (candidate > afterMillis) return candidate
            if (candidate - startMillis > 500L * 365L * 24L * 3600_000L) return null
        }
        return null
    }

    private fun candidateAt(parsed: Parsed, startMillis: Long, k: Long): Long {
        val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
        val interval = parsed.interval
        return when (parsed.freq) {
            "DAILY" -> startMillis + (k * interval) * 86_400_000L
            "WEEKLY" -> weeklyCandidate(parsed, startCal, k * interval)
            "MONTHLY" -> {
                val cal = startCal.clone() as Calendar
                val shift = (k * interval).toInt()
                val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                cal.add(Calendar.MONTH, shift)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                if (currentDay > maxDay) cal.set(Calendar.DAY_OF_MONTH, maxDay) else cal.set(Calendar.DAY_OF_MONTH, currentDay)
                cal.timeInMillis
            }
            "YEARLY" -> {
                val cal = startCal.clone() as Calendar
                cal.add(Calendar.YEAR, (k * interval).toInt())
                cal.timeInMillis
            }
            else -> startMillis + (k * interval) * 86_400_000L
        }
    }

    private fun weeklyCandidate(parsed: Parsed, startCal: Calendar, intervalWeeks: Long): Long {
        // Base day is always DTSTART's weekday.
        val base = (startCal.clone() as Calendar)
        base.add(Calendar.DAY_OF_MONTH, (intervalWeeks * 7).toInt())

        if (parsed.byDay.isEmpty()) return base.timeInMillis

        val startWd = startCal.get(Calendar.DAY_OF_WEEK)
        // Accumulate by the FIRST matching weekday so occurrences stay ordered & deduped.
        val chosen = parsed.byDay.minBy { ((it - startWd) + 7) % 7 }
        val diff = ((chosen - startWd) + 7) % 7
        base.add(Calendar.DAY_OF_MONTH, diff)
        return base.timeInMillis
    }
}
