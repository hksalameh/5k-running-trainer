package com.rakdatak.app.schedule

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rakdatak.core.training.TrainingSlot
import java.time.DayOfWeek
import java.time.LocalTime

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)

@Composable
fun TrainingScheduleScreen(
    existingSlots: List<TrainingSlot>,
    onSave: (List<TrainingSlot>) -> Unit,
    onCancel: () -> Unit,
) {
    val initial = remember(existingSlots) {
        if (existingSlots.size == 3) {
            existingSlots.sortedBy { it.dayOfWeek.value }
        } else {
            listOf(
                TrainingSlot(DayOfWeek.SUNDAY, LocalTime.of(19, 0)),
                TrainingSlot(DayOfWeek.TUESDAY, LocalTime.of(19, 0)),
                TrainingSlot(DayOfWeek.THURSDAY, LocalTime.of(19, 0)),
            )
        }
    }
    var slot1 by remember(initial) { mutableStateOf(initial[0]) }
    var slot2 by remember(initial) { mutableStateOf(initial[1]) }
    var slot3 by remember(initial) { mutableStateOf(initial[2]) }
    val currentSlots = listOf(slot1, slot2, slot3)
    val distinctDays = currentSlots.map { it.dayOfWeek }.distinct().size == 3
    val context = LocalContext.current
    var slotsWaitingForPermission by remember { mutableStateOf<List<TrainingSlot>?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        slotsWaitingForPermission?.let(onSave)
        slotsWaitingForPermission = null
    }

    fun saveWithNotificationPermission(slots: List<TrainingSlot>) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            slotsWaitingForPermission = slots
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSave(slots)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "مواعيد التدريب",
                color = Black,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "اختر ثلاثة أوقات تناسبك. نحاول الحفاظ على الراحة بين الجلسات، وإذا فاتك تمرين نرحله للموعد المناسب التالي.",
                color = Gray,
                style = MaterialTheme.typography.bodyLarge,
            )

            SlotEditor(
                number = 1,
                slot = slot1,
                onChange = { slot1 = it },
            )
            SlotEditor(
                number = 2,
                slot = slot2,
                onChange = { slot2 = it },
            )
            SlotEditor(
                number = 3,
                slot = slot3,
                onChange = { slot3 = it },
            )

            if (!distinctDays) {
                Text(
                    text = "اختر ثلاثة أيام مختلفة حتى يكون هناك مجال للراحة.",
                    color = Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { saveWithNotificationPermission(currentSlots) },
                enabled = distinctDays,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) {
                Text("حفظ المواعيد")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("رجوع", color = Black)
            }
        }
    }
}

@Composable
private fun SlotEditor(
    number: Int,
    slot: TrainingSlot,
    onChange: (TrainingSlot) -> Unit,
) {
    val context = LocalContext.current
    var dayMenuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "التمرين $number",
            color = Black,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { dayMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(dayLabel(slot.dayOfWeek), color = Black)
                }
                DropdownMenu(
                    expanded = dayMenuOpen,
                    onDismissRequest = { dayMenuOpen = false },
                ) {
                    DayOfWeek.entries.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(dayLabel(day)) },
                            onClick = {
                                onChange(slot.copy(dayOfWeek = day))
                                dayMenuOpen = false
                            },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onChange(slot.copy(time = LocalTime.of(hour, minute)))
                        },
                        slot.time.hour,
                        slot.time.minute,
                        false,
                    ).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(formatTime(slot.time), color = Black)
            }
        }
    }
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.SUNDAY -> "الأحد"
    DayOfWeek.MONDAY -> "الاثنين"
    DayOfWeek.TUESDAY -> "الثلاثاء"
    DayOfWeek.WEDNESDAY -> "الأربعاء"
    DayOfWeek.THURSDAY -> "الخميس"
    DayOfWeek.FRIDAY -> "الجمعة"
    DayOfWeek.SATURDAY -> "السبت"
}

private fun formatTime(time: LocalTime): String {
    val hour12 = when (val hour = time.hour % 12) {
        0 -> 12
        else -> hour
    }
    val period = if (time.hour < 12) "ص" else "م"
    return "%d:%02d %s".format(hour12, time.minute, period)
}
