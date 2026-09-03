package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import java.util.Calendar

/**
 * Month-grid widget.
 *
 * Improvements over the previous revision:
 * - Every day cell is tappable and deep-links the app into that day's schedule.
 * - Weekday initials follow the device locale instead of hardcoded English letters.
 * - The "today" footer only appears when the widget is large enough, so the grid
 *   never overflows on small placements.
 * - Event dots use Material You theme colors instead of a hardcoded palette.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 180.dp),  // small: compact grid only
            DpSize(250.dp, 230.dp),  // medium: grid + today footer
            DpSize(340.dp, 310.dp)   // large: grid + expanded today footer
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val monthDays = DateTimeUtils.generateMonthDays(Calendar.getInstance())
        val events = WidgetData.loadAllEvents(context)

        val counts = monthDays.associate { day ->
            val start = DateTimeUtils.getStartOfDay(day.calendar)
            val end = DateTimeUtils.getEndOfDay(day.calendar)
            day.dateMillis to events.count { DateTimeUtils.eventOccursOnDay(it, start, end) }
        }
        val nowCal = Calendar.getInstance()
        val todayEvents = events
            .filter {
                DateTimeUtils.eventOccursOnDay(
                    it,
                    DateTimeUtils.getStartOfDay(nowCal),
                    DateTimeUtils.getEndOfDay(nowCal)
                )
            }
            .sortedBy { it.startMillis }

        provideContent {
            GlanceTheme(colors = widgetColors(context)) {
                MonthContent(context, monthDays, counts, todayEvents)
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

@Composable
private fun MonthContent(
    context: Context,
    monthDays: List<DateTimeUtils.MonthDay>,
    counts: Map<Long, Int>,
    todayEvents: List<EventEntity>
) {
    val size = LocalSize.current
    val showFooter = size.width >= 250.dp
    val maxFooterEvents = if (size.width >= 340.dp) 3 else 2

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(12.dp)
    ) {
        // Header: month title + Today shortcut
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(WidgetActions.openApp(context))
            ) {
                Text(
                    text = WidgetFormat.monthYear(Calendar.getInstance()),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Text(
                    text = "Local Calendar",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    maxLines = 1
                )
            }
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(12.dp)
                    .clickable(WidgetActions.jumpToDate(context, System.currentTimeMillis()))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // Weekday header, locale-aware
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetFormat.dayInitials().forEach { initial ->
                Text(
                    text = initial,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(4.dp))

        // 6x7 day grid; each cell deep-links into that day's schedule
        monthDays.chunked(7).forEach { week ->
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                week.forEach { day ->
                    DayCell(context, day, counts[day.dateMillis] ?: 0)
                }
            }
        }

        // Today's schedule footer (medium & large layouts only)
        if (showFooter) {
            TodayFooter(context, todayEvents, maxFooterEvents)
        }
    }
}

@Composable
private fun RowScope.DayCell(context: Context, day: DateTimeUtils.MonthDay, eventCount: Int) {
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .padding(1.dp)
            .clickable(WidgetActions.jumpToDate(context, day.dateMillis)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .background(
                    if (day.isToday) GlanceTheme.colors.primary else GlanceTheme.colors.surface
                )
                .cornerRadius(14.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayNumber.toString(),
                style = TextStyle(
                    color = when {
                        day.isToday -> GlanceTheme.colors.onPrimary
                        day.isCurrentMonth -> GlanceTheme.colors.onSurface
                        else -> GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = 12.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
                )
            )
        }
        // Event dot (hidden on the today pill, where the fill already provides contrast)
        if (eventCount > 0 && !day.isToday) {
            Box(
                modifier = GlanceModifier
                    .padding(top = 3.dp)
                    .size(5.dp)
                    .background(eventDotColor(eventCount))
                    .cornerRadius(3.dp)
            ) { }
        } else if (eventCount > 0) {
            Spacer(GlanceModifier.height(8.dp))
        }
    }
}

@Composable
private fun TodayFooter(context: Context, todayEvents: List<EventEntity>, maxEvents: Int) {
    Spacer(GlanceModifier.height(8.dp))
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.outline)
    ) { }
    Spacer(GlanceModifier.height(6.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TODAY",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(GlanceModifier.width(6.dp))
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = if (todayEvents.isEmpty()) "Nothing scheduled"
            else "${todayEvents.size} ${if (todayEvents.size == 1) "event" else "events"}",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            maxLines = 1
        )
    }

    if (todayEvents.isNotEmpty()) {
        Spacer(GlanceModifier.height(4.dp))
        todayEvents.take(maxEvents).forEach { event ->
            EventLine(context, event)
        }
        if (todayEvents.size > maxEvents) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "+${todayEvents.size - maxEvents} more",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EventLine(context: Context, event: EventEntity) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clickable(WidgetActions.openEvent(context, event.id)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(16.dp)
                .background(eventColorProvider(event))
                .cornerRadius(2.dp)
        ) { }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = "${WidgetFormat.timeLabel(event)}  ${event.title.ifBlank { "Event" }}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 2
        )
    }
}

/** Event color falling back to the dynamic theme primary. */
@Composable
internal fun eventColorProvider(event: EventEntity): ColorProvider =
    if (event.color != 0) ColorProvider(ComposeColor(event.color)) else GlanceTheme.colors.primary

/** Busy-day dots tinted with theme roles: primary -> secondary -> tertiary. */
@Composable
private fun eventDotColor(count: Int): ColorProvider = when {
    count >= 3 -> GlanceTheme.colors.tertiary
    count == 2 -> GlanceTheme.colors.secondary
    else -> GlanceTheme.colors.primary
}
