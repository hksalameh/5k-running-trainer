package com.rakdatak.app.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF111111)
private val SoftBlack = Color(0xFF1B1B1B)
private val Gray = Color(0xFF737373)
private val LightGray = Color(0xFFF3F3F3)

@Composable
fun OnboardingScreen(
    onComplete: (ageYears: Int, environment: TrainingEnvironment, safetyReviewNeeded: Boolean) -> Unit,
) {
    var showQuestions by remember { mutableStateOf(false) }
    var ageText by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf(TrainingEnvironment.BOTH) }
    var safetyAnswer by remember { mutableStateOf<Boolean?>(null) }

    val age = ageText.toIntOrNull()
    val canContinue = age != null && age in 14..100 && safetyAnswer != null

    if (!showQuestions) {
        WelcomeScreen(onStart = { showQuestions = true })
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CompactHeader()

            Text(
                text = "خلّينا نضبط خطتك",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Black,
            )
            Text(
                text = "3 معلومات فقط، وبعدها تبدأ رحلتك. تقدر تغيّرها لاحقًا من الإعدادات.",
                style = MaterialTheme.typography.bodyMedium,
                color = Gray,
            )

            QuestionCard(number = "01", title = "كم عمرك؟") {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { input -> ageText = input.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("العمر") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text("نستخدمه فقط لتقدير شدة التمرين والنبض بشكل أكثر تحفظًا.")
                    },
                )
            }

            QuestionCard(number = "02", title = "وين غالبًا راح تتمرن؟") {
                EnvironmentChoice(
                    selected = environment,
                    onSelected = { environment = it },
                )
            }

            QuestionCard(
                number = "03",
                title = "هل عندك مشكلة قلب معروفة، ألم صدر أو دوخة مع الجهد، أو إصابة تمنعك من الركض؟",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChoiceButton(
                        modifier = Modifier.weight(1f),
                        text = "لا",
                        selected = safetyAnswer == false,
                        onClick = { safetyAnswer = false },
                    )
                    ChoiceButton(
                        modifier = Modifier.weight(1f),
                        text = "نعم",
                        selected = safetyAnswer == true,
                        onClick = { safetyAnswer = true },
                    )
                }
                if (safetyAnswer == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFFF3E8),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = "راح نوقف زيادة الشدة تلقائيًا وننصح بمراجعة مختص قبل بدء الركض.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF784000),
                        )
                    }
                }
            }

            Button(
                onClick = { onComplete(age!!, environment, safetyAnswer == true) },
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    disabledContainerColor = Color(0xFFFFC59C),
                ),
            ) {
                Text("جاهز، ابدأ خطتي", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showQuestions = false },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("رجوع", color = Black)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B0B0B), Color(0xFF191919), Color(0xFF0B0B0B)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ركضتك",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Surface(
                    color = Orange.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "من الصفر إلى 5 كم",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Orange,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                RakdatakHeroArtwork()

                Text(
                    text = "ابدأ بهدوء.\nوتقدّم بذكاء.",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "خطة تدريب تتكيّف مع تقدمك، وتوصلك تدريجيًا إلى 5 كم و30 دقيقة ركض متواصل.",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 25.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeaturePill(modifier = Modifier.weight(1f), big = "8", small = "أسابيع")
                    FeaturePill(modifier = Modifier.weight(1f), big = "3", small = "أيام بالأسبوع")
                    FeaturePill(modifier = Modifier.weight(1f), big = "5K", small = "+ 30 دقيقة")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) {
                    Text("ابدأ رحلتك", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Text(
                    text = "الأسئلة السريعة تظهر مرة واحدة فقط",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RakdatakHeroArtwork() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF272727), Color(0xFF111111)),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                color = Orange.copy(alpha = 0.16f),
                radius = w * 0.34f,
                center = Offset(w * 0.82f, h * 0.18f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = w * 0.52f,
                center = Offset(w * 0.1f, h * 1.02f),
            )

            val route = Path().apply {
                moveTo(-w * 0.05f, h * 0.79f)
                cubicTo(
                    w * 0.23f, h * 0.35f,
                    w * 0.50f, h * 0.98f,
                    w * 1.08f, h * 0.33f,
                )
            }
            drawPath(
                path = route,
                color = Color.White.copy(alpha = 0.12f),
                style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Round),
            )
            drawPath(
                path = route,
                color = Orange,
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
            )

            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(w * 0.12f, h * 0.59f),
            )
            drawCircle(
                color = Orange,
                radius = 11.dp.toPx(),
                center = Offset(w * 0.88f, h * 0.48f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                text = "YOUR RUN",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "5 KM",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun FeaturePill(
    modifier: Modifier,
    big: String,
    small: String,
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(big, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(
                small,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CompactHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Black,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ركضتك", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text(
                    "إعداد سريع للخطة",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Orange),
                contentAlignment = Alignment.Center,
            ) {
                Text("5K", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun QuestionCard(
    number: String,
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LightGray,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = Orange,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = number,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Black,
                )
            }
            content()
        }
    }
}

@Composable
private fun EnvironmentChoice(
    selected: TrainingEnvironment,
    onSelected: (TrainingEnvironment) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceButton(
            modifier = Modifier.weight(1f),
            text = "خارجي",
            selected = selected == TrainingEnvironment.OUTDOOR,
            onClick = { onSelected(TrainingEnvironment.OUTDOOR) },
        )
        ChoiceButton(
            modifier = Modifier.weight(1f),
            text = "تردمل",
            selected = selected == TrainingEnvironment.TREADMILL,
            onClick = { onSelected(TrainingEnvironment.TREADMILL) },
        )
        ChoiceButton(
            modifier = Modifier.weight(1f),
            text = "الاثنين",
            selected = selected == TrainingEnvironment.BOTH,
            onClick = { onSelected(TrainingEnvironment.BOTH) },
        )
    }
}

@Composable
private fun ChoiceButton(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = SoftBlack),
            shape = RoundedCornerShape(14.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(text, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(text, color = Black, maxLines = 1)
        }
    }
}
