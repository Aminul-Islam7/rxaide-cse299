package com.example.rxaide.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rxaide.R
import com.example.rxaide.ui.theme.HealingGreen
import com.example.rxaide.ui.theme.HealingGreenDark
import com.example.rxaide.ui.theme.MedicalBlue
import com.example.rxaide.ui.theme.MedicalBlueDark
import com.example.rxaide.ui.theme.AlertOrange
import com.example.rxaide.ui.theme.AlertRed
import com.example.rxaide.viewmodel.MedicationViewModel

@Composable
fun HomeScreen(
    viewModel: MedicationViewModel,
    onNavigateToMedications: () -> Unit,
    onNavigateToAddMedication: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAdherence: () -> Unit
) {
    val activeMedicationCount by viewModel.activeMedicationCount.collectAsState()
    val totalTaken by viewModel.totalTakenCount.collectAsState()
    val totalMissed by viewModel.totalMissedCount.collectAsState()
    val pendingDoses by viewModel.totalUnmarkedCount.collectAsState()
    val adherencePercent = calculateAdherence(totalTaken, totalMissed)
    val animatedAdherence by animateIntAsState(
        targetValue = adherencePercent,
        animationSpec = tween(durationMillis = 700),
        label = "adherencePercent"
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToChat,
                containerColor = MedicalBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Chat, contentDescription = "Chat with AI")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Hero Header with gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MedicalBlue.copy(alpha = 0.15f),
                                MedicalBlue.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // App Logo
                    Image(
                        painter = painterResource(id = R.drawable.rxaide_logo_with_name),
                        contentDescription = "RxAide Logo",
                        modifier = Modifier
                            .height(100.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Adherence Score",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$animatedAdherence%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (animatedAdherence >= 80) HealingGreen else AlertOrange
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stats Card - Enhanced styling
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatMetricCard(
                        modifier = Modifier.fillMaxWidth(0.31f),
                        value = activeMedicationCount,
                        label = "Active",
                        color = MedicalBlue,
                        icon = Icons.Default.Medication
                    )
                    StatMetricCard(
                        modifier = Modifier.fillMaxWidth(0.31f),
                        value = totalTaken,
                        label = "Taken",
                        color = HealingGreen,
                        icon = Icons.Default.CheckCircle
                    )
                    StatMetricCard(
                        modifier = Modifier.fillMaxWidth(0.31f),
                        value = totalMissed,
                        label = "Missed",
                        color = AlertRed,
                        icon = Icons.Default.Cancel
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            PendingDoseCard(
                pendingCount = pendingDoses,
                onClick = onNavigateToAdherence,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Section Title
            Text(
                text = "Quick Access",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2×2 Grid of feature buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Row 1
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cellWidth = (maxWidth - 16.dp) / 2
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GridButton(
                            modifier = Modifier.width(cellWidth),
                            icon = Icons.Default.CameraAlt,
                            title = "Scan\nPrescription",
                            gradientColors = listOf(MedicalBlue, MedicalBlueDark),
                            onClick = onNavigateToCamera
                        )
                        GridButton(
                            modifier = Modifier.width(cellWidth),
                            icon = Icons.Default.Add,
                            title = "Add\nMedication",
                            gradientColors = listOf(HealingGreen, HealingGreenDark),
                            onClick = onNavigateToAddMedication
                        )
                    }
                }

                // Row 2
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val cellWidth = (maxWidth - 16.dp) / 2
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GridButton(
                            modifier = Modifier.width(cellWidth),
                            icon = Icons.Default.Medication,
                            title = "My\nMedications",
                            gradientColors = listOf(AlertOrange, Color(0xFFD97706)),
                            onClick = onNavigateToMedications
                        )
                        GridButton(
                            modifier = Modifier.width(cellWidth),
                            icon = Icons.Default.CheckCircle,
                            title = "Adherence\nTracker",
                            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                            onClick = onNavigateToAdherence
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun PendingDoseCard(
    pendingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFF7ED), Color(0xFFFFEDD5))
    )
    val accentColor = if (pendingCount > 0) AlertOrange else HealingGreen
    val headline = if (pendingCount > 0) "Pending Doses" else "All Caught Up"
    val subtitle = if (pendingCount > 0) {
        "$pendingCount dose${if (pendingCount == 1) "" else "s"} waiting for status update"
    } else {
        "No pending dose updates right now"
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgBrush)
                .defaultMinSize(minHeight = 84.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    modifier: Modifier = Modifier,
    value: Int,
    label: String,
    color: Color,
    icon: ImageVector
) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 700),
        label = "${label}Count"
    )

    Card(
        modifier = modifier
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = animatedValue.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun GridButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(colors = gradientColors),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun calculateAdherence(taken: Int, missed: Int): Int {
    val total = taken + missed
    return if (total == 0) 0 else ((taken.toFloat() / total.toFloat()) * 100f).toInt()
}
