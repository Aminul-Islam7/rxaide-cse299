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

    companion object {
        private const val TAG = "ChatViewModel"

        // Patterns the user might type to confirm scheduling
        private val CONFIRMATION_PATTERNS = listOf(
            "yes", "yeah", "yep", "yup", "sure", "ok", "okay",
            "confirm", "continue", "go ahead", "create", "schedule",
            "correct", "looks good", "all good", "that's right", "right",
            "please create", "create reminders", "set reminders",
            "looks correct", "everything is correct", "it's correct",
            "yes please", "do it"
        )
    }

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

            // Check if this is a confirmation message and a Confirm button was available
            val isConfirmation = isConfirmationMessage(text)
            val wasWaitingForConfirmation = isConfirmation && hasPrescriptionSummaryInHistory()

            // Insert user message
            chatRepository.insertMessage(
                ChatMessage(
                    content = text,
                    isFromUser = true
                )
            )

            if (wasWaitingForConfirmation) {
                // User typed a confirmation instead of clicking the button — trigger scheduling
                performScheduling()
            } else {
                // Normal text message — generate bot reply
                generateBotReply(text, imageUri = null)
            }
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

            performScheduling()
        }
    }

    /**
     * Shared scheduling logic used by both button click and text confirmation.
     */
    private suspend fun performScheduling() {
        _isTyping.value = true

        if (!isApiKeyConfigured) {
            chatRepository.insertMessage(
                ChatMessage(
                    content = "⚠️ **Gemini API key not configured.** Cannot create automatic schedules.",
                    isFromUser = false
                )
            )
            _isTyping.value = false
            return
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
            return
        }

        // Create Medication + Schedule entries in the database
        var successCount = 0
        val createdMeds = mutableListOf<String>()

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

                // Schedule WorkManager reminders
                try {
                    val ctx = getApplication<RxAideApplication>()
                    val insertedMed = medication.copy(id = medId)
                    ReminderScheduler.scheduleAllReminders(ctx, insertedMed, schedules, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to schedule reminders for ${med.name}", e)
                }

                successCount++
                createdMeds.add(med.name)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create medication: ${med.name}", e)
            }
        }

        // Verify the medications actually exist in the database
        val verifiedCount = verifyMedicationsCreated(createdMeds)

        // Send confirmation message
        val reminderDetails = buildString {
            appendLine("✅ **Reminders created!** I've set up $verifiedCount medication reminder(s) with default timing:")
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

        // Parse and execute ALL action blocks, then get the display text
        val displayContent = parseAndExecuteAllActions(replyContent)

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

        // Re-show Confirm button after correction conversations —
        // detect when AI asks for confirmation or mentions scheduling
        if (text != null && imageUri == null) {
            val lower = displayContent.lowercase()
            val looksLikeConfirmationRequest = lower.contains("confirm & schedule") ||
                lower.contains("confirm and schedule") ||
                lower.contains("create reminders") ||
                lower.contains("create the schedules") ||
                lower.contains("set up reminders") ||
                lower.contains("everything correct") ||
                lower.contains("looks correct") ||
                (lower.contains("correct") && lower.contains("?")) ||
                (lower.contains("schedule") && lower.contains("?"))

            if (looksLikeConfirmationRequest && hasPrescriptionSummaryInHistory()) {
                _quickAction.value = QuickActionType.CONFIRM_SCHEDULE
            }

            // Safety net: if the bot CLAIMS it created schedules but didn't,
            // actually trigger scheduling so the user isn't lied to.
            val claimsScheduled = lower.contains("reminders have been") ||
                lower.contains("reminders set up") ||
                lower.contains("schedules created") ||
                lower.contains("schedules have been") ||
                lower.contains("set up reminders for") ||
                lower.contains("i've set up") ||
                lower.contains("i've created") ||
                (lower.contains("creating medication reminders") && lower.contains("done"))

            if (claimsScheduled && hasPrescriptionSummaryInHistory()) {
                // Bot said it did it but it didn't — actually do it now
                performScheduling()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ACTION BLOCK PARSING & EXECUTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Master method: parses all action block types from the AI response,
     * executes them, and returns the display text with action blocks stripped.
     */
    private suspend fun parseAndExecuteAllActions(response: String): String {
        var result = response

        // 1. Schedule updates
        result = parseAndExecuteScheduleUpdates(result)

        // 2. Medication updates
        result = parseAndExecuteMedicationUpdates(result)

        // 3. Medication deletions
        result = parseAndExecuteMedicationDeletes(result)

        return result.trim()
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

        var result = response
        val matches = pattern.findAll(response).toList()

        for (match in matches) {
            val block = match.groupValues[1].trim()
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }

            // Parse medication name
            val medLine = lines.firstOrNull { it.startsWith("medication:", ignoreCase = true) }
            val medName = medLine?.substringAfter(":")?.trim() ?: continue

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

            if (timeChanges.isEmpty()) continue

            // Look up medication in DB
            val medication = medicationRepository.findActiveMedicationByName(medName)
            if (medication == null) {
                Log.w(TAG, "Schedule update: medication '$medName' not found")
                result = result.replace(match.value, "").trim()
                continue
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
                    Log.w(TAG, "Schedule not found for ${change.oldHour}:${change.oldMin}")
                }
            }

            // Reschedule WorkManager reminders if changes were made
            if (updatedCount > 0) {
                try {
                    val ctx = getApplication<RxAideApplication>()
                    ReminderScheduler.cancelRemindersForMedication(ctx, medication.id)
                    val updatedSchedules = medicationRepository.getSchedulesForMedicationOnce(medication.id)
                    ReminderScheduler.scheduleAllReminders(
                        ctx, medication, updatedSchedules, medication.notificationSoundUri
                    )
                    Log.d(TAG, "Rescheduled $updatedCount reminder(s) for ${medication.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to reschedule reminders for ${medication.name}", e)
                }
            }

            // Strip action block from displayed message
            result = result.replace(match.value, "").trim()
        }

        return result
    }

    /**
     * Parses [MEDICATION_UPDATE] blocks and applies field changes to the database.
     */
    private suspend fun parseAndExecuteMedicationUpdates(response: String): String {
        val pattern = Regex(
            """\[MEDICATION_UPDATE]\s*\n(.*?)\[/MEDICATION_UPDATE]""",
            RegexOption.DOT_MATCHES_ALL
        )

        var result = response
        val matches = pattern.findAll(response).toList()

        for (match in matches) {
            val block = match.groupValues[1].trim()
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }

            // Parse medication name
            val medLine = lines.firstOrNull { it.startsWith("medication:", ignoreCase = true) }
            val medName = medLine?.substringAfter(":")?.trim() ?: continue

            // Look up medication in DB
            val medication = medicationRepository.findActiveMedicationByName(medName)
            if (medication == null) {
                Log.w(TAG, "Medication update: '$medName' not found")
                result = result.replace(match.value, "").trim()
                continue
            }
            val med: Medication = medication // non-null smart cast

            // Parse field changes (everything except the medication: line)
            var updated = med
            for (line in lines) {
                if (line.startsWith("medication:", ignoreCase = true)) continue
                val colonIdx = line.indexOf(':')
                if (colonIdx < 0) continue

                val field = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()

                updated = when (field) {
                    "name" -> updated.copy(name = value)
                    "dosage" -> updated.copy(dosage = value)
                    "dosageunit" -> updated.copy(dosageUnit = value)
                    "form" -> updated.copy(form = value)
                    "frequency" -> updated.copy(frequency = value)
                    "mealrelation" -> updated.copy(mealRelation = value)
                    "instructions" -> updated.copy(instructions = value)
                    "notes" -> updated.copy(notes = value)
                    "duration" -> updated.copy(duration = value)
                    else -> {
                        Log.w(TAG, "Unknown field in MEDICATION_UPDATE: $field")
                        updated
                    }
                }
            }

            // Save to database
            medicationRepository.updateMedication(updated.copy(updatedAt = System.currentTimeMillis()))
            Log.d(TAG, "Updated medication '${medication.name}' -> '${updated.name}'")

            // Strip action block
            result = result.replace(match.value, "").trim()
        }

        return result
    }

    /**
     * Parses [MEDICATION_DELETE] blocks and removes medications from the database.
     */
    private suspend fun parseAndExecuteMedicationDeletes(response: String): String {
        val pattern = Regex(
            """\[MEDICATION_DELETE]\s*\n(.*?)\[/MEDICATION_DELETE]""",
            RegexOption.DOT_MATCHES_ALL
        )

        var result = response
        val matches = pattern.findAll(response).toList()

        for (match in matches) {
            val block = match.groupValues[1].trim()
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }

            val medLine = lines.firstOrNull { it.startsWith("medication:", ignoreCase = true) }
            val medName = medLine?.substringAfter(":")?.trim() ?: continue

            val medication = medicationRepository.findActiveMedicationByName(medName)
            if (medication == null) {
                Log.w(TAG, "Medication delete: '$medName' not found")
                result = result.replace(match.value, "").trim()
                continue
            }

            // Cancel reminders first
            try {
                val ctx = getApplication<RxAideApplication>()
                ReminderScheduler.cancelRemindersForMedication(ctx, medication.id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel reminders for ${medication.name}", e)
            }

            // Delete schedules and medication
            medicationRepository.deleteSchedulesForMedication(medication.id)
            medicationRepository.deleteMedication(medication)
            Log.d(TAG, "Deleted medication '${medication.name}'")

            result = result.replace(match.value, "").trim()
        }

        return result
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a message looks like a confirmation (user saying "yes", "confirm", etc.)
     */
    private fun isConfirmationMessage(text: String): Boolean {
        val lower = text.trim().lowercase()
        return CONFIRMATION_PATTERNS.any { pattern ->
            lower == pattern || lower.startsWith("$pattern ") || lower.startsWith("$pattern,") ||
                lower.startsWith("$pattern.") || lower.startsWith("$pattern!")
        }
    }

    /**
     * Check if there's a prescription summary anywhere in chat history
     * that hasn't been scheduled yet. Walks ALL messages backward:
     * - If "Reminders created" is found first → already scheduled → false
     * - If "Prescription Summary" is found first → needs scheduling → true
     * This correctly handles the correction flow where intermediate bot
     * messages exist between the scan and the user's confirmation.
     */
    private suspend fun hasPrescriptionSummaryInHistory(): Boolean {
        val messages = chatRepository.getAllMessagesList()
        if (messages.isEmpty()) return false

        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            if (msg.isFromUser) continue

            // If we find a scheduling confirmation first → already done
            if (msg.content.contains("Reminders created", ignoreCase = true)) {
                return false
            }

            // If we find a prescription summary → not yet scheduled
            if (msg.content.contains("Prescription Summary", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Verify that medications with the given names actually exist in the database.
     */
    private suspend fun verifyMedicationsCreated(names: List<String>): Int {
        var verified = 0
        for (name in names) {
            val med = medicationRepository.findActiveMedicationByName(name)
            if (med != null) {
                verified++
            } else {
                Log.w(TAG, "Verification failed: medication '$name' not found after insert")
            }
        }
        return verified
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
            duration.equals("Continue", ignoreCase = true)
        ) {
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
