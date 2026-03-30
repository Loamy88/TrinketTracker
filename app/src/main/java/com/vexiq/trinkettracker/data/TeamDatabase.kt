package com.vexiq.trinkettracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Team::class],
    version = 1,
    exportSchema = false
)
abstract class TeamDatabase : RoomDatabase() {

    abstract fun teamDao(): TeamDao

    companion object {
        @Volatile
        private var INSTANCE: TeamDatabase? = null

        fun getDatabase(context: Context): TeamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TeamDatabase::class.java,
                    "trinket_tracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
