package com.example.rxaide.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val imageUri: String? = null,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
