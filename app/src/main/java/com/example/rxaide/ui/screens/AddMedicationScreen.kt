package com.example.rxaide.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.rxaide.data.entity.Medication
import com.example.rxaide.data.entity.Schedule
import com.example.rxaide.ui.theme.HealingGreen
import com.example.rxaide.ui.theme.MedicalBlue
import com.example.rxaide.viewmodel.MedicationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMedicationScreen(
    viewModel: MedicationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit
) {
    // ── Form state ──────────────────────────────────────────────────────
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var dosageUnit by remember { mutableStateOf("mg") }
    var form by remember { mutableStateOf("Tablet") }
    var frequency by remember { mutableStateOf("Once daily") }
    var mealRelation by remember { mutableStateOf("No relation") }
    var instructions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var endDate by remember { mutableStateOf<Long?>(null) }

    val capturedImagePath by viewModel.capturedImagePath.collectAsState()

    // Schedule times (hour, minute pairs)
    val scheduleTimes = remember { mutableStateListOf<Pair<Int, Int>>() }
    var showTimePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Validation
    var nameError by remember { mutableStateOf(false) }
    var dosageError by remember { mutableStateOf(false) }

    // Dropdowns expanded state
    var dosageUnitExpanded by remember { mutableStateOf(false) }
    var frequencyExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ── Options ─────────────────────────────────────────────────────────
    val formOptions = listOf("Tablet", "Capsule", "Syrup", "Injection", "Drops", "Cream", "Inhaler", "Other")
    val dosageUnitOptions = listOf("mg", "ml", "mcg", "g", "tablet", "capsule", "drop", "puff")
    val frequencyOptions = listOf("Once daily", "Twice daily", "Three times daily", "Four times daily", "Weekly", "As needed")
    val mealRelationOptions = listOf("Before meal", "After meal", "With meal", "No relation")

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Add Medication", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ════════════════════════════════════════════════════════════
            // Prescription Image Card
            // ════════════════════════════════════════════════════════════
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (capturedImagePath != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = Uri.parse(capturedImagePath)),
                                contentDescription = "Captured Prescription",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { viewModel.setCapturedImagePath(null) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove Image", tint = Color.White)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, MedicalBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onNavigateToCamera),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    modifier = Modifier.size(40.dp),
                                    tint = MedicalBlue
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tap to scan prescription",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MedicalBlue
                                )
                                Text(
                                    "or fill in details manually below",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ════════════════════════════════════════════════════════════
            // SECTION: Medication Information
            // ════════════════════════════════════════════════════════════
            SectionTitle("Medication Information")
            Spacer(modifier = Modifier.height(12.dp))

            // Medication Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) nameError = false
                },
                label = { Text("Medication Name *") },
                placeholder = { Text("e.g., Amoxicillin") },
                leadingIcon = { Icon(Icons.Default.Medication, contentDescription = null, tint = MedicalBlue) },
                isError = nameError,
                supportingText = if (nameError) {{ Text("Medication name is required") }} else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MedicalBlue,
                    focusedLabelColor = MedicalBlue,
                    cursorColor = MedicalBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dosage Amount + Unit (side by side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dosage amount
                OutlinedTextField(
                    value = dosage,
                    onValueChange = {
                        dosage = it
                        if (it.isNotBlank()) dosageError = false
                    },
                    label = { Text("Dosage *") },
                    placeholder = { Text("e.g., 500") },
                    isError = dosageError,
                    supportingText = if (dosageError) {{ Text("Required") }} else null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MedicalBlue,
                        focusedLabelColor = MedicalBlue,
                        cursorColor = MedicalBlue
                    )
                )

                // Dosage unit dropdown
                ExposedDropdownMenuBox(
                    expanded = dosageUnitExpanded,
                    onExpandedChange = { dosageUnitExpanded = it },
                    modifier = Modifier.weight(0.7f)
                ) {
                    OutlinedTextField(
                        value = dosageUnit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dosageUnitExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedicalBlue,
                            focusedLabelColor = MedicalBlue
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dosageUnitExpanded,
                        onDismissRequest = { dosageUnitExpanded = false }
                    ) {
                        dosageUnitOptions.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = {
                                    dosageUnit = unit
                                    dosageUnitExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Form Selection (chips)
            Text(
                text = "Form",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                formOptions.forEach { option ->
                    FilterChip(
                        selected = form == option,
                        onClick = { form = option },
                        label = { Text(option) },
                        leadingIcon = if (form == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedicalBlue.copy(alpha = 0.15f),
                            selectedLabelColor = MedicalBlue,
                            selectedLeadingIconColor = MedicalBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ════════════════════════════════════════════════════════════
            // SECTION: Frequency & Meal Relation
            // ════════════════════════════════════════════════════════════
            SectionTitle("Dosage Schedule")
            Spacer(modifier = Modifier.height(12.dp))

            // Frequency dropdown
            ExposedDropdownMenuBox(
                expanded = frequencyExpanded,
                onExpandedChange = { frequencyExpanded = it }
            ) {
                OutlinedTextField(
                    value = frequency,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Frequency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MedicalBlue,
                        focusedLabelColor = MedicalBlue
                    )
                )
                ExposedDropdownMenu(
                    expanded = frequencyExpanded,
                    onDismissRequest = { frequencyExpanded = false }
                ) {
                    frequencyOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                frequency = option
                                frequencyExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Meal Relation chips
            Text(
                text = "Meal Relation",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mealRelationOptions.forEach { option ->
                    FilterChip(
                        selected = mealRelation == option,
                        onClick = { mealRelation = option },
                        label = { Text(option) },
                        leadingIcon = if (mealRelation == option) {
                            {
                                Icon(
                                    if (option == "No relation") Icons.Default.Close else Icons.Default.Restaurant,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HealingGreen.copy(alpha = 0.15f),
                            selectedLabelColor = HealingGreen,
                            selectedLeadingIconColor = HealingGreen
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ════════════════════════════════════════════════════════════
            // SECTION: Dates
            // ════════════════════════════════════════════════════════════
            SectionTitle("Duration")
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Start Date
                OutlinedTextField(
                    value = dateFormat.format(Date(startDate)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Start Date") },
                    trailingIcon = {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MedicalBlue)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MedicalBlue
                    )
                )

                // End Date
                OutlinedTextField(
                    value = if (endDate != null) dateFormat.format(Date(endDate!!)) else "Ongoing",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("End Date") },
                    trailingIcon = {
                        if (endDate != null) {
                            IconButton(onClick = { endDate = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MedicalBlue)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledTrailingIconColor = MedicalBlue
                    )
                )
            }

            // Clickable overlays for date fields
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
            ) {} // date clicks handled by enabled=false + clickable modifier

            Spacer(modifier = Modifier.height(24.dp))

            // ════════════════════════════════════════════════════════════
            // SECTION: Schedule Times
            // ════════════════════════════════════════════════════════════
            SectionTitle("Reminder Times")
            Spacer(modifier = Modifier.height(12.dp))

            if (scheduleTimes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scheduleTimes.forEachIndexed { index, (hour, minute) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = MedicalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                val amPm = if (hour < 12) "AM" else "PM"
                                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                                Text(
                                    text = String.format("%d:%02d %s", displayHour, minute, amPm),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { scheduleTimes.removeAt(index) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove time",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Add Time Button
            Button(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MedicalBlue.copy(alpha = 0.1f),
                    contentColor = MedicalBlue
                )
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Reminder Time")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ════════════════════════════════════════════════════════════
            // SECTION: Additional Info
            // ════════════════════════════════════════════════════════════
            SectionTitle("Additional Information")
            Spacer(modifier = Modifier.height(12.dp))

            // Instructions
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("Instructions") },
                placeholder = { Text("e.g., Take with plenty of water") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MedicalBlue,
                    focusedLabelColor = MedicalBlue,
                    cursorColor = MedicalBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Any additional notes...") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = MedicalBlue) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MedicalBlue,
                    focusedLabelColor = MedicalBlue,
                    cursorColor = MedicalBlue
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ════════════════════════════════════════════════════════════
            // Save Button
            // ════════════════════════════════════════════════════════════
            Button(
                onClick = {
                    // Validate
                    nameError = name.isBlank()
                    dosageError = dosage.isBlank()
                    if (nameError || dosageError) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please fill in all required fields")
                        }
                        return@Button
                    }

                    val medication = Medication(
                        name = name.trim(),
                        dosage = dosage.trim(),
                        dosageUnit = dosageUnit,
                        form = form,
                        frequency = frequency,
                        mealRelation = mealRelation,
                        instructions = instructions.trim(),
                        startDate = startDate,
                        endDate = endDate,
                        notes = notes.trim(),
                        prescriptionImagePath = capturedImagePath
                    )

                    val schedules = scheduleTimes.map { (hour, minute) ->
                        Schedule(
                            medicationId = 0, // will be set by ViewModel
                            timeHour = hour,
                            timeMinute = minute
                        )
                    }

                    viewModel.addMedicationWithSchedules(medication, schedules) {
                        // Navigate back on success
                    }

                    scope.launch {
                        snackbarHostState.showSnackbar("Medication saved successfully!")
                    }
                    viewModel.setCapturedImagePath(null)
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HealingGreen)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Save Medication",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // ── Dialogs ─────────────────────────────────────────────────────
        // Time Picker Dialog
        if (showTimePicker) {
            TimePickerDialog(
                onDismiss = { showTimePicker = false },
                onConfirm = { hour, minute ->
                    scheduleTimes.add(Pair(hour, minute))
                    showTimePicker = false
                }
            )
        }

        // Start Date Picker Dialog
        if (showStartDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate)
            DatePickerDialog(
                onDismissRequest = { showStartDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { startDate = it }
                        showStartDatePicker = false
                    }) { Text("OK", color = MedicalBlue) }
                },
                dismissButton = {
                    TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // End Date Picker Dialog
        if (showEndDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate ?: System.currentTimeMillis())
            DatePickerDialog(
                onDismissRequest = { showEndDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        endDate = datePickerState.selectedDateMillis
                        showEndDatePicker = false
                    }) { Text("OK", color = MedicalBlue) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = 8,
        initialMinute = 0,
        is24Hour = false
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Reminder Time") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)
            ) {
                Text("Set Time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
