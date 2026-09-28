package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
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
import com.example.data.remote.CabinetAuthManager
import com.example.data.remote.CabinetScheduleParser
import com.example.data.remote.ZtuScheduleApi
import com.example.data.remote.ZtuScheduleParser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleRepository(
    private val context: Context,
    val cabinetAuth: CabinetAuthManager = CabinetAuthManager(context),
    private val api: ZtuScheduleApi = ZtuScheduleApi(cabinetAuth),
    private val dao: ScheduleDao = AppDatabase.getInstance(context).scheduleDao()
) {
    companion object {
        const val PREFS_NAME = "ztu_schedule_prefs"
        const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
        const val KEY_SELECTED_GROUP_ID = "selected_group_id"
        const val KEY_SELECTED_GROUP_NAME = "selected_group_name"
        const val KEY_SUBGROUP_FILTER = "subgroup_filter" // "ALL", "1", "2"
        const val DEFAULT_GROUP_ID = "612"
        const val DEFAULT_GROUP_NAME = "КІ-26-1"

        const val KEY_WIDGET_STYLE = "widget_style"
        const val KEY_WIDGET_OPACITY = "widget_opacity"
        const val KEY_OLED_MODE = "oled_mode"

        const val KEY_SCHEDULE_SOURCE = "schedule_source"
        const val SOURCE_PUBLIC = "PUBLIC"
        const val SOURCE_CABINET = "CABINET"

        const val WIDGET_STYLE_GLASS = "GLASS"
        const val WIDGET_STYLE_SYSTEM = "SYSTEM"
        const val WIDGET_STYLE_MONET = "MONET"
        const val WIDGET_STYLE_DARK = "DARK"
        const val WIDGET_STYLE_LIGHT = "LIGHT"

        const val ACTION_SCHEDULE_UPDATED = "com.example.ACTION_SCHEDULE_UPDATED"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDynamicColorEnabled(): Boolean {
        // Material You / Monet is supported on Android 12 (API 31, S) and newer; default to true there
        val defaultEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, defaultEnabled)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        notifyWidgetUpdate()
    }
    fun isOledModeEnabled() = prefs.getBoolean(KEY_OLED_MODE, false)
    fun setOledModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OLED_MODE, enabled).apply()
        notifyWidgetUpdate()
    }

    fun getWidgetStyle(): String {
        val defaultStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WIDGET_STYLE_MONET
        } else {
            WIDGET_STYLE_GLASS
        }
        return prefs.getString(KEY_WIDGET_STYLE, defaultStyle) ?: defaultStyle
    }

    fun setWidgetStyle(style: String) {
        prefs.edit().putString(KEY_WIDGET_STYLE, style).apply()
        notifyWidgetUpdate()
    }

    fun getWidgetOpacity(): Int {
        return prefs.getInt(KEY_WIDGET_OPACITY, 85)
    }

    fun setWidgetOpacity(opacity: Int) {
        prefs.edit().putInt(KEY_WIDGET_OPACITY, opacity).apply()
        notifyWidgetUpdate()
    }

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
        notifyWidgetUpdate()
    }

    fun getScheduleSource(): String {
        val defaultSource = if (isCabinetLoggedIn()) SOURCE_CABINET else SOURCE_PUBLIC
        return prefs.getString(KEY_SCHEDULE_SOURCE, defaultSource) ?: defaultSource
    }

    fun setScheduleSource(source: String) {
        prefs.edit().putString(KEY_SCHEDULE_SOURCE, source).apply()
        notifyWidgetUpdate()
    }

    fun isCabinetLoggedIn(): Boolean = cabinetAuth.isLoggedIn()

    fun getCabinetUsername(): String = cabinetAuth.getUsername()

    fun getCabinetStudentName(): String = cabinetAuth.getStudentName()

    suspend fun cabinetLogin(username: String, password: String): Result<String> {
        val result = cabinetAuth.login(username, password)
        if (result.isSuccess) {
            setScheduleSource(SOURCE_CABINET)
            refreshSchedule(getSelectedGroupId())
        }
        return result
    }

    fun cabinetLogout() {
        cabinetAuth.logout()
        setScheduleSource(SOURCE_PUBLIC)
        notifyWidgetUpdate()
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
        val fallbackName = if (groupId == getSelectedGroupId()) getSelectedGroupName() else "Група $groupId"
        try {
            val useCabinet = isCabinetLoggedIn() && getScheduleSource() == SOURCE_CABINET
            val scheduleData = if (useCabinet) {
                // 1. Fetch complete semester schedule from public source
                val pubHtml = api.fetchScheduleHtml(groupId)
                val pubData = ZtuScheduleParser.parseScheduleHtml(
                    html = pubHtml,
                    defaultGroupId = groupId,
                    fallbackGroupName = fallbackName
                )

                // 2. Fetch cabinet schedule and merge teacher notes
                try {
                    val todayCal = Calendar.getInstance()
                    val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    val tomorrowDateStr = SimpleDateFormat("dd.MM", Locale.getDefault()).format(tomorrowCal.time)
                    val studentOrGroupName = getCabinetStudentName().ifBlank { fallbackName }

                    // Fetch base schedule page (with no params)
                    val baseHtml = cabinetAuth.fetchScheduleHtml().getOrNull() ?: ""
                    val baseDoc = org.jsoup.Jsoup.parse(baseHtml)

                    val baseParsed = if (baseHtml.isNotBlank()) {
                        CabinetScheduleParser.parseCabinetSchedule(
                            html = baseHtml,
                            defaultGroupId = groupId,
                            fallbackGroupName = studentOrGroupName
                        )
                    } else null

                    // Resolve the actual Cabinet Week Numbers (0..16) for tomorrow and today
                    val tomorrowCabinetWeek = CabinetScheduleParser.resolveCabinetWeek(baseDoc, tomorrowCal)
                    val todayCabinetWeek = CabinetScheduleParser.resolveCabinetWeek(baseDoc, todayCal)
                    val detectedWeek = baseParsed?.detectedCurrentWeek

                    val availableCabinetWeeks = baseParsed?.availableWeeks ?: emptyList()
                    val activeCabinetWeek = detectedWeek ?: todayCabinetWeek ?: tomorrowCabinetWeek ?: 1
                    val isEvenActive = (activeCabinetWeek % 2 == 0)

                    // Cabinet week for Public Week 2 (even week):
                    val weekForPub2 = if (isEvenActive) activeCabinetWeek else {
                        if (availableCabinetWeeks.contains(activeCabinetWeek + 1)) activeCabinetWeek + 1
                        else if (availableCabinetWeeks.contains(activeCabinetWeek - 1)) activeCabinetWeek - 1
                        else 2
                    }

                    // Cabinet week for Public Week 1 (odd week):
                    val weekForPub1 = if (!isEvenActive) activeCabinetWeek else {
                        if (availableCabinetWeeks.contains(activeCabinetWeek - 1)) activeCabinetWeek - 1
                        else if (availableCabinetWeeks.contains(activeCabinetWeek + 1)) activeCabinetWeek + 1
                        else 1
                    }

                    val weeksToFetch = listOfNotNull(weekForPub1, weekForPub2, tomorrowCabinetWeek, todayCabinetWeek, detectedWeek)
                        .filter { it >= 0 }
                        .distinct()
                    Log.i("ScheduleRepository", "Cabinet weeks to fetch for public week 1 & 2: $weeksToFetch (activeCabinetWeek: $activeCabinetWeek)")

                    val baseWeek = baseParsed?.detectedCurrentWeek
                    val baseDayIndex = baseParsed?.scheduleData?.weeks?.firstOrNull()?.days?.firstOrNull()?.dayIndex
                    val baseDay1Based = if (baseDayIndex != null) baseDayIndex + 1 else null

                    // Determine non-empty days from baseDoc (e.g. days with classes) or fallback to 1..5
                    val cabinetAvailableDays = baseDoc.select(".sch-day:not(.is-empty)")
                        .mapNotNull { el ->
                            val m = java.util.regex.Pattern.compile("day=(\\d+)").matcher(el.attr("href"))
                            if (m.find()) m.group(1)?.toIntOrNull() else null
                        }
                        .distinct()
                    val daysToFetch = if (cabinetAvailableDays.isNotEmpty()) cabinetAvailableDays else (1..5).toList()

                    // Fetch days for the active week(s) in parallel (skipping base page if already loaded)
                    val dayFetches = coroutineScope {
                        val tasks = mutableListOf<Deferred<Triple<Int, Int, String>?>>()
                        for (w in weeksToFetch) {
                            for (d in daysToFetch) {
                                if (w == baseWeek && d == baseDay1Based) continue
                                tasks.add(async {
                                    val res = cabinetAuth.fetchScheduleHtml(week = w, day = d)
                                    val h = res.getOrNull()
                                    if (!h.isNullOrBlank()) Triple(w, d, h) else null
                                })
                            }
                        }
                        tasks.awaitAll().filterNotNull()
                    }

                    val allExtractedNotes = mutableListOf<com.example.data.remote.CabinetLessonNote>()

                    if (baseParsed != null) {
                        allExtractedNotes.addAll(baseParsed.extractedNotes)
                    }

                    for ((w, d, h) in dayFetches) {
                        if (h.contains("ЛААГ", ignoreCase = true) || h.contains("алгебр", ignoreCase = true)) {
                            cabinetAuth.saveLastCabinetHtml(h)
                        }
                        val parsed = CabinetScheduleParser.parseCabinetSchedule(
                            html = h,
                            defaultGroupId = groupId,
                            fallbackGroupName = studentOrGroupName,
                            explicitWeek = w,
                            explicitDay = d
                        )
                        allExtractedNotes.addAll(parsed.extractedNotes)
                    }

                    fun cabinetWeekToBiWeekly(cabWeek: Int): Int {
                        if (cabWeek <= 0) return 1
                        return if (cabWeek % 2 == 1) 1 else 2
                    }

                    fun isSubgroupMatching(noteSubgroup: String, pairSubgroup: String): Boolean {
                        val n1 = "1" in noteSubgroup
                        val n2 = "2" in noteSubgroup
                        val p1 = "1" in pairSubgroup
                        val p2 = "2" in pairSubgroup
                        if ((n1 || n2) && (p1 || p2)) {
                            return (n1 == p1) && (n2 == p2)
                        }
                        return true
                    }

                    fun findNoteForPair(pair: SchedulePair, dayIdx: Int, weekNumber: Int): String? {
                        // Only match notes corresponding to the same bi-weekly parity (1 or 2)
                        val candidateNotes = allExtractedNotes.filter { note ->
                            cabinetWeekToBiWeekly(note.weekNumber) == weekNumber
                        }

                        // STRICT MATCH:
                        // MUST match: same day + same pairNumber + subgroup compatibility + subject compatibility + NO subject conflict
                        val matchingNotes = candidateNotes.filter { note ->
                            note.dayIndex == dayIdx &&
                                note.pairNumber == pair.pairNumber &&
                                isSubgroupMatching(note.subgroup, pair.subgroup) &&
                                CabinetScheduleParser.isSubjectCompatible(note.subject, pair.subject) &&
                                !CabinetScheduleParser.isSubjectConflict(note.subject, pair.subject)
                        }

                        if (matchingNotes.isEmpty()) return null
                        if (matchingNotes.size == 1) return matchingNotes.first().note

                        // If multiple notes match for the same pair slot, prefer the one with matching teacher
                        val exactTeacher = matchingNotes.find { note ->
                            note.teacher.isNotBlank() && pair.teacher.isNotBlank() &&
                                (note.teacher.contains(pair.teacher) || pair.teacher.contains(note.teacher))
                        }
                        if (exactTeacher != null) return exactTeacher.note

                        return matchingNotes.first().note
                    }

                    if (allExtractedNotes.isNotEmpty()) {
                        Log.i("ScheduleRepository", "Found ${allExtractedNotes.size} cabinet notes across weeks $weeksToFetch")
                        val enrichedWeeks = pubData.weeks.map { week ->
                            val enrichedDays = week.days.map { day ->
                                val enrichedPairs = day.pairs.map { pair ->
                                    val note = findNoteForPair(pair, day.dayIndex, week.weekNumber) ?: pair.teacherNote
                                    if (note != pair.teacherNote) pair.copy(teacherNote = note) else pair
                                }
                                day.copy(pairs = enrichedPairs)
                            }
                            week.copy(days = enrichedDays)
                        }
                        pubData.copy(
                            groupName = if (baseParsed?.scheduleData?.groupName?.isNotBlank() == true && !baseParsed.scheduleData.groupName.contains("Мій розклад")) baseParsed.scheduleData.groupName else pubData.groupName,
                            weeks = enrichedWeeks
                        )
                    } else {
                        pubData
                    }
                } catch (ce: Exception) {
                    Log.w("ScheduleRepository", "Cabinet fetch failed, keeping public schedule: ${ce.message}", ce)
                    pubData
                }
            } else {
                val html = api.fetchScheduleHtml(groupId)
                ZtuScheduleParser.parseScheduleHtml(
                    html = html,
                    defaultGroupId = groupId,
                    fallbackGroupName = fallbackName
                )
            }

            val resolvedGroupName = if (scheduleData.groupName.isNotBlank()) scheduleData.groupName else fallbackName

            // Save to database
            val metadataEntity = MetadataEntity(
                groupId = groupId,
                groupName = resolvedGroupName,
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
            setSelectedGroupId(groupId, resolvedGroupName)

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
