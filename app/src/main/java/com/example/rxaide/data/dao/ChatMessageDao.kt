package com.example.rxaide.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.rxaide.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesList(): List<ChatMessage>

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}
