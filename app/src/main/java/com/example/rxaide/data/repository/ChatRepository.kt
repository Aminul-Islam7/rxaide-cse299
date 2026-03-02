package com.example.rxaide.data.repository

import com.example.rxaide.data.dao.ChatMessageDao
import com.example.rxaide.data.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    val allMessages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    suspend fun getAllMessagesList(): List<ChatMessage> = chatMessageDao.getAllMessagesList()

    suspend fun insertMessage(message: ChatMessage): Long = chatMessageDao.insert(message)

    suspend fun clearChat() = chatMessageDao.deleteAllMessages()
}
