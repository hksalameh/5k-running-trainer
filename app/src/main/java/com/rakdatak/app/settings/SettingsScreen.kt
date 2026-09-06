package com.rakdatak.app.settings

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val Light = Color(0xFFF5F5F5)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    trainingScheduleSummary: String,
    onBack: () -> Unit,
    onOpenTrainingSchedule: () -> Unit,
    onSoundCuesChanged: (Boolean) -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
) {
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
                text = "الخطة والتذكيرات",
                style = MaterialTheme.typography.titleLarge,
                color = Black,
            )

            SettingActionCard(
                title = "مواعيد التدريب",
                subtitle = trainingScheduleSummary,
                onClick = onOpenTrainingSchedule,
            )

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
                        text = "نسخة تجريبية 0.3.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gray,
                    )
                    Text(
                        text = "نسخة Wear OS مرافقة، وتتبع المسافة عبر GPS على الهاتف والساعة عند توفر الأذونات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray,
                    )
                }
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
