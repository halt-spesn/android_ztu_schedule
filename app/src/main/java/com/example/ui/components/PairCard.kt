package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LessonType
import com.example.data.model.PairStatus
import com.example.data.model.SchedulePair
import com.example.ui.theme.ColorLab
import com.example.ui.theme.ColorLecture
import com.example.ui.theme.ColorMeeting
import com.example.ui.theme.ColorPractice
import com.example.ui.theme.SleekBorderPurple
import com.example.ui.theme.SleekLightOutlineVariant

@Composable
fun PairCard(
    pair: SchedulePair,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status = pair.calculateStatus(isToday)
    val isCurrent = status == PairStatus.CURRENT
    val isPast = status == PairStatus.PAST

    val typeColor = when (pair.lessonType) {
        LessonType.LECTURE -> ColorLecture
        LessonType.PRACTICE -> ColorPractice
        LessonType.LAB -> ColorLab
        LessonType.MEETING -> ColorMeeting
        LessonType.OTHER -> MaterialTheme.colorScheme.primary
    }

    val startTime = remember(pair.timeRange) {
        pair.timeRange.split("-").firstOrNull()?.trim() ?: pair.timeRange
    }

    val cardBorder = when {
        isCurrent -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isPast -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pair_card_${pair.pairNumber}_${pair.id}")
            .clickable {
                val clip = "${pair.pairNumber} пара: ${pair.subject} (${pair.kind})\nЧас: ${pair.timeRange}\nАуд: ${pair.room}\nВикладач: ${pair.teacher}"
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Розклад", clip))
                Toast.makeText(context, "Інформацію про пару скопійовано!", Toast.LENGTH_SHORT).show()
            },
        shape = RoundedCornerShape(18.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Time & Pair Number (e.g. 12:20 / PAIR 3)
            Column(
                modifier = Modifier.width(52.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = startTime,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${pair.pairNumber} ПАРА",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Subtle vertical separator line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary
                        else typeColor.copy(alpha = if (isPast) 0.2f else 0.6f)
                    )
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Middle Column: Subject & Meta
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = pair.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Meta row: Kind • Room • Subgroup
                val metaParts = buildList {
                    if (pair.kind.isNotEmpty()) add(pair.kind)
                    if (pair.room.isNotEmpty()) add("ауд. ${pair.room}")
                    if (pair.subgroup.isNotEmpty()) add(pair.subgroup)
                }

                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                if (pair.teacher.isNotEmpty()) {
                    Text(
                        text = pair.teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Column: Status indicator or subtle chevron
            when {
                isCurrent -> {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Зараз",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                isPast && isToday -> {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Завершено",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

