package com.example.rxaide.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.rxaide.data.dao.DoseHistoryDao
import com.example.rxaide.data.dao.MedicationDao
import com.example.rxaide.data.dao.ScheduleDao
import com.example.rxaide.data.entity.DoseHistory
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule

@Database(
    entities = [Medication::class, Schedule::class, DoseHistory::class],
    version = 1,
    exportSchema = false
)
abstract class RxAideDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseHistoryDao(): DoseHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: RxAideDatabase? = null

        fun getDatabase(context: Context): RxAideDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RxAideDatabase::class.java,
                    "rxaide_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
