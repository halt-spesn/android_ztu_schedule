package com.example.data.remote

import android.util.Log
import com.example.data.model.ScheduleData
import com.example.data.model.ScheduleDay
import com.example.data.model.SchedulePair
import com.example.data.model.ScheduleWeek
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class CabinetLessonNote(
    val weekNumber: Int,
    val dayIndex: Int,
    val pairNumber: Int,
    val subject: String,
    val teacher: String = "",
    val note: String,
    val dateStr: String = "",
    val subgroup: String = ""
)

data class ParsedCabinetSchedule(
    val scheduleData: ScheduleData,
    val extractedNotes: List<CabinetLessonNote>,
    val detectedCurrentWeek: Int?,
    val availableWeeks: List<Int>
)

object CabinetScheduleParser {

    private const val TAG = "CabinetScheduleParser"

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("[$TAG] $msg")
        }
    }

    fun parseCabinetSchedule(
        html: String,
        defaultGroupId: String = "cabinet",
        fallbackGroupName: String = "",
        explicitWeek: Int? = null,
        explicitDay: Int? = null
    ): ParsedCabinetSchedule {
        val doc: Document = Jsoup.parse(html)

        // 1. Group / Student Name & Faculty
        val groupEl = doc.selectFirst(".sch-group b, .sch-group strong") ?: doc.selectFirst(".sch-group")
        val rawGroupName = groupEl?.text()?.trim()?.removePrefix("Група")?.trim()?.ifBlank { null }
        val titleEl = doc.selectFirst(".sch-title h1, .sch-title h2, header.sch-head h1, .sch-top h1, .navbar-brand, h1")
        val parsedGroupName = rawGroupName
            ?: titleEl?.text()?.trim()?.removePrefix("Група")?.trim()?.ifBlank { null }
        val groupName = parsedGroupName
            ?: fallbackGroupName.ifBlank { null }
            ?: "Мій розклад"

        val facultyEl = doc.selectFirst(".sch-title .sch-faculty, .sch-faculty")
        val faculty = facultyEl?.text()?.trim() ?: "Житомирська політехніка"

        val noticeEl = doc.selectFirst(".sch-notice, .schedule-page .alert-info, .schedule-page .alert-warning")
        val notice = noticeEl?.text()?.trim() ?: ""

        // 2. Detect Week Number & Available Weeks
        val detectedWeek = detectWeekNumber(doc, explicitWeek)
        val availableWeeks = doc.select("a.sch-week, select[name=week] option, .week-select option")
            .mapNotNull {
                it.attr("value").trim().toIntOrNull()
                    ?: Regex("week=(\\d+)").find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                    ?: it.text().trim().toIntOrNull()
            }
            .distinct()

        // 3. Detect Day Index & Date String
        val detectedDayIndex = detectDayIndex(doc, explicitDay)
        val dayDateStr = doc.selectFirst(".sch-day.is-active .sch-day-date")?.text()?.trim()
            ?: doc.selectFirst(".sch-bar-date")?.text()?.trim()
            ?: ""

        // Note storage list
        val extractedNotes = mutableListOf<CabinetLessonNote>()

        fun recordNote(weekNum: Int, dayIdx: Int, pairNum: Int, subject: String, teacher: String, note: String, dateStr: String = "", subgroup: String = "") {
            val cleanNote = note.trim()
            if (cleanNote.isBlank()) return
            val cleanSub = cleanSubjectName(subject).ifBlank { "Пара $pairNum" }

            // Avoid duplicate notes for same week, day, pair, subgroup
            if (extractedNotes.none { it.weekNumber == weekNum && it.dayIndex == dayIdx && it.pairNumber == pairNum && it.note == cleanNote && it.subgroup == subgroup.trim() }) {
                extractedNotes.add(
                    CabinetLessonNote(
                        weekNumber = weekNum,
                        dayIndex = dayIdx,
                        pairNumber = pairNum,
                        subject = cleanSub,
                        teacher = teacher.trim(),
                        note = cleanNote,
                        dateStr = dateStr.trim(),
                        subgroup = subgroup.trim()
                    )
                )
            }
        }

        val parsedWeeks = mutableListOf<ScheduleWeek>()

        // 4. Primary: Parse native Cabinet div layout (.sch-pair)
        val schPairs = doc.select(".schedule-page .sch-pair, .sch-pair")
        if (schPairs.isNotEmpty()) {
            val dayIndex = detectedDayIndex ?: 0
            val dayPairs = mutableListOf<SchedulePair>()

            for (pEl in schPairs) {
                val noText = pEl.selectFirst(".sch-no")?.text()?.trim() ?: ""
                val pairNum = Regex("(\\d+)").find(noText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: mapTimeToPairNumber(pEl.selectFirst(".sch-hh")?.text()?.trim() ?: "")
                    ?: continue

                val timeRange = pEl.selectFirst(".sch-hh")?.text()?.trim()
                    ?.ifEmpty { defaultTimeForPair(pairNum) } ?: defaultTimeForPair(pairNum)

                val subjectEl = pEl.selectFirst(".sch-subject")
                val rawSubject = subjectEl?.clone()?.apply { select(".sch-badge-next, .sch-badge-now, .badge").remove() }?.text()?.trim() ?: ""
                val cleanSub = cleanSubjectName(rawSubject).ifBlank { "Пара $pairNum" }

                val kind = pEl.selectFirst(".sch-chip-lect, .sch-chip-pract, .sch-chip-lab, .sch-chip")?.text()?.trim() ?: "Лекція"
                val room = pEl.selectFirst(".sch-chip-room")?.text()?.trim() ?: ""
                val subgroup = pEl.selectFirst(".sch-chip-sub")?.text()?.trim() ?: ""
                val teacher = pEl.selectFirst(".sch-teacher")?.text()?.trim() ?: ""

                val linkEl = pEl.selectFirst(".sch-link")
                val note = if (linkEl != null) {
                    extractFormattedNote(linkEl)
                } else {
                    ""
                }

                if (note.isNotBlank()) {
                    recordNote(detectedWeek, dayIndex, pairNum, cleanSub, teacher, note, dayDateStr, subgroup)
                }

                dayPairs.add(
                    SchedulePair(
                        id = 0,
                        weekNumber = detectedWeek,
                        dayIndex = dayIndex,
                        dayName = dayNameForIndex(dayIndex),
                        dateStr = dayDateStr,
                        pairNumber = pairNum,
                        timeRange = timeRange,
                        subject = cleanSub,
                        kind = kind,
                        room = room,
                        teacher = teacher,
                        subgroup = subgroup,
                        teacherNote = note
                    )
                )
            }

            if (dayPairs.isNotEmpty()) {
                parsedWeeks.add(
                    ScheduleWeek(
                        weekNumber = detectedWeek,
                        weekTitle = "Тиждень $detectedWeek",
                        note = "",
                        days = listOf(
                            ScheduleDay(
                                dayIndex = dayIndex,
                                dayName = dayNameForIndex(dayIndex),
                                dateStr = dayDateStr,
                                pairs = dayPairs.sortedBy { it.pairNumber }
                            )
                        )
                    )
                )
            }
        } else {
            // Secondary / Fallback: Try parsing tables
            val allTables = doc.select("table")

            // Check if there are .sch-fold details (Rozklad format)
            val weekElements = doc.select("details.sch-fold")
            if (weekElements.isNotEmpty()) {
                for (weekEl in weekElements) {
                    val weekAttr = weekEl.attr("data-week").trim()
                    val weekTitle = weekEl.selectFirst(".sch-week-title")?.text()?.trim() ?: "Тиждень $weekAttr"
                    val weekNumber = weekAttr.toIntOrNull() ?: parseWeekNumber(weekTitle)
                    val weekNote = weekEl.selectFirst(".sch-week-note")?.text()?.trim() ?: ""

                    val table = weekEl.selectFirst("table")
                    if (table != null) {
                        val days = parseGenericTable(table, weekNumber, detectedDayIndex)
                        for (day in days) {
                            for (p in day.pairs) {
                                if (p.teacherNote.isNotBlank()) {
                                    recordNote(weekNumber, day.dayIndex, p.pairNumber, p.subject, p.teacher, p.teacherNote, p.dateStr)
                                }
                            }
                        }
                        parsedWeeks.add(ScheduleWeek(weekNumber, weekTitle, weekNote, days))
                    }
                }
            } else if (allTables.isNotEmpty()) {
                for ((idx, table) in allTables.withIndex()) {
                    val weekNumber = if (idx == 0) detectedWeek else (detectedWeek + idx)
                    val days = parseGenericTable(table, weekNumber, detectedDayIndex)
                    for (day in days) {
                        for (p in day.pairs) {
                            if (p.teacherNote.isNotBlank()) {
                                recordNote(weekNumber, day.dayIndex, p.pairNumber, p.subject, p.teacher, p.teacherNote, p.dateStr)
                            }
                        }
                    }
                    if (days.any { it.pairs.isNotEmpty() }) {
                        parsedWeeks.add(
                            ScheduleWeek(
                                weekNumber = weekNumber,
                                weekTitle = "Тиждень $weekNumber",
                                note = "",
                                days = days
                            )
                        )
                    }
                }
            }
        }

        // 5. Targeted Scan for Notes (Safety Net without arbitrary pair fallbacks)
        scanDocumentForNotes(doc, detectedWeek, detectedDayIndex, ::recordNote)

        val scheduleData = ScheduleData(
            groupId = defaultGroupId,
            groupName = groupName,
            faculty = faculty,
            notice = notice,
            weeks = parsedWeeks.sortedBy { it.weekNumber },
            lastUpdatedMillis = System.currentTimeMillis()
        )

        return ParsedCabinetSchedule(
            scheduleData = scheduleData,
            extractedNotes = extractedNotes,
            detectedCurrentWeek = detectedWeek,
            availableWeeks = availableWeeks
        )
    }

    fun parseCabinetScheduleHtml(
        html: String,
        defaultGroupId: String = "cabinet",
        fallbackGroupName: String = ""
    ): ScheduleData {
        return parseCabinetSchedule(html, defaultGroupId, fallbackGroupName).scheduleData
    }

    private fun parseGenericTable(table: Element, weekNumber: Int, overrideDayIndex: Int?): List<ScheduleDay> {
        val headers = table.select("thead tr th, thead tr td, tr:first-child th, tr:first-child td")
        val headerTexts = headers.map { it.text().trim() }

        // Determine if Matrix Table (columns = days)
        val dayColMap = mutableMapOf<Int, Int>()
        for ((idx, text) in headerTexts.withIndex()) {
            val lower = text.lowercase(Locale.ROOT)
            val dIdx = when {
                "понеділ" in lower || "пн" == lower || lower.startsWith("пн.") -> 0
                "вівтор" in lower || "вт" == lower || lower.startsWith("вт.") -> 1
                "серед" in lower || "ср" == lower || lower.startsWith("ср.") -> 2
                "четвер" in lower || "чт" == lower || lower.startsWith("чт.") -> 3
                "п'ятниц" in lower || "пятниц" in lower || "пт" == lower || lower.startsWith("пт.") -> 4
                "субот" in lower || "сб" == lower || lower.startsWith("сб.") -> 5
                "неділ" in lower || "нд" == lower -> 6
                else -> null
            }
            if (dIdx != null) {
                dayColMap[idx] = dIdx
            }
        }

        if (dayColMap.size >= 2) {
            // Case A: Matrix Table (all days in columns)
            return parseMatrixTable(table, weekNumber, dayColMap)
        } else {
            // Case B: Day Schedule Table (pairs in rows for a single day)
            val dayIndex = overrideDayIndex ?: dayColMap.values.firstOrNull() ?: 0
            return parseDayTable(table, weekNumber, dayIndex, headerTexts)
        }
    }

    private fun parseMatrixTable(
        table: Element,
        weekNumber: Int,
        dayColMap: Map<Int, Int>
    ): List<ScheduleDay> {
        val pairsByDayIndex = mutableMapOf<Int, MutableList<SchedulePair>>()
        for (d in 0..6) {
            pairsByDayIndex[d] = mutableListOf()
        }

        val rows = table.select("tbody tr, tr").drop(1)
        for (row in rows) {
            val allCells = row.select("th, td")
            val firstCell = allCells.firstOrNull() ?: continue
            val firstText = firstCell.text().trim()
            val pairNum = Regex("(\\d+)").find(firstText)?.groupValues?.get(1)?.toIntOrNull()
                ?: mapTimeToPairNumber(firstText)
                ?: continue

            val timeRange = extractTimeRange(firstText).ifEmpty { defaultTimeForPair(pairNum) }

            // Day cells are the remaining cells in the row after the hour column
            val dayCells = allCells.drop(1)
            for ((colIdx, cell) in dayCells.withIndex()) {
                val headerColIdx = colIdx + 1
                val dayIndex = dayColMap[headerColIdx] ?: dayColMap[colIdx] ?: continue

                if (cell.hasClass("is-free") || cell.text().isBlank()) continue

                val pairEls = cell.select(".sch-pair, .pair-item")
                if (pairEls.isNotEmpty()) {
                    for (pEl in pairEls) {
                        val sub = cleanSubjectName(pEl.selectFirst(".sch-subject, .subject, b, strong")?.text()?.trim() ?: "")
                        if (sub.isBlank()) continue
                        val teacher = pEl.selectFirst(".sch-teachers, .teacher")?.text()?.trim() ?: ""
                        val room = pEl.selectFirst(".sch-room, .room")?.text()?.trim() ?: ""
                        val kind = pEl.selectFirst(".sch-kind, .kind")?.text()?.trim() ?: "Лекція"
                        val subgroup = pEl.selectFirst(".sch-subgroup, .subgroup")?.text()?.trim() ?: ""
                        val note = extractNoteFromCell(pEl, sub, teacher)

                        pairsByDayIndex[dayIndex]?.add(
                            SchedulePair(
                                id = 0,
                                weekNumber = weekNumber,
                                dayIndex = dayIndex,
                                dayName = dayNameForIndex(dayIndex),
                                dateStr = "",
                                pairNumber = pairNum,
                                timeRange = timeRange,
                                subject = sub,
                                kind = kind,
                                room = room,
                                teacher = teacher,
                                subgroup = subgroup,
                                teacherNote = note
                            )
                        )
                    }
                } else {
                    val sub = cleanSubjectName(
                        cell.selectFirst("b, strong, .title")?.text()?.trim()
                            ?: cell.text().split("\n", "<br>").firstOrNull()?.trim() ?: ""
                    )
                    if (sub.isNotBlank()) {
                        val note = extractNoteFromCell(cell, sub, "")
                        pairsByDayIndex[dayIndex]?.add(
                            SchedulePair(
                                id = 0,
                                weekNumber = weekNumber,
                                dayIndex = dayIndex,
                                dayName = dayNameForIndex(dayIndex),
                                dateStr = "",
                                pairNumber = pairNum,
                                timeRange = timeRange,
                                subject = sub,
                                kind = "Лекція",
                                room = "",
                                teacher = "",
                                subgroup = "",
                                teacherNote = note
                            )
                        )
                    }
                }
            }
        }

        return pairsByDayIndex.filter { it.value.isNotEmpty() }.map { (dIdx, pairs) ->
            ScheduleDay(
                dayIndex = dIdx,
                dayName = dayNameForIndex(dIdx),
                dateStr = "",
                isMarked = false,
                pairs = pairs.sortedBy { it.pairNumber }
            )
        }
    }

    private fun parseDayTable(
        table: Element,
        weekNumber: Int,
        dayIndex: Int,
        headerTexts: List<String>
    ): List<ScheduleDay> {
        val pairs = mutableListOf<SchedulePair>()
        val rows = table.select("tbody tr, tr")

        var autoPairNum = 1
        for (row in rows) {
            val cells = row.select("th, td")
            if (cells.isEmpty()) continue

            // Skip table header row if it contains header keywords
            val rowText = row.text().lowercase(Locale.ROOT)
            if ("пара" in rowText && "дисциплін" in rowText || "час" in rowText && "викладач" in rowText) {
                continue
            }

            var pairNum: Int? = null
            var timeRange = ""
            var subject = ""
            var teacher = ""
            var room = ""
            var kind = ""
            var subgroup = ""
            var note = ""

            for ((colIdx, cell) in cells.withIndex()) {
                val cellText = cell.text().trim()
                val headerName = headerTexts.getOrNull(colIdx)?.lowercase(Locale.ROOT) ?: ""

                // Pair number detection: "4", "4 пара", "№ 4"
                val numMatch = Regex("^(?:№\\s*)?(\\d+)(?:\\s*пара)?$").find(cellText)
                if (numMatch != null && pairNum == null) {
                    pairNum = numMatch.groupValues[1].toIntOrNull()
                    continue
                }

                val tNum = mapTimeToPairNumber(cellText)
                if (tNum != null) {
                    if (pairNum == null) pairNum = tNum
                    timeRange = extractTimeRange(cellText).ifEmpty { defaultTimeForPair(tNum) }
                    continue
                }

                when {
                    "примітк" in headerName || "коментар" in headerName || "посилан" in headerName || "завдан" in headerName -> {
                        note = extractNoteFromCell(cell)
                    }
                    "викладач" in headerName -> {
                        teacher = cellText
                    }
                    "аудитор" in headerName || "кабінет" in headerName -> {
                        room = cellText
                    }
                    "вид" in headerName || "тип" in headerName -> {
                        kind = cellText
                    }
                    "підгр" in headerName -> {
                        subgroup = cellText
                    }
                    "дисциплін" in headerName || "предмет" in headerName || "назва" in headerName -> {
                        subject = cell.selectFirst("b, strong, .title, .subject")?.text()?.trim() ?: cellText
                        val cellNote = extractNoteFromCell(cell, subject, teacher)
                        if (cellNote.isNotBlank() && note.isBlank()) {
                            note = cellNote
                        }
                    }
                    else -> {
                        // Guess by content if headers aren't explicit
                        if (subject.isEmpty() && cellText.length > 2 && !cellText.matches(Regex("^[\\d:.-]+$"))) {
                            subject = cell.selectFirst("b, strong, .title, .subject")?.text()?.trim() ?: cellText
                        }
                        val cellNote = extractNoteFromCell(cell, subject, teacher)
                        if (cellNote.isNotBlank()) {
                            note = if (note.isBlank()) cellNote else "$note\n$cellNote"
                        }
                    }
                }
            }

            val resolvedPairNum = pairNum ?: autoPairNum
            autoPairNum = resolvedPairNum + 1

            val cleanSub = cleanSubjectName(subject)
            if (cleanSub.isNotBlank() || note.isNotBlank()) {
                val finalSubject = cleanSub.ifBlank { "Пара $resolvedPairNum" }
                val finalTime = timeRange.ifBlank { defaultTimeForPair(resolvedPairNum) }
                pairs.add(
                    SchedulePair(
                        id = 0,
                        weekNumber = weekNumber,
                        dayIndex = dayIndex,
                        dayName = dayNameForIndex(dayIndex),
                        dateStr = "",
                        pairNumber = resolvedPairNum,
                        timeRange = finalTime,
                        subject = finalSubject,
                        kind = kind.ifBlank { "Лекція" },
                        room = room,
                        teacher = teacher,
                        subgroup = subgroup,
                        teacherNote = note
                    )
                )
            }
        }

        return listOf(
            ScheduleDay(
                dayIndex = dayIndex,
                dayName = dayNameForIndex(dayIndex),
                dateStr = "",
                isMarked = false,
                pairs = pairs.sortedBy { it.pairNumber }
            )
        )
    }

    private fun scanDocumentForNotes(
        doc: Document,
        weekNumber: Int,
        defaultDayIndex: Int?,
        recordNote: (Int, Int, Int, String, String, String, String) -> Unit
    ) {
        val targetDay = defaultDayIndex ?: 0

        // Look for any elements containing meeting links or explicit note containers
        val candidates = doc.select(
            "a[href*='meet.google.com'], a[href*='zoom.us'], a[href*='teams.microsoft.com'], a[href*='do.ztu.edu.ua'], " +
            ".note, .comment, .teacher-note, .sch-note, .alert-info, .alert-warning"
        )

        for (el in candidates) {
            val container = el.parents().firstOrNull { it.tagName() in listOf("tr", "td", "div", "li") && it.text().length > 10 } ?: el
            val containerText = container.text()

            // Detect pair number strictly (do NOT fallback to arbitrary numbers)
            val pairNum = Regex("(\\d+)\\s*(?:пара|pair)").find(containerText)?.groupValues?.get(1)?.toIntOrNull()
                ?: mapTimeToPairNumber(containerText)
                ?: Regex("^(\\d+)\\b").find(containerText.trim())?.groupValues?.get(1)?.toIntOrNull()

            // Detect subject
            val subject = when {
                "лааг" in containerText.lowercase(Locale.ROOT) -> "ЛААГ"
                "лінійн" in containerText.lowercase(Locale.ROOT) -> "Лінійна алгебра та аналітична геометрія"
                else -> container.selectFirst("b, strong, h3, h4, h5, .title, .subject")?.text()?.trim() ?: ""
            }

            val cleanSub = cleanSubjectName(subject).ifBlank { if (pairNum != null) "Пара $pairNum" else "" }
            val note = extractNoteFromCell(container, cleanSub, "")

            // Only record if we have an identified pair number and non-empty note
            if (pairNum != null && note.isNotBlank()) {
                recordNote(weekNumber, targetDay, pairNum, cleanSub, "", note, "")
            }
        }
    }

    fun extractFormattedNote(el: Element): String {
        val clone = el.clone()
        clone.select("br").append("___BR___")
        clone.select("p, div, li").prepend("___BR___")

        for (a in clone.select("a[href]")) {
            val href = a.attr("href").trim()
            val text = a.text().trim()
            if (href.startsWith("http://") || href.startsWith("https://")) {
                if (text.isNotBlank() && !text.equals(href, ignoreCase = true) && !href.contains(text)) {
                    a.text("$text: $href")
                } else {
                    a.text(href)
                }
            }
        }

        val text = clone.text()
        return text.split("___BR___")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun extractNoteFromCell(cell: Element, subject: String = "", teacher: String = ""): String {
        val notes = mutableListOf<String>()

        // 1. Direct meeting links (Google Meet, Zoom, Teams, Moodle, Drive)
        val linkElements = cell.select("a[href]")
        for (a in linkElements) {
            val href = a.attr("href").trim()
            val text = a.text().trim()
            if (href.startsWith("http://") || href.startsWith("https://")) {
                val isMeeting = href.contains("meet.google.com") || href.contains("zoom.us") ||
                        href.contains("teams.microsoft.com") || href.contains("do.ztu.edu.ua") ||
                        href.contains("classroom.google.com") || href.contains("drive.google.com")
                if (isMeeting || text.contains("приєдн", ignoreCase = true) || text.contains("лекці", ignoreCase = true) || text.contains("посилан", ignoreCase = true)) {
                    if (text.isNotBlank() && !text.equals("посилання", ignoreCase = true) && !text.equals(href, ignoreCase = true)) {
                        notes.add("$text: $href")
                    } else {
                        notes.add(href)
                    }
                }
            }
        }

        // 2. Elements with alert, badge, note, comment, well, callout classes
        val noteContainers = cell.select(".note, .comment, .teacher-note, .sch-note, .alert, .badge, .well, small, .help-block, .task, .homework, [class*='note'], [class*='comment']")
        for (container in noteContainers) {
            val t = container.text().trim()
            if (t.isNotBlank() && !isStandardCellLabel(t, subject, teacher)) {
                val aHref = container.selectFirst("a[href^=http]")?.attr("href")?.trim() ?: ""
                val fullNote = if (aHref.isNotEmpty() && !t.contains(aHref)) "$t\n$aHref" else t
                if (!notes.any { it.contains(fullNote) || fullNote.contains(it) }) {
                    notes.add(fullNote)
                }
            }
        }

        // 3. Tooltips and popovers
        val tooltipEls = cell.select("[data-content], [data-original-title], [title]")
        for (el in tooltipEls) {
            val content = el.attr("data-content").trim().ifEmpty { el.attr("data-original-title").trim() }.ifEmpty { el.attr("title").trim() }
            if (content.isNotBlank() && !content.contains("http://") && !isStandardCellLabel(content, subject, teacher)) {
                val clean = Jsoup.parse(content).text().trim()
                if (clean.length > 3 && !notes.any { it.contains(clean) }) {
                    notes.add(clean)
                }
            }
        }

        // 4. Line-by-line inspection of cell text
        val clone = cell.clone()
        clone.select("br").append("___BR___")
        clone.select("p, div, li").prepend("___BR___")
        val allText = clone.text()
        val lines = allText.split("___BR___").map { it.trim() }.filter { it.isNotBlank() }

        for (line in lines) {
            if (isStandardCellLabel(line, subject, teacher)) continue
            if (notes.any { it.contains(line) || line.contains(it) }) continue

            val isExplicitNote = line.startsWith("примітк", ignoreCase = true) ||
                    line.startsWith("завдан", ignoreCase = true) ||
                    line.startsWith("коментар", ignoreCase = true) ||
                    line.startsWith("посилан", ignoreCase = true) ||
                    line.startsWith("тема", ignoreCase = true) ||
                    line.startsWith("увага", ignoreCase = true) ||
                    line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true)

            if (isExplicitNote || line.length > 10) {
                notes.add(line)
            }
        }

        // 5. Any other external URLs in cell not yet captured
        for (a in linkElements) {
            val href = a.attr("href").trim()
            if ((href.startsWith("http://") || href.startsWith("https://")) && !notes.any { it.contains(href) }) {
                notes.add(href)
            }
        }

        return notes.distinct().joinToString("\n").trim()
    }

    fun isStandardCellLabel(text: String, subject: String, teacher: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return true

        if (subject.isNotBlank() && (clean.equals(subject, ignoreCase = true) || (subject.contains(clean, ignoreCase = true) && clean.length > 5))) {
            return true
        }

        if (teacher.isNotBlank() && (clean.equals(teacher, ignoreCase = true) || clean.contains(teacher, ignoreCase = true))) {
            return true
        }

        if (clean.matches(Regex("^(?:№\\s*)?(\\d+)\\s*(?:пара)?$", RegexOption.IGNORE_CASE))) return true
        if (clean.matches(Regex("^\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}$"))) return true

        val lower = clean.lowercase(Locale.ROOT)
        if (lower in listOf("лекція", "лек.", "практичне", "практ.", "пр.", "лабораторна", "лаб.", "семінар", "консультація", "іспит", "залік")) return true
        if (lower.startsWith("підгр.") || lower.startsWith("підгрупа")) return true
        if (lower.startsWith("ауд.") || lower == "дистанційно" || lower == "онлайн" || lower == "online") return true

        return false
    }

    fun cleanSubjectName(raw: String): String {
        return raw.replace(Regex("\\((?:лекція|лек\\.?|практичне|практ\\.?|пр\\.?|лабораторна|лаб\\.?|семінар|консультація|залік|іспит)\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()
    }

    fun getAcronym(title: String): String {
        val stopWords = setOf("та", "і", "й", "в", "у", "на", "з", "із", "до", "для", "по", "про")
        val words = title.split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in stopWords }
        return words.mapNotNull { it.firstOrNull()?.lowercaseChar() }.joinToString("")
    }

    /**
     * Strict check to ensure a note is only assigned to a compatible subject.
     * Prevents notes for "Математичний аналіз" from attaching to "Лінійна алгебра".
     */
    fun isSubjectCompatible(cabinetSubject: String, pubSubject: String): Boolean {
        val c = cleanSubjectName(cabinetSubject).lowercase(Locale.ROOT).trim()
        val p = cleanSubjectName(pubSubject).lowercase(Locale.ROOT).trim()
        if (c.isEmpty() || p.isEmpty()) return false
        if (c == p) return true

        // Direct Acronym Match: "лааг" <-> "лінійна алгебра та аналітична геометрія"
        val acrC = getAcronym(c)
        val acrP = getAcronym(p)
        if (c.length in 2..6 && (c == acrP || acrP == c)) return true
        if (p.length in 2..6 && (p == acrC || acrC == p)) return true

        if (c == "лааг" && "алгебр" in p) return true
        if (p == "лааг" && "алгебр" in c) return true

        // Generic academic words that must NOT be used alone to match subjects
        val genericWords = setOf(
            "та", "і", "й", "в", "у", "на", "з", "до", "для", "по", "про",
            "аналіз", "аналітична", "основи", "вступ", "теорія", "методи",
            "технології", "комп'ютерна", "комп'ютерні", "інженерія", "практикум",
            "вибрані", "розділи", "курс", "частина", "спецкурс"
        )

        // Check distinctive core words
        val distinctiveC = c.split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 4 && it !in genericWords }
        val distinctiveP = p.split(Regex("[\\s\\p{Punct}]+")).filter { it.length >= 4 && it !in genericWords }

        if (distinctiveC.isNotEmpty() && distinctiveP.isNotEmpty()) {
            for (wc in distinctiveC) {
                for (wp in distinctiveP) {
                    if (wc == wp || (wc.length >= 5 && wp.startsWith(wc)) || (wp.length >= 5 && wc.startsWith(wp))) {
                        return true
                    }
                }
            }
        }

        // Direct substring if long enough
        if (c.length >= 6 && p.contains(c)) return true
        if (p.length >= 6 && c.contains(p)) return true

        return false
    }

    /**
     * Checks if two subject names represent explicitly distinct academic subjects.
     * Used as a guard to ensure notes from one distinct subject (like "Математичний аналіз")
     * never bleed into another subject (like "Лінійна алгебра та аналітична геометрія").
     */
    fun isSubjectConflict(cabinetSubject: String, pubSubject: String): Boolean {
        val c = cleanSubjectName(cabinetSubject).lowercase(Locale.ROOT).trim()
        val p = cleanSubjectName(pubSubject).lowercase(Locale.ROOT).trim()
        if (c.isEmpty() || p.isEmpty()) return false
        if (c.startsWith("пара") || p.startsWith("пара")) return false
        if (isSubjectCompatible(c, p)) return false
        return true
    }

    /**
     * Resolves the Cabinet Week Number (0..16) for a given calendar date.
     * Looks at <select name="week"> options, date ranges, and falls back to semester math.
     */
    fun resolveCabinetWeek(doc: Document, targetDate: Calendar): Int {
        val dateShort = SimpleDateFormat("dd.MM", Locale.getDefault()).format(targetDate.time)
        val dateNoLeadZero = dateShort.replace(Regex("^0"), "").replace(Regex("\\.0"), ".")
        val dateFull = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(targetDate.time)

        // 1. Native Cabinet: Match .sch-day with date
        for (dayEl in doc.select(".sch-day")) {
            val dText = dayEl.selectFirst(".sch-day-date")?.text()?.trim() ?: ""
            if (dText == dateShort || dText == dateNoLeadZero) {
                val m = Pattern.compile("week=(\\d+)").matcher(dayEl.attr("href"))
                if (m.find()) {
                    m.group(1)?.toIntOrNull()?.let {
                        logInfo("Resolved Cabinet week $it from .sch-day date match: '$dText'")
                        return it
                    }
                }
            }
        }

        // 2. Native Cabinet: Match .sch-week title or text
        for (wEl in doc.select("a.sch-week")) {
            val title = wEl.attr("title")
            val href = wEl.attr("href")
            val weekNum = Regex("week=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: wEl.text().trim().toIntOrNull() ?: continue

            if (title.contains(dateShort) || title.contains(dateFull)) {
                logInfo("Resolved Cabinet week $weekNum from .sch-week title date match: '$title'")
                return weekNum
            }
        }

        // 3. Fallback: <select name="week"> options
        val options = doc.select("select[name=week] option, .week-select option")
        for (opt in options) {
            val weekVal = opt.attr("value").trim().toIntOrNull() ?: continue
            val optText = opt.text()
            if (optText.contains(dateShort) || optText.contains(dateFull)) {
                logInfo("Resolved Cabinet week $weekVal from option date match: '$optText'")
                return weekVal
            }
        }

        // 4. Current/Active week on page if targetDate is close to current date
        val currentWeekEl = doc.selectFirst(".sch-week.is-current, .sch-week.is-active")
        if (currentWeekEl != null) {
            val m = Pattern.compile("week=(\\d+)").matcher(currentWeekEl.attr("href"))
            val curWeek = if (m.find()) m.group(1)?.toIntOrNull() else currentWeekEl.text().trim().toIntOrNull()
            if (curWeek != null) {
                val now = Calendar.getInstance()
                val diffDays = Math.abs((targetDate.timeInMillis - now.timeInMillis) / (1000L * 60 * 60 * 24))
                if (diffDays <= 3) {
                    logInfo("Resolved Cabinet week $curWeek from active/current week element on page")
                    return curWeek
                }
            }
        }

        // 5. Calendar math fallback
        val calculated = calculateSemesterWeek(targetDate)
        logInfo("Resolved Cabinet week $calculated from calendar math for date $dateShort")
        return calculated
    }

    fun calculateSemesterWeek(date: Calendar): Int {
        val year = date.get(Calendar.YEAR)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        val week1StartMillis = cal.timeInMillis
        val diffMillis = date.timeInMillis - week1StartMillis
        val diffDays = (diffMillis / (1000L * 60 * 60 * 24)).toInt()
        val weekNum = (diffDays / 7) + 1
        return weekNum.coerceIn(0, 18)
    }

    fun mapTimeToPairNumber(text: String): Int? {
        val clean = text.replace(" ", "")
        return when {
            "08:30" in clean || "8:30" in clean -> 1
            "10:00" in clean -> 2
            "11:40" in clean || "12:00" in clean || "12:10" in clean -> 3
            "14:40" in clean || "13:30" in clean || "14:00" in clean || "14:10" in clean -> 4
            "15:00" in clean || "16:10" in clean || "15:20" in clean || "16:00" in clean -> 5
            "16:30" in clean || "17:40" in clean || "17:10" in clean || "17:30" in clean -> 6
            "18:00" in clean || "19:10" in clean || "19:00" in clean -> 7
            "20:40" in clean || "20:30" in clean -> 8
            else -> null
        }
    }

    private fun extractTimeRange(text: String): String {
        val m = Pattern.compile("(\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2})").matcher(text)
        return if (m.find()) m.group(1) ?: "" else ""
    }

    private fun defaultTimeForPair(pairNum: Int): String {
        return when (pairNum) {
            1 -> "08:30-09:50"
            2 -> "10:00-11:20"
            3 -> "11:40-13:00"
            4 -> "13:30-14:50"
            5 -> "15:00-16:20"
            6 -> "16:30-17:50"
            7 -> "18:00-19:20"
            8 -> "19:30-20:50"
            else -> "08:30-09:50"
        }
    }

    private fun dayNameForIndex(dayIndex: Int): String {
        return when (dayIndex) {
            0 -> "Понеділок"
            1 -> "Вівторок"
            2 -> "Середа"
            3 -> "Четвер"
            4 -> "П'ятниця"
            5 -> "Субота"
            6 -> "Неділя"
            else -> "День ${dayIndex + 1}"
        }
    }

    fun detectWeekNumber(doc: Document, explicitWeek: Int?): Int {
        if (explicitWeek != null && explicitWeek >= 0) return explicitWeek

        // 1. Native Cabinet active week link: <a class="sch-week is-active is-current" href="/site/schedule?week=4&day=1">4</a>
        val schWeekActive = doc.selectFirst(".sch-week.is-active, .sch-week.is-current, a[href*='week='].is-active")
        if (schWeekActive != null) {
            val m = Pattern.compile("week=(\\d+)").matcher(schWeekActive.attr("href"))
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { return it }
            }
            schWeekActive.text().trim().toIntOrNull()?.let { return it }
        }

        // 2. Week select option
        val selectedOption = doc.selectFirst("select[name=week] option[selected], .week-select option[selected]")
        selectedOption?.attr("value")?.trim()?.toIntOrNull()?.let { return it }

        // 3. Week tabs or links
        val activeWeekLink = doc.selectFirst(".nav-tabs li.active a[href*='week='], .pagination li.active a[href*='week='], a.active[href*='week=']")
        activeWeekLink?.let { a ->
            val m = Pattern.compile("week=(\\d+)").matcher(a.attr("href"))
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { return it }
            }
        }

        val headerText = doc.select("h1, h2, h3, .week-title, .sch-week-title, .sch-hint").text()
        val pattern = Pattern.compile("(\\d+)\\s*(тиждень|тижд|week)|(тиждень|тижд|week)\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
        val m = pattern.matcher(headerText)
        if (m.find()) {
            val num = m.group(1) ?: m.group(4)
            num?.toIntOrNull()?.let { return it }
        }

        return 1
    }

    fun detectDayIndex(doc: Document, explicitDay: Int?): Int? {
        if (explicitDay != null && explicitDay in 1..7) {
            return (explicitDay - 1).coerceIn(0, 6)
        }

        // 1. Native Cabinet active day link: <a class="sch-day is-active is-today" href="/site/schedule?week=4&day=1">
        val schDayActive = doc.selectFirst(".sch-day.is-active, a[href*='day='].is-active")
        if (schDayActive != null) {
            val m = Pattern.compile("day=(\\d+)").matcher(schDayActive.attr("href"))
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { return (it - 1).coerceIn(0, 6) }
            }
        }

        // 2. Bar day: <span class="sch-bar-day">Понеділок</span>
        val barDay = doc.selectFirst(".sch-bar-day, .day-title, .sch-day-name")?.text()?.lowercase(Locale.ROOT)
        if (barDay != null) {
            when {
                "понеділ" in barDay || barDay == "пн" -> return 0
                "вівтор" in barDay || barDay == "вт" -> return 1
                "серед" in barDay || barDay == "ср" -> return 2
                "четвер" in barDay || barDay == "чт" -> return 3
                "п'ятниц" in barDay || "пятниц" in barDay || barDay == "пт" -> return 4
                "субот" in barDay || barDay == "сб" -> return 5
                "неділ" in barDay || barDay == "нд" -> return 6
            }
        }

        val activeDayLink = doc.selectFirst(".nav-tabs li.active a[href*='day='], a.active[href*='day=']")
        activeDayLink?.let { a ->
            val m = Pattern.compile("day=(\\d+)").matcher(a.attr("href"))
            if (m.find()) {
                m.group(1)?.toIntOrNull()?.let { return (it - 1).coerceIn(0, 6) }
            }
        }

        val titleText = doc.select("h1, h2, h3, h4, .day-title, .sch-day-name").text().lowercase(Locale.ROOT)
        return when {
            "понеділ" in titleText -> 0
            "вівтор" in titleText -> 1
            "серед" in titleText -> 2
            "четвер" in titleText -> 3
            "п'ятниц" in titleText || "пятниц" in titleText -> 4
            "субот" in titleText -> 5
            "неділ" in titleText -> 6
            else -> null
        }
    }

    private fun parseWeekNumber(title: String): Int {
        val pattern = Pattern.compile("(\\d+)")
        val matcher = pattern.matcher(title)
        return if (matcher.find()) {
            matcher.group(1)?.toIntOrNull() ?: 1
        } else {
            1
        }
    }
}
