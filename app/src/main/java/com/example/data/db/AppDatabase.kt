package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [RoutineEntity::class, ActionLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omni_agent_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        populateInitialRoutines(database.routineDao())
                    }
                }
            }

            private suspend fun populateInitialRoutines(dao: RoutineDao) {
                dao.insertRoutine(
                    RoutineEntity(
                        title = "Morning Focus Lo-Fi",
                        description = "Opens YouTube, searches for peaceful lo-fi chill beats, and starts playing.",
                        promptGoal = "Open YouTube app, search for 'Lofi hip hop beats to study and relax', and click on the first video to play.",
                        stepsJson = """[{"action":"openApp","arg":"com.google.android.youtube"},{"action":"clickElementByText","arg":"Search"},{"action":"typeText","arg":"Lofi hip hop"},{"action":"pressEnter","arg":""}]""",
                        iconKey = "music",
                        isFavorite = true
                    )
                )
                dao.insertRoutine(
                    RoutineEntity(
                        title = "Social Check-In",
                        description = "Returns to Home screen, opens Clock app, and prepares morning alarms.",
                        promptGoal = "Press Home screen, open Clock app, and check tomorrow's alarm.",
                        stepsJson = """[{"action":"pressHome","arg":""},{"action":"openApp","arg":"com.google.android.deskclock"}]""",
                        iconKey = "alarm",
                        isFavorite = true
                    )
                )
                dao.insertRoutine(
                    RoutineEntity(
                        title = "Quick Note Scratchpad",
                        description = "Opens Notes or Keep, creates a new note, and types current date stamp.",
                        promptGoal = "Open Keep Notes or default notes app, click new note button, and prepare voice scratchpad.",
                        stepsJson = """[{"action":"openApp","arg":"com.google.android.keep"},{"action":"clickElementByText","arg":"New text note"}]""",
                        iconKey = "note",
                        isFavorite = false
                    )
                )
            }
        }
    }
}
