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
 * Weekly time-allocation widget: how much scheduled time each category takes up
 * during the current week (Sun-Sat, matching the app's week grid), rendered as
 * Material 3 proportional bars. Tapping the widget opens the schedule view.
 *
 * All-day events are excluded: they block a date, not a slice of time.
 */
class TimeAllocationWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(240.dp, 140.dp),  // compact: top 3 categories
            DpSize(320.dp, 180.dp)   // expanded: top 5 categories
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weekStart = DateTimeUtils.getStartOfWeek(Calendar.getInstance())
        val events = WidgetData.loadAllEvents(context)
        val categoryMillis = weekCategoryMillis(events, weekStart)
        provideContent {
            GlanceTheme(colors = widgetColors(context)) {
                TimeAllocationContent(context, categoryMillis)
            }
        }
    }
}

class TimeAllocationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeAllocationWidget()
}

/**
 * Sums scheduled time per category for the week starting at [weekStart] (in millis).
 * Recurring events are expanded day by day; all-day events are skipped.
 */
private fun weekCategoryMillis(
    events: List<EventEntity>,
    weekStart: Calendar
): Map<String, Long> {
    val weekEnd = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 7) }
    val weekStartMillis = weekStart.timeInMillis
    val weekEndMillis = weekEnd.timeInMillis - 1

    val totals = linkedMapOf<String, Long>()
    fun add(category: String, millis: Long) {
        if (millis > 0) totals[category] = (totals[category] ?: 0L) + millis
    }

    for (event in events) {
        if (event.isAllDay) continue
        val rule = DateTimeUtils.effectiveRrule(event)
        if (rule == null) {
            val overlap = minOf(event.endMillis, weekEndMillis) -
                maxOf(event.startMillis, weekStartMillis)
            add(event.category, overlap)
        } else {
            val duration = (event.endMillis - event.startMillis).coerceAtLeast(0L)
            if (duration <= 0) continue
            val cursor = weekStart.clone() as Calendar
            repeat(7) {
                val dayStart = DateTimeUtils.getStartOfDay(cursor)
                val dayEnd = DateTimeUtils.getEndOfDay(cursor)
                if (DateTimeUtils.eventOccursOnDay(event, dayStart, dayEnd)) {
                    add(event.category, duration)
                }
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }
    return totals
}

@Composable
private fun TimeAllocationContent(
    context: Context,
    categoryMillis: Map<String, Long>
) {
    val size = LocalSize.current
    val maxCategories = if (size.width >= 320.dp) 5 else 3

    val top = categoryMillis.entries
        .sortedByDescending { it.value }
        .take(maxCategories)
    val maxMillis = top.firstOrNull()?.value ?: 0L
    val totalMillis = categoryMillis.values.sum()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .clickable(WidgetActions.openView(context, "agenda"))
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "THIS WEEK",
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.width(6.dp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (totalMillis > 0) "${WidgetFormat.duration(totalMillis)} planned"
                else "Nothing planned",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(10.dp))

        if (top.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add timed events to see your week",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }
        } else {
            top.forEach { (category, millis) ->
                CategoryBar(category, millis, maxMillis)
                Spacer(GlanceModifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryBar(category: String, millis: Long, maxMillis: Long) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(categoryColor(category))
                    .cornerRadius(4.dp)
            ) { }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = category,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = WidgetFormat.duration(millis),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        // Proportional bar: 10 equal segments, filled relative to the busiest category.
        val filled = if (maxMillis <= 0L) 0
        else ((millis * 10 + maxMillis - 1) / maxMillis).toInt().coerceIn(1, 10)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(5.dp)
        ) {
            repeat(10) { segment ->
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(5.dp)
                        .padding(horizontal = 1.dp)
                        .background(
                            if (segment < filled) categoryColor(category)
                            else GlanceTheme.colors.surfaceVariant
                        )
                        .cornerRadius(2.dp)
                ) { }
            }
        }
    }
}

/** Stable per-category color from the app's shared data palette. */
@Composable
private fun categoryColor(category: String): ColorProvider {
    val palette = com.example.ui.theme.CategoryPalette
    val index = (category.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) %
        palette.size
    return ColorProvider(ComposeColor(palette[index]))
}
