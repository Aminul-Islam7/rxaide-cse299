package com.example.rxaide.ui.screens

import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import com.example.rxaide.ui.theme.AlertRed
import com.example.rxaide.ui.theme.HealingGreen
import com.example.rxaide.ui.theme.MedicalBlue
import com.example.rxaide.ui.theme.MedicalBlueDark
import com.example.rxaide.ui.util.medicationFormIcon
import com.example.rxaide.viewmodel.MedicationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Reusable option lists
private val formOptions = listOf("", "Tablet", "Capsule", "Syrup", "Injection", "Drops", "Cream", "Inhaler", "Nasal Spray", "Other")
private val dosageUnitOptions = listOf("", "mg", "ml", "mcg", "g", "tablet", "capsule", "drop", "puff")
private val frequencyOptions = listOf("Once daily", "Twice daily", "Three times daily", "Four times daily", "Weekly", "As needed")
private val mealRelationOptions = listOf("Before meal", "After meal", "With meal", "No relation")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationDetailScreen(
    viewModel: MedicationViewModel,
    medicationId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit = {}
) {
    val medication by viewModel.getMedicationById(medicationId).collectAsState(initial = null)
    val schedules by viewModel.getSchedulesForMedication(medicationId).collectAsState(initial = emptyList())
    val context = LocalContext.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // ── Edit dialog state ──────────────────────────────────────────────
    var editingField by remember { mutableStateOf<String?>(null) }
    var editTextValue by remember { mutableStateOf("") }
    var editChipValue by remember { mutableStateOf("") }

    // Date picker state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Time picker state
    var showTimePicker by remember { mutableStateOf(false) }
    val editScheduleTimes = remember { mutableStateListOf<Pair<Int, Int>>() }
    var schedulesInitialized by remember { mutableStateOf(false) }

    // Notification sound
    var editSoundUri by remember { mutableStateOf<Uri?>(null) }
    var editSoundName by remember { mutableStateOf("Default") }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            editSoundUri = uri
            val ringtone = RingtoneManager.getRingtone(context, uri)
            editSoundName = ringtone?.getTitle(context) ?: "Custom"
        } else {
            editSoundUri = null
            editSoundName = "Default"
        }
        // Save immediately
        medication?.let { med ->
            val updated = med.copy(notificationSoundUri = uri?.toString())
            viewModel.updateMedicationWithSchedules(
                medication = updated,
                schedules = schedules.map { Schedule(medicationId = med.id, timeHour = it.timeHour, timeMinute = it.timeMinute) },
                soundUri = uri?.toString(),
                onComplete = {}
            )
        }
    }

    // Initialize sound info from medication
    LaunchedEffect(medication) {
        medication?.notificationSoundUri?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                editSoundUri = uri
                val ringtone = RingtoneManager.getRingtone(context, uri)
                editSoundName = ringtone?.getTitle(context) ?: "Custom"
            } catch (_: Exception) {}
        }
    }

    // Initialize schedule times
    LaunchedEffect(schedules) {
        if (!schedulesInitialized && schedules.isNotEmpty()) {
            editScheduleTimes.clear()
            schedules.forEach { s -> editScheduleTimes.add(Pair(s.timeHour, s.timeMinute)) }
            schedulesInitialized = true
        }
    }

    // Helper to save a quick field edit
    fun saveFieldEdit(med: Medication, field: String, value: String) {
        val updated = when (field) {
            "name" -> med.copy(name = value.trim())
            "dosage" -> {
                val normalizedDosage = value.trim()
                med.copy(
                    dosage = normalizedDosage,
                    dosageUnit = if (normalizedDosage.isBlank()) "" else med.dosageUnit
                )
            }
            "dosageUnit" -> med.copy(dosageUnit = value)
            "form" -> med.copy(form = value)
            "frequency" -> med.copy(frequency = value)
            "mealRelation" -> med.copy(mealRelation = value)
            "instructions" -> med.copy(instructions = value.trim())
            "notes" -> med.copy(notes = value.trim())
            "duration" -> med.copy(duration = value.trim())
            else -> med
        }
        viewModel.updateMedicationWithSchedules(
            medication = updated,
            schedules = schedules.map { Schedule(medicationId = med.id, timeHour = it.timeHour, timeMinute = it.timeMinute) },
            soundUri = editSoundUri?.toString(),
            onComplete = {}
        )
    }

    fun saveSchedules(med: Medication) {
        val newSchedules = editScheduleTimes.map { (h, m) ->
            Schedule(medicationId = med.id, timeHour = h, timeMinute = m)
        }
        viewModel.updateMedicationWithSchedules(
            medication = med,
            schedules = newSchedules,
            soundUri = editSoundUri?.toString(),
            onComplete = {}
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        medication?.name ?: "Medication Detail",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = AlertRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        medication?.let { med ->
            val isCompleted = med.endDate != null && med.endDate < System.currentTimeMillis()
            val statusText = if (med.isActive && !isCompleted) "Active" else "Completed"
            val isActive = med.isActive && !isCompleted

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Card with form-specific icon
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MedicalBlue, MedicalBlueDark)
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                medicationFormIcon(med.form),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val headerMeta = buildList {
                                if (med.dosage.isNotBlank()) {
                                    add(
                                        buildString {
                                            append(med.dosage)
                                            if (med.dosageUnit.isNotBlank()) append(" ${med.dosageUnit}")
                                        }
                                    )
                                }
                                if (med.form.isNotBlank()) {
                                    add(med.form)
                                }
                            }.joinToString(" • ")

                            Text(
                                text = med.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (headerMeta.isNotBlank()) {
                                Text(
                                    text = headerMeta,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isActive) HealingGreen.copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.2f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tap to edit hint
                Text(
                    text = "Tap any field to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                // Editable detail rows
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Name
                    EditableDetailRow(
                        icon = Icons.Default.Edit,
                        label = "Name",
                        value = med.name,
                        onClick = {
                            editingField = "name"
                            editTextValue = med.name
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Dosage & Unit
                    EditableDetailRow(
                        icon = Icons.Default.Info,
                        label = "Dosage",
                        value = buildString {
                            if (med.dosage.isNotBlank()) {
                                append(med.dosage)
                                if (med.dosageUnit.isNotBlank()) append(" ${med.dosageUnit}")
                            } else {
                                append("Not set")
                            }
                        },
                        onClick = {
                            editingField = "dosage"
                            editTextValue = med.dosage
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Dosage Unit
                    EditableDetailRow(
                        icon = Icons.Default.Info,
                        label = "Dosage Unit",
                        value = med.dosageUnit.ifBlank { "Not set" },
                        onClick = {
                            editingField = "dosageUnit"
                            editChipValue = med.dosageUnit.ifBlank { "mg" }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Form
                    EditableDetailRow(
                        icon = medicationFormIcon(med.form),
                        label = "Form",
                        value = med.form.ifBlank { "Not set" },
                        onClick = {
                            editingField = "form"
                            editChipValue = med.form
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Frequency
                    EditableDetailRow(
                        icon = Icons.Default.Schedule,
                        label = "Frequency",
                        value = med.frequency.ifBlank { "Not set" },
                        onClick = {
                            editingField = "frequency"
                            editChipValue = med.frequency
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Meal Relation
                    EditableDetailRow(
                        icon = Icons.Default.Restaurant,
                        label = "Meal Relation",
                        value = med.mealRelation.ifBlank { "No relation" },
                        onClick = {
                            editingField = "mealRelation"
                            editChipValue = med.mealRelation
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Instructions
                    EditableDetailRow(
                        icon = Icons.Default.Info,
                        label = "Instructions",
                        value = med.instructions.ifBlank { "None" },
                        onClick = {
                            editingField = "instructions"
                            editTextValue = med.instructions
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Start Date
                    EditableDetailRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "Start Date",
                        value = dateFormat.format(Date(med.startDate)),
                        onClick = { showStartDatePicker = true }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // End Date
                    EditableDetailRow(
                        icon = Icons.Default.EventAvailable,
                        label = "End Date",
                        value = med.endDate?.let { dateFormat.format(Date(it)) } ?: "Ongoing",
                        onClick = { showEndDatePicker = true }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Duration
                    if (med.duration.isNotBlank()) {
                        EditableDetailRow(
                            icon = Icons.Default.CalendarMonth,
                            label = "Duration",
                            value = med.duration,
                            onClick = {
                                editingField = "duration"
                                editTextValue = med.duration
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Notes
                    EditableDetailRow(
                        icon = Icons.AutoMirrored.Filled.Notes,
                        label = "Notes",
                        value = med.notes.ifBlank { "None" },
                        onClick = {
                            editingField = "notes"
                            editTextValue = med.notes
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Notification Sound
                    EditableDetailRow(
                        icon = Icons.Default.Notifications,
                        label = "Notification Sound",
                        value = editSoundName,
                        onClick = {
                            val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                editSoundUri?.let {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
                                }
                            }
                            ringtoneLauncher.launch(intent)
                        }
                    )
                }

                // Reminder Schedule (editable inline)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Reminder Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add time",
                            tint = MedicalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editScheduleTimes.isEmpty() && schedules.isEmpty()) {
                        Text(
                            "No reminders set. Tap + to add.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    val timesToShow = if (editScheduleTimes.isNotEmpty()) editScheduleTimes
                    else schedules.map { Pair(it.timeHour, it.timeMinute) }

                    timesToShow.forEachIndexed { index, (hour, minute) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MedicalBlue.copy(alpha = 0.06f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MedicalBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                val amPm = if (hour < 12) "AM" else "PM"
                                val displayHour = if (hour == 0) 12
                                else if (hour > 12) hour - 12
                                else hour
                                Text(
                                    text = String.format("%d:%02d %s", displayHour, minute, amPm),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (editScheduleTimes.isNotEmpty()) {
                                            editScheduleTimes.removeAt(index)
                                            saveSchedules(med)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = AlertRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── Edit Dialogs ──────────────────────────────────────────────

            // Text field edit dialog (name, dosage, instructions, notes, duration)
            if (editingField in listOf("name", "dosage", "instructions", "notes", "duration")) {
                val fieldLabel = when (editingField) {
                    "name" -> "Medicine Name"
                    "dosage" -> "Dosage"
                    "instructions" -> "Special Instructions"
                    "notes" -> "Notes"
                    "duration" -> "Duration"
                    else -> ""
                }
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text("Edit $fieldLabel") },
                    text = {
                        OutlinedTextField(
                            value = editTextValue,
                            onValueChange = { editTextValue = it },
                            label = { Text(fieldLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = editingField != "instructions" && editingField != "notes"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            saveFieldEdit(med, editingField!!, editTextValue)
                            editingField = null
                        }) { Text("Save", color = MedicalBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) { Text("Cancel") }
                    }
                )
            }

            // Chip selection dialog (form, frequency, mealRelation)
            if (editingField in listOf("form", "frequency", "mealRelation")) {
                val options = when (editingField) {
                    "form" -> formOptions
                    "frequency" -> frequencyOptions
                    "mealRelation" -> mealRelationOptions
                    else -> emptyList()
                }
                val fieldLabel = when (editingField) {
                    "form" -> "Medicine Form"
                    "frequency" -> "Frequency"
                    "mealRelation" -> "Meal Relation"
                    else -> ""
                }
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text("Select $fieldLabel") },
                    text = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            options.forEach { option ->
                                FilterChip(
                                    selected = editChipValue == option,
                                    onClick = { editChipValue = option },
                                    label = { Text(if (option.isBlank()) "Not set" else option) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MedicalBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            saveFieldEdit(med, editingField!!, editChipValue)
                            editingField = null
                        }) { Text("Save", color = MedicalBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) { Text("Cancel") }
                    }
                )
            }

            // Dosage Unit dialog — shown when dosage is being edited
            if (editingField == "dosageUnit") {
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text("Select Dosage Unit") },
                    text = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dosageUnitOptions.forEach { option ->
                                FilterChip(
                                    selected = editChipValue == option,
                                    onClick = { editChipValue = option },
                                    label = { Text(if (option.isBlank()) "Not set" else option) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MedicalBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val safeUnit = if (med.dosage.isBlank()) "" else editChipValue
                            saveFieldEdit(med, "dosageUnit", safeUnit)
                            editingField = null
                        }) { Text("Save", color = MedicalBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingField = null }) { Text("Cancel") }
                    }
                )
            }

            // Date Pickers
            if (showStartDatePicker) {
                val state = rememberDatePickerState(initialSelectedDateMillis = med.startDate)
                DatePickerDialog(
                    onDismissRequest = { showStartDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { newDate ->
                                val updated = med.copy(startDate = newDate)
                                viewModel.updateMedicationWithSchedules(
                                    medication = updated,
                                    schedules = schedules.map { Schedule(medicationId = med.id, timeHour = it.timeHour, timeMinute = it.timeMinute) },
                                    soundUri = editSoundUri?.toString(),
                                    onComplete = {}
                                )
                            }
                            showStartDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = state)
                }
            }

            if (showEndDatePicker) {
                val state = rememberDatePickerState(initialSelectedDateMillis = med.endDate ?: System.currentTimeMillis())
                DatePickerDialog(
                    onDismissRequest = { showEndDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val updated = med.copy(endDate = state.selectedDateMillis)
                            viewModel.updateMedicationWithSchedules(
                                medication = updated,
                                schedules = schedules.map { Schedule(medicationId = med.id, timeHour = it.timeHour, timeMinute = it.timeMinute) },
                                soundUri = editSoundUri?.toString(),
                                onComplete = {}
                            )
                            showEndDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            // Clear end date = ongoing
                            val updated = med.copy(endDate = null)
                            viewModel.updateMedicationWithSchedules(
                                medication = updated,
                                schedules = schedules.map { Schedule(medicationId = med.id, timeHour = it.timeHour, timeMinute = it.timeMinute) },
                                soundUri = editSoundUri?.toString(),
                                onComplete = {}
                            )
                            showEndDatePicker = false
                        }) { Text("Clear / Ongoing") }
                    }
                ) {
                    DatePicker(state = state)
                }
            }

            // Time Picker
            if (showTimePicker) {
                val timeState = rememberTimePickerState(initialHour = 8, initialMinute = 0)
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Add Reminder Time") },
                    text = { TimePicker(state = timeState) },
                    confirmButton = {
                        TextButton(onClick = {
                            editScheduleTimes.add(Pair(timeState.hour, timeState.minute))
                            saveSchedules(med)
                            showTimePicker = false
                        }) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    }
                )
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Delete dialog
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                icon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = AlertRed
                    )
                },
                title = { Text("Delete Medication?") },
                text = {
                    Text("This will permanently delete this medication and its schedule. Your dose history records will be preserved.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteMedicationById(medicationId)
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text("Delete", color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * A detail row that looks tappable — has a subtle edit indicator.
 */
@Composable
private fun EditableDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MedicalBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
