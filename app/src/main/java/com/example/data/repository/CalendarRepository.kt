package com.example.data.repository

import com.example.data.backup.BackupRestoreManager
import com.example.data.backup.RestoreResult
import com.example.data.dao.CalendarDao
import com.example.data.dao.EventDao
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import com.example.data.sync.DeviceCalendarInfo
import com.example.data.sync.DeviceCalendarSyncManager
import com.example.data.sync.SyncResult
import kotlinx.coroutines.flow.Flow

class CalendarRepository(
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
    private val syncManager: DeviceCalendarSyncManager,
    private val backupManager: BackupRestoreManager
) {
    val allCalendars: Flow<List<CalendarEntity>> = calendarDao.getAllCalendars()
    val allEvents: Flow<List<EventEntity>> = eventDao.getAllEvents()

    fun getEventsForRange(startRange: Long, endRange: Long): Flow<List<EventEntity>> {
        return eventDao.getEventsForRange(startRange, endRange)
    }

    fun searchEvents(query: String): Flow<List<EventEntity>> {
        return eventDao.searchEvents(query)
    }

    suspend fun getEventById(id: Long): EventEntity? = eventDao.getEventById(id)

    suspend fun insertEvent(event: EventEntity): Long = eventDao.insertEvent(event)

    suspend fun updateEvent(event: EventEntity) = eventDao.updateEvent(event)

    suspend fun deleteEvent(event: EventEntity) = eventDao.deleteEvent(event)

    suspend fun deleteEventById(id: Long) = eventDao.deleteEventById(id)

    suspend fun insertCalendar(calendar: CalendarEntity): Long = calendarDao.insertCalendar(calendar)

    suspend fun updateCalendar(calendar: CalendarEntity) = calendarDao.updateCalendar(calendar)

    suspend fun deleteCalendar(calendar: CalendarEntity) {
        eventDao.deleteEventsByCalendarId(calendar.id)
        calendarDao.deleteCalendar(calendar)
    }

    // Sync features
    fun hasCalendarPermission(): Boolean = syncManager.hasCalendarPermission()

    suspend fun getDeviceCalendars(): List<DeviceCalendarInfo> = syncManager.getDeviceCalendars()

    suspend fun importFromDevice(selectedCalendarIds: Set<Long>? = null): SyncResult =
        syncManager.importFromDevice(selectedCalendarIds)

    suspend fun importSampleGoogleCalendar(): SyncResult =
        syncManager.importSampleGoogleCalendar()

    // Backup & Restore features
    suspend fun exportToJson(): String = backupManager.exportToJson()

    suspend fun restoreFromJson(json: String, clearExisting: Boolean = false): RestoreResult =
        backupManager.restoreFromJson(json, clearExisting)

    suspend fun exportToIcs(): String = backupManager.exportToIcs()

    suspend fun importFromIcs(ics: String): RestoreResult =
        backupManager.importFromIcs(ics)
}
