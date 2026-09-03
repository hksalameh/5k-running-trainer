package com.rakdatak.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val Orange = Color(0xFFFF6D00)
private val Black = Color(0xFF141414)
private val Gray = Color(0xFF747474)

@Composable
fun OnboardingScreen(
    onComplete: (ageYears: Int, environment: TrainingEnvironment, safetyReviewNeeded: Boolean) -> Unit,
) {
    var ageText by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf(TrainingEnvironment.BOTH) }
    var safetyAnswer by remember { mutableStateOf<Boolean?>(null) }

    val age = ageText.toIntOrNull()
    val canContinue = age != null && age in 14..100 && safetyAnswer != null

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "أهلًا بك في ركضتك",
                style = MaterialTheme.typography.headlineMedium,
                color = Black,
            )
            Text(
                text = "ثلاث معلومات سريعة فقط حتى نبدأ بشكل أنسب لك. لن تظهر هذه الشاشة كل مرة.",
                style = MaterialTheme.typography.bodyLarge,
                color = Gray,
            )

            Text(
                text = "1. كم عمرك؟",
                style = MaterialTheme.typography.titleMedium,
                color = Black,
            )
            OutlinedTextField(
                value = ageText,
                onValueChange = { input -> ageText = input.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("العمر") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text("نستخدم العمر فقط لتقدير شدة النبض، وليس كتشخيص طبي.")
                },
            )

            Text(
                text = "2. أين تتوقع أن تتدرب غالبًا؟",
                style = MaterialTheme.typography.titleMedium,
                color = Black,
            )
            EnvironmentChoice(
                selected = environment,
                onSelected = { environment = it },
            )

            Text(
                text = "3. هل لديك مشكلة قلب معروفة، ألم صدر أو دوخة مع الجهد، أو إصابة تمنعك من الركض؟",
                style = MaterialTheme.typography.titleMedium,
                color = Black,
            )
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
                Text(
                    text = "سنحفظ هذه المعلومة ونوقف زيادة شدة الخطة. قبل بدء الركض ننصح بمراجعة مختص.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    onComplete(age!!, environment, safetyAnswer == true)
                },
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) {
                Text("ابدأ")
            }
        }
    }
}

@Composable
private fun EnvironmentChoice(
    selected: TrainingEnvironment,
    onSelected: (TrainingEnvironment) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceButton(
            modifier = Modifier.fillMaxWidth(),
            text = "خارج البيت",
            selected = selected == TrainingEnvironment.OUTDOOR,
            onClick = { onSelected(TrainingEnvironment.OUTDOOR) },
        )
        ChoiceButton(
            modifier = Modifier.fillMaxWidth(),
            text = "تردمل",
            selected = selected == TrainingEnvironment.TREADMILL,
            onClick = { onSelected(TrainingEnvironment.TREADMILL) },
        )
        ChoiceButton(
            modifier = Modifier.fillMaxWidth(),
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
            colors = ButtonDefaults.buttonColors(containerColor = Black),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(text, color = Black)
        }
    }
}
