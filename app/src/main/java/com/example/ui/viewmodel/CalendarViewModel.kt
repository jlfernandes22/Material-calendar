package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupRestoreManager
import com.example.data.database.AppDatabase
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import com.example.data.repository.CalendarRepository
import com.example.data.sync.DeviceCalendarInfo
import com.example.data.sync.DeviceCalendarSyncManager
import com.example.data.sync.SyncResult
import com.example.notification.ReminderScheduler
import com.example.ui.util.DateTimeUtils
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class CalendarViewMode(val title: String) {
    MONTH("Month"),
    WEEK("Week"),
    DAY("Day"),
    SCHEDULE("Agenda"),
    WIDGETS("Widgets"),
    SYNC_BACKUP("Backup")
}

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val selectedDate: Calendar = Calendar.getInstance(),
    val calendars: List<CalendarEntity> = emptyList(),
    val allEvents: List<EventEntity> = emptyList(),
    val filteredEvents: List<EventEntity> = emptyList(),
    val selectedDayEvents: List<EventEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val selectedCalendarFilter: Set<Long> = emptySet(),
    val selectedCategoryFilter: String? = null,
    val hasCalendarPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val syncResult: SyncResult? = null,
    val deviceCalendars: List<DeviceCalendarInfo> = emptyList(),
    val editingEvent: EventEntity? = null,
    val isEventDialogOpen: Boolean = false,
    val detailEvent: EventEntity? = null,
    val isDetailOpen: Boolean = false,
    val isFilterSheetOpen: Boolean = false,
    val isDatePickerOpen: Boolean = false,
    val isBackupDialogOpen: Boolean = false,
    val snackbarMessage: String? = null
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val syncManager = DeviceCalendarSyncManager(application, database.calendarDao(), database.eventDao())
    private val backupManager = BackupRestoreManager(database.calendarDao(), database.eventDao())

    val repository = CalendarRepository(
        calendarDao = database.calendarDao(),
        eventDao = database.eventDao(),
        syncManager = syncManager,
        backupManager = backupManager
    )

    private val _viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _selectedCalendarFilter = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    private val _hasCalendarPermission = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    private val _deviceCalendars = MutableStateFlow<List<DeviceCalendarInfo>>(emptyList())
    private val _editingEvent = MutableStateFlow<EventEntity?>(null)
    private val _isEventDialogOpen = MutableStateFlow(false)
    private val _isFilterSheetOpen = MutableStateFlow(false)
    private val _isDatePickerOpen = MutableStateFlow(false)
    private val _isBackupDialogOpen = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _detailEvent = MutableStateFlow<EventEntity?>(null)
    private val _isDetailOpen = MutableStateFlow(false)

    val uiState: StateFlow<CalendarUiState> = combine(
        _viewMode,
        _selectedDate,
        repository.allCalendars,
        repository.allEvents,
        _searchQuery,
        _isSearching,
        _selectedCalendarFilter,
        _selectedCategoryFilter,
        _hasCalendarPermission,
        _isSyncing,
        _syncResult,
        _deviceCalendars,
        _editingEvent,
        _isEventDialogOpen,
        _isFilterSheetOpen,
        _isDatePickerOpen,
        _isBackupDialogOpen,
        _snackbarMessage,
        _detailEvent,
        _isDetailOpen
    ) { params ->
        val viewMode = params[0] as CalendarViewMode
        val selectedDate = params[1] as Calendar
        val calendars = params[2] as List<CalendarEntity>
        val allEvents = params[3] as List<EventEntity>
        val searchQuery = params[4] as String
        val isSearching = params[5] as Boolean
        val selectedCalFilter = params[6] as Set<Long>
        val selectedCatFilter = params[7] as String?
        val hasCalendarPermission = params[8] as Boolean
        val isSyncing = params[9] as Boolean
        val syncResult = params[10] as SyncResult?
        val deviceCalendars = params[11] as List<DeviceCalendarInfo>
        val editingEvent = params[12] as EventEntity?
        val isEventDialogOpen = params[13] as Boolean
        val isFilterSheetOpen = params[14] as Boolean
        val isDatePickerOpen = params[15] as Boolean
        val isBackupDialogOpen = params[16] as Boolean
        val snackbarMessage = params[17] as String?
        val detailEvent = params[18] as EventEntity?
        val isDetailOpen = params[19] as Boolean

        // Active visible calendars
        val activeCalIds = if (selectedCalFilter.isEmpty()) {
            calendars.filter { it.isVisible }.map { it.id }.toSet()
        } else {
            selectedCalFilter
        }

        // Filter events by calendar, category and search query
        val filteredEvents = allEvents.filter { event ->
            val matchesCal = activeCalIds.contains(event.calendarId)
            val matchesCat = selectedCatFilter == null || event.category == selectedCatFilter
            val matchesQuery = if (searchQuery.isBlank()) true else {
                event.title.contains(searchQuery, ignoreCase = true) ||
                        event.description.contains(searchQuery, ignoreCase = true) ||
                        event.location.contains(searchQuery, ignoreCase = true) ||
                        event.category.contains(searchQuery, ignoreCase = true)
            }
            matchesCal && matchesCat && matchesQuery
        }

        // Events for selected day
        val dayStart = DateTimeUtils.getStartOfDay(selectedDate)
        val dayEnd = DateTimeUtils.getEndOfDay(selectedDate)
        val selectedDayEvents = filteredEvents.filter { event ->
            DateTimeUtils.eventOccursOnDay(event, dayStart, dayEnd)
        }.sortedBy { it.startMillis }

        CalendarUiState(
            viewMode = viewMode,
            selectedDate = selectedDate,
            calendars = calendars,
            allEvents = allEvents,
            filteredEvents = filteredEvents,
            selectedDayEvents = selectedDayEvents,
            searchQuery = searchQuery,
            isSearching = isSearching,
            selectedCalendarFilter = selectedCalFilter,
            selectedCategoryFilter = selectedCatFilter,
            hasCalendarPermission = hasCalendarPermission,
            isSyncing = isSyncing,
            syncResult = syncResult,
            deviceCalendars = deviceCalendars,
            editingEvent = editingEvent,
            isEventDialogOpen = isEventDialogOpen,
            isFilterSheetOpen = isFilterSheetOpen,
            isDatePickerOpen = isDatePickerOpen,
            isBackupDialogOpen = isBackupDialogOpen,
            detailEvent = detailEvent,
            isDetailOpen = isDetailOpen,
            snackbarMessage = snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun setViewMode(mode: CalendarViewMode) {
        _viewMode.value = mode
    }

    fun setSelectedDate(calendar: Calendar) {
        _selectedDate.value = calendar.clone() as Calendar
    }

    fun jumpToToday() {
        _selectedDate.value = Calendar.getInstance()
    }

    fun nextPeriod() {
        val cal = _selectedDate.value.clone() as Calendar
        when (_viewMode.value) {
            CalendarViewMode.MONTH -> cal.add(Calendar.MONTH, 1)
            CalendarViewMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            CalendarViewMode.DAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }
        _selectedDate.value = cal
    }

    fun previousPeriod() {
        val cal = _selectedDate.value.clone() as Calendar
        when (_viewMode.value) {
            CalendarViewMode.MONTH -> cal.add(Calendar.MONTH, -1)
            CalendarViewMode.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -1)
            CalendarViewMode.DAY -> cal.add(Calendar.DAY_OF_MONTH, -1)
            else -> cal.add(Calendar.MONTH, -1)
        }
        _selectedDate.value = cal
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
        if (!searching) _searchQuery.value = ""
    }

    fun toggleCalendarVisibility(calendarId: Long) {
        viewModelScope.launch {
            val cal = uiState.value.calendars.find { it.id == calendarId }
            if (cal != null) {
                repository.updateCalendar(cal.copy(isVisible = !cal.isVisible))
            }
        }
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    // Event operations
    fun openCreateEventDialog(
        prefilledStartMillis: Long? = null,
        title: String? = null,
        category: String? = null,
        durationMinutes: Int = 60
    ) {
        val start = prefilledStartMillis ?: _selectedDate.value.timeInMillis
        val defaultCal = uiState.value.calendars.firstOrNull()?.id ?: 1L

        _editingEvent.value = EventEntity(
            calendarId = defaultCal,
            title = title ?: "",
            category = category ?: "General",
            startMillis = start,
            endMillis = start + durationMinutes * 60_000L,
            isAllDay = false
        )
        _isEventDialogOpen.value = true
    }

    fun openEditEventDialog(event: EventEntity) {
        _editingEvent.value = event
        _isEventDialogOpen.value = true
    }

    fun openEventById(id: Long) {
        viewModelScope.launch {
            repository.getEventById(id)?.let { event ->
                _detailEvent.value = event
                _isDetailOpen.value = true
            }
        }
    }

    fun openEventDetails(event: EventEntity) {
        _detailEvent.value = event
        _isDetailOpen.value = true
    }

    fun closeDetails() {
        _isDetailOpen.value = false
        _detailEvent.value = null
    }

    fun editFromDetails(event: EventEntity) {
        _isDetailOpen.value = false
        _detailEvent.value = null
        _editingEvent.value = event
        _isEventDialogOpen.value = true
    }

    fun closeEventDialog() {
        _isEventDialogOpen.value = false
        _editingEvent.value = null
    }

    fun saveEvent(event: EventEntity) {
        viewModelScope.launch {
            val normalized = event.copy(rrule = event.rrule ?: derivedRrule(event.recurrence))
            if (normalized.id == 0L) {
                repository.insertEvent(normalized)
                showSnackbar("Event created: ${normalized.title}")
            } else {
                repository.updateEvent(normalized.copy(updatedAt = System.currentTimeMillis()))
                showSnackbar("Event updated: ${normalized.title}")
            }
            ReminderScheduler.schedule(getApplication(), normalized)
            WidgetUpdater.update(getApplication())
            closeEventDialog()
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), event.id)
            repository.deleteEvent(event)
            WidgetUpdater.update(getApplication())
            showSnackbar("Deleted event: ${event.title}")
            if (_editingEvent.value?.id == event.id) {
                closeEventDialog()
            }
        }
    }

    fun toggleEventCompletion(event: EventEntity) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(isCompleted = !event.isCompleted))
        }
    }

    // Google Calendar Sync
    fun checkAndLoadDeviceCalendars() {
        viewModelScope.launch {
            val granted = repository.hasCalendarPermission()
            _hasCalendarPermission.value = granted
            if (granted) {
                val list = repository.getDeviceCalendars()
                _deviceCalendars.value = list
            }
        }
    }

    fun syncFromGoogleCalendar(selectedIds: Set<Long>? = null) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.importFromDevice(selectedIds)
            _syncResult.value = result
            _isSyncing.value = false
            ReminderScheduler.rescheduleAll(getApplication())
            WidgetUpdater.update(getApplication())
            showSnackbar(result.message)
        }
    }

    fun importSampleGoogleData() {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.importSampleGoogleCalendar()
            _syncResult.value = result
            _isSyncing.value = false
            ReminderScheduler.rescheduleAll(getApplication())
            WidgetUpdater.update(getApplication())
            showSnackbar(result.message)
        }
    }

    // Backup & Restore
    suspend fun getJsonBackup(): String {
        return repository.exportToJson()
    }

    suspend fun getIcsBackup(): String {
        return repository.exportToIcs()
    }

    fun restoreFromJsonString(json: String, clearExisting: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.restoreFromJson(json, clearExisting)
            _isSyncing.value = false
            ReminderScheduler.rescheduleAll(getApplication())
            WidgetUpdater.update(getApplication())
            showSnackbar(result.message)
        }
    }

    fun restoreFromIcsString(ics: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.importFromIcs(ics)
            _isSyncing.value = false
            ReminderScheduler.rescheduleAll(getApplication())
            WidgetUpdater.update(getApplication())
            showSnackbar(result.message)
        }
    }

    // UI Dialog Toggles
    fun setFilterSheetOpen(isOpen: Boolean) {
        _isFilterSheetOpen.value = isOpen
    }

    fun setDatePickerOpen(isOpen: Boolean) {
        _isDatePickerOpen.value = isOpen
    }

    fun setBackupDialogOpen(isOpen: Boolean) {
        _isBackupDialogOpen.value = isOpen
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun derivedRrule(recurrence: String): String? = when (recurrence) {
        "NONE" -> null
        "DAILY" -> "FREQ=DAILY"
        "WEEKLY" -> "FREQ=WEEKLY"
        "MONTHLY" -> "FREQ=MONTHLY"
        "YEARLY" -> "FREQ=YEARLY"
        else -> null
    }
}
