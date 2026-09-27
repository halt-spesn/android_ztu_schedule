package com.example

import com.example.data.remote.CabinetScheduleParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CabinetScheduleParserTest {

    @Test
    fun testParseDayTableWithLaagAndMeetLink() {
        val html = """
            <!DOCTYPE html>
            <html lang="uk">
            <head><title>Розклад - Кабінет студента</title></head>
            <body>
                <div class="navbar-brand">КІ-26-1</div>
                <div class="sch-faculty">Факультет ІКТ</div>
                
                <div class="row">
                    <div class="col-md-12">
                        <h2>Понеділок (Тиждень 4)</h2>
                        <table class="table table-bordered table-striped">
                            <thead>
                                <tr>
                                    <th>№</th>
                                    <th>Час</th>
                                    <th>Дисципліна</th>
                                    <th>Викладач</th>
                                    <th>Аудиторія</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>4</td>
                                    <td>14:40 - 16:00</td>
                                    <td>
                                        <strong>Лінійна алгебра та аналітична геометрія (ЛААГ)</strong> (Лекція)
                                        <br>
                                        <div class="alert alert-info">
                                            Лекція відбудеться в Google Meet: 
                                            <a href="https://meet.google.com/abc-defg-hij">meet.google.com/abc-defg-hij</a>
                                        </div>
                                    </td>
                                    <td>Петренко П. П.</td>
                                    <td>Дистанційно</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val parsed = CabinetScheduleParser.parseCabinetSchedule(
            html = html,
            defaultGroupId = "612",
            fallbackGroupName = "КІ-26-1",
            explicitWeek = 4,
            explicitDay = 1
        )

        // Verify extracted notes list
        val laagNote = parsed.extractedNotes.find { it.pairNumber == 4 && it.dayIndex == 0 }
        assertNotNull("Note for Monday 4th pair should not be null", laagNote)
        assertTrue("Note should contain Google Meet link", laagNote!!.note.contains("https://meet.google.com/abc-defg-hij"))
        assertTrue("Subject should be recognized", CabinetScheduleParser.isSubjectCompatible(laagNote.subject, "Лінійна алгебра та аналітична геометрія"))
    }

    @Test
    fun testSubjectCompatibilityStrictness() {
        // ЛААГ must match Лінійна алгебра та аналітична геометрія
        assertTrue(CabinetScheduleParser.isSubjectCompatible("лааг", "Лінійна алгебра та аналітична геометрія"))
        assertTrue(CabinetScheduleParser.isSubjectCompatible("ЛААГ", "Лінійна алгебра та аналітична геометрія (ЛААГ)"))
        assertTrue(CabinetScheduleParser.isSubjectCompatible("Лінійна алгебра", "Лінійна алгебра та аналітична геометрія"))

        // CRITICAL: Математичний аналіз MUST NOT match Лінійна алгебра та аналітична геометрія!
        assertFalse(CabinetScheduleParser.isSubjectCompatible("Математичний аналіз", "Лінійна алгебра та аналітична геометрія"))
        assertFalse(CabinetScheduleParser.isSubjectCompatible("Мат аналіз", "Лінійна алгебра та аналітична геометрія"))
        assertFalse(CabinetScheduleParser.isSubjectCompatible("Мат. аналіз", "Лінійна алгебра та аналітична геометрія"))
        assertFalse(CabinetScheduleParser.isSubjectCompatible("математичний аналіз (лек)", "Лінійна алгебра та аналітична геометрія"))

        // Unrelated subjects must not match
        assertFalse(CabinetScheduleParser.isSubjectCompatible("Фізика", "Хімія"))
        assertFalse(CabinetScheduleParser.isSubjectCompatible("Комп'ютерна графіка", "Лінійна алгебра"))

        // Subject conflicts
        assertTrue(CabinetScheduleParser.isSubjectConflict("Математичний аналіз", "Лінійна алгебра та аналітична геометрія"))
        assertTrue(CabinetScheduleParser.isSubjectConflict("Фізика", "Хімія"))
        assertFalse(CabinetScheduleParser.isSubjectConflict("Пара 4", "Лінійна алгебра та аналітична геометрія"))
        assertFalse(CabinetScheduleParser.isSubjectConflict("ЛААГ", "Лінійна алгебра та аналітична геометрія"))
    }

    @Test
    fun testResolveCabinetWeekForDate() {
        val html = """
            <select name="week">
                <option value="1">Тиждень 1 (07.09.2026 - 13.09.2026)</option>
                <option value="2">Тиждень 2 (14.09.2026 - 20.09.2026)</option>
                <option value="3" selected>Тиждень 3 (21.09.2026 - 27.09.2026)</option>
                <option value="4">Тиждень 4 (28.09.2026 - 04.10.2026)</option>
                <option value="5">Тиждень 5 (05.10.2026 - 11.10.2026)</option>
            </select>
        """.trimIndent()

        val doc = Jsoup.parse(html)

        // Monday September 28, 2026
        val sep28 = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 28)
        }
        val resolvedWeek = CabinetScheduleParser.resolveCabinetWeek(doc, sep28)
        assertEquals("September 28 must resolve to Week 4 in Cabinet", 4, resolvedWeek)

        // Sunday September 27, 2026
        val sep27 = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 27)
        }
        val resolvedWeekToday = CabinetScheduleParser.resolveCabinetWeek(doc, sep27)
        assertEquals("September 27 must resolve to Week 3 in Cabinet", 3, resolvedWeekToday)
    }

    @Test
    fun testCalendarMathSemesterWeek() {
        // Sep 7, 2026 is Week 1
        val sep7 = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 7) }
        assertEquals(1, CabinetScheduleParser.calculateSemesterWeek(sep7))

        // Sep 28, 2026 is Week 4
        val sep28 = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 28) }
        assertEquals(4, CabinetScheduleParser.calculateSemesterWeek(sep28))
    }

    @Test
    fun testParseMatrixTableWithoutSchClasses() {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
                <table class="table">
                    <thead>
                        <tr>
                            <th>Час</th>
                            <th>Понеділок</th>
                            <th>Вівторок</th>
                            <th>Середа</th>
                            <th>Четвер</th>
                            <th>П'ятниця</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>4 пара<br>14:40-16:00</td>
                            <td>
                                <b>ЛААГ</b> (Лекція)<br>
                                доц. Коваль В. В.<br>
                                <span class="note">Підключення: https://meet.google.com/xyz-test</span>
                            </td>
                            <td></td>
                            <td></td>
                            <td></td>
                            <td></td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val parsed = CabinetScheduleParser.parseCabinetSchedule(
            html = html,
            explicitWeek = 4
        )

        val note = parsed.extractedNotes.find { it.dayIndex == 0 && it.pairNumber == 4 }
        assertNotNull("Matrix table Monday pair 4 note must be captured", note)
        assertTrue(note!!.note.contains("https://meet.google.com/xyz-test"))
        assertTrue(CabinetScheduleParser.isSubjectCompatible(note.subject, "Лінійна алгебра та аналітична геометрія"))
    }

    @Test
    fun testParseRealCabHtml() {
        val fileCandidates = listOf(
            java.io.File("cab.html"),
            java.io.File("../cab.html")
        )
        val file = fileCandidates.firstOrNull { it.exists() }
        if (file == null) return

        val html = file.readText()
        val parsed = CabinetScheduleParser.parseCabinetSchedule(
            html = html,
            defaultGroupId = "612",
            fallbackGroupName = "КІ-26-1"
        )

        // 1. Group name
        assertEquals("КІ-26-1", parsed.scheduleData.groupName)

        // 2. Detected week
        assertEquals("Detected week must be 4", 4, parsed.detectedCurrentWeek)

        // 3. Available weeks
        assertTrue("Available weeks must include 0 to 16", parsed.availableWeeks.contains(4))
        assertTrue("Available weeks must include 0 to 16", parsed.availableWeeks.contains(0))

        // 4. Extracted notes for Monday pair 4 (LAAG)
        val laagNote = parsed.extractedNotes.find { it.dayIndex == 0 && it.pairNumber == 4 }
        assertNotNull("Note for Monday 4th pair (LAAG) must be extracted from cab.html", laagNote)
        assertEquals("Лінійна алгебра та аналітична геометрія", laagNote!!.subject)
        assertEquals("Головня Руслан Миколайович", laagNote.teacher)
        assertEquals("підгрупа 1", laagNote.subgroup)
        assertTrue("Note must contain topic", laagNote.note.contains("Практичне заняття. Тема 2: Матриці та визначники"))
        assertTrue("Note must contain learn.ztu.edu.ua link", laagNote.note.contains("https://learn.ztu.edu.ua"))
        assertTrue("Note must contain google drive link", laagNote.note.contains("https://drive.google.com"))

        // 5. Subject compatibility
        assertTrue(CabinetScheduleParser.isSubjectCompatible(laagNote.subject, "Лінійна алгебра та аналітична геометрія"))
        assertFalse(CabinetScheduleParser.isSubjectConflict(laagNote.subject, "Лінійна алгебра та аналітична геометрія"))

        // 6. Resolve Cabinet week for Sep 28
        val doc = Jsoup.parse(html)
        val sep28 = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 28) }
        assertEquals(4, CabinetScheduleParser.resolveCabinetWeek(doc, sep28))
    }

    @Test
    fun testWednesdayNotesDoNotBleedAcrossDifferentPairNumbers() {
        val notes = listOf(
            com.example.data.remote.CabinetLessonNote(
                weekNumber = 4,
                dayIndex = 2, // Wednesday
                pairNumber = 2,
                subject = "Іноземна мова",
                note = "Завдання до 2 пари",
                subgroup = ""
            )
        )

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

        fun findNoteForPair(pair: com.example.data.model.SchedulePair, dayIdx: Int, weekNumber: Int): String? {
            val candidateNotes = notes.filter { note ->
                cabinetWeekToBiWeekly(note.weekNumber) == weekNumber
            }
            val matchingNotes = candidateNotes.filter { note ->
                note.dayIndex == dayIdx &&
                    note.pairNumber == pair.pairNumber &&
                    isSubgroupMatching(note.subgroup, pair.subgroup) &&
                    CabinetScheduleParser.isSubjectCompatible(note.subject, pair.subject) &&
                    !CabinetScheduleParser.isSubjectConflict(note.subject, pair.subject)
            }
            return matchingNotes.firstOrNull()?.note
        }

        val wedPair2 = com.example.data.model.SchedulePair(
            id = 1, weekNumber = 2, dayIndex = 2, dayName = "Середа", dateStr = "30.09",
            pairNumber = 2, timeRange = "10:00-11:20",
            subject = "Іноземна мова, професійна та ділова комунікація", kind = "Практична",
            room = "310", teacher = "Викладач 1"
        )
        val wedPair3 = com.example.data.model.SchedulePair(
            id = 2, weekNumber = 2, dayIndex = 2, dayName = "Середа", dateStr = "30.09",
            pairNumber = 3, timeRange = "11:40-13:00",
            subject = "Іноземна мова", kind = "Практична",
            room = "310", teacher = "Викладач 1"
        )

        // Pair 2 MUST get the note
        val noteForPair2 = findNoteForPair(wedPair2, 2, 2)
        assertEquals("Завдання до 2 пари", noteForPair2)

        // Pair 3 MUST NOT get the note (no bleeding across pair numbers!)
        val noteForPair3 = findNoteForPair(wedPair3, 2, 2)
        org.junit.Assert.assertNull("Pair 3 must not get note meant for pair 2", noteForPair3)

        // Week 1 MUST NOT get the note (no bleeding across bi-weekly parity!)
        val noteForWeek1Pair2 = findNoteForPair(wedPair2.copy(weekNumber = 1), 2, 1)
        org.junit.Assert.assertNull("Week 1 must not get note meant for Week 2 (Cabinet week 4)", noteForWeek1Pair2)
    }
}
