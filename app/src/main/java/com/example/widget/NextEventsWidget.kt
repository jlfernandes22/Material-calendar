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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Up next" schedule widget listing the upcoming local events.
 *
 * Merge of the two branches, keeping the best of each:
 * - Recurring events are expanded to their next concrete occurrences (not the
 *   stale DTSTART from months ago); duration is preserved per occurrence.
 * - Rows deep-link to the event details sheet, "+ New" opens the editor
 *   directly via the shared [WidgetActions] contract.
 * - Friendly day labels: Today / Tomorrow / weekday+date.
 * - All-day events render as "All day" instead of a misleading "12:00 AM".
 * - Event location is surfaced on the secondary line when present.
 */
class NextEventsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 80.dp),
            DpSize(250.dp, 120.dp),
            DpSize(300.dp, 160.dp),
            DpSize(400.dp, 260.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val events = loadUpcomingEvents(context)
        val colors = widgetColors(context)
        provideContent {
            GlanceTheme(colors = colors) { FutureEventsContent(context, events) }
        }
    }
}

class NextEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventsWidget()
}

/** An event plus the concrete occurrence time it should be displayed at. */
private data class UpcomingEvent(val event: EventEntity, val occurrenceStart: Long)

private const val HORIZON_DAYS = 45L
private const val DAY_MS = 24L * 3600_000L

private suspend fun loadUpcomingEvents(context: Context): List<UpcomingEvent> {
    val all = WidgetData.loadAllEvents(context)
    val now = System.currentTimeMillis()
    val near = now + HORIZON_DAYS * DAY_MS

    val upcoming = mutableListOf<UpcomingEvent>()
    for (event in all) {
        val rule = DateTimeUtils.effectiveRrule(event)
        if (rule != null) {
            // Recurring events: show their next concrete occurrences.
            val occurrences = runCatching {
                DateTimeUtils.occurrencesBetween(event, now - DAY_MS, near)
            }.getOrDefault(emptyList())
            val shown = occurrences
                .filter { occ -> occ + (event.endMillis - event.startMillis) >= now }
                .take(3)
            if (shown.isNotEmpty()) {
                shown.forEach { occ -> upcoming.add(UpcomingEvent(event, occ)) }
            } else if (event.endMillis >= now && event.startMillis <= near) {
                upcoming.add(UpcomingEvent(event, event.startMillis))
            }
        } else if (event.endMillis >= now && event.startMillis <= near) {
            upcoming.add(UpcomingEvent(event, event.startMillis))
        }
        if (upcoming.size > 60) break
    }

    upcoming.sortedBy { it.occurrenceStart }.take(20)
    return upcoming
}

@Composable
private fun FutureEventsContent(context: Context, events: List<UpcomingEvent>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Header(context)
        Spacer(GlanceModifier.height(6.dp))

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
            // Plain items(): several occurrences of one recurring event can be
            // listed, so a stable itemId per event would collide.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(events) { upcoming ->
                    EventRow(context, upcoming)
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
private fun EventRow(context: Context, upcoming: UpcomingEvent) {
    val event = upcoming.event
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
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
                text = timeText(upcoming),
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
 * "Today · 2:30 PM · ↻", "Tomorrow · All day", "Mon, Sep 8 · 9:00 AM".
 * Uses the concrete occurrence start, so recurring events show the upcoming
 * time rather than the original DTSTART; all-day occurrences show "All day".
 */
private fun timeText(upcoming: UpcomingEvent): String {
    val event = upcoming.event
    val day = WidgetFormat.friendlyDay(upcoming.occurrenceStart)
    return if (event.isAllDay) {
        "$day · All day"
    } else {
        val time = SimpleDateFormat("h:mm a", Locale.getDefault())
            .format(Date(upcoming.occurrenceStart))
        val repeatSuffix = if (DateTimeUtils.effectiveRrule(event) != null) " · ↻" else ""
        "$day · $time$repeatSuffix"
    }
}
