package com.example.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
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
import java.util.Date
import java.util.Locale

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

private suspend fun loadUpcomingEvents(context: Context): List<EventEntity> = withContext(Dispatchers.IO) {
    val db = AppDatabase.getDatabase(context.applicationContext, null)
    val all = runCatching { db.eventDao().getAllEvents().first() }.getOrDefault(emptyList())
    val now = System.currentTimeMillis()
    val near = now + 45L * 24 * 3600_000L
    all.filter {
        (it.endMillis >= now && it.startMillis <= near) || DateTimeUtils.nextOccurrenceAfter(it, now) != null
    }
        .sortedBy { it.startMillis }
        .take(20)
}

private val EventIdKey = ActionParameters.Key<Long>("event_id")

private fun openApp(context: Context) =
    actionStartActivity(ComponentName(context, MainActivity::class.java))

@OptIn(ExperimentalGlanceApi::class)
private fun openEvent(context: Context, event: EventEntity): Action =
    actionStartActivity(
        ComponentName(context, MainActivity::class.java),
        actionParametersOf(EventIdKey to event.id)
    )

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
        Spacer(GlanceModifier.height(6.dp))

        if (events.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No upcoming events",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(events) { event ->
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
                .clickable(openApp(context))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "New",
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
    val eventColor = if (event.color != 0) ColorProvider(ComposeColor(event.color)) else GlanceTheme.colors.primary
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .clickable(openEvent(context, event))
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(14.dp)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .background(eventColor)
                .cornerRadius(3.dp)
                .width(4.dp)
                .height(34.dp)
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
                maxLines = 2
            )
            if (event.location.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
            }
            Text(
                text = timeText(event),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1
            )
        }
    }
}

private fun timeText(event: EventEntity): String {
    val day = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(event.startMillis))
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.startMillis))
    val suffix = if (event.isAllDay) "· All day" else ""
    return "$day · $time$suffix"
}
