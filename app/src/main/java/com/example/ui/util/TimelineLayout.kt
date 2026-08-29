package com.example.ui.util

import com.example.data.model.EventEntity

/**
 * Lays timed events out on a day timeline (minutes from midnight), clustering
 * overlapping events into side-by-side columns so back-to-back events stay
 * contiguous and multi-hour events render as a single block.
 */
object TimelineLayout {
    const val MINUTES_PER_DAY = 24 * 60
    const val DP_PER_MINUTE = 2.0f

    fun hourLabel(hour: Int): String = when (hour) {
        0 -> "12 AM"
        in 1..11 -> "$hour AM"
        12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    data class Block(
        val event: EventEntity,
        val topMin: Int,      // minutes from midnight
        val heightMin: Int,   // duration in minutes
        val column: Int,      // column index within the overlap cluster
        val columns: Int      // total columns in the cluster
    )

    fun layout(events: List<EventEntity>, dayStartMillis: Long): List<Block> {
        val items = events.map { e ->
            val startMin = ((e.startMillis - dayStartMillis) / 60_000L).coerceIn(0L, 1440L).toInt()
            var endMin = ((e.endMillis - dayStartMillis) / 60_000L).coerceIn(0L, 1440L).toInt()
            if (endMin <= startMin) endMin = (startMin + 30).coerceAtMost(1440)
            Triple(e, startMin, endMin)
        }.sortedWith(compareBy({ it.second }, { -it.third }))

        val result = mutableListOf<Block>()

        // Cluster overlapping events (a new cluster starts when an event begins
        // after the current cluster's latest end time → adjacent events). 
        val clusters = mutableListOf<MutableList<Triple<EventEntity, Int, Int>>>()
        var clusterEnd = -1
        for (item in items) {
            if (clusters.isNotEmpty() && item.second < clusterEnd) {
                clusters.last().add(item)
                clusterEnd = maxOf(clusterEnd, item.third)
            } else {
                clusters.add(mutableListOf(item))
                clusterEnd = item.third
            }
        }

        for (cluster in clusters) {
            val colEnds = mutableListOf<Int>()
            val assigned = mutableListOf<Pair<Triple<EventEntity, Int, Int>, Int>>()
            for (item in cluster) {
                var col = -1
                for (i in colEnds.indices) {
                    if (colEnds[i] <= item.second) {
                        col = i
                        break
                    }
                }
                if (col == -1) {
                    colEnds.add(item.third)
                    col = colEnds.size - 1
                } else {
                    colEnds[col] = maxOf(colEnds[col], item.third)
                }
                assigned.add(item to col)
            }
            val totalColumns = colEnds.size
            assigned.forEach { (item, col) ->
                result.add(
                    Block(
                        event = item.first,
                        topMin = item.second,
                        heightMin = item.third - item.second,
                        column = col,
                        columns = totalColumns
                    )
                )
            }
        }

        return result
    }
}
