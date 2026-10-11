package com.fabmue.checkoutmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DartsDao {

    @Insert
    suspend fun insertMatch(match: MatchEntity): Long

    @Insert
    suspend fun insertMatchPlayers(players: List<MatchPlayerEntity>)

    @Insert
    suspend fun insertTraining(training: TrainingEntity): Long

    @Query("SELECT * FROM MatchEntity ORDER BY createdAt DESC")
    fun matches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM MatchPlayerEntity WHERE matchId = :matchId ORDER BY id ASC")
    suspend fun playersForMatch(matchId: Long): List<MatchPlayerEntity>

    @Query("SELECT * FROM MatchPlayerEntity ORDER BY createdAt ASC")
    fun allPlayerRecords(): Flow<List<MatchPlayerEntity>>

    @Query("SELECT * FROM MatchPlayerEntity ORDER BY createdAt ASC")
    suspend fun allPlayerRecordsOnce(): List<MatchPlayerEntity>

    @Query("SELECT DISTINCT name FROM MatchPlayerEntity ORDER BY name ASC")
    fun playerNames(): Flow<List<String>>

    @Query("DELETE FROM MatchEntity WHERE id = :matchId")
    suspend fun deleteMatch(matchId: Long)

    @Query("DELETE FROM MatchPlayerEntity WHERE matchId = :matchId")
    suspend fun deleteMatchPlayers(matchId: Long)

    @Query("SELECT * FROM TrainingEntity ORDER BY createdAt DESC")
    fun trainings(): Flow<List<TrainingEntity>>
}
