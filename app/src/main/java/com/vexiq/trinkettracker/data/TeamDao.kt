package com.vexiq.trinkettracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {

    @Query("SELECT * FROM teams WHERE isCollected = 0 ORDER BY teamNumber ASC")
    fun getNotCollectedTeams(): Flow<List<Team>>

    @Query("SELECT * FROM teams WHERE isCollected = 1 ORDER BY collectedAt DESC")
    fun getCollectedTeams(): Flow<List<Team>>

    @Query("SELECT * FROM teams")
    fun getAllTeams(): Flow<List<Team>>

    @Query("SELECT COUNT(*) FROM teams")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM teams WHERE isCollected = 1")
    suspend fun getCollectedCount(): Int

    @Query("SELECT teamNumber FROM teams WHERE isCollected = 1")
    suspend fun getCollectedTeamNumbers(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTeam(team: Team)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTeams(teams: List<Team>)

    @Update
    suspend fun updateTeam(team: Team)

    @Query("UPDATE teams SET isCollected = 1, photoPath = :photoPath, collectedAt = :timestamp WHERE teamNumber = :teamNumber")
    suspend fun markCollected(teamNumber: String, photoPath: String, timestamp: Long)

    /** Reset a team back to not-collected (e.g. remove from collected list) */
    @Query("UPDATE teams SET isCollected = 0, photoPath = NULL, collectedAt = NULL WHERE teamNumber = :teamNumber")
    suspend fun unmarkCollected(teamNumber: String)

    /** Batch reset – used for the "Remove" action in selection mode */
    @Query("UPDATE teams SET isCollected = 0, photoPath = NULL, collectedAt = NULL WHERE teamNumber IN (:teamNumbers)")
    suspend fun unmarkCollectedBatch(teamNumbers: List<String>)

    @Query("SELECT * FROM teams WHERE teamNumber = :teamNumber")
    suspend fun getTeamByNumber(teamNumber: String): Team?
}
