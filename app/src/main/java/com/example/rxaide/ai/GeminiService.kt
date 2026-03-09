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
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class representing a single medication extracted from a prescription.
 */
data class ExtractedMedication(
    val name: String,
    val dosage: String,
    val dosageUnit: String,
    val form: String,
    val frequency: String,
    val mealRelation: String,
    val instructions: String,
    val duration: String,
    val notes: String
)

class GeminiService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiService"
        private const val MODEL_NAME = "gemini-2.5-flash"

        // ── System prompt: who RxAide is and how it should behave ──
        private val SYSTEM_PROMPT = """
You are **RxAide**, an AI medication assistant embedded in a Bangladeshi mobile app.

━━━ CORE RULES ━━━
• You help users READ, ORGANIZE, and UNDERSTAND prescriptions. You do NOT diagnose, prescribe, or give medical advice.
• If a dosage looks unusual or potentially dangerous, include a ⚠️ note suggesting the user verify with their doctor.
• Be concise. No filler, no pleasantries. Use bullet points and the structured format below.
• Use chat history for context. Don't repeat information you've already provided.

━━━ PRESCRIPTION EXTRACTION RULES ━━━
When you receive a prescription image, follow these rules strictly:

1. **EXTRACT ONLY MEDICATION DATA.** Do NOT extract or mention:
   - Patient name, age, gender, weight, blood pressure, or any personal/demographic information
   - Doctor name, registration number, clinic address, hospital name, or any doctor/clinic information
   - Date of prescription, prescription ID, or any non-medication metadata
   → Focus EXCLUSIVELY on the medications and their details.

2. **READ THE ENTIRE PRESCRIPTION THOROUGHLY.**
   - Scan the FULL image from top to bottom, left to right, covering every section — including margins, corners, and any text written sideways or at angles.
   - For handwritten prescriptions: examine each line character by character. Look at the pen strokes carefully. Consider common Bangladeshi medicine names when deciphering unclear handwriting.
   - Re-read the prescription a second time to catch any medications or details you may have missed on the first pass.
   - Cross-verify: ensure each medication you extract actually appears in the image, and that you haven't missed any.

3. **DECODE BANGLADESH-STYLE DOSAGE NOTATION:**
   These indicate doses per time of day as (Morning + Noon/Afternoon + Night/Evening):
   • "1+1+1" → 1 in morning, 1 at noon, 1 at night → frequency = "Three times daily"
   • "1+0+1" → 1 in morning, 1 at night → frequency = "Twice daily"
   • "1+1+0" → 1 in morning, 1 at noon → frequency = "Twice daily"
   • "0+0+1" → 1 at night only → frequency = "Once daily"
   • "0+1+0" → 1 at noon only → frequency = "Once daily"
   • "1+0+0" → 1 in morning only → frequency = "Once daily"
   • "2+1+0" → 2 in morning, 1 at noon → frequency = "Three times daily"
   • "½+0+½" → half in morning, half at night → frequency = "Twice daily"
   • Numbers > 1 mean multiple doses at that time
   • Bengali digits: ০=0, ১=1, ২=2, ৩=3, ৪=4, ৫=5, ৬=6, ৭=7, ৮=8, ৯=9

4. **DURATION — READ CAREFULLY (ESPECIALLY BANGLA TEXT):**
   Doctors in Bangladesh frequently write duration in Bangla. You MUST recognize and translate:
   • "দৈনিক" = Daily
   • "১ সপ্তাহ" or "১ সপ্তা" = 1 week
   • "২ সপ্তাহ" = 2 weeks
   • "১ মাস" = 1 month
   • "২ মাস" or "২মাস" = 2 months
   • "৩ মাস" = 3 months
   • "৬ মাস" or "৬মাস" = 6 months
   • "চলবে" = Ongoing / Continue
   • Look for duration text NEXT TO or BELOW the dosage notation — it's often written in the same line or on the line immediately after
   • NEVER skip or omit the duration. If it is written in the Rx, you MUST include it.

5. **MULTI-LANGUAGE SUPPORT:**
   Bangladeshi prescriptions often mix English medicine names with Bangla (বাংলা) instructions.
   - Always output medicine names in English
   - Translate any Bangla text to English

6. **MEDICINE VERIFICATION:**
   Use search to verify that each extracted medicine name is a real, commercially available medication. Correct obvious OCR/handwriting misreads (e.g., "Omeprazol" → "Omeprazole").

━━━ FIELD DEFINITIONS (MUST MATCH APP's MEDICATION FORM) ━━━
Use ONLY these exact values for each field:

• **Form** — one of: "Tablet", "Capsule", "Syrup", "Injection", "Drops", "Cream", "Inhaler", "Nasal Spray", "Other"
• **Dosage** — the numeric strength value only (e.g., "500", "10", "1%", "0.5")
• **Unit** — one of: "mg", "ml", "mcg", "g", "%" (use whatever matches the strength)
• **Frequency** — one of: "Once daily", "Twice daily", "Three times daily", "Four times daily", "Weekly", "As needed"
• **Meal Relation** — one of: "Before meal", "After meal", "With meal", "No relation"
  → This is ONLY about meals. Nasal sprays, creams, drops, and inhalers should default to "No relation" unless explicitly stated.
• **Duration** — output in English even if written in Bangla. Examples: "2 months", "6 months", "1 week", "14 days", "Ongoing"
• **Instructions** — any OTHER special instructions beyond meal relation (e.g., "Take with water", "Avoid alcohol", "For external use only", "Apply on affected area"). If there are none, omit this field entirely.
• **Notes** — any warnings or side effect info mentioned in the Rx for this specific medicine (e.g., "May cause temporary increase in itching")

━━━ REQUIRED OUTPUT FORMAT FOR PRESCRIPTIONS ━━━
Use this EXACT format for every prescription. Omit any field whose value is not specified in the Rx:

---
**💊 Prescription Summary**

**1. [Medicine Name] [Dosage][Unit]** — *[Form]*
   - **Frequency:** [frequency value, e.g., "Twice daily"]
   - **Schedule:** [the raw dose notation AND decoded meaning, e.g., "1+0+1 (1 in the morning, 1 at night)"]
   - **Duration:** [e.g., "2 months" / "6 months" / "Ongoing"]
   - **Meal Relation:** [e.g., "After meal" / "Before meal" / "No relation"]
   - **Instructions:** [only if there are special instructions beyond meal relation]
   - **Notes:** [only if there are warnings/side effects mentioned]

*(repeat for ALL medications in the prescription)*

**📝 Additional Notes:** [any general instructions from the Rx not specific to a single medicine, e.g., "Follow-up after 1 month", "Keep area dry"]
---

After listing all medications, ask: *"Please review the above. If everything looks correct, tap **✅ Confirm & Schedule** below. If anything is misread, let me know what to change."*

━━━ SCHEDULING FLOW ━━━
When the user confirms the prescription data is correct (says "yes", "correct", "confirm", "continue", taps the confirm button, etc.):
- Respond with: "✅ Creating medication reminders... Done! I've set up reminders for all [N] medications with default meal-based timing (breakfast ~8:00 AM, lunch ~1:00 PM, dinner ~9:00 PM). You can adjust the reminder times in **My Medications** to match your personal schedule."
- Do NOT re-list the medications again.

When the user wants to modify a previously extracted field:
- Acknowledge the change, state what was changed, and ask if everything else is correct now.

━━━ GENERAL CHAT ━━━
For non-prescription questions (medication info, side effects, interactions, etc.):
- Answer concisely and accurately
- Always recommend consulting a doctor for serious concerns
- Use search to verify medication information when needed
""".trimIndent()

        // ── Detailed prompt sent along with the prescription image ──
        private val IMAGE_ANALYSIS_PROMPT = """
Analyze this prescription image carefully using these steps:

**PASS 1 — Full Scan:** Look at the entire prescription from top to bottom, left to right, including margins and corners. Identify every medication entry, every dosage notation, every duration marking. Pay special attention to Bangla text (বাংলা) — durations are very often written in Bengali (e.g., ২মাস, ৬মাস, ১ সপ্তাহ, চলবে).

**PASS 2 — Detailed Extraction:** Go through each medication line one by one:
  - Read the medicine name character by character (especially for handwritten text)
  - Identify the dosage strength (mg, ml, %, etc.)
  - Identify the form (Tab. = Tablet, Cap. = Capsule, N/spray = Nasal Spray, N/drop = Drops, etc.)
  - Extract the dosage schedule notation (e.g., 1+0+1, ১+০+১)
  - Extract the DURATION — look carefully next to or below the dosage line for Bangla text indicating weeks/months
  - Note meal relation if specified (খাওয়ার পরে = After meal, খাওয়ার আগে = Before meal)
  - Note any special instructions

**PASS 3 — Cross-Verify:** Count the total medications in the image. Re-check to ensure your count matches. Verify no medication was skipped. Verify each medicine name is a real, commercially available medication via search. Double-check all durations are captured.

Extract ONLY the medication data. Do NOT extract patient name, age, doctor info, dates, or any non-medication details. Use the structured output format from your instructions.
""".trimIndent()

        // ── Prompt to ask Gemini to output structured JSON for scheduling ──
        private val SCHEDULE_JSON_PROMPT = """
Based on the prescription data from our conversation, output a JSON array of all medications. Use this EXACT structure — no extra text, no markdown, just the raw JSON array:

[
  {
    "name": "Medicine Name",
    "dosage": "500",
    "dosageUnit": "mg",
    "form": "Tablet",
    "frequency": "Twice daily",
    "mealRelation": "After meal",
    "instructions": "",
    "duration": "2 months",
    "notes": "",
    "scheduleTimes": [
      {"hour": 8, "minute": 0},
      {"hour": 21, "minute": 0}
    ]
  }
]

Rules for scheduleTimes:
- If frequency = "Once daily" and schedule has "morning" or "1+0+0": use [{"hour": 8, "minute": 0}]
- If frequency = "Once daily" and schedule has "noon" or "0+1+0": use [{"hour": 13, "minute": 0}]
- If frequency = "Once daily" and schedule has "night" or "0+0+1": use [{"hour": 21, "minute": 0}]
- If frequency = "Twice daily" and schedule has "1+0+1": use [{"hour": 8, "minute": 0}, {"hour": 21, "minute": 0}]
- If frequency = "Twice daily" and schedule has "1+1+0": use [{"hour": 8, "minute": 0}, {"hour": 13, "minute": 0}]
- If frequency = "Three times daily": use [{"hour": 8, "minute": 0}, {"hour": 13, "minute": 0}, {"hour": 21, "minute": 0}]
- If frequency = "Weekly": use [{"hour": 13, "minute": 0}]
- Default: use [{"hour": 8, "minute": 0}]

For "mealRelation": use EXACTLY one of: "Before meal", "After meal", "With meal", "No relation"
For "form": use EXACTLY one of: "Tablet", "Capsule", "Syrup", "Injection", "Drops", "Cream", "Inhaler", "Other"
For "frequency": use EXACTLY one of: "Once daily", "Twice daily", "Three times daily", "Four times daily", "Weekly", "As needed"

Output ONLY the JSON array. No other text.
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
                .temperature(0.1f)
                .topP(0.95f)
                .maxOutputTokens(2048)
                .thinkingConfig(
                    ThinkingConfig.builder()
                        .thinkingBudget(8192)
                        .build()
                )
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
     * Uses maximum thinking budget for thorough handwriting analysis.
     */
    suspend fun sendImageMessage(
        imageUri: String,
        chatHistory: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        try {
            val imageBytes = loadImageBytes(Uri.parse(imageUri))
                ?: return@withContext "⚠️ Could not load the image. Please try again."

            val contents = buildChatContents(chatHistory, IMAGE_ANALYSIS_PROMPT, imageBytes)

            val config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .tools(googleSearchTool)
                .temperature(0.1f)
                .topP(0.95f)
                .maxOutputTokens(4096)
                .thinkingConfig(
                    ThinkingConfig.builder()
                        .thinkingBudget(24576)
                        .build()
                )
                .build()

            val response = client.models.generateContent(MODEL_NAME, contents, config)
            response.text() ?: "I couldn't analyze this prescription. Please try with a clearer image."
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image message", e)
            "⚠️ Error: ${e.message ?: "Something went wrong. Check your internet connection and API key."}"
        }
    }

    /**
     * Request structured JSON output for creating medication schedules.
     * Called after the user confirms the prescription data is correct.
     */
    suspend fun requestMedicationJson(
        chatHistory: List<ChatMessage>
    ): List<ExtractedMedication>? = withContext(Dispatchers.IO) {
        try {
            val contents = buildChatContents(chatHistory, SCHEDULE_JSON_PROMPT, imageBytes = null)

            val config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .temperature(0.0f)
                .topP(0.95f)
                .maxOutputTokens(4096)
                .thinkingConfig(
                    ThinkingConfig.builder()
                        .thinkingBudget(4096)
                        .build()
                )
                .build()

            val response = client.models.generateContent(MODEL_NAME, contents, config)
            val text = response.text() ?: return@withContext null

            parseMedicationJson(text)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting medication JSON", e)
            null
        }
    }

    /**
     * Parse the JSON array of medications from Gemini's response.
     */
    private fun parseMedicationJson(text: String): List<ExtractedMedication>? {
        return try {
            // Extract JSON array from response (strip any markdown formatting)
            val jsonText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonArray = JSONArray(jsonText)
            val medications = mutableListOf<ExtractedMedication>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                medications.add(
                    ExtractedMedication(
                        name = obj.optString("name", ""),
                        dosage = obj.optString("dosage", ""),
                        dosageUnit = obj.optString("dosageUnit", "mg"),
                        form = obj.optString("form", "Tablet"),
                        frequency = obj.optString("frequency", "Once daily"),
                        mealRelation = obj.optString("mealRelation", "No relation"),
                        instructions = obj.optString("instructions", ""),
                        duration = obj.optString("duration", ""),
                        notes = obj.optString("notes", "")
                    )
                )
            }

            if (medications.isEmpty()) null else medications
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse medication JSON: $text", e)
            null
        }
    }

    /**
     * Parse schedule times from a JSON object's scheduleTimes array.
     */
    fun parseScheduleTimes(text: String): Map<Int, List<Pair<Int, Int>>> {
        val result = mutableMapOf<Int, List<Pair<Int, Int>>>()
        try {
            val jsonText = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonArray = JSONArray(jsonText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val timesArray = obj.optJSONArray("scheduleTimes")
                val times = mutableListOf<Pair<Int, Int>>()
                if (timesArray != null) {
                    for (j in 0 until timesArray.length()) {
                        val timeObj = timesArray.getJSONObject(j)
                        times.add(Pair(timeObj.getInt("hour"), timeObj.getInt("minute")))
                    }
                }
                result[i] = times
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse schedule times", e)
        }
        return result
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
