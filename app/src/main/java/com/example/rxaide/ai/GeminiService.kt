package com.example.rxaide.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.rxaide.BuildConfig
import com.example.rxaide.data.entity.ChatMessage
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GoogleSearch
import com.google.genai.types.Part
import com.google.genai.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiService"
        private const val MODEL_NAME = "gemini-2.5-flash"

        private val SYSTEM_PROMPT = """
You are RxAide, an AI medication assistant embedded in a mobile app. Your role:

1. **Prescription OCR**: When a user sends a prescription image, extract ALL information:
   - Medicine names (verify spelling, use generic/brand names properly)
   - Dosages (mg, ml, etc.)
   - Frequency/schedule notations — especially Bangladeshi notation:
     • "1+1+0" = morning + afternoon, skip evening
     • "1+0+1" = morning + evening, skip afternoon
     • "0+0+1" = evening only
     • "1+1+1" = morning + afternoon + evening
     • "0+1+0" = afternoon only
     • Numbers >1 like "2+1+0" = 2 in morning, 1 in afternoon
   - Duration (e.g., 7 days, 14 days, 1 month)
   - Any special instructions (before/after meals, etc.)
   
2. **Multi-language support**: Read prescriptions in English, Bangla (বাংলা), or mixed. Doctors in Bangladesh often mix English medicine names with Bangla instructions.

3. **Medicine verification**: Verify medicine names and dosages are valid and reasonable. Flag anything suspicious.

4. **Response style**:
   - Be concise. Use bullet points.
   - No filler text or unnecessary pleasantries.
   - For prescriptions: list each medicine with name, dose, schedule, duration in a clean format.
   - After listing, ask user to confirm before creating schedule.

5. **Safety**: Never provide medical diagnoses. For dosage concerns, suggest consulting the doctor. You assist with reading and organizing, not prescribing.

6. **Context**: Use the chat history for context. Don't repeat yourself.
""".trimIndent()
    }

    private val client: Client by lazy {
        Client.builder()
            .apiKey(BuildConfig.GEMINI_API_KEY)
            .build()
    }

    private val googleSearchTool: Tool by lazy {
        Tool.builder()
            .googleSearch(GoogleSearch.builder())
            .build()
    }

    /**
     * Send a text message with full chat history as context.
     */
    suspend fun sendTextMessage(
        userMessage: String,
        chatHistory: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            val contents = buildChatContents(chatHistory, userMessage, imageBytes = null)

            val config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .tools(googleSearchTool)
                .maxOutputTokens(1024)
                .build()

            val response = client.models.generateContent(MODEL_NAME, contents, config)
            response.text() ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text message", e)
            "⚠️ Error: ${e.message ?: "Something went wrong. Check your internet connection and API key."}"
        }
    }

    /**
     * Send an image (prescription) with full chat history as context.
     */
    suspend fun sendImageMessage(
        imageUri: String,
        chatHistory: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            val imageBytes = loadImageBytes(Uri.parse(imageUri))
                ?: return@withContext "⚠️ Could not load the image. Please try again."

            val prompt = "Analyze this prescription image. Extract all medicines, dosages, " +
                "schedules, and instructions. Verify medicine names via search."

            val contents = buildChatContents(chatHistory, prompt, imageBytes)

            val config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .tools(googleSearchTool)
                .maxOutputTokens(1500)
                .build()

            val response = client.models.generateContent(MODEL_NAME, contents, config)
            response.text() ?: "I couldn't analyze this prescription. Please try with a clearer image."
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image message", e)
            "⚠️ Error: ${e.message ?: "Something went wrong. Check your internet connection and API key."}"
        }
    }

    /**
     * Build the full conversation contents from chat history + the new user message.
     * We keep only the last 20 messages to avoid token limits.
     */
    private fun buildChatContents(
        chatHistory: List<ChatMessage>,
        newUserMessage: String,
        imageBytes: ByteArray?
    ): List<Content> {
        val contents = mutableListOf<Content>()

        // Add recent chat history (last 20 messages to control token usage)
        val recentHistory = chatHistory.takeLast(20)
        for (msg in recentHistory) {
            val role = if (msg.isFromUser) "user" else "model"
            contents.add(
                Content.builder()
                    .role(role)
                    .parts(Part.fromText(msg.content))
                    .build()
            )
        }

        // Add the new user message (with optional image)
        val parts = mutableListOf<Part>()
        if (imageBytes != null) {
            parts.add(Part.fromBytes(imageBytes, "image/jpeg"))
        }
        parts.add(Part.fromText(newUserMessage))

        contents.add(
            Content.builder()
                .role("user")
                .parts(parts)
                .build()
        )

        return contents
    }

    /**
     * Load image bytes from a content URI.
     */
    private fun loadImageBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image from URI: $uri", e)
            null
        }
    }
}
