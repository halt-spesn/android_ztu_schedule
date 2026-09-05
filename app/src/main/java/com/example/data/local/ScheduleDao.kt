package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_pairs WHERE groupId = :groupId ORDER BY weekNumber ASC, dayIndex ASC, pairNumber ASC")
    fun getPairsForGroup(groupId: String): Flow<List<PairEntity>>

    @Query("SELECT * FROM schedule_pairs WHERE groupId = :groupId AND weekNumber = :weekNumber ORDER BY dayIndex ASC, pairNumber ASC")
    fun getPairsForWeek(groupId: String, weekNumber: Int): Flow<List<PairEntity>>

    @Query("SELECT * FROM schedule_pairs WHERE groupId = :groupId ORDER BY weekNumber ASC, dayIndex ASC, pairNumber ASC")
    suspend fun getAllPairsSync(groupId: String): List<PairEntity>

    @Query("SELECT * FROM schedule_pairs WHERE groupId = :groupId AND weekNumber = :weekNumber AND dayIndex = :dayIndex ORDER BY pairNumber ASC")
    suspend fun getPairsForDaySync(groupId: String, weekNumber: Int, dayIndex: Int): List<PairEntity>

    @Query("SELECT * FROM schedule_pairs WHERE groupId = :groupId AND dateStr = :dateStr ORDER BY pairNumber ASC")
    suspend fun getPairsByDateSync(groupId: String, dateStr: String): List<PairEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairs(pairs: List<PairEntity>)

    @Query("DELETE FROM schedule_pairs WHERE groupId = :groupId")
    suspend fun clearPairsForGroup(groupId: String)

    @Query("SELECT * FROM schedule_metadata WHERE groupId = :groupId LIMIT 1")
    fun getMetadata(groupId: String): Flow<MetadataEntity?>

    @Query("SELECT * FROM schedule_metadata WHERE groupId = :groupId LIMIT 1")
    suspend fun getMetadataSync(groupId: String): MetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MetadataEntity)

    @Query("SELECT * FROM schedule_weeks_info WHERE groupId = :groupId ORDER BY weekNumber ASC")
    fun getWeeksInfo(groupId: String): Flow<List<WeekInfoEntity>>

    @Query("SELECT * FROM schedule_weeks_info WHERE groupId = :groupId ORDER BY weekNumber ASC")
    suspend fun getWeeksInfoSync(groupId: String): List<WeekInfoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeksInfo(weeks: List<WeekInfoEntity>)

    @Query("DELETE FROM schedule_weeks_info WHERE groupId = :groupId")
    suspend fun clearWeeksInfoForGroup(groupId: String)

    @Transaction
    suspend fun updateScheduleData(
        groupId: String,
        metadata: MetadataEntity,
        weeks: List<WeekInfoEntity>,
        pairs: List<PairEntity>
    ) {
        clearPairsForGroup(groupId)
        clearWeeksInfoForGroup(groupId)
        insertMetadata(metadata)
        insertWeeksInfo(weeks)
        insertPairs(pairs)
    }

    @Query("SELECT * FROM cached_groups ORDER BY name ASC")
    fun getAllCachedGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM cached_groups ORDER BY name ASC")
    suspend fun getAllCachedGroupsSync(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)
}
