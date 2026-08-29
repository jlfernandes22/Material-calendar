package com.example.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import com.example.ui.util.TimelineLayout
import java.util.Calendar

private val HourHeight = (60 * TimelineLayout.DP_PER_MINUTE).dp
private val TimelineHeight = (TimelineLayout.MINUTES_PER_DAY * TimelineLayout.DP_PER_MINUTE).dp
private val Gutter = 50.dp

@Composable
fun WeekCalendarView(
    selectedDate: Calendar,
    filteredEvents: List<EventEntity>,
    calendars: List<CalendarEntity>,
    onSelectDate: (Calendar) -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onAddEventForTime: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = remember(selectedDate.get(Calendar.WEEK_OF_YEAR), selectedDate.get(Calendar.YEAR)) {
        DateTimeUtils.generateWeekDays(selectedDate)
    }
    val dayData = remember(weekDays, filteredEvents) {
        weekDays.map { day ->
            val start = DateTimeUtils.getStartOfDay(day.calendar)
            val end = DateTimeUtils.getEndOfDay(day.calendar)
            val timed = filteredEvents.filter { !it.isAllDay && DateTimeUtils.eventOccursOnDay(it, start, end) }
            val allDay = filteredEvents.filter { it.isAllDay && DateTimeUtils.eventOccursOnDay(it, start, end) }
            Triple(day, start, allDay to TimelineLayout.layout(timed, start))
        }
    }

    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Week Days Header Row
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Spacer(modifier = Modifier.width(Gutter))
                weekDays.forEach { day ->
                    val isSelected = DateTimeUtils.isSameDay(day.calendar, selectedDate)
                    val isToday = day.isToday
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isToday && isSelected -> MaterialTheme.colorScheme.primary
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onSelectDate(day.calendar) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = DateTimeUtils.formatDayOfWeek(day.calendar),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isToday && isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = day.dayNumber.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isToday && isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // All-day lane
        val hasAllDay = dayData.any { it.third.first.isNotEmpty() }
        if (hasAllDay) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "All-day",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.width(Gutter).padding(start = 4.dp, top = 4.dp)
                    )
                    Row(modifier = Modifier.weight(1f)) {
                        dayData.forEach { (_, _, pair) ->
                            val allDay = pair.first
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 1.dp)
                            ) {
                                allDay.take(2).forEach { ev ->
                                    val calColor = calendars.find { it.id == ev.calendarId }?.color ?: 0xFF039BE5.toInt()
                                    val color = if (ev.color != 0) Color(ev.color) else Color(calColor)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = color.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 1.dp)
                                            .clickable { onEventClick(ev) }
                                    ) {
                                        Text(
                                            text = ev.title.ifEmpty { "Event" },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Continuous timeline
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columnWidth = (maxWidth - Gutter) / 7f
                Box(modifier = Modifier.fillMaxWidth().height(TimelineHeight)) {
                    // Hour labels (gutter)
                    repeat(24) { hour ->
                        Text(
                            text = TimelineLayout.hourLabel(hour),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier
                                .offset(y = (hour * 60 * TimelineLayout.DP_PER_MINUTE).dp)
                                .padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    dayData.forEachIndexed { d, (_, dayStart, pair) ->
                        val blocks = pair.second
                        val xBase = Gutter + columnWidth * d.toFloat()
                        // Hour rows (tap to add for this day)
                        Column(
                            modifier = Modifier
                                .offset(x = xBase)
                                .width(columnWidth)
                                .height(TimelineHeight)
                        ) {
                            repeat(24) { hour ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(HourHeight)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onAddEventForTime(dayStart + hour * 3600_000L) }
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    )
                                }
                            }
                        }

                        // Event blocks positioned at true time within the day column
                        blocks.forEach { block ->
                            val calColor = calendars.find { it.id == block.event.calendarId }?.color ?: 0xFF039BE5.toInt()
                            val color = if (block.event.color != 0) Color(block.event.color) else Color(calColor)
                            val colW = columnWidth / block.columns.toFloat()
                            val w = colW - 2.dp
                            val left = xBase + colW * block.column.toFloat() + 1.dp
                            val h = ((block.heightMin * TimelineLayout.DP_PER_MINUTE).dp).coerceAtLeast(20.dp)

                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = color.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .offset(x = left, y = (block.topMin * TimelineLayout.DP_PER_MINUTE).dp)
                                    .width(w)
                                    .height(h)
                                    .clickable { onEventClick(block.event) }
                            ) {
                                Text(
                                    text = block.event.title.ifEmpty { "Event" },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
