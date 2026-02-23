package com.example.rxaide.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,            // e.g., "500mg"
    val form: String = "",         // e.g., "Tablet", "Capsule", "Syrup"
    val frequency: String = "",    // e.g., "3 times daily"
    val instructions: String = "", // e.g., "Take after meals"
    val duration: String = "",     // e.g., "7 days"
    val prescriptionImagePath: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
