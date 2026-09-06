package com.example.ui

import android.os.Build
import com.example.data.repository.ScheduleRepository
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CurrentPairBanner
import com.example.ui.components.DaySelector
import com.example.ui.components.GroupSelectionDialog
import com.example.ui.components.PairCard
import com.example.ui.components.WeekTabs
import com.example.ui.theme.SleekBorderPurple
import com.example.ui.theme.ZtuAccent
import com.example.ui.theme.ZtuPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    // Rotating animation during refresh
    val infiniteTransition = rememberInfiniteTransition(label = "refreshAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.showGroupDialog(true) }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("group_selector_button")
                    ) {
                        // Sleek school badge circle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = uiState.displayGroupName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Вибрати групу",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Zhytomyr Polytechnic • Житомирська політехніка",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = "Пошук дисциплін",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Monet / Material You Theme Settings
                    IconButton(
                        onClick = { viewModel.showThemeDialog(true) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .testTag("theme_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Оформлення та тема",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Home Screen Widget info button
                    IconButton(
                        onClick = { viewModel.showWidgetGuide(true) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .testTag("widget_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Віджет розкладу",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { viewModel.refreshSchedule() },
                        enabled = !uiState.isRefreshing,
                        modifier = Modifier
                            .clip(CircleShape)
                            .testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Оновити розклад",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (uiState.isRefreshing) Modifier.rotate(rotation) else Modifier
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input (Collapsible)
            AnimatedVisibility(visible = isSearchExpanded) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("search_text_field"),
                        placeholder = { Text("Пошук за предметом, аудиторією або викладачем") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Очистити")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Subgroup Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Підгрупа:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                listOf("ALL" to "Всі", "підгр. 1" to "Підгр. 1", "підгр. 2" to "Підгр. 2").forEach { (filter, label) ->
                    val isSelected = uiState.subgroupFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSubgroupFilter(filter) },
                        label = { Text(label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Last updated timestamp & offline badge
                if (uiState.isOffline) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Офлайн",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Weeks selector Tabs
            val weeks = uiState.scheduleData?.weeks ?: emptyList()
            if (weeks.isNotEmpty()) {
                WeekTabs(
                    weeks = weeks,
                    selectedWeekNumber = uiState.selectedWeekNumber,
                    onWeekSelected = { viewModel.selectWeek(it) }
                )
            }

            // Week Note (if present)
            val weekNote = uiState.currentWeek?.note ?: ""
            if (weekNote.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = weekNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Days Strip
            val days = uiState.currentWeek?.days ?: emptyList()
            if (days.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ДНІ ТИЖНЯ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )

                    if (!uiState.isCurrentDaySelected) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { viewModel.jumpToToday() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = "Сьогодні",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Сьогодні",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                DaySelector(
                    days = days,
                    selectedDayIndex = uiState.selectedDayIndex,
                    onDaySelected = { viewModel.selectDay(it) },
                    todayDateStr = uiState.todayDateStr
                )
            }

            // University notice banner (if schedule is adjusting)
            val notice = uiState.scheduleData?.notice ?: ""
            if (notice.isNotEmpty()) {
                Surface(
                    color = ZtuAccent.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, ZtuAccent.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ZtuAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Main pairs content
            val pairs = uiState.filteredPairs
            val activePair = uiState.activePairToday
            val nextPair = uiState.nextPairToday

            if (uiState.isLoading && uiState.scheduleData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Завантаження розкладу ЖТУ...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pairs_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Active or upcoming pair banner on top
                    if (activePair != null) {
                        item {
                            CurrentPairBanner(pair = activePair, isCurrent = true)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    } else if (nextPair != null) {
                        item {
                            CurrentPairBanner(pair = nextPair, isCurrent = false)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    if (pairs.isEmpty()) {
                        item {
                            EmptyDayState(
                                dayName = uiState.currentDay?.dayName ?: "",
                                isFiltered = uiState.searchQuery.isNotEmpty() || uiState.subgroupFilter != "ALL"
                            )
                        }
                    } else {
                        // Sleek "Up Next" section header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "UP NEXT • РОЗКЛАД ПАР",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = "${pairs.size} ${if (pairs.size == 1) "пара" else if (pairs.size in 2..4) "пари" else "пар"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        items(pairs, key = { "${it.pairNumber}_${it.subject}_${it.subgroup}_${it.room}" }) { pair ->
                            val isToday = uiState.isCurrentDaySelected
                            PairCard(
                                pair = pair,
                                isToday = isToday
                            )
                        }
                    }

                    // Sleek Live Widget Preview section from design
                    item {
                        LiveWidgetPreviewCard(
                            nextPair = activePair ?: nextPair ?: pairs.firstOrNull(),
                            onClick = { viewModel.showWidgetGuide(true) }
                        )
                    }

                    // Footer with update time
                    item {
                        if (uiState.lastUpdatedFormatted.isNotEmpty()) {
                            Text(
                                text = "Останнє оновлення розкладу: ${uiState.lastUpdatedFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Group Selection Dialog
    if (uiState.isGroupDialogVisible) {
        GroupSelectionDialog(
            currentGroupId = uiState.displayGroupId,
            groups = uiState.cachedGroups,
            isLoadingGroups = uiState.isLoadingGroups,
            onGroupSelected = { id, name ->
                viewModel.selectGroup(id, name)
            },
            onDismiss = { viewModel.showGroupDialog(false) }
        )
    }

    // Widget Guide Dialog
    if (uiState.isWidgetGuideVisible) {
        WidgetGuideDialog(onDismiss = { viewModel.showWidgetGuide(false) })
    }

    // Theme Settings Dialog (Monet / Material You & Widget Styling)
    if (uiState.isThemeDialogVisible) {
        ThemeSettingsDialog(
            isDynamicColor = uiState.isDynamicColor,
            widgetStyle = uiState.widgetStyle,
            widgetOpacity = uiState.widgetOpacity,
            onToggleDynamicColor = { viewModel.toggleDynamicColor(it) },
            onSelectWidgetStyle = { viewModel.setWidgetStyle(it) },
            onSelectWidgetOpacity = { viewModel.setWidgetOpacity(it) },
            onDismiss = { viewModel.showThemeDialog(false) }
        )
    }
}

@Composable
fun EmptyDayState(
    dayName: String,
    isFiltered: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isFiltered) "Не знайдено занять за обраним фільтром" else "У цей день занять немає!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isFiltered) "Спробуйте очистити пошуковий запит або змінити фільтр підгрупи."
                else "Гарна нагода для самостійної підготовки або відпочинку 🎉",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WidgetGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Віджет розкладу на головний екран",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Додаток містить інтерактивний віджет, який самостійно оновлює розклад занять на сьогодні:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Як додати віджет:\n" +
                            "1. Перейдіть на головний екран смартфона\n" +
                            "2. Затисніть вільне місце на екрані\n" +
                            "3. Оберіть пункт «Віджети» (Widgets)\n" +
                            "4. Знайдіть «Розклад ЖТУ» і перетягніть віджет на екран\n\n" +
                            "✨ Особливості віджета:\n" +
                            "• Показує актуальні пари на сьогодні з аудиторіями та часом\n" +
                            "• Кнопка «Оновити» прямо на віджеті оновлює дані з сайту ЖТУ\n" +
                            "• Натискання на віджет одразу відкриває повний розклад у додатку",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Зрозуміло")
            }
        }
    )
}

@Composable
fun LiveWidgetPreviewCard(
    nextPair: com.example.data.model.SchedulePair?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "LIVE WIDGET PREVIEW • ВІДЖЕТ НА ЕКРАНІ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (nextPair != null) "NEXT: ${nextPair.timeRange.split("-").firstOrNull() ?: nextPair.timeRange}" else "РОЗКЛАД ЖТУ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "ztu.edu.ua",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (nextPair != null) "${nextPair.subject} (${if (nextPair.room.isNotEmpty()) "ауд. ${nextPair.room}" else nextPair.kind})" else "Немає призначених занять",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "Updates automatically for the next 2 weeks • Оновлюється автоматично",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ThemeSettingsDialog(
    isDynamicColor: Boolean,
    widgetStyle: String,
    widgetOpacity: Int,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectWidgetStyle: (String) -> Unit,
    onSelectWidgetOpacity: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isMonetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Оформлення та віджет",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Material You & Glassmorphism",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Monet Engine Switch
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Динамічні кольори Monet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isMonetSupported) {
                                    "Адаптує палітру під шпалери вашого пристрою (Material You / M3)"
                                } else {
                                    "Потрібен Android 12+ для динамічних кольорів Monet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isDynamicColor && isMonetSupported,
                            onCheckedChange = { onToggleDynamicColor(it) },
                            enabled = isMonetSupported,
                            modifier = Modifier.testTag("monet_toggle_switch")
                        )
                    }
                }

                // Section 2: System palette swatches
                Text(
                    text = "ПОТОЧНА ПАЛІТРА СИСТЕМИ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Primary" to MaterialTheme.colorScheme.primary,
                        "Secondary" to MaterialTheme.colorScheme.secondary,
                        "Tertiary" to MaterialTheme.colorScheme.tertiary,
                        "Container" to MaterialTheme.colorScheme.primaryContainer,
                        "Surface" to MaterialTheme.colorScheme.surfaceVariant
                    ).forEach { (label, color) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Section 3: Widget Appearance Mode
                Text(
                    text = "ДИЗАЙН ВІДЖЕТА НА РОБОЧОМУ СТОЛІ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "✨ Скло" to ScheduleRepository.WIDGET_STYLE_GLASS,
                        "🎨 Monet" to ScheduleRepository.WIDGET_STYLE_MONET,
                        "🌑 Темний" to ScheduleRepository.WIDGET_STYLE_DARK
                    ).forEach { (label, styleKey) ->
                        val isSelected = widgetStyle == styleKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectWidgetStyle(styleKey) },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("widget_style_$styleKey")
                        )
                    }
                }

                // Section 4: Widget Opacity
                if (widgetStyle == ScheduleRepository.WIDGET_STYLE_GLASS || widgetStyle == ScheduleRepository.WIDGET_STYLE_SYSTEM) {
                    Text(
                        text = "ПРОЗОРІСТЬ МАТОВОГО СКЛА",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "70% Прозоре" to 70,
                            "85% Матове" to 85,
                            "100% Суцільне" to 100
                        ).forEach { (label, opacityVal) ->
                            val isSelected = widgetOpacity == opacityVal
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectWidgetOpacity(opacityVal) },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("widget_opacity_$opacityVal")
                            )
                        }
                    }
                }

                // Section 5: Live Widget Preview Card
                Text(
                    text = "ПОПЕРЕДНІЙ ПЕРЕГЛЯД ВІДЖЕТА",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Simulated Wallpaper + Frosted Glass / Monet Widget
                val isMonetApplicable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val isMonetActive = widgetStyle == ScheduleRepository.WIDGET_STYLE_MONET || 
                                    ((widgetStyle == ScheduleRepository.WIDGET_STYLE_GLASS || widgetStyle == ScheduleRepository.WIDGET_STYLE_SYSTEM) && isMonetApplicable)

                val previewBg = when {
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_MONET -> MaterialTheme.colorScheme.surface
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_LIGHT -> Color(0xFFFDF8FD)
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_DARK -> Color(0xFF14100F)
                    isMonetActive -> {
                        // Frosted glass tinted with dynamic surface neutral
                        MaterialTheme.colorScheme.surface.copy(alpha = if (widgetOpacity <= 75) 0.72f else 0.85f)
                    }
                    else -> Color(0xFF1E1715).copy(alpha = if (widgetOpacity <= 75) 0.72f else 0.88f)
                }
                val previewCardBg = when {
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_MONET -> MaterialTheme.colorScheme.surfaceVariant
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_LIGHT -> Color(0xFFECE5ED)
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_DARK -> Color(0xFF2B2220)
                    isMonetActive -> {
                        // Subtle frosted acrylic card tinted with Monet surface variant
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                    else -> Color(0xFFFFFFFF).copy(alpha = 0.14f)
                }
                val previewTitleColor = when {
                    isMonetActive -> MaterialTheme.colorScheme.onSurface
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_LIGHT -> Color(0xFF1D1B20)
                    else -> Color(0xFFF6EEF5)
                }
                val previewSubtitleColor = when {
                    isMonetActive -> MaterialTheme.colorScheme.onSurfaceVariant
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_LIGHT -> Color(0xFF49454F)
                    else -> Color(0xFFD6C8CE)
                }
                val previewAccent = when {
                    isMonetActive -> MaterialTheme.colorScheme.primary
                    widgetStyle == ScheduleRepository.WIDGET_STYLE_LIGHT -> Color(0xFF6750A4)
                    else -> Color(0xFFFF8A65)
                }

                val previewRootBorder = when {
                    isMonetActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    else -> BorderStroke(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.22f))
                }
                val previewBorder = when {
                    isMonetActive -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    else -> BorderStroke(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.12f))
                }
                val previewChipTextColor = when {
                    isMonetActive -> MaterialTheme.colorScheme.onPrimary
                    else -> Color.White
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = previewBg,
                    border = previewRootBorder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = previewAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ЖТУ • КІ-26-1",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = previewTitleColor
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = previewAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preview Pair Row
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = previewCardBg,
                            border = previewBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(previewAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = previewChipTextColor)
                                }
                                Column {
                                    Text(
                                        text = "Комп'ютерна графіка",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = previewTitleColor
                                    )
                                    Text(
                                        text = "08:30-09:50 • Лекція • ауд. 233",
                                        fontSize = 9.sp,
                                        color = previewSubtitleColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_theme_dialog_button")
            ) {
                Text("Застосувати", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(26.dp)
    )
}
