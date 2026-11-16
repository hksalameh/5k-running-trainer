package com.rakdatak.app.settings

import android.media.MediaPlayer
import android.util.Log
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rakdatak.app.wear.rememberWearLiveMetrics
import com.rakdatak.app.rawVoiceCue

private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val Light = Color(0xFFF5F5F5)
private val Orange = Color(0xFFFF6D00)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    profileSummary: String,
    trainingScheduleSummary: String,
    currentWeek: Int,
    previewWeekSelectionEnabled: Boolean,
    onPreviewWeekChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenProfileSettings: () -> Unit,
    onOpenTrainingSchedule: () -> Unit,
    onSoundCuesChanged: (Boolean) -> Unit,
    onWorkoutVoiceChanged: (WorkoutVoice) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
) {
    val watch = rememberWearLiveMetrics()
    val watchConnected = watch.isFresh()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
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
                    text = "الإعدادات",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Black,
                )
            }

            Text(
                text = "أثناء التمرين",
                style = MaterialTheme.typography.titleLarge,
                color = Black,
            )

            SettingToggleCard(
                title = "التوجيهات الصوتية",
                subtitle = "تنبيه صوتي عند الانتقال بين المشي والركض والتهدئة.",
                checked = settings.soundCuesEnabled,
                onCheckedChange = onSoundCuesChanged,
            )

            VoiceChoiceCard(
                selected = settings.workoutVoice,
                enabled = true,
                onSelected = onWorkoutVoiceChanged,
            )

            SettingToggleCard(
                title = "الاهتزاز",
                subtitle = "اهتزاز قصير عند تغيّر مرحلة التمرين.",
                checked = settings.vibrationEnabled,
                onCheckedChange = onVibrationChanged,
            )

            SettingToggleCard(
                title = "إبقاء الشاشة مضاءة",
                subtitle = "يمنع الشاشة من الإطفاء تلقائيًا أثناء التمرين.",
                checked = settings.keepScreenOnDuringWorkout,
                onCheckedChange = onKeepScreenOnChanged,
            )

            Text(
                text = "الساعة",
                style = MaterialTheme.typography.titleLarge,
                color = Black,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (watchConnected) Color(0xFFFFF3E8) else Light,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = if (watchConnected) "Wear OS متصلة" else "Wear OS غير متصلة الآن",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (watchConnected) Orange else Black,
                    )
                    Text(
                        text = if (watchConnected) {
                            "النبض والمسافة وحالة التمرين تصل مباشرة من الساعة إلى الهاتف."
                        } else {
                            "شغّل ركضتك على الساعة أثناء التمرين ليظهر النبض والبيانات على الهاتف."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray,
                    )
                }
            }

            Text(
                text = "الخطة والتذكيرات",
                style = MaterialTheme.typography.titleLarge,
                color = Black,
            )

            SettingActionCard(
                title = "بيانات التدريب",
                subtitle = profileSummary,
                onClick = onOpenProfileSettings,
            )

            SettingActionCard(
                title = "مواعيد التدريب",
                subtitle = trainingScheduleSummary,
                onClick = onOpenTrainingSchedule,
            )

            if (previewWeekSelectionEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Light),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "اختيار أسبوع للتجربة",
                            color = Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "هذا الاختيار يخص النسخة التجريبية فقط، ولا يغيّر تقدمك في التطبيق الأصلي.",
                            color = Gray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        repeat(3) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                repeat(3) { column ->
                                    val week = row * 3 + column + 1
                                    FilterChip(
                                        selected = currentWeek == week,
                                        onClick = { onPreviewWeekChanged(week) },
                                        label = { Text("الأسبوع $week") },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Light),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "ركضتك",
                        style = MaterialTheme.typography.titleMedium,
                        color = Black,
                    )
                    Text(
                        text = "نسخة تجريبية 0.5.3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray,
                    )
                    Text(
                        text = "تتبع GPS، حفظ التمرين، مواعيد التدريب، واختيار صوت التوجيهات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChoiceCard(
    selected: WorkoutVoice,
    enabled: Boolean,
    onSelected: (WorkoutVoice) -> Unit,
) {
    val context = LocalContext.current
    var activePreview by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { activePreview?.release() }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Light),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "صوت المدرب",
                style = MaterialTheme.typography.titleMedium,
                color = Black,
            )
            Text(
                text = "صوت رجل وصوت امرأة من نفس محرك الأصوات الطبيعي. يعملان دون إنترنت أثناء التدريب.",
                style = MaterialTheme.typography.bodySmall,
                color = Gray,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    selected = selected == WorkoutVoice.CHARON,
                    onClick = { onSelected(WorkoutVoice.CHARON) },
                    enabled = enabled,
                    label = { Text("رجل — Charon") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = selected == WorkoutVoice.DESPINA,
                    onClick = { onSelected(WorkoutVoice.DESPINA) },
                    enabled = enabled,
                    label = { Text("امرأة — Despina") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "التوجيهات مسجلة داخل التطبيق بصوت Charon وDespina من Auditext.",
                style = MaterialTheme.typography.bodySmall,
                color = Gray,
            )
            OutlinedButton(
                onClick = {
                    runCatching { activePreview?.release() }
                    activePreview = null
                    try {
                        val rawId = rawVoiceCue(selected, "warmup_300")
                        val player = rawId?.let { MediaPlayer.create(context, it) }
                        if (player == null) {
                            previewMessage = "تعذر فتح الملف الصوتي. أرسل هذه الرسالة لتشخيص المشكلة."
                            Log.e("RakdatakVoice", "Preview: MediaPlayer.create failed voice=" + selected)
                        } else {
                            activePreview = player
                            player.setOnCompletionListener {
                                it.release()
                                if (activePreview === it) activePreview = null
                                previewMessage = "انتهت العينة الصوتية."
                            }
                            player.setOnErrorListener { failed, what, extra ->
                                Log.e("RakdatakVoice", "Preview failed " + what + "/" + extra)
                                failed.release()
                                if (activePreview === failed) activePreview = null
                                previewMessage = "فشل تشغيل الصوت. رمز الخطأ: " + what
                                true
                            }
                            player.setVolume(1f, 1f)
                            player.start()
                            Log.i("RakdatakVoice", "PREVIEW_PLAY voice=" + selected)
                            previewMessage = "تشغيل العينة الآن. ارفع صوت الوسائط إذا لم تسمعها."
                        }
                    } catch (failure: Exception) {
                        Log.e("RakdatakVoice", "Preview playback exception", failure)
                        previewMessage = "تعذر تشغيل الصوت: " + (failure.message ?: "خطأ غير معروف")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("استمع لعينة الصوت المختار")
            }
            if (previewMessage != null) {
                Text(
                    text = previewMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray,
                )
            }
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Light),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Black,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun SettingActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Light),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Black,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Gray,
            )
        }
    }
}
