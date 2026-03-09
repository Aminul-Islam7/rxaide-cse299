package com.example.rxaide.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rxaide.ui.theme.AlertRed
import com.example.rxaide.ui.theme.HealingGreen
import com.example.rxaide.ui.theme.MedicalBlue
import com.example.rxaide.viewmodel.AdherenceViewModel
import com.example.rxaide.viewmodel.DoseHistoryEntry
import com.example.rxaide.viewmodel.MedicationAdherenceStat
import com.example.rxaide.viewmodel.TimePeriod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdherenceScreen(
    viewModel: AdherenceViewModel,
    onNavigateBack: () -> Unit
) {
    val overallPercent by viewModel.overallAdherencePercent.collectAsState()
    val totalTaken by viewModel.totalTakenCount.collectAsState()
    val totalMissed by viewModel.totalMissedCount.collectAsState()
    val perMedStats by viewModel.perMedicationStats.collectAsState()
    val historyEntries by viewModel.doseHistoryEntries.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adherence Tracker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall compliance card
            item {
                Spacer(modifier = Modifier.height(4.dp))
                OverallComplianceCard(overallPercent, totalTaken, totalMissed)
            }

            // Per-medication section header
            if (perMedStats.isNotEmpty()) {
                item {
                    Text(
                        text = "Per Medication",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(perMedStats, key = { it.medication.id }) { stat ->
                    MedicationStatCard(stat)
                }
            }

            // Dose history section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dose History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Period filter chips
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimePeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MedicalBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (historyEntries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Medication,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No dose history yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else {
                // Group by date
                val grouped = historyEntries.groupBy { entry ->
                    dateLabel(entry.doseHistory.scheduledTime)
                }
                grouped.forEach { (dateLabel, entries) ->
                    item {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(entries, key = { it.doseHistory.id }) { entry ->
                        DoseHistoryRow(entry)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun OverallComplianceCard(percent: Float, taken: Int, missed: Int) {
    val indicatorColor = when {
        percent >= 80f -> HealingGreen
        percent >= 50f -> Color(0xFFF59E0B) // orange
        else -> AlertRed
    }
    val animatedPercent by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(durationMillis = 800),
        label = "compliance"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Overall Compliance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    // Background ring
                    drawArc(
                        color = indicatorColor.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    // Progress ring
                    drawArc(
                        color = indicatorColor,
                        startAngle = -90f,
                        sweepAngle = animatedPercent / 100f * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedPercent.toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = indicatorColor
                    )
                    Text(
                        "Adherence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$taken",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = HealingGreen
                    )
                    Text("Taken", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$missed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = AlertRed
                    )
                    Text("Missed", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MedicationStatCard(stat: MedicationAdherenceStat) {
    val indicatorColor = when {
        stat.adherencePercent >= 80f -> HealingGreen
        stat.adherencePercent >= 50f -> Color(0xFFF59E0B)
        else -> AlertRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stat.medication.name,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "${stat.medication.dosage}${stat.medication.dosageUnit} · ${stat.medication.form}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${stat.adherencePercent.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = indicatorColor
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (stat.adherencePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = indicatorColor,
                trackColor = indicatorColor.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "${stat.takenCount} taken",
                    style = MaterialTheme.typography.bodySmall,
                    color = HealingGreen
                )
                Text(
                    " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${stat.missedCount} missed",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed
                )
            }
        }
    }
}

@Composable
private fun DoseHistoryRow(entry: DoseHistoryEntry) {
    val isTaken = entry.doseHistory.status == "taken"
    val statusColor = if (isTaken) HealingGreen else AlertRed
    val statusIcon = if (isTaken) Icons.Default.CheckCircle else Icons.Default.Cancel
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = statusColor.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(statusColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                statusIcon,
                contentDescription = entry.doseHistory.status,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.medicationName,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                timeFormat.format(Date(entry.doseHistory.scheduledTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            if (isTaken) "Taken" else "Missed",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = statusColor
        )
    }
}

private fun dateLabel(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.timeInMillis = timestamp

    val sameDay = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"

    today.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "Yesterday"

    return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
}
