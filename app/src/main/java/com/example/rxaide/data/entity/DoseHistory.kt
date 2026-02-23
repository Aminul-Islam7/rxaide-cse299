package com.example.rxaide.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_history",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["medicationId"]),
        Index(value = ["scheduleId"])
    ]
)
data class DoseHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long? = null,
    val status: String,          // "taken", "missed", "skipped"
    val scheduledTime: Long,     // timestamp when dose was scheduled
    val actionTime: Long? = null, // timestamp when user took action
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
