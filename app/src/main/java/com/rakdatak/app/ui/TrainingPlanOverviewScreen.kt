package com.rakdatak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rakdatak.core.training.model.WorkoutPhase
import com.rakdatak.core.training.model.WorkoutPhaseType
import com.rakdatak.core.training.model.WorkoutPlan

private val Ink = Color(0xFF171717)
private val Accent = Color(0xFFFF6D00)
private val Faded = Color(0xFF686868)

@Composable
fun TrainingPlanOverviewScreen(
    plans: List<WorkoutPlan>,
    currentIndex: Int,
    onBack: () -> Unit,
) {
    if (plans.isEmpty()) return
    val safeIndex = currentIndex.coerceIn(plans.indices)
    val current = plans[safeIndex]
    var expandedPlanId by rememberSaveable { mutableStateOf(current.id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = onBack) { Text("العودة للرئيسية") }
            Spacer(Modifier.height(12.dp))
            Text("برنامج ركضتك كاملًا", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text("8 أسابيع • " + plans.size + " تمرينًا", color = Faded)
            Spacer(Modifier.height(8.dp))
            Text(
                "موقعك الآن: الأسبوع " + current.week + "، الجلسة " + current.session +
                    " (" + (safeIndex + 1) + " من " + plans.size + ")",
                color = Accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "بعد هذا التمرين " + (plans.size - safeIndex - 1) + " تمرينًا مخططًا. " +
                    "قد تتغير المرحلة التالية حسب تقييمك بعد الركض.",
                color = Faded,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "اضغط على أي تمرين لعرض فترات المشي والركض بالترتيب.",
                color = Faded,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        plans.groupBy { it.week }.forEach { (week, sessions) ->
            item(key = "week-$week") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "الأسبوع $week",
                    color = Ink,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(sessions, key = { it.id }) { plan ->
                val absoluteIndex = plans.indexOf(plan)
                val isCurrent = absoluteIndex == safeIndex
                val expanded = expandedPlanId == plan.id
                val phaseTotals = plan.phases.groupBy { it.type }
                    .mapValues { (_, stages) -> stages.sumOf { it.durationSeconds } }
                val running = phaseTotals[WorkoutPhaseType.RUN] ?: 0
                val walking = phaseTotals[WorkoutPhaseType.WALK] ?: 0
                val warmup = phaseTotals[WorkoutPhaseType.WARM_UP] ?: 0
                val cooldown = phaseTotals[WorkoutPhaseType.COOL_DOWN] ?: 0

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) Color(0xFFFFF0DE) else Color(0xFFF5F5F5),
                    ),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedPlanId = if (expanded) "" else plan.id
                            }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "الجلسة " + plan.session + " — " + plan.titleAr,
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) Text("أنت هنا", color = Accent)
                        }
                        Text(
                            "التمرين " + (absoluteIndex + 1) + "/" + plans.size +
                                " • المدة " + duration(plan.totalDurationSeconds),
                            color = Faded,
                        )
                        Text(
                            "ركض " + duration(running) + " • مشي " + duration(walking),
                            color = Ink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "إحماء " + duration(warmup) + " • تهدئة " + duration(cooldown),
                            color = Faded,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (expanded) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "تفاصيل المراحل بالترتيب:",
                                color = Accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                            plan.phases.forEachIndexed { i, phase ->
                                Text(
                                    (i + 1).toString() + ". " + phaseName(phase) +
                                        " • " + duration(phase.durationSeconds),
                                    color = Ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            Text("عرض تفاصيل المشي والركض", color = Accent)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "المراحل السابقة لموقعك في الخطة ليست بالضرورة مكتملة؛ " +
                    "التقدم يتغير حسب التقييم ويمكن تكرار الجلسات.",
                color = Faded,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

private fun phaseName(phase: WorkoutPhase) = when (phase.type) {
    WorkoutPhaseType.RUN -> "ركض"
    WorkoutPhaseType.WALK -> "مشي"
    WorkoutPhaseType.WARM_UP -> "إحماء"
    WorkoutPhaseType.COOL_DOWN -> "تهدئة"
}

private fun duration(seconds: Int): String {
    if (seconds <= 0) return "0 دقيقة"
    val min = seconds / 60
    val sec = seconds % 60
    return when {
        min == 0 -> "$sec ثانية"
        sec == 0 -> "$min دقيقة"
        else -> "$min دقيقة و$sec ثانية"
    }
}
