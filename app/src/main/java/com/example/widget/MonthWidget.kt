package com.example.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 180.dp),
            DpSize(250.dp, 220.dp),
            DpSize(300.dp, 280.dp),
            DpSize(400.dp, 360.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val monthDays = DateTimeUtils.generateMonthDays(Calendar.getInstance())
        val data = withContext(Dispatchers.IO) { loadData(context, monthDays) }
        val colors = widgetColors(context)
        provideContent {
            GlanceTheme(colors = colors) { MonthContent(context, monthDays, data.counts, data.todayEvents) }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

private data class MonthData(
    val counts: Map<Long, Int>,
    val todayEvents: List<EventEntity>
)

private suspend fun loadData(
    context: Context,
    monthDays: List<DateTimeUtils.MonthDay>
): MonthData {
    val db = AppDatabase.getDatabase(context.applicationContext, null)
    val events = runCatching { db.eventDao().getAllEvents().first() }.getOrDefault(emptyList())
    val counts = monthDays.associate { day ->
        val start = DateTimeUtils.getStartOfDay(day.calendar)
        val end = DateTimeUtils.getEndOfDay(day.calendar)
        day.dateMillis to events.count { DateTimeUtils.eventOccursOnDay(it, start, end) }
    }
    val nowCal = Calendar.getInstance()
    val todayStart = DateTimeUtils.getStartOfDay(nowCal)
    val todayEnd = DateTimeUtils.getEndOfDay(nowCal)
    val todayEvents = events
        .filter { DateTimeUtils.eventOccursOnDay(it, todayStart, todayEnd) }
        .sortedBy { it.startMillis }
    return MonthData(counts, todayEvents)
}

private val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")

private fun openApp(context: Context) =
    actionStartActivity(ComponentName(context, MainActivity::class.java))

@Composable
private fun MonthContent(
    context: Context,
    monthDays: List<DateTimeUtils.MonthDay>,
    counts: Map<Long, Int>,
    todayEvents: List<EventEntity>
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        .format(Calendar.getInstance().time),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Local Calendar",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(10.dp)
                    .clickable(openApp(context))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
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

        // Weekday header
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            dayNames.forEach { day ->
                Text(
                    text = day,
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

        // Week rows
        monthDays.chunked(7).forEach { week ->
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                week.forEach { day ->
                    Column(
                        modifier = GlanceModifier.defaultWeight().padding(1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isToday = day.isToday
                        Box(
                            modifier = GlanceModifier
                                .background(if (isToday) GlanceTheme.colors.primary else GlanceTheme.colors.surface)
                                .cornerRadius(16.dp)
                                .padding(horizontal = 3.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.dayNumber.toString(),
                                style = TextStyle(
                                    color = if (isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                        // Event dot
                        val count = counts[day.dateMillis] ?: 0
                        if (count > 0) {
                            Box(
                                modifier = GlanceModifier
                                    .padding(top = 3.dp)
                                    .size(5.dp)
                                    .background(eventDotColor(count))
                                    .cornerRadius(3.dp)
                            ) { }
                        }
                    }
                }
            }
        }

        // Today's events footer
        if (todayEvents.isNotEmpty()) {
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
                    text = "${todayEvents.size} ${if (todayEvents.size == 1) "event" else "events"}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp)
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            todayEvents.take(2).forEach { ev ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = GlanceModifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(
                                if (ev.color != 0) ColorProvider(ComposeColor(ev.color))
                                else GlanceTheme.colors.primary
                            )
                            .cornerRadius(2.dp)
                    ) { }
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "${timeOf(ev)}  ${ev.title}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2
                    )
                }
            }
            if (todayEvents.size > 2) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = "+${todayEvents.size - 2} more",
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

private fun timeOf(event: EventEntity): String {
    if (event.isAllDay) return "All day"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.startMillis))
}

@Composable
private fun eventDotColor(count: Int) = when {
    count >= 3 -> ColorProvider(ComposeColor(0xFFD50000))
    count == 2 -> ColorProvider(ComposeColor(0xFFF6BF26))
    else -> GlanceTheme.colors.primary
}
