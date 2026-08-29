package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY id ASC")
    fun getAllCalendars(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getCalendarById(id: Long): CalendarEntity?

    @Query("SELECT * FROM calendars WHERE systemCalendarId = :systemId LIMIT 1")
    suspend fun getCalendarBySystemId(systemId: Long): CalendarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendar(calendar: CalendarEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(calendars: List<CalendarEntity>): List<Long>

    @Update
    suspend fun updateCalendar(calendar: CalendarEntity)

    @Delete
    suspend fun deleteCalendar(calendar: CalendarEntity)

    @Query("DELETE FROM calendars")
    suspend fun deleteAllCalendars()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startMillis ASC")
    fun getAllEvents(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE systemEventId = :systemId LIMIT 1")
    suspend fun getEventBySystemId(systemId: Long): EventEntity?

    @Query("""
        SELECT * FROM events 
        WHERE (startMillis >= :startRange AND startMillis <= :endRange)
           OR (endMillis >= :startRange AND endMillis <= :endRange)
           OR (startMillis <= :startRange AND endMillis >= :endRange)
           OR recurrence != 'NONE'
        ORDER BY startMillis ASC
    """)
    fun getEventsForRange(startRange: Long, endRange: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%' ORDER BY startMillis ASC")
    fun searchEvents(query: String): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>): List<Long>

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM events WHERE calendarId = :calendarId")
    suspend fun deleteEventsByCalendarId(calendarId: Long)

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()
}
