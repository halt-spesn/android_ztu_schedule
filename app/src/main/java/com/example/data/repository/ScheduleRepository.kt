package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.GroupEntity
import com.example.data.local.MetadataEntity
import com.example.data.local.PairEntity
import com.example.data.local.ScheduleDao
import com.example.data.local.WeekInfoEntity
import com.example.data.model.ScheduleData
import com.example.data.model.ScheduleDay
import com.example.data.model.ScheduleGroup
import com.example.data.model.SchedulePair
import com.example.data.model.ScheduleWeek
import com.example.data.remote.ZtuScheduleApi
import com.example.data.remote.ZtuScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleRepository(
    private val context: Context,
    private val api: ZtuScheduleApi = ZtuScheduleApi(),
    private val dao: ScheduleDao = AppDatabase.getInstance(context).scheduleDao()
) {
    companion object {
        const val PREFS_NAME = "ztu_schedule_prefs"
        const val KEY_SELECTED_GROUP_ID = "selected_group_id"
        const val KEY_SELECTED_GROUP_NAME = "selected_group_name"
        const val KEY_SUBGROUP_FILTER = "subgroup_filter" // "ALL", "1", "2"
        const val DEFAULT_GROUP_ID = "612"
        const val DEFAULT_GROUP_NAME = "КІ-26-1"

        const val ACTION_SCHEDULE_UPDATED = "com.example.ACTION_SCHEDULE_UPDATED"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedGroupId(): String {
        return prefs.getString(KEY_SELECTED_GROUP_ID, DEFAULT_GROUP_ID) ?: DEFAULT_GROUP_ID
    }

    fun setSelectedGroupId(groupId: String, groupName: String? = null) {
        prefs.edit().apply {
            putString(KEY_SELECTED_GROUP_ID, groupId)
            if (groupName != null) {
                putString(KEY_SELECTED_GROUP_NAME, groupName)
            }
            apply()
        }
    }

    fun getSelectedGroupName(): String {
        return prefs.getString(KEY_SELECTED_GROUP_NAME, DEFAULT_GROUP_NAME) ?: DEFAULT_GROUP_NAME
    }

    fun getSubgroupFilter(): String {
        return prefs.getString(KEY_SUBGROUP_FILTER, "ALL") ?: "ALL"
    }

    fun setSubgroupFilter(filter: String) {
        prefs.edit().putString(KEY_SUBGROUP_FILTER, filter).apply()
    }

    fun observeSchedule(groupId: String): Flow<ScheduleData?> {
        return combine(
            dao.getMetadata(groupId),
            dao.getWeeksInfo(groupId),
            dao.getPairsForGroup(groupId)
        ) { metadata, weeksInfo, pairEntities ->
            if (metadata == null && pairEntities.isEmpty()) {
                null
            } else {
                val pairs = pairEntities.map { it.toDomainModel() }
                val pairsByWeek = pairs.groupBy { it.weekNumber }

                val weeks = if (weeksInfo.isNotEmpty()) {
                    weeksInfo.map { weekEntity ->
                        val weekPairs = pairsByWeek[weekEntity.weekNumber] ?: emptyList()
                        buildScheduleWeek(weekEntity.weekNumber, weekEntity.weekTitle, weekEntity.note, weekPairs)
                    }
                } else {
                    // Reconstruct from pairs if week info wasn't cached separately
                    pairsByWeek.keys.sorted().map { weekNum ->
                        val weekPairs = pairsByWeek[weekNum] ?: emptyList()
                        buildScheduleWeek(weekNum, "Тиждень $weekNum", "", weekPairs)
                    }
                }

                ScheduleData(
                    groupId = groupId,
                    groupName = metadata?.groupName ?: getSelectedGroupName(),
                    faculty = metadata?.faculty ?: "",
                    notice = metadata?.notice ?: "",
                    weeks = weeks,
                    lastUpdatedMillis = metadata?.lastUpdatedMillis ?: System.currentTimeMillis()
                )
            }
        }
    }

    private fun buildScheduleWeek(
        weekNumber: Int,
        weekTitle: String,
        note: String,
        pairs: List<SchedulePair>
    ): ScheduleWeek {
        val pairsByDay = pairs.groupBy { it.dayIndex }
        val days = mutableListOf<ScheduleDay>()
        
        // Typical days 0..4 (Mon-Fri) or up to 5 (Sat)
        val maxDayIndex = (pairsByDay.keys.maxOrNull() ?: 4).coerceAtLeast(4)
        val dayNames = listOf("Понеділок", "Вівторок", "Середа", "Четвер", "П'ятниця", "Субота", "Неділя")

        for (dIndex in 0..maxDayIndex) {
            val dayPairs = pairsByDay[dIndex] ?: emptyList()
            val dayName = dayPairs.firstOrNull()?.dayName?.ifEmpty { null }
                ?: dayNames.getOrElse(dIndex) { "День ${dIndex + 1}" }
            val dateStr = dayPairs.firstOrNull()?.dateStr ?: ""

            days.add(
                ScheduleDay(
                    dayIndex = dIndex,
                    dayName = dayName,
                    dateStr = dateStr,
                    pairs = dayPairs.sortedBy { it.pairNumber }
                )
            )
        }

        return ScheduleWeek(
            weekNumber = weekNumber,
            weekTitle = weekTitle,
            note = note,
            days = days
        )
    }

    suspend fun refreshSchedule(groupId: String): Result<ScheduleData> = withContext(Dispatchers.IO) {
        try {
            val html = api.fetchScheduleHtml(groupId)
            val scheduleData = ZtuScheduleParser.parseScheduleHtml(html, defaultGroupId = groupId)

            // Save to database
            val metadataEntity = MetadataEntity(
                groupId = groupId,
                groupName = scheduleData.groupName,
                faculty = scheduleData.faculty,
                notice = scheduleData.notice,
                lastUpdatedMillis = scheduleData.lastUpdatedMillis
            )

            val weekEntities = scheduleData.weeks.map { week ->
                WeekInfoEntity(
                    groupId = groupId,
                    weekNumber = week.weekNumber,
                    weekTitle = week.weekTitle,
                    note = week.note
                )
            }

            val pairEntities = scheduleData.weeks.flatMap { week ->
                week.days.flatMap { day ->
                    day.pairs.map { PairEntity.fromDomainModel(groupId, it) }
                }
            }

            dao.updateScheduleData(groupId, metadataEntity, weekEntities, pairEntities)
            setSelectedGroupId(groupId, scheduleData.groupName)

            // Notify widget to update
            notifyWidgetUpdate()

            Result.success(scheduleData)
        } catch (e: Exception) {
            Log.e("ScheduleRepository", "Error fetching schedule: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTodayPairs(groupId: String): List<SchedulePair> = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        // Day of week in Ukrainian format: Monday is 0, Tuesday 1, ..., Sunday 6
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val dayIndex = when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
        val todayDateStr = dateFormat.format(calendar.time)

        // Try date matching first
        val datePairs = dao.getPairsByDateSync(groupId, todayDateStr)
        if (datePairs.isNotEmpty()) {
            return@withContext datePairs.map { it.toDomainModel() }
        }

        // Fallback: get pairs for current day of week in first available week
        val allPairs = dao.getAllPairsSync(groupId)
        if (allPairs.isEmpty()) return@withContext emptyList()

        val pairsForDay = allPairs.filter { it.dayIndex == dayIndex }
        if (pairsForDay.isNotEmpty()) {
            // Take the current/first active week's day pairs
            val currentWeek = pairsForDay.first().weekNumber
            return@withContext pairsForDay.filter { it.weekNumber == currentWeek }.map { it.toDomainModel() }
        }

        emptyList()
    }

    suspend fun loadAllGroups(): List<ScheduleGroup> = withContext(Dispatchers.IO) {
        val cached = dao.getAllCachedGroupsSync()
        if (cached.isNotEmpty()) {
            return@withContext cached.map { it.toDomainModel() }
        }

        try {
            val html = api.fetchGroupListHtml()
            val groups = ZtuScheduleParser.parseGroupListHtml(html)
            if (groups.isNotEmpty()) {
                val entities = groups.map { GroupEntity(it.id, it.name, it.faculty) }
                dao.insertGroups(entities)
            }
            groups
        } catch (e: Exception) {
            Log.e("ScheduleRepository", "Error fetching group list", e)
            cached.map { it.toDomainModel() }
        }
    }

    fun notifyWidgetUpdate() {
        val intent = Intent(context, com.example.widget.ScheduleWidgetProvider::class.java).apply {
            action = com.example.widget.ScheduleWidgetProvider.ACTION_UPDATE_FROM_APP
        }
        context.sendBroadcast(intent)
    }
}
