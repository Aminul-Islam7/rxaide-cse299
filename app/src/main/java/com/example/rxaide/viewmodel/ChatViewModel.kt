package com.example.rxaide.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rxaide.BuildConfig
import com.example.rxaide.RxAideApplication
import com.example.rxaide.ai.GeminiService
import com.example.rxaide.data.entity.ChatMessage
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import com.example.rxaide.notification.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Types of quick-action buttons that can appear in the chat.
 */
enum class QuickActionType {
    CONFIRM_SCHEDULE,     // "✅ Confirm & Schedule" — after prescription scan
    VIEW_MEDICATIONS      // "📋 My Medications" — after scheduling is done
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatRepository = (application as RxAideApplication).chatRepository
    private val medicationRepository = (application as RxAideApplication).repository
    private val geminiService = GeminiService(application.applicationContext)

    private val isApiKeyConfigured: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    val allMessages: StateFlow<List<ChatMessage>> = chatRepository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /** Current quick action to show in the chat UI. */
    private val _quickAction = MutableStateFlow<QuickActionType?>(null)
    val quickAction: StateFlow<QuickActionType?> = _quickAction.asStateFlow()

    /** Navigation event: set to a route when we want the UI to navigate. */
    private val _navigateToRoute = MutableStateFlow<String?>(null)
    val navigateToRoute: StateFlow<String?> = _navigateToRoute.asStateFlow()

    fun onNavigationHandled() {
        _navigateToRoute.value = null
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            // Clear quick action when user sends a message
            _quickAction.value = null

            // Insert user message
            chatRepository.insertMessage(
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
            _quickAction.value = null

            // Insert user image message
            chatRepository.insertMessage(
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

    /**
     * Called when user taps "✅ Confirm & Schedule" quick action button.
     */
    fun confirmAndSchedule() {
        viewModelScope.launch {
            _quickAction.value = null

            // Insert user's confirmation message
            chatRepository.insertMessage(
                ChatMessage(
                    content = "✅ Yes, please create medication reminders for these.",
                    isFromUser = true
                )
            )

            _isTyping.value = true

            if (!isApiKeyConfigured) {
                chatRepository.insertMessage(
                    ChatMessage(
                        content = "⚠️ **Gemini API key not configured.** Cannot create automatic schedules.",
                        isFromUser = false
                    )
                )
                _isTyping.value = false
                return@launch
            }

            // Request structured JSON from Gemini
            val chatHistory = chatRepository.getAllMessagesList()
            val medications = geminiService.requestMedicationJson(chatHistory)

            if (medications == null || medications.isEmpty()) {
                chatRepository.insertMessage(
                    ChatMessage(
                        content = "⚠️ Unable to parse medication data for scheduling. " +
                            "Please try adding medications manually from the **My Medications** page.",
                        isFromUser = false
                    )
                )
                _isTyping.value = false
                _quickAction.value = QuickActionType.VIEW_MEDICATIONS
                return@launch
            }

            // Create Medication + Schedule entries in the database
            var successCount = 0
            for (med in medications) {
                try {
                    // Calculate end date from duration
                    val endDate = calculateEndDate(med.duration)

                    val medication = Medication(
                        name = med.name,
                        dosage = med.dosage,
                        dosageUnit = med.dosageUnit,
                        form = med.form,
                        frequency = med.frequency,
                        mealRelation = med.mealRelation,
                        instructions = med.instructions,
                        duration = med.duration,
                        startDate = System.currentTimeMillis(),
                        endDate = endDate,
                        notes = med.notes,
                        isActive = true
                    )

                    val medId = medicationRepository.insertMedication(medication)

                    // Create schedules based on frequency
                    val scheduleTimes = getDefaultScheduleTimes(med.frequency)
                    val schedules = scheduleTimes.map { (hour, minute) ->
                        Schedule(
                            medicationId = medId,
                            timeHour = hour,
                            timeMinute = minute
                        )
                    }
                    if (schedules.isNotEmpty()) {
                        medicationRepository.insertSchedules(schedules)
                    }
                    successCount++
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to create medication: ${med.name}", e)
                }
            }

            // Send confirmation message
            val reminderDetails = buildString {
                appendLine("✅ **Reminders created!** I've set up $successCount medication reminder(s) with default timing:")
                appendLine()
                appendLine("• 🌅 Morning doses → **8:00 AM**")
                appendLine("• 🌞 Noon doses → **1:00 PM**")
                appendLine("• 🌙 Night doses → **9:00 PM**")
                appendLine()
                appendLine("You can adjust the reminder times in **My Medications** to match your personal schedule, or tell me here what times work better for you.")
            }

            chatRepository.insertMessage(
                ChatMessage(
                    content = reminderDetails,
                    isFromUser = false
                )
            )

            _isTyping.value = false
            _quickAction.value = QuickActionType.VIEW_MEDICATIONS
        }
    }

    private suspend fun generateBotReply(text: String?, imageUri: String?) {
        _isTyping.value = true

        // Build medication context for text messages so the AI knows current schedules
        val medicationContext = if (text != null) buildMedicationContext() else null

        val replyContent = if (!isApiKeyConfigured) {
            generateFallbackReply(text, imageUri)
        } else if (imageUri != null) {
            val chatHistory = chatRepository.getAllMessagesList()
            geminiService.sendImageMessage(imageUri, chatHistory)
        } else {
            val chatHistory = chatRepository.getAllMessagesList()
            // Prepend medication context as a hidden system message
            val contextPrefix = medicationContext ?: ""
            val enrichedMessage = if (contextPrefix.isNotBlank()) {
                "$contextPrefix\n\nUser message: ${text ?: ""}"
            } else {
                text ?: ""
            }
            geminiService.sendTextMessage(enrichedMessage, chatHistory)
        }

        // Check for [SCHEDULE_UPDATE] action blocks and execute them
        val displayContent = parseAndExecuteScheduleUpdates(replyContent)

        chatRepository.insertMessage(
            ChatMessage(
                content = displayContent,
                isFromUser = false
            )
        )

        _isTyping.value = false

        // Show "Confirm & Schedule" if this was a prescription scan
        if (imageUri != null && replyContent.contains("Prescription Summary", ignoreCase = true)) {
            _quickAction.value = QuickActionType.CONFIRM_SCHEDULE
        }

        // Also detect if the AI asked for confirmation in a text reply (e.g., after corrections)
        if (text != null && replyContent.contains("Confirm & Schedule", ignoreCase = true)) {
            _quickAction.value = QuickActionType.CONFIRM_SCHEDULE
        }
    }

    /**
     * Builds a context block with current medication names and schedule times
     * so the AI knows what medications/schedules exist when the user asks for changes.
     */
    private suspend fun buildMedicationContext(): String? {
        val medications = medicationRepository.getActiveMedicationsOnce()
        if (medications.isEmpty()) return null

        val sb = StringBuilder("[CURRENT_MEDICATIONS]\n")
        for ((index, med) in medications.withIndex()) {
            val schedules = medicationRepository.getSchedulesForMedicationOnce(med.id)
            val timesStr = schedules.joinToString(", ") { s ->
                "%02d:%02d".format(s.timeHour, s.timeMinute)
            }
            sb.appendLine("${index + 1}. ${med.name} ${med.dosage}${med.dosageUnit} (${med.form}) - ${med.frequency}")
            if (timesStr.isNotBlank()) {
                sb.appendLine("   Schedules: $timesStr")
            }
        }
        sb.append("[/CURRENT_MEDICATIONS]")
        return sb.toString()
    }

    /**
     * Parses [SCHEDULE_UPDATE] blocks from the AI response, executes DB/WorkManager
     * changes, and returns the display text with action blocks stripped out.
     */
    private suspend fun parseAndExecuteScheduleUpdates(response: String): String {
        val pattern = Regex(
            """\[SCHEDULE_UPDATE]\s*\n(.*?)\[/SCHEDULE_UPDATE]""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = pattern.find(response) ?: return response

        val block = match.groupValues[1].trim()
        val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Parse medication name
        val medLine = lines.firstOrNull { it.startsWith("medication:", ignoreCase = true) }
        val medName = medLine?.substringAfter(":")?.trim() ?: return response

        // Parse time changes
        data class TimeChange(val oldHour: Int, val oldMin: Int, val newHour: Int, val newMin: Int)
        val timeChanges = mutableListOf<TimeChange>()
        val timePattern = Regex("""old_time:\s*(\d{1,2}):(\d{2})\s*->\s*new_time:\s*(\d{1,2}):(\d{2})""")
        for (line in lines) {
            val tm = timePattern.find(line)
            if (tm != null) {
                timeChanges.add(
                    TimeChange(
                        tm.groupValues[1].toInt(), tm.groupValues[2].toInt(),
                        tm.groupValues[3].toInt(), tm.groupValues[4].toInt()
                    )
                )
            }
        }

        if (timeChanges.isEmpty()) return response

        // Look up medication in DB
        val medication = medicationRepository.findActiveMedicationByName(medName)
        if (medication == null) {
            Log.w("ChatViewModel", "Schedule update: medication '$medName' not found")
            return response.replace(match.value, "").trim()
        }

        // Get existing schedules
        val schedules = medicationRepository.getSchedulesForMedicationOnce(medication.id)

        // Apply each time change
        var updatedCount = 0
        for (change in timeChanges) {
            val schedule = schedules.firstOrNull { s ->
                s.timeHour == change.oldHour && s.timeMinute == change.oldMin
            }
            if (schedule != null) {
                val updated = schedule.copy(
                    timeHour = change.newHour,
                    timeMinute = change.newMin
                )
                medicationRepository.updateSchedule(updated)
                updatedCount++
            } else {
                Log.w("ChatViewModel", "Schedule not found for ${change.oldHour}:${change.oldMin}")
            }
        }

        // Reschedule WorkManager reminders if changes were made
        if (updatedCount > 0) {
            val ctx = getApplication<RxAideApplication>()
            ReminderScheduler.cancelRemindersForMedication(ctx, medication.id)
            val updatedSchedules = medicationRepository.getSchedulesForMedicationOnce(medication.id)
            ReminderScheduler.scheduleAllReminders(
                ctx, medication, updatedSchedules, medication.notificationSoundUri
            )
            Log.d("ChatViewModel", "Rescheduled $updatedCount reminder(s) for ${medication.name}")
        }

        // Strip action block from displayed message
        return response.replace(match.value, "").trim()
    }

    /**
     * Returns default schedule times based on frequency.
     */
    private fun getDefaultScheduleTimes(frequency: String): List<Pair<Int, Int>> {
        return when (frequency) {
            "Once daily" -> listOf(Pair(8, 0))
            "Twice daily" -> listOf(Pair(8, 0), Pair(21, 0))
            "Three times daily" -> listOf(Pair(8, 0), Pair(13, 0), Pair(21, 0))
            "Four times daily" -> listOf(Pair(8, 0), Pair(12, 0), Pair(17, 0), Pair(21, 0))
            "Weekly" -> listOf(Pair(13, 0))
            else -> listOf(Pair(8, 0))
        }
    }

    /**
     * Calculate an end date from a duration string like "2 months", "6 months", "1 week".
     * Returns null if the duration is empty, "Ongoing", or unparseable.
     */
    private fun calculateEndDate(duration: String): Long? {
        if (duration.isBlank() || duration.equals("Ongoing", ignoreCase = true) ||
            duration.equals("Continue", ignoreCase = true)) {
            return null
        }

        val cal = Calendar.getInstance()
        val lower = duration.lowercase().trim()

        try {
            val num = lower.filter { it.isDigit() }.toIntOrNull() ?: return null

            when {
                lower.contains("month") -> cal.add(Calendar.MONTH, num)
                lower.contains("week") -> cal.add(Calendar.WEEK_OF_YEAR, num)
                lower.contains("day") -> cal.add(Calendar.DAY_OF_YEAR, num)
                lower.contains("year") -> cal.add(Calendar.YEAR, num)
                else -> return null
            }
            return cal.timeInMillis
        } catch (e: Exception) {
            return null
        }
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
            chatRepository.clearChat()
            _quickAction.value = null
        }
    }
}
