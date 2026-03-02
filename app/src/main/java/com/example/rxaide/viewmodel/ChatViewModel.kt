package com.example.rxaide.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxaide.BuildConfig
import com.example.rxaide.RxAideApplication
import com.example.rxaide.ai.GeminiService
import com.example.rxaide.data.entity.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as RxAideApplication).chatRepository
    private val geminiService = GeminiService(application.applicationContext)

    private val isApiKeyConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    val allMessages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // Insert user message
            repository.insertMessage(
                ChatMessage(
                    content = text,
                    isFromUser = true
                )
            )
            // Generate bot reply
            generateBotReply(text, imageUri = null)
        }
    }

    fun sendImageMessage(imageUri: String) {
        viewModelScope.launch {
            // Insert user image message
            repository.insertMessage(
                ChatMessage(
                    content = "📷 Prescription image sent for analysis",
                    imageUri = imageUri,
                    isFromUser = true
                )
            )
            // Generate bot reply for the image
            generateBotReply(text = null, imageUri = imageUri)
        }
    }

    private suspend fun generateBotReply(text: String?, imageUri: String?) {
        _isTyping.value = true

        val replyContent = if (!isApiKeyConfigured) {
            // Fallback: no API key configured
            generateFallbackReply(text, imageUri)
        } else if (imageUri != null) {
            // Send image to Gemini for prescription analysis
            val chatHistory = repository.getAllMessagesList()
            geminiService.sendImageMessage(imageUri, chatHistory)
        } else {
            // Send text to Gemini with full chat context
            val chatHistory = repository.getAllMessagesList()
            geminiService.sendTextMessage(text ?: "", chatHistory)
        }

        repository.insertMessage(
            ChatMessage(
                content = replyContent,
                isFromUser = false
            )
        )
        _isTyping.value = false
    }

    /**
     * Fallback replies when API key is not configured.
     */
    private fun generateFallbackReply(text: String?, imageUri: String?): String {
        if (imageUri != null) {
            return "⚠️ **Gemini API key not configured.**\n\n" +
                "To analyze prescriptions, add your API key to `local.properties`:\n" +
                "```\nGEMINI_API_KEY=your_key_here\n```\n" +
                "Then rebuild the app."
        }

        val lowerText = (text ?: "").lowercase()
        return when {
            lowerText.contains("hello") || lowerText.contains("hi") || lowerText.contains("hey") ->
                "Hello! 👋 I'm RxAide (running in offline mode).\n\n" +
                    "⚠️ Add your Gemini API key to `local.properties` to enable AI features."

            else ->
                "⚠️ **Gemini API key not configured.**\n\n" +
                    "Add `GEMINI_API_KEY=your_key` to `local.properties` and rebuild."
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }
}
