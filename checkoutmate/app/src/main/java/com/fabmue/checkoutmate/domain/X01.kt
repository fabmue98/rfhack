package com.fabmue.checkoutmate.domain

/**
 * Pure, deterministic X01 scoring state machine. No Android dependencies, so it
 * is fully unit-tested. The UI holds a snapshot stack of [X01Match] for undo.
 */

/** Match configuration. [setsToWin] is 1 when sets are not used. */
data class X01Config(
    val players: List<String>,
    val start: Int,          // 301 / 501 / 701
    val legsToWin: Int,      // legs to win a set
    val setsToWin: Int,      // sets to win the match (1 => no sets)
    val doubleIn: Boolean,
    val doubleOut: Boolean,
)

data class X01Player(
    val name: String,
    val remaining: Int,
    val legs: Int = 0,
    val sets: Int = 0,
    val dartsThrown: Int = 0,
    val pointsScored: Int = 0,
    val first9Points: Int = 0,
    val first9Darts: Int = 0,
    val doubleInNeeded: Boolean = false,
    val checkoutChances: Int = 0,
    val checkoutHits: Int = 0,
    val count180: Int = 0,
    val highestFinish: Int = 0,
    val bestLegDarts: Int = 0,   // fewest darts to win a leg; 0 = none yet
    val legDarts: Int = 0,
) {
    val threeDartAverage: Double
        get() = if (dartsThrown > 0) pointsScored.toDouble() / dartsThrown * 3 else 0.0
    val first9Average: Double
        get() = if (first9Darts > 0) first9Points.toDouble() / first9Darts * 3 else 0.0
    val checkoutPercent: Double
        get() = if (checkoutChances > 0) checkoutHits * 100.0 / checkoutChances else 0.0
}

sealed interface MatchEvent {
    data class VisitScored(val player: Int, val scored: Int, val remaining: Int, val is180: Boolean) : MatchEvent
    data class Bust(val player: Int) : MatchEvent
    data class LegWon(val player: Int, val finishScore: Int) : MatchEvent
    data class SetWon(val player: Int) : MatchEvent
    data class MatchWon(val player: Int) : MatchEvent
}

data class X01Match(
    val config: X01Config,
    val players: List<X01Player>,
    val current: Int,
    val legNumber: Int,
    val setNumber: Int,
    val legStarter: Int,
    val finished: Boolean,
    val winner: Int?,
    val lastEvent: MatchEvent?,
) {
    val currentPlayer: X01Player get() = players[current]
}

/** Non-committing projection of a visit, for live UI and auto-commit. */
data class VisitPreview(
    val scored: Int,
    val remaining: Int,
    val bust: Boolean,
    val legWon: Boolean,
    val dartsUsed: Int,
) {
    /** The visit is over: three darts thrown, a bust, or the leg finished. */
    val closed: Boolean get() = bust || legWon || dartsUsed >= 3
}

object X01Engine {

    fun start(config: X01Config): X01Match {
        val players = config.players.map {
            X01Player(name = it, remaining = config.start, doubleInNeeded = config.doubleIn)
        }
        return X01Match(
            config = config,
            players = players,
            current = 0,
            legNumber = 1,
            setNumber = 1,
            legStarter = 0,
            finished = false,
            winner = null,
            lastEvent = null,
        )
    }

    /** Apply a per-dart visit (1..3 darts). Precise double-in/out and bust handling. */
    fun applyDarts(match: X01Match, darts: List<Dart>): X01Match {
        if (match.finished || darts.isEmpty()) return match
        val p = match.currentPlayer
        val start = p.remaining
        var remaining = start
        var doubleInNeeded = p.doubleInNeeded
        var scored = 0
        var bust = false
        var legWon = false
        var dartsUsed = 0
        var first9Pts = 0
        var first9D = 0
        var legDarts = p.legDarts

        for (d in darts) {
            if (legWon || bust) break
            dartsUsed++
            // Double-in gate: before opening, non-doubles score nothing.
            if (match.config.doubleIn && doubleInNeeded) {
                if (!d.isDouble) {
                    if (legDarts < 9) first9D++
                    legDarts++
                    continue
                }
                doubleInNeeded = false
            }
            val after = remaining - d.value
            val bustHere = when {
                after < 0 -> true
                match.config.doubleOut && after == 1 -> true
                after == 0 && match.config.doubleOut && !d.isDouble -> true
                else -> false
            }
            if (bustHere) {
                bust = true
                if (legDarts < 9) first9D++
                legDarts++
                break
            }
            remaining = after
            scored += d.value
            if (legDarts < 9) {
                first9Pts += d.value
                first9D++
            }
            legDarts++
            if (remaining == 0) legWon = true
        }

        val is180 = !bust && scored == 180
        return advance(
            match, start, if (bust) start else remaining, scored, dartsUsed,
            bust, legWon, is180, doubleInNeeded, first9Pts, first9D, legDarts
        )
    }

    /** Project a per-dart visit without committing it (for live display). */
    fun preview(match: X01Match, darts: List<Dart>): VisitPreview {
        val p = match.currentPlayer
        val start = p.remaining
        var remaining = start
        var doubleInNeeded = p.doubleInNeeded
        var scored = 0
        var bust = false
        var legWon = false
        var used = 0
        for (d in darts) {
            if (legWon || bust) break
            used++
            if (match.config.doubleIn && doubleInNeeded) {
                if (!d.isDouble) continue
                doubleInNeeded = false
            }
            val after = remaining - d.value
            val bustHere = when {
                after < 0 -> true
                match.config.doubleOut && after == 1 -> true
                after == 0 && match.config.doubleOut && !d.isDouble -> true
                else -> false
            }
            if (bustHere) { bust = true; break }
            remaining = after
            scored += d.value
            if (remaining == 0) legWon = true
        }
        return VisitPreview(
            scored = if (bust) 0 else scored,
            remaining = if (bust) start else remaining,
            bust = bust,
            legWon = legWon,
            dartsUsed = used,
        )
    }

    /** Apply a quick 3-dart total. Double-in/out are trusted (assumed satisfied on a finish). */
    fun applyTotal(match: X01Match, total: Int): X01Match {
        if (match.finished) return match
        val p = match.currentPlayer
        val start = p.remaining
        var doubleInNeeded = p.doubleInNeeded
        if (match.config.doubleIn && doubleInNeeded && total > 0) doubleInNeeded = false
        val after = start - total
        val bust = when {
            after < 0 -> true
            match.config.doubleOut && after == 1 -> true
            else -> false
        }
        val legWon = !bust && after == 0
        val remaining = if (bust) start else after
        val scored = if (bust) 0 else total
        val is180 = !bust && total == 180

        val legDartsStart = p.legDarts
        val dIn9 = maxOf(0, minOf(9, legDartsStart + 3) - minOf(9, legDartsStart))
        val first9Pts = if (!bust && dIn9 > 0) scored * dIn9 / 3 else 0
        val legDarts = legDartsStart + 3

        return advance(
            match, start, remaining, scored, 3,
            bust, legWon, is180, doubleInNeeded, first9Pts, dIn9, legDarts
        )
    }

    private fun advance(
        match: X01Match,
        start: Int,
        remaining: Int,
        scored: Int,
        dartsUsed: Int,
        bust: Boolean,
        legWon: Boolean,
        is180: Boolean,
        doubleInNeeded: Boolean,
        first9Pts: Int,
        first9D: Int,
        legDarts: Int,
    ): X01Match {
        val idx = match.current
        val old = match.players[idx]
        val chance = CheckoutMath.isCheckoutable(start, match.config.doubleOut)

        var np = old.copy(
            remaining = remaining,
            dartsThrown = old.dartsThrown + dartsUsed,
            pointsScored = old.pointsScored + scored,
            first9Points = old.first9Points + first9Pts,
            first9Darts = old.first9Darts + first9D,
            doubleInNeeded = doubleInNeeded,
            count180 = old.count180 + if (is180) 1 else 0,
            checkoutChances = old.checkoutChances + if (chance) 1 else 0,
            legDarts = legDarts,
        )
        val players = match.players.toMutableList()

        if (!legWon) {
            players[idx] = np
            val next = (idx + 1) % players.size
            val event = if (bust) MatchEvent.Bust(idx)
            else MatchEvent.VisitScored(idx, scored, remaining, is180)
            return match.copy(players = players, current = next, lastEvent = event)
        }

        // Leg won.
        val legDartsUsed = legDarts
        val newBestLeg = if (old.bestLegDarts == 0) legDartsUsed
        else minOf(old.bestLegDarts, legDartsUsed)
        np = np.copy(
            legs = old.legs + 1,
            checkoutHits = old.checkoutHits + 1,
            highestFinish = maxOf(old.highestFinish, start),
            bestLegDarts = newBestLeg,
        )
        players[idx] = np

        if (np.legs >= match.config.legsToWin) {
            val newSets = np.sets + 1
            if (newSets >= match.config.setsToWin) {
                players[idx] = np.copy(sets = newSets)
                return match.copy(
                    players = players, finished = true, winner = idx,
                    lastEvent = MatchEvent.MatchWon(idx)
                )
            }
            // New set: reset legs and scores-to-throw for everyone.
            val reset = players.mapIndexed { i, pl ->
                pl.copy(
                    legs = 0,
                    sets = if (i == idx) newSets else pl.sets,
                    remaining = match.config.start,
                    doubleInNeeded = match.config.doubleIn,
                    legDarts = 0,
                )
            }
            val nextStarter = (match.legStarter + 1) % players.size
            return match.copy(
                players = reset,
                current = nextStarter,
                legStarter = nextStarter,
                legNumber = match.legNumber + 1,
                setNumber = match.setNumber + 1,
                lastEvent = MatchEvent.SetWon(idx),
            )
        }

        // New leg within the set.
        val reset = players.map {
            it.copy(
                remaining = match.config.start,
                doubleInNeeded = match.config.doubleIn,
                legDarts = 0,
            )
        }
        val nextStarter = (match.legStarter + 1) % players.size
        return match.copy(
            players = reset,
            current = nextStarter,
            legStarter = nextStarter,
            legNumber = match.legNumber + 1,
            lastEvent = MatchEvent.LegWon(idx, start),
        )
    }
}
