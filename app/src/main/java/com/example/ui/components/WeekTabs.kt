package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScheduleWeek

@Composable
fun WeekTabs(
    weeks: List<ScheduleWeek>,
    selectedWeekNumber: Int,
    onWeekSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hide week 0 (not relevant during the semester), keeping it safely preserved in data structures
    val visibleWeeks = weeks.filter { it.weekNumber != 0 }.ifEmpty { weeks }
    if (visibleWeeks.isEmpty()) return

    val selectedIndex = visibleWeeks.indexOfFirst { it.weekNumber == selectedWeekNumber }
        .let { if (it >= 0) it else 0 }

    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        indicator = { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            }
        }
    ) {
        visibleWeeks.forEachIndexed { index, week ->
            val isSelected = index == selectedIndex
            Tab(
                selected = isSelected,
                onClick = { onWeekSelected(week.weekNumber) },
                text = {
                    Text(
                        text = week.weekTitle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}
