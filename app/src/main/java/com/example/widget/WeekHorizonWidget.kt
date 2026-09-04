package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 7-day horizon strip: one tappable column per day showing the weekday initial,
 * the date (highlighted for today) and up to three event dots tinted with the
 * corresponding event colors. Tapping a day opens the app's Day view on it.
 */
class WeekHorizonWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(230.dp, 82.dp),
            DpSize(300.dp, 100.dp),
            DpSize(380.dp, 120.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weekDays = buildWeekDays()
        val events = WidgetData.loadAllEvents(context)
        val dayEvents = weekDays.associate { day ->
            val start = DateTimeUtils.getStartOfDay(day.calendar)
            val end = DateTimeUtils.getEndOfDay(day.calendar)
            day.dateMillis to events
                .filter { DateTimeUtils.eventOccursOnDay(it, start, end) }
                .sortedBy { it.startMillis }
        }
        provideContent {
            GlanceTheme(colors = widgetColors(context)) {
                WeekHorizonContent(context, weekDays, dayEvents)
            }
        }
    }
}

class WeekHorizonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekHorizonWidget()
}

/** The 7 calendar days starting today (horizon), reused grid model from DateTimeUtils. */
private fun buildWeekDays(): List<DateTimeUtils.MonthDay> {
    val days = mutableListOf<DateTimeUtils.MonthDay>()
    val cursor = Calendar.getInstance()
    val today = Calendar.getInstance()
    repeat(7) {
        days.add(
            DateTimeUtils.MonthDay(
                dateMillis = cursor.timeInMillis,
                dayNumber = cursor.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = true,
                isToday = DateTimeUtils.isSameDay(cursor, today),
                calendar = cursor.clone() as Calendar
            )
        )
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    return days
}

@Composable
private fun WeekHorizonContent(
    context: Context,
    weekDays: List<DateTimeUtils.MonthDay>,
    dayEvents: Map<Long, List<EventEntity>>
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        // Center the strip vertically so taller placements don't leave all the
        // empty space hanging below the days.
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekDays.forEachIndexed { index, day ->
            DayColumn(
                context = context,
                day = day,
                events = dayEvents[day.dateMillis].orEmpty()
            )
        }
    }
}

@Composable
private fun RowScope.DayColumn(
    context: Context,
    day: DateTimeUtils.MonthDay,
    events: List<EventEntity>
) {
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .padding(horizontal = 2.dp)
            .clickable(WidgetActions.jumpToDate(context, day.dateMillis)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Weekday initial, locale-aware
        Text(
            text = dayInitial(day.calendar),
            style = TextStyle(
                color = if (day.isToday) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(3.dp))
        // Date pill, highlighted for today
        Box(
            modifier = GlanceModifier
                .background(
                    if (day.isToday) GlanceTheme.colors.primaryContainer
                    else GlanceTheme.colors.surface
                )
                .cornerRadius(12.dp)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayNumber.toString(),
                style = TextStyle(
                    color = if (day.isToday) GlanceTheme.colors.onPrimaryContainer
                    else GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        EventDots(events)
    }
}

/** Up to three dots colored by each event's calendar color, then an overflow dot. */
@Composable
private fun EventDots(events: List<EventEntity>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shown = events.take(3)
        shown.forEach { event ->
            Box(
                modifier = GlanceModifier
                    .padding(horizontal = 1.dp)
                    .size(5.dp)
                    .background(eventColorProvider(event))
                    .cornerRadius(3.dp)
            ) { }
        }
        if (events.size > 3) {
            Box(
                modifier = GlanceModifier
                    .padding(horizontal = 1.dp)
                    .size(5.dp)
                    .background(GlanceTheme.colors.onSurfaceVariant)
                    .cornerRadius(3.dp)
            ) { }
        }
    }
}

private fun dayInitial(calendar: Calendar): String {
    val label = SimpleDateFormat("EEEEE", Locale.getDefault()).format(calendar.time)
    return if (label.isEmpty()) "?" else label.substring(0, 1).uppercase(Locale.getDefault())
}
