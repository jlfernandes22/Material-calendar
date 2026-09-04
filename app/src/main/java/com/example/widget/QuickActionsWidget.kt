package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Home-screen shortcut pad: one-tap templates for common events plus navigation
 * chips, so the most frequent actions never require opening the app first.
 *
 * - "New" opens the event editor on the selected day.
 * - "Today" jumps to the day view for today.
 * - "Agenda" opens the schedule list.
 * - Template chips pre-fill the editor (title, category and duration).
 *
 * All data lives locally; the widget merely launches intents into the app.
 */
class QuickActionsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(140.dp, 56.dp),   // compact: navigation chips only
            DpSize(230.dp, 110.dp),  // medium: navigation + 2 templates
            DpSize(320.dp, 150.dp)   // large: navigation + 4 templates
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = widgetColors(context)) {
                QuickActionsContent(context)
            }
        }
    }
}

class QuickActionsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickActionsWidget()
}

private data class QuickTemplate(
    val label: String,
    val title: String,
    val category: String,
    val durationMinutes: Int
)

private val quickTemplates = listOf(
    QuickTemplate("+ Meeting 30m", "Meeting", "Meeting", 30),
    QuickTemplate("+ Focus 1h", "Focus session", "Work", 60),
    QuickTemplate("+ Workout 1h", "Workout", "Health", 60),
    QuickTemplate("+ Study 1h", "Study block", "Study", 60)
)

@Composable
private fun QuickActionsContent(context: Context) {
    val size = LocalSize.current
    val showAgenda = size.width >= 230.dp
    val templateRows: List<List<QuickTemplate>> = when {
        size.width >= 320.dp -> quickTemplates.chunked(2)
        size.width >= 230.dp -> listOf(quickTemplates.take(2))
        else -> emptyList()
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp)
            .padding(10.dp),
        // No template rows: center the nav chips vertically. Otherwise keep the
        // nav row on top and distribute the template rows in the space below
        // (Glance has no Arrangement - flexible Spacers do the spreading).
        verticalAlignment = if (templateRows.isEmpty()) Alignment.CenterVertically
        else Alignment.Top
    ) {
        NavigationRow(context, showAgenda)
        templateRows.forEach { rowTemplates ->
            Spacer(GlanceModifier.defaultWeight())
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                rowTemplates.forEachIndexed { index, template ->
                    if (index > 0) Spacer(GlanceModifier.width(8.dp))
                    TemplateChip(context, template)
                }
            }
        }
        if (templateRows.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun NavigationRow(context: Context, showAgenda: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionChip(
            label = "+ New",
            container = GlanceTheme.colors.primaryContainer,
            content = GlanceTheme.colors.onPrimaryContainer,
            action = WidgetActions.createEvent(context)
        )
        Spacer(GlanceModifier.width(8.dp))
        ActionChip(
            label = "Today",
            container = GlanceTheme.colors.secondaryContainer,
            content = GlanceTheme.colors.onSecondaryContainer,
            action = WidgetActions.jumpToDate(context, System.currentTimeMillis())
        )
        if (showAgenda) {
            Spacer(GlanceModifier.width(8.dp))
            ActionChip(
                label = "Agenda",
                container = GlanceTheme.colors.secondaryContainer,
                content = GlanceTheme.colors.onSecondaryContainer,
                action = WidgetActions.openView(context, "agenda")
            )
        }
    }
}

@Composable
private fun TemplateChip(context: Context, template: QuickTemplate) {
    ActionChip(
        label = template.label,
        container = GlanceTheme.colors.tertiaryContainer,
        content = GlanceTheme.colors.onTertiaryContainer,
        action = WidgetActions.createEvent(
            context,
            title = template.title,
            category = template.category,
            durationMinutes = template.durationMinutes
        )
    )
}

/** Material 3 tonal chip: tappable pill with a single text label. */
@Composable
private fun ActionChip(
    label: String,
    container: ColorProvider,
    content: ColorProvider,
    action: Action
) {
    Box(
        modifier = GlanceModifier
            .clickable(action)
            .background(container)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = content,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}
