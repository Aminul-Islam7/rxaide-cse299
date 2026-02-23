package com.example.rxaide

import android.app.Application
import com.example.rxaide.data.RxAideDatabase
import com.example.rxaide.data.repository.MedicationRepository

class RxAideApplication : Application() {

    val database by lazy { RxAideDatabase.getDatabase(this) }

    val repository by lazy {
        MedicationRepository(
            database.medicationDao(),
            database.scheduleDao(),
            database.doseHistoryDao()
        )
    }
}
