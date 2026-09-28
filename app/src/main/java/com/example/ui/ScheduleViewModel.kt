package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZtuScheduleApplication
import com.example.data.model.PairStatus
import com.example.data.model.ScheduleData
import com.example.data.model.ScheduleDay
import com.example.data.model.ScheduleGroup
import com.example.data.model.SchedulePair
import com.example.data.model.ScheduleWeek
import com.example.data.repository.ScheduleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val scheduleData: ScheduleData? = null,
    val selectedGroupId: String = "",
    val selectedGroupName: String = "",
    val isDynamicColor: Boolean = true,
    val isOledMode: Boolean = false,
    val isThemeDialogVisible: Boolean = false,
    val widgetStyle: String = ScheduleRepository.WIDGET_STYLE_MONET,
    val widgetOpacity: Int = 85,
    val selectedWeekNumber: Int = 1,
    val selectedDayIndex: Int = 0,
    val subgroupFilter: String = "ALL", // "ALL", "підгр. 1", "підгр. 2"
    val onlyWithNotes: Boolean = false,
    val searchQuery: String = "",
    val cachedGroups: List<ScheduleGroup> = emptyList(),
    val isLoadingGroups: Boolean = false,
    val isGroupDialogVisible: Boolean = false,
    val isWidgetGuideVisible: Boolean = false,
    val isOffline: Boolean = false,
    val todayDateStr: String = "",
    val clockTick: Long = 0L,
    val isCabinetLoggedIn: Boolean = false,
    val cabinetUsername: String = "",
    val cabinetStudentName: String = "",
    val useCabinetSchedule: Boolean = false,
    val isCabinetDialogVisible: Boolean = false,
    val isCabinetLoading: Boolean = false,
    val cabinetErrorMessage: String? = null
) {
    val displayGroupName: String
        get() = scheduleData?.groupName?.ifBlank { null }
            ?: selectedGroupName.ifBlank { null }
            ?: "КІ-26-1"

    val displayGroupId: String
        get() = scheduleData?.groupId?.ifBlank { null }
            ?: selectedGroupId.ifBlank { null }
            ?: "612"

    val currentWeek: ScheduleWeek?
        get() = scheduleData?.weeks?.find { it.weekNumber == selectedWeekNumber }
            ?: scheduleData?.weeks?.find { it.weekNumber != 0 }
            ?: scheduleData?.weeks?.firstOrNull()

    val currentDay: ScheduleDay?
        get() = currentWeek?.days?.find { it.dayIndex == selectedDayIndex }
            ?: currentWeek?.days?.firstOrNull()

    val currentDayNotesCount: Int
        get() = currentDay?.pairs?.count { it.teacherNote.isNotBlank() } ?: 0

    val currentWeekNotesCount: Int
        get() = currentWeek?.days?.sumOf { day -> day.pairs.count { it.teacherNote.isNotBlank() } } ?: 0

    val totalNotesCount: Int
        get() = scheduleData?.weeks?.sumOf { w -> w.days.sumOf { d -> d.pairs.count { p -> p.teacherNote.isNotBlank() } } } ?: 0

    val filteredPairs: List<SchedulePair>
        get() {
            val pairs = currentDay?.pairs ?: emptyList()
            return pairs.filter { pair ->
                val matchesSubgroup = when (subgroupFilter) {
                    "ALL" -> true
                    "підгр. 1" -> pair.subgroup.isEmpty() || "1" in pair.subgroup
                    "підгр. 2" -> pair.subgroup.isEmpty() || "2" in pair.subgroup
                    else -> true
                }
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    val q = searchQuery.trim().lowercase(Locale.ROOT)
                    pair.subject.lowercase(Locale.ROOT).contains(q) ||
                    pair.teacher.lowercase(Locale.ROOT).contains(q) ||
                    pair.room.lowercase(Locale.ROOT).contains(q) ||
                    pair.kind.lowercase(Locale.ROOT).contains(q)
                }
                val matchesNotes = if (onlyWithNotes) pair.teacherNote.isNotBlank() else true
                matchesSubgroup && matchesSearch && matchesNotes
            }
        }

    val isCurrentDaySelected: Boolean
        get() = (currentDay?.isMarked == true) ||
                (currentDay?.dateStr?.trim()?.isNotEmpty() == true && currentDay?.dateStr?.trim() == todayDateStr.trim())

    val activePairToday: SchedulePair?
        get() {
            if (!isCurrentDaySelected) return null
            return filteredPairs.find { it.calculateStatus(true) == PairStatus.CURRENT }
        }

    val nextPairToday: SchedulePair?
        get() {
            if (!isCurrentDaySelected) return null
            return filteredPairs.find { it.calculateStatus(true) == PairStatus.UPCOMING }
        }

    val lastUpdatedFormatted: String
        get() {
            val millis = scheduleData?.lastUpdatedMillis ?: return ""
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(millis))
        }
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduleRepository =
        (application as? ZtuScheduleApplication)?.repository
            ?: ScheduleRepository(application)

    private var hasUserManuallyNavigated: Boolean = false
    private var observeJob: Job? = null
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(
        ScheduleUiState(
            selectedGroupId = repository.getSelectedGroupId(),
            selectedGroupName = repository.getSelectedGroupName(),
            isDynamicColor = repository.isDynamicColorEnabled(),
            isOledMode = repository.isOledModeEnabled(),
            widgetStyle = repository.getWidgetStyle(),
            widgetOpacity = repository.getWidgetOpacity(),
            subgroupFilter = repository.getSubgroupFilter(),
            todayDateStr = getTodayFormattedDate(),
            selectedDayIndex = getCurrentDayIndex(),
            isCabinetLoggedIn = repository.isCabinetLoggedIn(),
            cabinetUsername = repository.getCabinetUsername(),
            cabinetStudentName = repository.getCabinetStudentName(),
            useCabinetSchedule = repository.isCabinetLoggedIn() && repository.getScheduleSource() == ScheduleRepository.SOURCE_CABINET
        )
    )
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        val groupId = repository.getSelectedGroupId()
        observeDatabaseSchedule(groupId)
        // Refresh from remote on startup
        refreshSchedule(silent = false)
        loadGroups()
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(clockTick = System.currentTimeMillis()) }
                delay(30_000)
            }
        }
    }

    private fun observeDatabaseSchedule(groupId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeSchedule(groupId).collect { cachedData ->
                _uiState.update { state ->
                    // Guard against race conditions if group changed
                    if (state.selectedGroupId != groupId) return@update state

                    if (cachedData != null) {
                        val targetSelection = if (!hasUserManuallyNavigated) {
                            determineCurrentWeekAndDay(cachedData)
                        } else null

                        val weekNum = targetSelection?.first
                            ?: if (cachedData.weeks.any { it.weekNumber == state.selectedWeekNumber && it.weekNumber != 0 }) {
                                state.selectedWeekNumber
                            } else {
                                cachedData.weeks.firstOrNull { it.weekNumber != 0 }?.weekNumber ?: 1
                            }

                        val dayIdx = targetSelection?.second
                            ?: state.selectedDayIndex

                        state.copy(
                            isLoading = false,
                            scheduleData = cachedData,
                            selectedGroupName = cachedData.groupName.ifBlank { state.selectedGroupName },
                            selectedWeekNumber = weekNum,
                            selectedDayIndex = dayIdx,
                            todayDateStr = getTodayFormattedDate()
                        )
                    } else {
                        // Keep scheduleData null if not yet cached for this group
                        state
                    }
                }
            }
        }
    }

    private fun refreshScheduleForGroup(groupId: String, silent: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            val result = repository.refreshSchedule(groupId)
            if (result.isSuccess) {
                val data = result.getOrNull()
                _uiState.update { state ->
                    if (state.selectedGroupId != groupId) return@update state
                    val updatedData = data ?: state.scheduleData
                    val targetSelection = if (!hasUserManuallyNavigated && updatedData != null) {
                        determineCurrentWeekAndDay(updatedData)
                    } else null

                    val weekNum = targetSelection?.first
                        ?: if (updatedData?.weeks?.any { it.weekNumber == state.selectedWeekNumber && it.weekNumber != 0 } == true) {
                            state.selectedWeekNumber
                        } else {
                            updatedData?.weeks?.firstOrNull { it.weekNumber != 0 }?.weekNumber ?: 1
                        }

                    state.copy(
                        isRefreshing = false,
                        isLoading = false,
                        isOffline = false,
                        scheduleData = updatedData,
                        selectedGroupName = updatedData?.groupName?.ifBlank { state.selectedGroupName } ?: state.selectedGroupName,
                        selectedWeekNumber = weekNum,
                        selectedDayIndex = targetSelection?.second ?: state.selectedDayIndex,
                        todayDateStr = getTodayFormattedDate()
                    )
                }
            } else {
                _uiState.update { state ->
                    if (state.selectedGroupId != groupId) return@update state
                    val hasCached = state.scheduleData != null
                    state.copy(
                        isRefreshing = false,
                        isLoading = false,
                        isOffline = true,
                        errorMessage = if (!silent) {
                            val err = result.exceptionOrNull()?.message
                            if (err?.contains("авториз", ignoreCase = true) == true || err?.contains("увійдіть", ignoreCase = true) == true) {
                                err
                            } else if (hasCached) {
                                "Не вдалося оновити розклад. Показано збережену версію."
                            } else {
                                "Не вдалося завантажити розклад для групи. Перевірте з'єднання з інтернетом."
                            }
                        } else null
                    )
                }
            }
        }
    }

    fun refreshSchedule(silent: Boolean = false) {
        val currentGroupId = _uiState.value.selectedGroupId.ifBlank { repository.getSelectedGroupId() }
        refreshScheduleForGroup(currentGroupId, silent)
    }

    fun selectGroup(groupId: String, groupName: String) {
        hasUserManuallyNavigated = false
        repository.setSelectedGroupId(groupId, groupName)

        // Immediately clear old group data so user does not see previous group's schedule
        _uiState.update {
            it.copy(
                selectedGroupId = groupId,
                selectedGroupName = groupName,
                scheduleData = null,
                isLoading = true,
                errorMessage = null,
                isGroupDialogVisible = false
            )
        }

        // Cancel previous database observation and start observing new group
        observeDatabaseSchedule(groupId)

        // Fetch fresh schedule from server for the new group
        refreshScheduleForGroup(groupId, silent = false)
    }

    fun toggleDynamicColor(enabled: Boolean) {
        repository.setDynamicColorEnabled(enabled)
        _uiState.update { it.copy(isDynamicColor = enabled) }
    }
    fun toggleOledMode(enabled: Boolean) {
        repository.setOledModeEnabled(enabled)
        _uiState.update { it.copy(isOledMode = enabled) }
    }

    fun setWidgetStyle(style: String) {
        repository.setWidgetStyle(style)
        _uiState.update { it.copy(widgetStyle = style) }
    }

    fun setWidgetOpacity(opacity: Int) {
        repository.setWidgetOpacity(opacity)
        _uiState.update { it.copy(widgetOpacity = opacity) }
    }

    fun showThemeDialog(visible: Boolean) {
        _uiState.update { it.copy(isThemeDialogVisible = visible) }
    }

    fun selectWeek(weekNumber: Int) {
        hasUserManuallyNavigated = true
        _uiState.update { it.copy(selectedWeekNumber = weekNumber) }
    }

    fun selectDay(dayIndex: Int) {
        hasUserManuallyNavigated = true
        _uiState.update { it.copy(selectedDayIndex = dayIndex) }
    }

    fun jumpToToday() {
        hasUserManuallyNavigated = false
        val data = _uiState.value.scheduleData ?: return
        val target = determineCurrentWeekAndDay(data) ?: return
        _uiState.update {
            it.copy(
                selectedWeekNumber = target.first,
                selectedDayIndex = target.second,
                todayDateStr = getTodayFormattedDate()
            )
        }
    }

    fun setSubgroupFilter(subgroup: String) {
        repository.setSubgroupFilter(subgroup)
        _uiState.update { it.copy(subgroupFilter = subgroup) }
    }

    fun toggleOnlyWithNotes(enabled: Boolean) {
        _uiState.update { it.copy(onlyWithNotes = enabled) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showGroupDialog(visible: Boolean) {
        _uiState.update { it.copy(isGroupDialogVisible = visible) }
        if (visible && _uiState.value.cachedGroups.isEmpty()) {
            loadGroups()
        }
    }

    fun showWidgetGuide(visible: Boolean) {
        _uiState.update { it.copy(isWidgetGuideVisible = visible) }
    }

    fun showCabinetDialog(visible: Boolean) {
        _uiState.update { it.copy(isCabinetDialogVisible = visible, cabinetErrorMessage = null) }
    }

    fun loginToCabinet(username: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCabinetLoading = true, cabinetErrorMessage = null) }
            val result = repository.cabinetLogin(username, password)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isCabinetLoading = false,
                        isCabinetLoggedIn = true,
                        cabinetUsername = repository.getCabinetUsername(),
                        cabinetStudentName = repository.getCabinetStudentName(),
                        useCabinetSchedule = true,
                        isCabinetDialogVisible = false
                    )
                }
                refreshSchedule(silent = false)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Помилка входу в кабінет"
                _uiState.update {
                    it.copy(
                        isCabinetLoading = false,
                        cabinetErrorMessage = err
                    )
                }
            }
        }
    }

    fun logoutFromCabinet() {
        repository.cabinetLogout()
        _uiState.update {
            it.copy(
                isCabinetLoggedIn = false,
                cabinetUsername = "",
                cabinetStudentName = "",
                useCabinetSchedule = false
            )
        }
        refreshSchedule()
    }

    fun toggleUseCabinetSchedule(enabled: Boolean) {
        repository.setScheduleSource(
            if (enabled) ScheduleRepository.SOURCE_CABINET else ScheduleRepository.SOURCE_PUBLIC
        )
        _uiState.update { it.copy(useCabinetSchedule = enabled) }
        refreshSchedule()
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadGroups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGroups = true) }
            val groups = repository.loadAllGroups()
            _uiState.update { it.copy(cachedGroups = groups, isLoadingGroups = false) }
        }
    }

    fun determineCurrentWeekAndDay(scheduleData: ScheduleData): Pair<Int, Int>? {
        val weeks = scheduleData.weeks
        if (weeks.isEmpty()) return null

        val todayDate = getTodayFormattedDate()

        // 1. Priority: check university's marked day (th.is-marked in HTML), preferring non-zero weeks
        for (week in weeks) {
            if (week.weekNumber == 0 && weeks.any { it.weekNumber != 0 }) continue
            val markedDay = week.days.find { it.isMarked }
            if (markedDay != null) {
                return Pair(week.weekNumber, markedDay.dayIndex)
            }
        }

        // 2. Priority: exact date match with today (dd.MM)
        for (week in weeks) {
            val matchingDay = week.days.find { it.dateStr.trim() == todayDate }
            if (matchingDay != null) {
                return Pair(week.weekNumber, matchingDay.dayIndex)
            }
        }

        // 3. Proximity: find the study day closest to today
        val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val nowMillis = cal.timeInMillis

        var closestUpcomingDay: Pair<Int, Int>? = null
        var minUpcomingDiff = Long.MAX_VALUE

        var closestAnyDay: Pair<Int, Int>? = null
        var minAnyDiff = Long.MAX_VALUE

        for (week in weeks) {
            for (day in week.days) {
                val cleanDate = day.dateStr.trim()
                if (cleanDate.isNotEmpty()) {
                    try {
                        val parsed = sdf.parse(cleanDate)
                        if (parsed != null) {
                            val dayCal = Calendar.getInstance().apply {
                                time = parsed
                                set(Calendar.YEAR, currentYear)
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                            }
                            val diff = dayCal.timeInMillis - nowMillis
                            val absDiff = Math.abs(diff)

                            if (diff >= -86400000L && absDiff < minUpcomingDiff) {
                                minUpcomingDiff = absDiff
                                closestUpcomingDay = Pair(week.weekNumber, day.dayIndex)
                            }

                            if (absDiff < minAnyDiff) {
                                minAnyDiff = absDiff
                                closestAnyDay = Pair(week.weekNumber, day.dayIndex)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        if (closestUpcomingDay != null) return closestUpcomingDay
        if (closestAnyDay != null) return closestAnyDay

        // 4. Fallback to first week and first day
        val firstWeek = weeks.firstOrNull() ?: return null
        val firstDay = firstWeek.days.firstOrNull()?.dayIndex ?: 0
        return Pair(firstWeek.weekNumber, firstDay)
    }

    companion object {
        fun getTodayFormattedDate(): String {
            val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
            return sdf.format(Date())
        }

        fun getCurrentDayIndex(): Int {
            val cal = Calendar.getInstance()
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 0 // default to Monday for next week
                else -> 0
            }
        }
    }
}
