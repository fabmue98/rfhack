package com.fabmue.checkoutmate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val finishedAt: Long,
    val gameStart: Int,
    val legsToWin: Int,
    val setsToWin: Int,
    val doubleIn: Boolean,
    val doubleOut: Boolean,
    val playersCsv: String,
    val winnerName: String,
)

@Entity(indices = [Index("matchId")])
data class MatchPlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val name: String,
    val dartsThrown: Int,
    val pointsScored: Int,
    val average: Double,
    val first9Average: Double,
    val legsWon: Int,
    val setsWon: Int,
    val count180: Int,
    val checkoutChances: Int,
    val checkoutHits: Int,
    val highestFinish: Int,
    val bestLegDarts: Int,
    val won: Boolean,
    val createdAt: Long,
)

@Entity
data class TrainingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: String,
    val score: Int,
    val darts: Int,
    val createdAt: Long,
)
