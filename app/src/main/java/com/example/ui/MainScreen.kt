package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EventEntity
import com.example.ui.components.CalendarFilterSheet
import com.example.ui.components.EventDetailsDialog
import com.example.ui.components.EventEditDialog
import com.example.ui.components.MiniDatePickerModal
import com.example.ui.util.DateTimeUtils
import com.example.ui.viewmodel.CalendarUiState
import com.example.ui.viewmodel.CalendarViewModel
import com.example.ui.viewmodel.CalendarViewMode
import com.example.ui.views.DayCalendarView
import com.example.ui.views.MonthCalendarView
import com.example.ui.views.ScheduleCalendarView
import com.example.ui.views.SyncAndBackupView
import com.example.ui.views.WeekCalendarView
import com.example.ui.views.WidgetsShowcaseView
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CalendarTopAppBar(
                uiState = uiState,
                onOpenFilter = { viewModel.setFilterSheetOpen(true) },
                onOpenDatePicker = { viewModel.setDatePickerOpen(true) },
                onJumpToday = { viewModel.jumpToToday() },
                onPrevious = { viewModel.previousPeriod() },
                onNext = { viewModel.nextPeriod() },
                onSearchChange = { viewModel.setSearchQuery(it) },
                onToggleSearch = { viewModel.setSearching(!uiState.isSearching) }
            )
        },
        bottomBar = {
            CalendarBottomNav(
                currentMode = uiState.viewMode,
                onModeSelected = { viewModel.setViewMode(it) }
            )
        },
        floatingActionButton = {
            if (uiState.viewMode != CalendarViewMode.SYNC_BACKUP && uiState.viewMode != CalendarViewMode.WIDGETS) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openCreateEventDialog() },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Event") },
                    text = { Text("Event", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("fab_add_event")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState.viewMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "view_mode_transition"
            ) { mode ->
                when (mode) {
                    CalendarViewMode.MONTH -> {
                        MonthCalendarView(
                            selectedDate = uiState.selectedDate,
                            filteredEvents = uiState.filteredEvents,
                            selectedDayEvents = uiState.selectedDayEvents,
                            calendars = uiState.calendars,
                            onSelectDate = { viewModel.setSelectedDate(it) },
                            onEventClick = { viewModel.openEventDetails(it) },
                            onDeleteEvent = { viewModel.deleteEvent(it) },
                            onToggleComplete = { viewModel.toggleEventCompletion(it) },
                            onAddEventForDate = { viewModel.openCreateEventDialog(it) }
                        )
                    }
                    CalendarViewMode.WEEK -> {
                        WeekCalendarView(
                            selectedDate = uiState.selectedDate,
                            filteredEvents = uiState.filteredEvents,
                            calendars = uiState.calendars,
                            onSelectDate = { viewModel.setSelectedDate(it) },
                            onEventClick = { viewModel.openEventDetails(it) },
                            onAddEventForTime = { viewModel.openCreateEventDialog(it) }
                        )
                    }
                    CalendarViewMode.DAY -> {
                        DayCalendarView(
                            selectedDate = uiState.selectedDate,
                            filteredEvents = uiState.filteredEvents,
                            calendars = uiState.calendars,
                            onPreviousDay = { viewModel.previousPeriod() },
                            onNextDay = { viewModel.nextPeriod() },
                            onEventClick = { viewModel.openEventDetails(it) },
                            onAddEventForTime = { viewModel.openCreateEventDialog(it) }
                        )
                    }
                    CalendarViewMode.SCHEDULE -> {
                        ScheduleCalendarView(
                            filteredEvents = uiState.filteredEvents,
                            calendars = uiState.calendars,
                            searchQuery = uiState.searchQuery,
                            onEventClick = { viewModel.openEventDetails(it) },
                            onDeleteEvent = { viewModel.deleteEvent(it) },
                            onToggleComplete = { viewModel.toggleEventCompletion(it) }
                        )
                    }
                    CalendarViewMode.WIDGETS -> {
                        WidgetsShowcaseView(
                            allEvents = uiState.allEvents,
                            calendars = uiState.calendars,
                            onEventClick = { viewModel.openEventDetails(it) },
                            onQuickAddEvent = { title, category, durationMinutes ->
                                val now = System.currentTimeMillis()
                                val newEvent = EventEntity(
                                    calendarId = uiState.calendars.firstOrNull()?.id ?: 1L,
                                    title = title,
                                    category = category,
                                    startMillis = now,
                                    endMillis = now + durationMinutes * 60_000L,
                                    isAllDay = false
                                )
                                viewModel.saveEvent(newEvent)
                            },
                            onSelectDate = {
                                viewModel.setSelectedDate(it)
                                viewModel.setViewMode(CalendarViewMode.DAY)
                            }
                        )
                    }
                    CalendarViewMode.SYNC_BACKUP -> {
                        SyncAndBackupView(
                            hasPermission = viewModel.repository.hasCalendarPermission(),
                            isSyncing = uiState.isSyncing,
                            syncResult = uiState.syncResult,
                            deviceCalendars = uiState.deviceCalendars,
                            onCheckPermission = { viewModel.checkAndLoadDeviceCalendars() },
                            onSyncDevice = { viewModel.syncFromGoogleCalendar(it) },
                            onImportSampleData = { viewModel.importSampleGoogleData() },
                            onGetJsonBackup = { viewModel.getJsonBackup() },
                            onGetIcsBackup = { viewModel.getIcsBackup() },
                            onRestoreJson = { json, clear -> viewModel.restoreFromJsonString(json, clear) },
                            onRestoreIcs = { ics -> viewModel.restoreFromIcsString(ics) }
                        )
                    }
                }
            }
        }
    }

    // Event Create/Edit Modal
    if (uiState.isEventDialogOpen && uiState.editingEvent != null) {
        EventEditDialog(
            event = uiState.editingEvent!!,
            calendars = uiState.calendars,
            onSave = { viewModel.saveEvent(it) },
            onDelete = { viewModel.deleteEvent(it) },
            onDismiss = { viewModel.closeEventDialog() }
        )
    }

    // Event Details Modal (read-only)
    if (uiState.isDetailOpen && uiState.detailEvent != null) {
        EventDetailsDialog(
            event = uiState.detailEvent!!,
            calendars = uiState.calendars,
            onEdit = { viewModel.editFromDetails(it) },
            onDelete = { e -> viewModel.closeDetails(); viewModel.deleteEvent(e) },
            onDismiss = { viewModel.closeDetails() }
        )
    }

    // Filter BottomSheet
    if (uiState.isFilterSheetOpen) {
        CalendarFilterSheet(
            calendars = uiState.calendars,
            selectedCategory = uiState.selectedCategoryFilter,
            onToggleCalendar = { viewModel.toggleCalendarVisibility(it) },
            onSelectCategory = { viewModel.setCategoryFilter(it) },
            onNavigateToSync = { viewModel.setViewMode(CalendarViewMode.SYNC_BACKUP) },
            onDismiss = { viewModel.setFilterSheetOpen(false) }
        )
    }

    // Date Picker Modal
    if (uiState.isDatePickerOpen) {
        MiniDatePickerModal(
            initialDate = uiState.selectedDate,
            onDateSelected = { viewModel.setSelectedDate(it) },
            onDismiss = { viewModel.setDatePickerOpen(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTopAppBar(
    uiState: CalendarUiState,
    onOpenFilter: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onJumpToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
            if (uiState.isSearching) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search events, places, tags...", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(onClick = onToggleSearch) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("search_events_input")
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenDatePicker)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = DateTimeUtils.formatMonthYear(uiState.selectedDate),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Date",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenFilter, modifier = Modifier.testTag("open_filter_button")) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Calendars & Filters",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            if (!uiState.isSearching) {
                IconButton(onClick = onToggleSearch, modifier = Modifier.testTag("open_search_button")) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }

                // Previous and Next chevrons
                IconButton(onClick = onPrevious, modifier = Modifier.testTag("previous_period_button")) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous")
                }
                IconButton(onClick = onNext, modifier = Modifier.testTag("next_period_button")) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next")
                }

                // Today Quick Button
                val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onJumpToday)
                        .testTag("jump_today_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Today",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$todayDay",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun CalendarBottomNav(
    currentMode: CalendarViewMode,
    onModeSelected: (CalendarViewMode) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        val items = listOf(
            CalendarViewMode.MONTH to Icons.Default.CalendarMonth,
            CalendarViewMode.WEEK to Icons.Default.CalendarViewWeek,
            CalendarViewMode.DAY to Icons.Default.CalendarViewDay,
            CalendarViewMode.SCHEDULE to Icons.Default.FormatListNumbered,
            CalendarViewMode.WIDGETS to Icons.Default.Widgets,
            CalendarViewMode.SYNC_BACKUP to Icons.Default.Sync
        )

        items.forEach { (mode, icon) ->
            val isSelected = currentMode == mode
            NavigationBarItem(
                selected = isSelected,
                onClick = { onModeSelected(mode) },
                icon = { Icon(imageVector = icon, contentDescription = mode.title) },
                label = {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_${mode.name.lowercase()}")
            )
        }
    }
}
