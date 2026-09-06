package com.rakdatak.app.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val Light = Color(0xFFF5F5F5)
private val Orange = Color(0xFFFF6D00)

@Composable
fun WorkoutHistoryScreen(
    entries: List<WorkoutHistoryEntry>,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Black,
                    )
                }
                Text(
                    text = "سجل التمارين",
                    color = Black,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            if (entries.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Light),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "لا توجد تمارين محفوظة بعد",
                            color = Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "بعد أول تمرين ستظهر هنا المدة والمسافة ونسبة الإنجاز.",
                            color = Gray,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                val completedCount = entries.count { it.completed }
                val totalSeconds = entries.sumOf { it.elapsedSeconds.toLong() }
                val totalDistance = entries.sumOf { it.distanceMeters }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryBox(
                        modifier = Modifier.weight(1f),
                        value = completedCount.toString(),
                        label = "مكتملة",
                    )
                    SummaryBox(
                        modifier = Modifier.weight(1f),
                        value = "%.1f".format(totalDistance / 1_000.0),
                        label = "كم",
                    )
                    SummaryBox(
                        modifier = Modifier.weight(1f),
                        value = (totalSeconds / 60L).toString(),
                        label = "دقيقة",
                    )
                }

                Text(
                    text = "الأحدث أولًا",
                    color = Black,
                    style = MaterialTheme.typography.titleLarge,
                )

                entries.forEach { entry ->
                    HistoryCard(entry = entry)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HistoryCard(entry: WorkoutHistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Light),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (entry.completed) "تمرين مكتمل" else "تمرين محفوظ",
                    color = if (entry.completed) Orange else Black,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatDate(entry.timestampMillis),
                    color = Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = "${formatTime(entry.elapsedSeconds)} • ${formatDistance(entry.distanceMeters)} • ${(entry.completionRatio * 100).toInt()}%",
                color = Gray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SummaryBox(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Light),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, color = Black, style = MaterialTheme.typography.titleLarge)
            Text(label, color = Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatTime(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatDistance(distanceMeters: Double): String =
    "%.2f كم".format(distanceMeters.coerceAtLeast(0.0) / 1_000.0)

private fun formatDate(timestampMillis: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestampMillis))
