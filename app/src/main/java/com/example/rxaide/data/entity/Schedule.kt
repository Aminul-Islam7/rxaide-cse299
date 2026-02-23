package com.example.rxaide.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicationId"])]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val timeHour: Int,       // 0-23
    val timeMinute: Int,     // 0-59
    val daysOfWeek: String = "1,2,3,4,5,6,7", // comma-separated day numbers (1=Mon, 7=Sun)
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
