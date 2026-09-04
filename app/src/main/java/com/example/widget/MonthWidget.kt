package com.example.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.example.MainActivity
import com.example.data.model.EventEntity
import com.example.ui.util.DateTimeUtils
import java.util.Calendar

/**
 * Month-grid widget with month navigation.
 *
 * Improvements over the previous revision:
 * - "‹ / ›" buttons browse months directly on the widget; "Today" returns to
 *   the current month (and jumps into the app's Day view when already there).
 * - The grid renders only the weeks the month actually needs (4-6 rows) instead
 *   of a fixed 6-row block, so the last row is no longer clipped off.
 * - Rows adapt to the placed height: on very short placements the footer is
 *   dropped first, then trailing weeks (reachable via the "›" button).
 * - The today-footer is only shown when the whole grid fits and the widget is
 *   showing the current month.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 170.dp),  // small: compact header + grid
            DpSize(250.dp, 220.dp),  // medium: + today footer
            DpSize(340.dp, 300.dp)   // large: + expanded today footer
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val offset = monthOffset(context)
        val displayCal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
        val monthDays = DateTimeUtils.generateMonthDays(displayCal)
        val events = WidgetData.loadAllEvents(context)

        // Exact number of grid weeks this month needs (4..6) instead of a fixed 6.
        val firstMonthIndex = monthDays.indexOfFirst { it.isCurrentMonth }.let { if (it < 0) 0 else it }
        val daysInMonth = displayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weeksNeeded = ((firstMonthIndex + daysInMonth + 6) / 7).coerceIn(1, 6)

        val counts = monthDays.associate { day ->
            val start = DateTimeUtils.getStartOfDay(day.calendar)
            val end = DateTimeUtils.getEndOfDay(day.calendar)
            day.dateMillis to events.count { DateTimeUtils.eventOccursOnDay(it, start, end) }
        }

        val nowCal = Calendar.getInstance()
        val todayEvents = if (offset == 0) {
            events
                .filter {
                    DateTimeUtils.eventOccursOnDay(
                        it,
                        DateTimeUtils.getStartOfDay(nowCal),
                        DateTimeUtils.getEndOfDay(nowCal)
                    )
                }
                .sortedBy { it.startMillis }
        } else {
            emptyList()
        }

        provideContent {
            GlanceTheme(colors = widgetColors(context)) {
                MonthContent(
                    context = context,
                    displayCal = displayCal,
                    monthDays = monthDays,
                    counts = counts,
                    weeksNeeded = weeksNeeded,
                    viewingCurrentMonth = offset == 0,
                    todayEvents = todayEvents
                )
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

// ---------------------------------------------------------------------------
// Month navigation state: a simple month offset shared by all month widgets.
// ActionCallbacks shift it and re-render; provideGlance reads it.
// ---------------------------------------------------------------------------

private const val MONTH_NAV_PREFS = "widget_month_nav"
private const val KEY_MONTH_OFFSET = "month_offset"

private fun monthOffset(context: Context): Int =
    context.getSharedPreferences(MONTH_NAV_PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_MONTH_OFFSET, 0)

private suspend fun shiftMonthOffset(context: Context, delta: Int) {
    val prefs = context.getSharedPreferences(MONTH_NAV_PREFS, Context.MODE_PRIVATE)
    val next = (prefs.getInt(KEY_MONTH_OFFSET, 0) + delta).coerceIn(-120, 120)
    prefs.edit().putInt(KEY_MONTH_OFFSET, next).apply()
    MonthWidget().updateAll(context)
}

/** Back one month. */
class PrevMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        shiftMonthOffset(context, -1)
    }
}

/** Forward one month. */
class NextMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        shiftMonthOffset(context, 1)
    }
}

/** Back to the current month, then open the app on today's schedule. */
class TodayMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        context.getSharedPreferences(MONTH_NAV_PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MONTH_OFFSET, 0).apply()
        MonthWidget().updateAll(context)
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WidgetIntents.EXTRA_JUMP_DATE, System.currentTimeMillis())
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

@Composable
private fun MonthContent(
    context: Context,
    displayCal: Calendar,
    monthDays: List<DateTimeUtils.MonthDay>,
    counts: Map<Long, Int>,
    weeksNeeded: Int,
    viewingCurrentMonth: Boolean,
    todayEvents: List<EventEntity>
) {
    val size = LocalSize.current
    val compact = size.width < 250.dp
    val wantFooter = viewingCurrentMonth && !compact

    // Height budget, priority order: (1) the full month grid, (2) the today
    // footer, (3) trailing weeks (reachable via the "›" button). Never render
    // more than fits, so nothing is ever clipped mid-row.
    val headerHeight = if (compact) 62.dp else 70.dp
    val rowHeight = 28.dp
    val availNoFooter = size.height - headerHeight
    val rowsFit = (availNoFooter / rowHeight).toInt().coerceIn(1, 6)
    val rowsShown = minOf(weeksNeeded, rowsFit)

    // Footer only when the whole grid already fits and space remains for it.
    val leftover = availNoFooter - rowHeight * rowsShown
    val showFooter = wantFooter && rowsShown == weeksNeeded && leftover >= 44.dp
    val maxFooterEvents = if (size.width >= 340.dp) 3 else 2
    val footerMaxEvents = (((leftover - 28.dp) / 17.dp).toInt()).coerceIn(0, maxFooterEvents)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        MonthHeader(context, displayCal, compact)

        Spacer(GlanceModifier.height(if (compact) 4.dp else 6.dp))

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
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.height(3.dp))

        // Day grid — only the weeks the month needs, only what fits the height.
        monthDays.take(rowsShown * 7).chunked(7).forEach { week ->
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                week.forEach { day ->
                    DayCell(context, day, counts[day.dateMillis] ?: 0)
                }
            }
        }

        if (rowsShown < weeksNeeded && leftover >= 22.dp) {
            // Trailing weeks don't fit this placement — offer a way to reach them.
            Spacer(GlanceModifier.height(2.dp))
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.secondaryContainer)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .clickable(actionRunCallback<NextMonthAction>())
            ) {
                Text(
                    text = "+ more ›",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontSize = 9.sp
                    ),
                    maxLines = 1
                )
            }
        }

        if (showFooter) {
            TodayFooter(context, todayEvents, footerMaxEvents)
        }
    }
}

@Composable
private fun MonthHeader(context: Context, displayCal: Calendar, compact: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ‹ previous month
        Text(
            text = "‹",
            modifier = GlanceModifier
                .clickable(actionRunCallback<PrevMonthAction>())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Column(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = WidgetFormat.monthYear(displayCal),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            if (!compact) {
                Text(
                    text = "Local Calendar",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
        // › next month
        Text(
            text = "›",
            modifier = GlanceModifier
                .clickable(actionRunCallback<NextMonthAction>())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(12.dp)
                .clickable(
                    if (displayCal.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH) &&
                        displayCal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
                    ) {
                        // Already on the current month: jump into the app.
                        WidgetActions.jumpToDate(context, System.currentTimeMillis())
                    } else {
                        actionRunCallback<TodayMonthAction>()
                    }
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Today",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
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
                .cornerRadius(12.dp)
                .padding(horizontal = 5.dp, vertical = 1.dp),
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
                ),
                maxLines = 1
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
        } else {
            Spacer(GlanceModifier.height(8.dp))
        }
    }
}

@Composable
private fun TodayFooter(context: Context, todayEvents: List<EventEntity>, maxEvents: Int) {
    Spacer(GlanceModifier.height(6.dp))
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.outline)
    ) { }
    Spacer(GlanceModifier.height(5.dp))

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
            ),
            maxLines = 1
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

    if (todayEvents.isNotEmpty() && maxEvents > 0) {
        Spacer(GlanceModifier.height(3.dp))
        todayEvents.take(maxEvents).forEach { event ->
            EventLine(context, event)
        }
        if (todayEvents.size > maxEvents) {
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
                .height(14.dp)
                .background(eventColorProvider(event))
                .cornerRadius(2.dp)
        ) { }
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = "${WidgetFormat.timeLabel(event)}  ${event.title.ifBlank { "Event" }}",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
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
