package com.example.rxaide.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rxaide.ui.theme.AlertOrange
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

@Composable
fun AdherenceScreen(
    viewModel: AdherenceViewModel,
    onNavigateBack: () -> Unit
) {
    val overallPercent by viewModel.overallAdherencePercent.collectAsState()
    val totalTaken by viewModel.totalTakenCount.collectAsState()
    val totalMissed by viewModel.totalMissedCount.collectAsState()
    val totalUnmarked by viewModel.totalUnmarkedCount.collectAsState()
    val perMedStats by viewModel.perMedicationStats.collectAsState()
    val historyEntries by viewModel.doseHistoryEntries.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val periodTaken by viewModel.periodTakenCount.collectAsState()
    val periodMissed by viewModel.periodMissedCount.collectAsState()

    val periodUnmarked by viewModel.periodUnmarkedCount.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MedicalBlue.copy(alpha = 0.06f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Adherence Tracker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Track how well you follow your schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Compact compliance card with unmarked count
        item {
            CompactComplianceCard(
                overallPercent, totalTaken, totalMissed, totalUnmarked,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Per-medication stats
        if (perMedStats.isNotEmpty()) {
            item {
                Text(
                    text = "By Medication",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            items(perMedStats, key = { it.medication.id }) { stat ->
                MedicationStatCard(
                    stat,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Period filter + History
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "History",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                items(TimePeriod.entries.size) { index ->
                    val period = TimePeriod.entries[index]
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedicalBlue,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Show period counts
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Taken: $periodTaken",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = HealingGreen
                )
                Text(
                    "Missed: $periodMissed",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AlertRed
                )
                Text(
                    "Pending: $periodUnmarked",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF59E0B)
                )
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
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No dose history yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Doses will appear here as you take them",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val grouped = historyEntries.groupBy { entry ->
                dateLabel(entry.doseHistory.scheduledTime)
            }
            grouped.forEach { (dateLabel, entries) ->
                item {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                items(entries) { entry ->
                    DoseHistoryCard(
                        entry, viewModel,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun CompactComplianceCard(
    percent: Float,
    taken: Int,
    missed: Int,
    unmarked: Int,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when {
        percent >= 80f -> HealingGreen
        percent >= 50f -> Color(0xFFF59E0B)
        else -> AlertRed
    }
    val animatedPercent by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(durationMillis = 800),
        label = "compliance"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact circular progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    drawArc(
                        color = indicatorColor.copy(alpha = 0.12f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
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
                Text(
                    text = "${animatedPercent.toInt()}%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = indicatorColor
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Overall Compliance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text(
                            "$taken",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = HealingGreen
                        )
                        Text(
                            "Taken",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(
                            "$missed",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AlertRed
                        )
                        Text(
                            "Missed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column {
                        Text(
                            "$unmarked",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AlertOrange
                        )
                        Text(
                            "Pending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationStatCard(
    stat: MedicationAdherenceStat,
    modifier: Modifier = Modifier
) {
    val total = stat.total
    val indicatorColor = when {
        stat.adherencePercent >= 80f -> HealingGreen
        stat.adherencePercent >= 50f -> Color(0xFFF59E0B)
        else -> AlertRed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stat.medication.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${stat.takenCount}/$total",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = indicatorColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (stat.adherencePercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = indicatorColor,
                trackColor = indicatorColor.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

/**
 * Dose history card with two large persistent toggle buttons (Taken / Missed).
 * The active button is highlighted. Tapping the active one unmarks it.
 * Tapping the inactive one switches the status.
 */
@Composable
private fun DoseHistoryCard(
    entry: DoseHistoryEntry,
    viewModel: AdherenceViewModel,
    modifier: Modifier = Modifier
) {
    val status = entry.doseHistory.status
    val isTaken = status == "taken"
    val isMissed = status == "missed"

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Taken button — full height, left side
            Box(
                modifier = Modifier
                    .weight(0.18f)
                    .fillMaxHeight()
                    .background(
                        if (isTaken) HealingGreen.copy(alpha = 0.2f)
                        else HealingGreen.copy(alpha = 0.04f)
                    )
                    .clickable {
                        if (isTaken) {
                            viewModel.updateDoseStatus(entry.doseHistory.id, "unmarked")
                        } else {
                            viewModel.updateDoseStatus(entry.doseHistory.id, "taken")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Taken",
                    tint = if (isTaken) HealingGreen else HealingGreen.copy(alpha = 0.3f),
                    modifier = Modifier.size(26.dp)
                )
            }

            // Med info — center
            Column(
                modifier = Modifier
                    .weight(0.64f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    entry.medicationName,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        timeFormat.format(Date(entry.doseHistory.scheduledTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when {
                            isTaken -> "Taken"
                            isMissed -> "Missed"
                            else -> "Pending"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isTaken -> HealingGreen
                            isMissed -> AlertRed
                            else -> Color(0xFFF59E0B)
                        }
                    )
                }
            }

            // Missed button — full height, right side
            Box(
                modifier = Modifier
                    .weight(0.18f)
                    .fillMaxHeight()
                    .background(
                        if (isMissed) AlertRed.copy(alpha = 0.2f)
                        else AlertRed.copy(alpha = 0.04f)
                    )
                    .clickable {
                        if (isMissed) {
                            viewModel.updateDoseStatus(entry.doseHistory.id, "unmarked")
                        } else {
                            viewModel.updateDoseStatus(entry.doseHistory.id, "missed")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Missed",
                    tint = if (isMissed) AlertRed else AlertRed.copy(alpha = 0.3f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
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
