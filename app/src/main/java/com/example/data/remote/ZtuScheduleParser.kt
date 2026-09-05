package com.example.data.remote

import com.example.data.model.ScheduleData
import com.example.data.model.ScheduleDay
import com.example.data.model.ScheduleGroup
import com.example.data.model.SchedulePair
import com.example.data.model.ScheduleWeek
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Pattern

object ZtuScheduleParser {

    fun parseScheduleHtml(html: String, defaultGroupId: String = "612"): ScheduleData {
        val doc: Document = Jsoup.parse(html)

        // Extract group name and faculty from header
        val titleEl = doc.selectFirst(".sch-title h1")
        val groupName = titleEl?.text()?.trim() ?: "КІ-26-1"

        val facultyEl = doc.selectFirst(".sch-title .sch-faculty")
        val faculty = facultyEl?.text()?.trim() ?: "Факультет інформаційно-комп'ютерних технологій"

        val noticeEl = doc.selectFirst(".sch-notice")
        val notice = noticeEl?.text()?.trim() ?: ""

        val weeks = mutableListOf<ScheduleWeek>()

        // Process all week folds
        val weekElements = doc.select("details.sch-fold")
        for (weekEl in weekElements) {
            val weekAttr = weekEl.attr("data-week").trim()
            val weekTitle = weekEl.selectFirst(".sch-week-title")?.text()?.trim()
                ?: "Тиждень $weekAttr"
            val weekNumber = weekAttr.toIntOrNull() ?: parseWeekNumber(weekTitle)
            val weekNote = weekEl.selectFirst(".sch-week-note")?.text()?.trim() ?: ""

            val table = weekEl.selectFirst("table.sch-table")
            if (table == null) {
                weeks.add(ScheduleWeek(weekNumber, weekTitle, weekNote, emptyList()))
                continue
            }

            // Parse days from thead
            data class DayHeader(val dayIndex: Int, val dayName: String, val dateStr: String, val isMarked: Boolean)
            val dayHeaders = mutableListOf<DayHeader>()
            val thDays = table.select("thead th.sch-day-name")
            for ((index, th) in thDays.withIndex()) {
                val dateSpan = th.selectFirst(".sch-date")
                val dateStr = dateSpan?.text()?.trim() ?: ""
                
                // Day name without date span or badges
                val dateSpanHtml = dateSpan?.outerHtml() ?: ""
                val flagsHtml = th.select(".sch-day-flag").outerHtml()
                var dayNameText = th.text()
                if (dateStr.isNotEmpty()) {
                    dayNameText = dayNameText.replace(dateStr, "")
                }
                dayNameText = dayNameText.replace("початок тижня", "").trim()

                val isMarked = th.hasClass("is-marked")
                dayHeaders.add(DayHeader(index, dayNameText, dateStr, isMarked))
            }

            // Map dayIndex to list of pairs
            val pairsByDayIndex = mutableMapOf<Int, MutableList<SchedulePair>>()
            for (header in dayHeaders) {
                pairsByDayIndex[header.dayIndex] = mutableListOf()
            }

            // Parse hour rows in tbody
            val rows = table.select("tbody tr")
            for (row in rows) {
                val hourTh = row.selectFirst("th.sch-hour") ?: continue
                val pairNumStr = hourTh.selectFirst(".sch-hour-num")?.text()?.trim() ?: "1"
                val pairNum = pairNumStr.toIntOrNull() ?: 1
                val timeRange = hourTh.selectFirst(".sch-hour-time")?.text()?.trim() ?: "08:30-09:50"

                val cells = row.select("td.sch-cell")
                for ((dayColIndex, cell) in cells.withIndex()) {
                    if (cell.hasClass("is-free") && cell.select(".sch-pair").isEmpty()) {
                        continue
                    }

                    val pairElements = cell.select(".sch-pair")
                    val dayHeader = dayHeaders.getOrNull(dayColIndex)
                    val dayName = dayHeader?.dayName ?: ""
                    val dateStr = dayHeader?.dateStr ?: ""

                    for (pairEl in pairElements) {
                        val subject = pairEl.selectFirst(".sch-subject")?.text()?.trim() ?: ""
                        if (subject.isEmpty()) continue

                        val kind = pairEl.selectFirst(".sch-kind")?.text()?.trim() ?: "Лекція"
                        
                        val roomEl = pairEl.selectFirst(".sch-room")
                        val room = roomEl?.text()?.trim() ?: ""
                        val roomUrl = roomEl?.selectFirst("a")?.attr("href")?.trim() ?: ""

                        val teacherEl = pairEl.selectFirst(".sch-teachers")
                        val teacher = teacherEl?.text()?.trim() ?: ""
                        val teacherUrl = teacherEl?.selectFirst("a")?.attr("href")?.trim() ?: ""

                        val subgroupEl = pairEl.selectFirst(".sch-subgroup")
                        val subgroup = subgroupEl?.text()?.trim() ?: ""

                        val pairObj = SchedulePair(
                            id = 0,
                            weekNumber = weekNumber,
                            dayIndex = dayColIndex,
                            dayName = dayName,
                            dateStr = dateStr,
                            pairNumber = pairNum,
                            timeRange = timeRange,
                            subject = subject,
                            kind = kind,
                            room = room,
                            roomUrl = roomUrl,
                            teacher = teacher,
                            teacherUrl = teacherUrl,
                            subgroup = subgroup
                        )

                        pairsByDayIndex[dayColIndex]?.add(pairObj)
                    }
                }
            }

            // Build ScheduleDay objects
            val days = dayHeaders.map { header ->
                ScheduleDay(
                    dayIndex = header.dayIndex,
                    dayName = header.dayName,
                    dateStr = header.dateStr,
                    isMarked = header.isMarked,
                    pairs = pairsByDayIndex[header.dayIndex]?.sortedBy { it.pairNumber } ?: emptyList()
                )
            }

            weeks.add(ScheduleWeek(weekNumber, weekTitle, weekNote, days))
        }

        return ScheduleData(
            groupId = defaultGroupId,
            groupName = groupName,
            faculty = faculty,
            notice = notice,
            weeks = weeks.sortedBy { it.weekNumber },
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    fun parseGroupListHtml(html: String): List<ScheduleGroup> {
        val doc: Document = Jsoup.parse(html)
        val links = doc.select("a[href*=/schedule/group?id=]")
        val list = mutableListOf<ScheduleGroup>()
        val seenIds = mutableSetOf<String>()

        val pattern = Pattern.compile("id=(\\d+)")

        for (a in links) {
            val href = a.attr("href")
            val matcher = pattern.matcher(href)
            if (matcher.find()) {
                val id = matcher.group(1) ?: continue
                if (seenIds.add(id)) {
                    val name = a.text().trim()
                    if (name.isNotEmpty()) {
                        list.add(ScheduleGroup(id = id, name = name))
                    }
                }
            }
        }
        return list.sortedBy { it.name }
    }

    private fun parseWeekNumber(title: String): Int {
        val digits = title.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}
