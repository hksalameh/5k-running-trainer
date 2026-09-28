package com.rakdatak.app.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)
private val Light = Color(0xFFF5F5F5)

@Composable
fun ProfileSettingsScreen(
    profile: RunnerProfile,
    onSave: (RunnerProfile) -> Unit,
    onBack: () -> Unit,
) {
    var ageText by remember(profile.ageYears) {
        mutableStateOf(profile.ageYears?.toString().orEmpty())
    }
    var sex by remember(profile.sex) { mutableStateOf(profile.sex) }
    var heightText by remember(profile.heightCm) {
        mutableStateOf(profile.heightCm?.toString().orEmpty())
    }
    var weightText by remember(profile.weightKg) {
        mutableStateOf(profile.weightKg?.let(::formatDecimal).orEmpty())
    }
    var restingHeartRateText by remember(profile.restingHeartRateBpm) {
        mutableStateOf(profile.restingHeartRateBpm?.toString().orEmpty())
    }
    var environment by remember(profile.trainingEnvironment) {
        mutableStateOf(profile.trainingEnvironment)
    }
    var safetyReviewNeeded by remember(profile.safetyReviewNeeded) {
        mutableStateOf(profile.safetyReviewNeeded)
    }

    val age = ageText.toIntOrNull()
    val heightCm = heightText.toIntOrNull()
    val weightKg = parseDecimal(weightText)
    val restingHeartRate = restingHeartRateText.toIntOrNull()

    val validAge = age != null && age in 14..100
    val validHeight = heightText.isBlank() || (heightCm != null && heightCm in 120..230)
    val validWeight = weightText.isBlank() || (weightKg != null && weightKg in 30.0..250.0)
    val validRestingHeartRate = restingHeartRateText.isBlank() ||
        (restingHeartRate != null && restingHeartRate in 35..120)
    val canSave = validAge && validHeight && validWeight && validRestingHeartRate

    BackHandler(onBack = onBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    text = "بيانات التدريب",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Black,
                )
            }

            Text(
                text = "العمر ومكان التدريب أساسيان للخطة. بقية البيانات اختيارية وتساعد ركضتك على تقديم معلومات أدق مع تطور التطبيق.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray,
            )

            OutlinedTextField(
                value = ageText,
                onValueChange = { value -> ageText = value.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("العمر") },
                supportingText = {
                    if (ageText.isNotBlank() && !validAge) {
                        Text("أدخل عمرًا بين 14 و100 سنة")
                    } else {
                        Text("يُستخدم لتقدير شدة النبض بشكل محافظ أثناء التمرين.")
                    }
                },
                isError = ageText.isNotBlank() && !validAge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Light),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "بيانات اختيارية",
                        style = MaterialTheme.typography.titleMedium,
                        color = Black,
                    )
                    Text(
                        text = "يمكنك ترك أي حقل فارغًا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray,
                    )

                    Text(
                        text = "الجنس",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Black,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompactChoice(
                            modifier = Modifier.weight(1f),
                            text = "ذكر",
                            selected = sex == RunnerSex.MALE,
                            onClick = { sex = RunnerSex.MALE },
                        )
                        CompactChoice(
                            modifier = Modifier.weight(1f),
                            text = "أنثى",
                            selected = sex == RunnerSex.FEMALE,
                            onClick = { sex = RunnerSex.FEMALE },
                        )
                        CompactChoice(
                            modifier = Modifier.weight(1f),
                            text = "بدون تحديد",
                            selected = sex == RunnerSex.PREFER_NOT_TO_SAY,
                            onClick = { sex = RunnerSex.PREFER_NOT_TO_SAY },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { value ->
                                heightText = value.filter(Char::isDigit).take(3)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("الطول سم") },
                            isError = !validHeight,
                            supportingText = {
                                if (!validHeight) Text("120–230")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { value ->
                                weightText = filterDecimalInput(value, maxLength = 6)
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("الوزن كغ") },
                            isError = !validWeight,
                            supportingText = {
                                if (!validWeight) Text("30–250")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = restingHeartRateText,
                        onValueChange = { value ->
                            restingHeartRateText = value.filter(Char::isDigit).take(3)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نبض الراحة - نبضة/دقيقة") },
                        supportingText = {
                            if (!validRestingHeartRate) {
                                Text("أدخل قيمة بين 35 و120")
                            } else {
                                Text("اختياري؛ أدخله فقط إذا تعرف متوسط نبضك أثناء الراحة.")
                            }
                        },
                        isError = !validRestingHeartRate,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }

            Text(
                text = "مكان التدريب المعتاد",
                style = MaterialTheme.typography.titleMedium,
                color = Black,
            )

            TrainingEnvironmentChoice(
                text = "خارج المنزل",
                selected = environment == TrainingEnvironment.OUTDOOR,
                onClick = { environment = TrainingEnvironment.OUTDOOR },
            )
            TrainingEnvironmentChoice(
                text = "جهاز التردمل",
                selected = environment == TrainingEnvironment.TREADMILL,
                onClick = { environment = TrainingEnvironment.TREADMILL },
            )
            TrainingEnvironmentChoice(
                text = "أستخدم الاثنين",
                selected = environment == TrainingEnvironment.BOTH,
                onClick = { environment = TrainingEnvironment.BOTH },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Light),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "مراجعة السلامة قبل الركض",
                            style = MaterialTheme.typography.titleMedium,
                            color = Black,
                        )
                        Text(
                            text = if (safetyReviewNeeded) {
                                "بدء جلسات الركض متوقف حتى تصبح جاهزًا للمتابعة."
                            } else {
                                "جلسات الركض متاحة حسب خطتك الحالية."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray,
                        )
                    }
                    Switch(
                        checked = safetyReviewNeeded,
                        onCheckedChange = { safetyReviewNeeded = it },
                    )
                }
            }

            Button(
                onClick = {
                    val safeAge = age ?: return@Button
                    onSave(
                        profile.copy(
                            onboardingComplete = true,
                            ageYears = safeAge,
                            sex = sex,
                            heightCm = heightCm,
                            weightKg = weightKg,
                            restingHeartRateBpm = restingHeartRate,
                            trainingEnvironment = environment,
                            safetyReviewNeeded = safetyReviewNeeded,
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) {
                Text("حفظ التعديلات")
            }
        }
    }
}

@Composable
private fun CompactChoice(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 4.dp,
                vertical = 10.dp,
            ),
        ) {
            Text(text, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 4.dp,
                vertical = 10.dp,
            ),
        ) {
            Text(text, color = Black, maxLines = 1)
        }
    }
}

@Composable
private fun TrainingEnvironmentChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Black),
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(text, color = Black)
        }
    }
}

private fun filterDecimalInput(value: String, maxLength: Int): String =
    value.filter { character ->
        character.isDigit() || character == '.' || character == ',' || character == '٫'
    }.take(maxLength)

private fun parseDecimal(raw: String): Double? =
    raw.trim()
        .replace(',', '.')
        .replace('٫', '.')
        .toDoubleOrNull()

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
