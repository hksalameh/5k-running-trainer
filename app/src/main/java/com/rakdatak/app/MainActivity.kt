package com.rakdatak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection

private val RakdatakOrange = Color(0xFFFF6D00)
private val RakdatakBlack = Color(0xFF141414)
private val RakdatakGray = Color(0xFF747474)
private val RakdatakSurface = Color(0xFFF5F5F5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RakdatakHomeScreen()
                }
            }
        }
    }
}

@Composable
private fun RakdatakHomeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ركضتك",
                style = MaterialTheme.typography.headlineMedium,
                color = RakdatakBlack,
            )
            Text(
                text = "خطوة ثابتة اليوم، فرق كبير بكرة.",
                style = MaterialTheme.typography.bodyLarge,
                color = RakdatakGray,
            )

            GoalCard()
            NextWorkoutCard()

            Text(
                text = "تقدمك",
                style = MaterialTheme.typography.titleLarge,
                color = RakdatakBlack,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "0",
                    label = "تمارين",
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "0.0",
                    label = "كم",
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = "0",
                    label = "دقيقة",
                )
            }
        }
    }
}

@Composable
private fun GoalCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RakdatakBlack),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "هدفك",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "5 كم + 30 دقيقة",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "نبدأ من الصفر ونبني قدرتك تدريجيًا",
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 0.04f },
                    modifier = Modifier.size(64.dp),
                    color = RakdatakOrange,
                    trackColor = Color(0xFF333333),
                )
                Text(
                    text = "ابدأ",
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun NextWorkoutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RakdatakSurface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "التمرين القادم",
                        color = RakdatakGray,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "بداية هادئة",
                        color = RakdatakBlack,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = RakdatakOrange,
                    modifier = Modifier.size(36.dp),
                )
            }

            Text(
                text = "إحماء خفيف ثم مشي وركض بفترات قصيرة ومريحة.",
                color = RakdatakGray,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = RakdatakOrange,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "  نبض مراقب",
                        color = RakdatakGray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RakdatakOrange),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "  ابدأ التمرين",
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier
            .background(RakdatakSurface, RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = RakdatakBlack,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = label,
            color = RakdatakGray,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
