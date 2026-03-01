package com.example.rxaide.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,                          // e.g., "500"
    val dosageUnit: String = "mg",               // "mg", "ml", "tablet", "capsule"
    val form: String = "",                        // e.g., "Tablet", "Capsule", "Syrup"
    val frequency: String = "",                   // e.g., "Once daily", "Twice daily"
    val mealRelation: String = "No relation",     // "Before meal", "After meal", "With meal", "No relation"
    val instructions: String = "",                // e.g., "Take with water"
    val duration: String = "",                    // kept for backward compat
    val startDate: Long = System.currentTimeMillis(), // When to start taking
    val endDate: Long? = null,                    // null = ongoing
    val notes: String = "",                       // Additional notes
    val prescriptionImagePath: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
