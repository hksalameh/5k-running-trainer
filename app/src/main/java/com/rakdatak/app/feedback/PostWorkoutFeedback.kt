package com.rakdatak.app.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakdatak.core.training.AdaptiveTrainingEngine
import com.rakdatak.core.training.WorkoutSessionSnapshot
import com.rakdatak.core.training.WorkoutSessionStatus
import com.rakdatak.core.training.model.PainLevel
import com.rakdatak.core.training.model.PerceivedDifficulty
import com.rakdatak.core.training.model.TrainingDecision
import com.rakdatak.core.training.model.WorkoutResult

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val Light = Color(0xFFF5F5F5)

@Composable
fun PostWorkoutFeedbackScreen(
    planId: String,
    snapshot: WorkoutSessionSnapshot,
    distanceMeters: Double,
    manualDistanceEntryEnabled: Boolean,
    onManualDistanceSaved: (Double) -> Unit,
    onDone: (PerceivedDifficulty, PainLevel, TrainingDecision) -> Unit,
    onSkip: () -> Unit,
) {
    var difficulty by remember { mutableStateOf<PerceivedDifficulty?>(null) }
    var pain by remember { mutableStateOf<PainLevel?>(null) }
    var decision by remember { mutableStateOf<TrainingDecision?>(null) }
    var treadmillDistanceText by remember { mutableStateOf("") }

    val treadmillDistanceKm = parseKilometers(treadmillDistanceText)
    val canSaveTreadmillDistance = treadmillDistanceKm != null && treadmillDistanceKm > 0.0

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (snapshot.status == WorkoutSessionStatus.COMPLETED) "أحسنت!" else "تم حفظ تمرينك",
                color = Black,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("الوقت ${formatTime(snapshot.totalElapsedSeconds)}")
                    append(" • ${(snapshot.completionRatio * 100).toInt()}% مكتمل")
                    if (distanceMeters > 0.0) {
                        append(" • ${formatDistance(distanceMeters)}")
                    }
                },
                color = Gray,
                style = MaterialTheme.typography.bodyLarge,
            )

            if (manualDistanceEntryEnabled && distanceMeters <= 0.0 && decision == null) {
                Spacer(modifier = Modifier.height(18.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Light),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "مسافة التردمل",
                            color = Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "أدخل المسافة النهائية الظاهرة على جهاز التردمل حتى تُحفظ في سجلك وإحصاءاتك.",
                            color = Gray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = treadmillDistanceText,
                            onValueChange = { value ->
                                treadmillDistanceText = value
                                    .filter { character ->
                                        character.isDigit() || character == '.' ||
                                            character == ',' || character == '٫'
                                    }
                                    .take(8)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("المسافة بالكيلومتر") },
                            placeholder = { Text("مثال: 3.25") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                val kilometers = treadmillDistanceKm ?: return@Button
                                onManualDistanceSaved(kilometers * 1_000.0)
                            },
                            enabled = canSaveTreadmillDistance,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Black),
                        ) {
                            Text("حفظ المسافة")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (decision == null) {
                Text(
                    text = "التقييم اختياري ويساعد ركضتك على تحسين الخطة.",
                    color = Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "كيف كان التمرين؟",
                    color = Black,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "سهل",
                        selected = difficulty == PerceivedDifficulty.EASY,
                        onClick = { difficulty = PerceivedDifficulty.EASY },
                    )
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "مناسب",
                        selected = difficulty == PerceivedDifficulty.RIGHT,
                        onClick = { difficulty = PerceivedDifficulty.RIGHT },
                    )
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "صعب جدًا",
                        selected = difficulty == PerceivedDifficulty.VERY_HARD,
                        onClick = { difficulty = PerceivedDifficulty.VERY_HARD },
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "هل عندك ألم أو انزعاج؟",
                    color = Black,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "لا",
                        selected = pain == PainLevel.NONE,
                        onClick = { pain = PainLevel.NONE },
                    )
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "خفيف",
                        selected = pain == PainLevel.MILD,
                        onClick = { pain = PainLevel.MILD },
                    )
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "متوسط",
                        selected = pain == PainLevel.MODERATE,
                        onClick = { pain = PainLevel.MODERATE },
                    )
                    FeedbackChoice(
                        modifier = Modifier.weight(1f),
                        text = "قوي",
                        selected = pain == PainLevel.STRONG,
                        onClick = { pain = PainLevel.STRONG },
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        val chosenDifficulty = difficulty ?: return@Button
                        val chosenPain = pain ?: return@Button
                        decision = AdaptiveTrainingEngine().decide(
                            result = WorkoutResult(
                                planId = planId,
                                completionRatio = snapshot.completionRatio,
                                distanceMeters = distanceMeters.takeIf { it > 0.0 },
                                continuousRunSeconds = 0,
                                averageHeartRateBpm = null,
                                excessiveHeartRateFraction = null,
                                unplannedWalkBreaks = 0,
                                difficulty = chosenDifficulty,
                                pain = chosenPain,
                            ),
                            missedWorkoutsThisWeek = 0,
                            recentModerateOrStrongPainCount = 0,
                            recentStrongPerformanceCount = 0,
                        )
                    },
                    enabled = difficulty != null && pain != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) {
                    Text("حفظ التقييم")
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = "تخطي والعودة للرئيسية",
                        color = Black,
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Light),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "الخطوة التالية",
                            color = Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = decision!!.reasonAr,
                            color = Gray,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (pain == PainLevel.STRONG || pain == PainLevel.MODERATE) {
                            Text(
                                text = "إذا استمر الألم أو تكرر، توقف عن زيادة الحمل وراجع مختصًا.",
                                color = Gray,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onDone(difficulty!!, pain!!, decision!!) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) {
                    Text("تم")
                }
            }
        }
    }
}

@Composable
private fun FeedbackChoice(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)

    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black),
            contentPadding = contentPadding,
        ) {
            Text(
                text = text,
                maxLines = 1,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = contentPadding,
        ) {
            Text(
                text = text,
                color = Black,
                maxLines = 1,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun parseKilometers(raw: String): Double? =
    raw.trim()
        .replace(',', '.')
        .replace('٫', '.')
        .toDoubleOrNull()
        ?.takeIf { it in 0.01..100.0 }

private fun formatTime(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatDistance(distanceMeters: Double): String =
    "%.2f كم".format(distanceMeters.coerceAtLeast(0.0) / 1_000.0)
