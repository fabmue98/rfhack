package com.fabmue.checkoutmate.data

import com.fabmue.checkoutmate.data.local.DartsDao
import com.fabmue.checkoutmate.data.local.MatchEntity
import com.fabmue.checkoutmate.data.local.MatchPlayerEntity
import com.fabmue.checkoutmate.data.local.TrainingEntity
import com.fabmue.checkoutmate.domain.X01Match
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

data class PlayerStats(
    val name: String,
    val matches: Int,
    val legsWon: Int,
    val average: Double,
    val first9Average: Double,
    val checkoutPercent: Double,
    val count180: Int,
    val highestFinish: Int,
    val bestLegDarts: Int?,
    val averageSeries: List<Double>,
)

class HistoryRepository(private val dao: DartsDao) {

    val matches: Flow<List<MatchEntity>> = dao.matches()
    val trainings: Flow<List<TrainingEntity>> = dao.trainings()
    val playerNames: Flow<List<String>> = dao.playerNames()
    val stats: Flow<List<PlayerStats>> = dao.allPlayerRecords().map(::aggregate)

    suspend fun playersForMatch(matchId: Long): List<MatchPlayerEntity> =
        dao.playersForMatch(matchId)

    suspend fun saveFinishedMatch(match: X01Match) {
        val now = System.currentTimeMillis()
        val c = match.config
        val winnerName = match.winner?.let { c.players.getOrNull(it) } ?: ""
        val matchId = dao.insertMatch(
            MatchEntity(
                createdAt = now,
                finishedAt = now,
                gameStart = c.start,
                legsToWin = c.legsToWin,
                setsToWin = c.setsToWin,
                doubleIn = c.doubleIn,
                doubleOut = c.doubleOut,
                playersCsv = c.players.joinToString(", "),
                winnerName = winnerName,
            )
        )
        val rows = match.players.mapIndexed { i, p ->
            MatchPlayerEntity(
                matchId = matchId,
                name = p.name,
                dartsThrown = p.dartsThrown,
                pointsScored = p.pointsScored,
                average = p.threeDartAverage,
                first9Average = p.first9Average,
                legsWon = p.legs,
                setsWon = p.sets,
                count180 = p.count180,
                checkoutChances = p.checkoutChances,
                checkoutHits = p.checkoutHits,
                highestFinish = p.highestFinish,
                bestLegDarts = p.bestLegDarts,
                won = match.winner == i,
                createdAt = now,
            )
        }
        dao.insertMatchPlayers(rows)
    }

    suspend fun deleteMatch(matchId: Long) {
        dao.deleteMatchPlayers(matchId)
        dao.deleteMatch(matchId)
    }

    suspend fun saveTraining(game: String, score: Int, darts: Int) {
        dao.insertTraining(
            TrainingEntity(game = game, score = score, darts = darts, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun buildCsv(): String {
        val records = dao.allPlayerRecordsOnce()
        val sb = StringBuilder()
        sb.append(
            "date,player,won,darts,points,average,first9,legsWon,setsWon,180s," +
                "checkoutChances,checkoutHits,highestFinish,bestLegDarts\n"
        )
        records.forEach { r ->
            sb.append(r.createdAt).append(',')
                .append(escape(r.name)).append(',')
                .append(r.won).append(',')
                .append(r.dartsThrown).append(',')
                .append(r.pointsScored).append(',')
                .append(fmt(r.average)).append(',')
                .append(fmt(r.first9Average)).append(',')
                .append(r.legsWon).append(',')
                .append(r.setsWon).append(',')
                .append(r.count180).append(',')
                .append(r.checkoutChances).append(',')
                .append(r.checkoutHits).append(',')
                .append(r.highestFinish).append(',')
                .append(r.bestLegDarts).append('\n')
        }
        return sb.toString()
    }

    private fun aggregate(records: List<MatchPlayerEntity>): List<PlayerStats> =
        records.groupBy { it.name }.map { (name, list) ->
            val sorted = list.sortedBy { it.createdAt }
            val totalDarts = sorted.sumOf { it.dartsThrown }
            val totalPoints = sorted.sumOf { it.pointsScored }
            val avg = if (totalDarts > 0) totalPoints.toDouble() / totalDarts * 3 else 0.0
            val chances = sorted.sumOf { it.checkoutChances }
            val hits = sorted.sumOf { it.checkoutHits }
            val checkoutPct = if (chances > 0) hits * 100.0 / chances else 0.0
            val first9values = sorted.map { it.first9Average }.filter { it > 0 }
            val first9 = if (first9values.isEmpty()) 0.0 else first9values.average()
            val bestLeg = sorted.mapNotNull { if (it.bestLegDarts > 0) it.bestLegDarts else null }.minOrNull()
            PlayerStats(
                name = name,
                matches = sorted.size,
                legsWon = sorted.sumOf { it.legsWon },
                average = avg,
                first9Average = first9,
                checkoutPercent = checkoutPct,
                count180 = sorted.sumOf { it.count180 },
                highestFinish = sorted.maxOf { it.highestFinish },
                bestLegDarts = bestLeg,
                averageSeries = sorted.map { it.average },
            )
        }.sortedByDescending { it.matches }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun escape(s: String): String =
        if (s.contains(',') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
