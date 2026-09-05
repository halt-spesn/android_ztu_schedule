package com.example

import com.example.data.model.LessonType
import com.example.data.model.PairStatus
import com.example.data.remote.ZtuScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZtuScheduleParserTest {

    private val sampleHtml = """
        <!DOCTYPE html>
        <html lang="uk">
        <head><title>Розклад ЖТУ</title></head>
        <body>
            <div class="sch-title">
                <h1>КІ-26-1</h1>
                <p class="sch-faculty">Факультет інформаційно-комп'ютерних технологій</p>
            </div>
            <div class="sch-notice">Увага! Розклад коригується, слідкуйте за змінами!</div>
            <details class="sch-fold" data-week="0">
                <summary>
                    <h2 class="sch-week-title">Тиждень 0</h2>
                    <p class="sch-week-note">Перший тиждень занять</p>
                </summary>
                <table class="sch-table">
                    <thead>
                        <tr>
                            <th></th>
                            <th class="sch-day-name is-marked">Понеділок <span class="sch-date">31.08</span></th>
                            <th class="sch-day-name">Вівторок <span class="sch-date">01.09</span></th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <th class="sch-hour">
                                <span class="sch-hour-num">1</span>
                                <span class="sch-hour-time">08:30-09:50</span>
                            </th>
                            <td class="sch-cell">
                                <div class="sch-pair">
                                    <div class="sch-subject">Комп'ютерна графіка</div>
                                    <div class="sch-kind">Лекція</div>
                                    <div class="sch-room"><a href="/schedule/room?id=233">233</a></div>
                                    <div class="sch-teachers"><a href="/schedule/teacher?id=12">Лобанчикова Н. М.</a></div>
                                </div>
                            </td>
                            <td class="sch-cell is-free"></td>
                        </tr>
                        <tr>
                            <th class="sch-hour">
                                <span class="sch-hour-num">2</span>
                                <span class="sch-hour-time">10:00-11:20</span>
                            </th>
                            <td class="sch-cell">
                                <div class="sch-pair">
                                    <div class="sch-subject">Вища математика</div>
                                    <div class="sch-kind">Практичне</div>
                                    <div class="sch-room"><a href="/schedule/room?id=315">315</a></div>
                                    <div class="sch-teachers">Ковальчук В. В.</div>
                                    <div class="sch-subgroup">підгр. 1</div>
                                </div>
                            </td>
                            <td class="sch-cell is-free"></td>
                        </tr>
                    </tbody>
                </table>
            </details>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun parseScheduleHtml_correctlyExtractsGroupAndPairs() {
        val data = ZtuScheduleParser.parseScheduleHtml(sampleHtml, defaultGroupId = "612")

        assertEquals("КІ-26-1", data.groupName)
        assertEquals("Факультет інформаційно-комп'ютерних технологій", data.faculty)
        assertTrue(data.notice.contains("Розклад коригується"))
        assertEquals(1, data.weeks.size)

        val week0 = data.weeks[0]
        assertEquals(0, week0.weekNumber)
        assertEquals("Тиждень 0", week0.weekTitle)
        assertEquals("Перший тиждень занять", week0.note)

        val monday = week0.days.find { it.dayIndex == 0 }
        assertNotNull(monday)
        assertEquals("31.08", monday?.dateStr)
        assertEquals(2, monday?.pairs?.size)

        val pair1 = monday!!.pairs[0]
        assertEquals(1, pair1.pairNumber)
        assertEquals("08:30-09:50", pair1.timeRange)
        assertEquals("Комп'ютерна графіка", pair1.subject)
        assertEquals("Лекція", pair1.kind)
        assertEquals(LessonType.LECTURE, pair1.lessonType)
        assertEquals("233", pair1.room)
        assertEquals("Лобанчикова Н. М.", pair1.teacher)

        val pair2 = monday.pairs[1]
        assertEquals(2, pair2.pairNumber)
        assertEquals("Вища математика", pair2.subject)
        assertEquals(LessonType.PRACTICE, pair2.lessonType)
        assertEquals("підгр. 1", pair2.subgroup)
    }

    @Test
    fun parseGroupListHtml_extractsGroups() {
        val html = """
            <div class="groups-list">
                <a href="/schedule/group?id=612">КІ-26-1</a>
                <a href="/schedule/group?id=613">КІ-26-2</a>
                <a href="/schedule/group?id=701">ІПЗ-23-1</a>
            </div>
        """.trimIndent()

        val groups = ZtuScheduleParser.parseGroupListHtml(html)
        assertEquals(3, groups.size)
        assertTrue(groups.any { it.id == "612" && it.name == "КІ-26-1" })
        assertTrue(groups.any { it.id == "613" && it.name == "КІ-26-2" })
        assertTrue(groups.any { it.id == "701" && it.name == "ІПЗ-23-1" })
    }
}
