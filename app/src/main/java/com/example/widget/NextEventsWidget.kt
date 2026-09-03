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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils

/**
 * "Up next" schedule widget listing the upcoming local events.
 *
 * Improvements over the previous revision:
 * - The "New" chip now opens the event editor directly (create_event deep link).
 * - Friendly day labels: Today / Tomorrow / weekday+date instead of raw dates.
 * - All-day events render as "All day" instead of a misleading "12:00 AM" start.
 * - Event location is surfaced on the secondary line when present.
 * - Rows deep-link to the event details sheet.
 */
class NextEventsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 100.dp),
            DpSize(250.dp, 140.dp),
            DpSize(320.dp, 200.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val events = loadUpcomingEvents(context)
        provideContent {
            GlanceTheme(colors = widgetColors(context)) {
                FutureEventsContent(context, events)
            }
        }
    }
}

class NextEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventsWidget()
}

private suspend fun loadUpcomingEvents(context: Context): List<EventEntity> {
    val now = System.currentTimeMillis()
    val horizon = now + 45L * 24 * 3600_000L
    return WidgetData.loadAllEvents(context)
        .filter {
            (it.endMillis >= now && it.startMillis <= horizon) ||
                DateTimeUtils.nextOccurrenceAfter(it, now) != null
        }
        .sortedBy { it.startMillis }
        .take(20)
}

@Composable
private fun FutureEventsContent(context: Context, events: List<EventEntity>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Header(context)
        Spacer(GlanceModifier.height(8.dp))

        if (events.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No upcoming events",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "Tap + to plan something",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(events, itemId = { it.id }) { event ->
                    EventRow(context, event)
                }
            }
        }
    }
}

@Composable
private fun Header(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(10.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "UP NEXT",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primary)
                .cornerRadius(14.dp)
                .clickable(WidgetActions.createEvent(context))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "+ New",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun EventRow(context: Context, event: EventEntity) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(WidgetActions.openEvent(context, event.id))
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(14.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .background(eventColorProvider(event))
                .cornerRadius(3.dp)
                .width(4.dp)
                .height(38.dp)
        ) { }
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title.ifBlank { "Event" },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = scheduleLine(event),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1
            )
            if (event.location.isNotBlank()) {
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = event.location,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * "Today · 2:30 PM", "Tomorrow · All day", "Mon, Sep 8 · 9:00 AM".
 * All-day events no longer show a misleading midnight start time.
 */
private fun scheduleLine(event: EventEntity): String =
    "${WidgetFormat.friendlyDay(event.startMillis)} · ${WidgetFormat.timeLabel(event)}"
