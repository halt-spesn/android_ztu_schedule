package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ScheduleGroup
import com.example.data.model.SchedulePair

@Entity(tableName = "schedule_pairs")
data class PairEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,
    val weekNumber: Int,
    val dayIndex: Int,
    val dayName: String,
    val dateStr: String,
    val pairNumber: Int,
    val timeRange: String,
    val subject: String,
    val kind: String,
    val room: String,
    val roomUrl: String,
    val teacher: String,
    val teacherUrl: String,
    val subgroup: String
) {
    fun toDomainModel(): SchedulePair {
        return SchedulePair(
            id = id,
            weekNumber = weekNumber,
            dayIndex = dayIndex,
            dayName = dayName,
            dateStr = dateStr,
            pairNumber = pairNumber,
            timeRange = timeRange,
            subject = subject,
            kind = kind,
            room = room,
            roomUrl = roomUrl,
            teacher = teacher,
            teacherUrl = teacherUrl,
            subgroup = subgroup
        )
    }

    companion object {
        fun fromDomainModel(groupId: String, pair: SchedulePair): PairEntity {
            return PairEntity(
                id = pair.id,
                groupId = groupId,
                weekNumber = pair.weekNumber,
                dayIndex = pair.dayIndex,
                dayName = pair.dayName,
                dateStr = pair.dateStr,
                pairNumber = pair.pairNumber,
                timeRange = pair.timeRange,
                subject = pair.subject,
                kind = pair.kind,
                room = pair.room,
                roomUrl = pair.roomUrl,
                teacher = pair.teacher,
                teacherUrl = pair.teacherUrl,
                subgroup = pair.subgroup
            )
        }
    }
}

@Entity(tableName = "schedule_metadata")
data class MetadataEntity(
    @PrimaryKey
    val groupId: String,
    val groupName: String,
    val faculty: String,
    val notice: String,
    val lastUpdatedMillis: Long
)

@Entity(tableName = "schedule_weeks_info")
data class WeekInfoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,
    val weekNumber: Int,
    val weekTitle: String,
    val note: String
)

@Entity(tableName = "cached_groups")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val faculty: String = ""
) {
    fun toDomainModel(): ScheduleGroup = ScheduleGroup(id, name, faculty)
}
