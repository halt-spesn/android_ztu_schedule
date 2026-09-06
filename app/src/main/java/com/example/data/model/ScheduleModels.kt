package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ScheduleGroup(
    val id: String,
    val name: String,
    val faculty: String = ""
)

enum class PairStatus {
    PAST,
    CURRENT,
    UPCOMING
}

enum class LessonType(val labelUk: String) {
    LECTURE("Лекція"),
    PRACTICE("Практичне"),
    LAB("Лабораторна"),
    MEETING("Зустріч"),
    OTHER("Заняття");

    companion object {
        fun fromString(kind: String): LessonType {
            val lower = kind.lowercase(Locale.ROOT)
            return when {
                "лекц" in lower -> LECTURE
                "практ" in lower -> PRACTICE
                "лабор" in lower -> LAB
                "зустріч" in lower -> MEETING
                else -> OTHER
            }
        }
    }
}

data class SchedulePair(
    val id: Long = 0,
    val weekNumber: Int,
    val dayIndex: Int,
    val dayName: String,
    val dateStr: String,
    val pairNumber: Int,
    val timeRange: String,
    val subject: String,
    val kind: String,
    val room: String,
    val roomUrl: String = "",
    val teacher: String,
    val teacherUrl: String = "",
    val subgroup: String = "" // "підгр. 1", "підгр. 2" or empty
) {
    val lessonType: LessonType
        get() = LessonType.fromString(kind)

    fun calculateStatus(isToday: Boolean): PairStatus {
        if (!isToday) return PairStatus.UPCOMING
            val parts = timeRange.split(Regex("\\s*[-–—]\\s*"))
        if (parts.size != 2) return PairStatus.UPCOMING
        
        try {
            val startParts = parts[0].trim().split(":")
            val endParts = parts[1].trim().split(":")
            if (startParts.size != 2 || endParts.size != 2) return PairStatus.UPCOMING

            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            val startMinutes = startParts[0].trim().toInt() * 60 + startParts[1].trim().toInt()
            val endMinutes = endParts[0].trim().toInt() * 60 + endParts[1].trim().toInt()

            return when {
                currentMinutes in startMinutes..endMinutes -> PairStatus.CURRENT
                currentMinutes > endMinutes -> PairStatus.PAST
                else -> PairStatus.UPCOMING
            }
        } catch (e: Exception) {
            return PairStatus.UPCOMING
        }
    }
}

data class ScheduleDay(
    val dayIndex: Int,
    val dayName: String,
    val dateStr: String,
    val isMarked: Boolean = false,
    val pairs: List<SchedulePair> = emptyList()
)

data class ScheduleWeek(
    val weekNumber: Int,
    val weekTitle: String,
    val note: String = "",
    val days: List<ScheduleDay> = emptyList()
)

data class ScheduleData(
    val groupId: String,
    val groupName: String,
    val faculty: String,
    val notice: String,
    val weeks: List<ScheduleWeek>,
    val lastUpdatedMillis: Long
)
