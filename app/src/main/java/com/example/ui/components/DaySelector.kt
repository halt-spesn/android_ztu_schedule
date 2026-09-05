package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScheduleDay
import com.example.ui.theme.SleekBorderPurple

@Composable
fun DaySelector(
    days: List<ScheduleDay>,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    todayDateStr: String,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { day ->
            val isSelected = day.dayIndex == selectedDayIndex
            val isToday = day.isMarked || (day.dateStr.trim().isNotEmpty() && day.dateStr.trim() == todayDateStr.trim())

            val bgColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "dayBg"
            )

            val textColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "dayText"
            )

            val shortDayName = when {
                "понед" in day.dayName.lowercase() -> "Пн"
                "вівтор" in day.dayName.lowercase() -> "Вт"
                "серед" in day.dayName.lowercase() -> "Ср"
                "четвер" in day.dayName.lowercase() -> "Чт"
                "п'ятниц" in day.dayName.lowercase() -> "Пт"
                "субот" in day.dayName.lowercase() -> "Сб"
                "неділ" in day.dayName.lowercase() -> "Нд"
                else -> day.dayName.take(2)
            }

            Box(
                modifier = Modifier
                    .width(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .then(
                        if (isToday && !isSelected) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onDaySelected(day.dayIndex) }
                    .testTag("day_selector_${day.dayIndex}")
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = shortDayName,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = day.dateStr.ifEmpty { "—" },
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) textColor.copy(alpha = 0.9f) else textColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Small indicator dot for days with pairs
                    if (day.pairs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    } else {
                        Spacer(modifier = Modifier.height(5.dp))
                    }
                }
            }
        }
    }
}

