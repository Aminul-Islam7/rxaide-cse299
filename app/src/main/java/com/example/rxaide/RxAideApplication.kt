package com.example.rxaide

import android.app.Application
import com.example.rxaide.data.RxAideDatabase
import com.example.rxaide.data.repository.ChatRepository
import com.example.rxaide.data.repository.MedicationRepository
import com.example.rxaide.notification.RxAideNotificationChannel

class RxAideApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RxAideNotificationChannel.create(this)
    }

    val database by lazy { RxAideDatabase.getDatabase(this) }

    val repository by lazy {
        MedicationRepository(
            database.medicationDao(),
            database.scheduleDao(),
            database.doseHistoryDao()
        )
    }

    val chatRepository by lazy {
        ChatRepository(database.chatMessageDao())
    }
}
