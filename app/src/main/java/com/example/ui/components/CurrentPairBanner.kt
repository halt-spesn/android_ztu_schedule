package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SchedulePair
import com.example.ui.theme.SleekBorderPurple
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun CurrentPairBanner(
    pair: SchedulePair,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pair.timeRange, isCurrent) {
        while (isCurrent) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    // Calculate progress fraction for the ongoing pair
    val progressFraction = remember(pair.timeRange, isCurrent, nowMillis) {
        if (!isCurrent) 0f
        else {
            try {
                val parts = pair.timeRange.split("-")
                if (parts.size == 2) {
                    val fmt = DateTimeFormatter.ofPattern("H:mm")
                    val start = LocalTime.parse(parts[0].trim(), fmt)
                    val end = LocalTime.parse(parts[1].trim(), fmt)
                    val now = java.time.Instant.ofEpochMilli(nowMillis)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                    val startMin = start.hour * 60 + start.minute
                    val endMin = end.hour * 60 + end.minute
                    val nowMin = now.hour * 60 + now.minute
                    if (endMin > startMin) {
                        ((nowMin - startMin).toFloat() / (endMin - startMin)).coerceIn(0f, 1f)
                    } else 0.5f
                } else 0.5f
            } catch (_: Exception) {
                0.5f
            }
        }
    }

    // Remaining minutes calculation
    val remainingText = remember(pair.timeRange, isCurrent, nowMillis) {
        if (!isCurrent) "СЬОГОДНІ"
        else {
            try {
                val parts = pair.timeRange.split("-")
                if (parts.size == 2) {
                    val fmt = DateTimeFormatter.ofPattern("H:mm")
                    val end = LocalTime.parse(parts[1].trim(), fmt)
                    val now = java.time.Instant.ofEpochMilli(nowMillis)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                    val endMin = end.hour * 60 + end.minute
                    val nowMin = now.hour * 60 + now.minute
                    val diff = endMin - nowMin
                    if (diff > 0) "$diff ХВ ЗАЛИШИЛОСЬ" else "ТРИВАЄ"
                } else "ТРИВАЄ"
            } catch (_: Exception) {
                "ТРИВАЄ"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("current_pair_banner")
    ) {
        // Section Header Row: Title & Badge Tag
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isCurrent) "ONGOING NOW • ЗАРАЗ ТРИВАЄ" else "UP NEXT • НАСТУПНА ПАРА",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            // Sleek rose badge tag (#FFD8E4 / #31111D)
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = remainingText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sleek Hero Card (#E8DEF8, rounded 28dp, border #D0BCFF)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(1.dp, SleekBorderPurple),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pair.subject,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val metaParts = buildList {
                            if (pair.kind.isNotEmpty()) add(pair.kind)
                            if (pair.room.isNotEmpty()) add("ауд. ${pair.room}")
                            if (pair.subgroup.isNotEmpty()) add(pair.subgroup)
                        }

                        Text(
                            text = metaParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (pair.teacher.isNotEmpty()) {
                            Text(
                                text = pair.teacher,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Subject Icon badge in deep purple
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress track or time range indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.28f))
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }

                    Text(
                        text = pair.timeRange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
