package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import com.example.data.model.PresetColors
import com.example.data.model.RecurrenceType
import com.example.ui.util.DateTimeUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditDialog(
    event: EventEntity,
    calendars: List<CalendarEntity>,
    onSave: (EventEntity) -> Unit,
    onDelete: (EventEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(event.title) }
    var description by remember { mutableStateOf(event.description) }
    var location by remember { mutableStateOf(event.location) }
    var selectedCalId by remember { mutableLongStateOf(event.calendarId) }
    var isAllDay by remember { mutableStateOf(event.isAllDay) }
    var startMillis by remember { mutableLongStateOf(event.startMillis) }
    var endMillis by remember { mutableLongStateOf(event.endMillis) }
    var selectedCategory by remember { mutableStateOf(event.category) }
    var recurrence by remember { mutableStateOf(event.recurrence) }
    var selectedColor by remember { mutableIntStateOf(event.color) }
    var reminderMinutes by remember { mutableIntStateOf(event.reminderMinutes) }

    var recurrenceMenuExpanded by remember { mutableStateOf(false) }
    var calendarMenuExpanded by remember { mutableStateOf(false) }
    var reminderMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_event_dialog")) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }

                Text(
                    text = if (event.id == 0L) "New Event" else "Edit Event",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            title = "New Event"
                        }
                        val finalEvent = event.copy(
                            calendarId = selectedCalId,
                            title = title.trim(),
                            description = description.trim(),
                            location = location.trim(),
                            startMillis = startMillis,
                            endMillis = if (endMillis <= startMillis) startMillis + 3600_000L else endMillis,
                            isAllDay = isAllDay,
                            category = selectedCategory,
                            recurrence = recurrence,
                            color = selectedColor,
                            reminderMinutes = reminderMinutes
                        )
                        onSave(finalEvent)
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("save_event_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title Field (Clean Material style)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Add title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_title_input"),
                textStyle = MaterialTheme.typography.headlineSmall,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Calendar & Category Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calendar Dropdown
                ExposedDropdownMenuBox(
                    expanded = calendarMenuExpanded,
                    onExpandedChange = { calendarMenuExpanded = !calendarMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    val currentCal = calendars.find { it.id == selectedCalId } ?: calendars.firstOrNull()
                    OutlinedTextField(
                        value = currentCal?.name ?: "Personal",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Calendar") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = calendarMenuExpanded,
                        onDismissRequest = { calendarMenuExpanded = false }
                    ) {
                        calendars.forEach { cal ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(cal.color))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cal.name)
                                    }
                                },
                                onClick = {
                                    selectedCalId = cal.id
                                    calendarMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Chips Row
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetColors.Categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // All Day Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All-day",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Switch(
                    checked = isAllDay,
                    onCheckedChange = { isAllDay = it },
                    modifier = Modifier.testTag("all_day_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start Date & Time
            val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                startCal.set(y, m, d)
                                startMillis = startCal.timeInMillis
                                if (endMillis < startMillis) {
                                    endMillis = startMillis + 3600_000L
                                }
                            },
                            startCal.get(Calendar.YEAR),
                            startCal.get(Calendar.MONTH),
                            startCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(DateTimeUtils.formatShortDate(startMillis), fontSize = 13.sp)
                }

                if (!isAllDay) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    startCal.set(Calendar.HOUR_OF_DAY, h)
                                    startCal.set(Calendar.MINUTE, min)
                                    startMillis = startCal.timeInMillis
                                    if (endMillis <= startMillis) {
                                        endMillis = startMillis + 3600_000L
                                    }
                                },
                                startCal.get(Calendar.HOUR_OF_DAY),
                                startCal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(DateTimeUtils.formatTime(startMillis), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // End Date & Time
            val endCal = Calendar.getInstance().apply { timeInMillis = endMillis }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                endCal.set(y, m, d)
                                endMillis = endCal.timeInMillis
                            },
                            endCal.get(Calendar.YEAR),
                            endCal.get(Calendar.MONTH),
                            endCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(DateTimeUtils.formatShortDate(endMillis), fontSize = 13.sp)
                }

                if (!isAllDay) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    endCal.set(Calendar.HOUR_OF_DAY, h)
                                    endCal.set(Calendar.MINUTE, min)
                                    endMillis = endCal.timeInMillis
                                },
                                endCal.get(Calendar.HOUR_OF_DAY),
                                endCal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(DateTimeUtils.formatTime(endMillis), fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recurrence Dropdown
            ExposedDropdownMenuBox(
                expanded = recurrenceMenuExpanded,
                onExpandedChange = { recurrenceMenuExpanded = !recurrenceMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = RecurrenceType.values().find { it.name == recurrence }?.displayName ?: "Does not repeat",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Repeat") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Repeat, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = recurrenceMenuExpanded,
                    onDismissRequest = { recurrenceMenuExpanded = false }
                ) {
                    RecurrenceType.values().forEach { rec ->
                        DropdownMenuItem(
                            text = { Text(rec.displayName) },
                            onClick = {
                                recurrence = rec.name
                                recurrenceMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reminder Dropdown
            ExposedDropdownMenuBox(
                expanded = reminderMenuExpanded,
                onExpandedChange = { reminderMenuExpanded = !reminderMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                val reminderText = when (reminderMinutes) {
                    -1 -> "No reminder"
                    0 -> "At time of event"
                    10 -> "10 minutes before"
                    30 -> "30 minutes before"
                    60 -> "1 hour before"
                    1440 -> "1 day before"
                    else -> "$reminderMinutes minutes before"
                }

                OutlinedTextField(
                    value = reminderText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Reminder") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Notifications, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = reminderMenuExpanded,
                    onDismissRequest = { reminderMenuExpanded = false }
                ) {
                    listOf(
                        -1 to "No reminder",
                        0 to "At time of event",
                        10 to "10 minutes before",
                        30 to "30 minutes before",
                        60 to "1 hour before",
                        1440 to "1 day before"
                    ).forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                reminderMinutes = minutes
                                reminderMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Color Palette Selector
            Text(
                text = "Color Accent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PresetColors.GoogleColors.forEach { col ->
                    val isSelected = selectedColor == col.colorInt
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(col.colorInt))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = col.colorInt }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Field
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Add location") },
                leadingIcon = { Icon(imageVector = Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("event_location_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Add description or notes") },
                leadingIcon = { Icon(imageVector = Icons.Default.Description, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .testTag("event_description_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Delete Event option if editing
            if (event.id != 0L) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onDelete(event) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_event_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete this event", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
