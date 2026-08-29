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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@Composable
fun DayCalendarView(
    selectedDate: Calendar,
    filteredEvents: List<EventEntity>,
    calendars: List<CalendarEntity>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onEventClick: (EventEntity) -> Unit,
    onAddEventForTime: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayStart = DateTimeUtils.getStartOfDay(selectedDate)
    val dayEnd = DateTimeUtils.getEndOfDay(selectedDate)
    val isToday = DateTimeUtils.isToday(selectedDate)

    val dayEvents = filteredEvents.filter {
        DateTimeUtils.eventOccursOnDay(it, dayStart, dayEnd)
    }
    val allDayEvents = dayEvents.filter { it.isAllDay }
    val timedEvents = dayEvents.filter { !it.isAllDay }
    val blocks = remember(timedEvents, dayStart) { TimelineLayout.layout(timedEvents, dayStart) }

    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        // Day Banner
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousDay) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DateTimeUtils.formatFullDate(selectedDate.timeInMillis),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                IconButton(onClick = onNextDay) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Day")
                }
            }
        }

        // All-day lane (each all-day event shown exactly once)
        if (allDayEvents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "ALL-DAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "${allDayEvents.size} ${if (allDayEvents.size == 1) "event" else "events"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                allDayEvents.forEach { ev ->
                    val calColor = calendars.find { it.id == ev.calendarId }?.color ?: 0xFF039BE5.toInt()
                    val color = if (ev.color != 0) Color(ev.color) else Color(calColor)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onEventClick(ev) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = ev.title.ifEmpty { "Untitled Event" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = DateTimeUtils.formatTimeRange(ev.startMillis, ev.endMillis, ev.isAllDay),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
        }

        // Continuous timeline (timed events only)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val gutter = 54.dp
                val availWidth = maxWidth - gutter

                // Hour rows: tap empty area to add an event at that hour
                Column(modifier = Modifier.fillMaxWidth()) {
                    repeat(24) { hour ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HourHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAddEventForTime(dayStart + hour * 3600_000L) }
                                .padding(start = gutter, end = 12.dp)
                        ) {
                            if (hour < 23) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.align(Alignment.TopStart)
                                )
                            }
                        }
                    }
                }

                // Hour labels
                repeat(24) { hour ->
                    Text(
                        text = TimelineLayout.hourLabel(hour),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier
                            .offset(y = (hour * 60 * TimelineLayout.DP_PER_MINUTE).dp)
                            .width(gutter)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Event blocks positioned at their true time
                blocks.forEach { block ->
                    val calColor = calendars.find { it.id == block.event.calendarId }?.color ?: 0xFF039BE5.toInt()
                    val color = if (block.event.color != 0) Color(block.event.color) else Color(calColor)
                    val widthFrac = 1f / block.columns
                    val left = gutter + availWidth * (block.column * widthFrac)
                    val w = (availWidth * widthFrac) - 4.dp
                    val h = ((block.heightMin * TimelineLayout.DP_PER_MINUTE).dp).coerceAtLeast(22.dp)

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f)),
                        modifier = Modifier
                            .offset(x = left, y = (block.topMin * TimelineLayout.DP_PER_MINUTE).dp)
                            .width(w)
                            .height(h)
                            .clickable { onEventClick(block.event) }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = block.event.title.ifEmpty { "Event" },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = DateTimeUtils.formatTimeRange(
                                    block.event.startMillis,
                                    block.event.endMillis,
                                    block.event.isAllDay
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
