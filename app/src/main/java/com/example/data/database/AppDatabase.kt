package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CalendarDao
import com.example.data.dao.EventDao
import com.example.data.model.CalendarEntity
import com.example.data.model.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CalendarEntity::class, EventEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN timezone TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN rrule TEXT")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_calendar_db"
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope?
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    val coroutineScope = scope ?: CoroutineScope(Dispatchers.IO)
                    coroutineScope.launch {
                        populateInitialData(database.calendarDao(), database.eventDao())
                    }
                }
            }

            private suspend fun populateInitialData(calendarDao: CalendarDao, eventDao: EventDao) {
                // Create a single empty default local calendar so the app is immediately usable.
                // No events are seeded on install — the calendar starts completely empty.
                calendarDao.insertCalendar(
                    CalendarEntity(
                        name = "Personal",
                        accountName = "Local Storage",
                        color = 0xFF039BE5.toInt(), // Peacock
                        isVisible = true,
                        isLocal = true
                    )
                )
            }
        }
    }
}
