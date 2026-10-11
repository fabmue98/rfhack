package com.fabmue.checkoutmate.domain.training

import com.fabmue.checkoutmate.domain.Dart
import com.fabmue.checkoutmate.domain.Ring

enum class TrainingType { CRICKET, BOBS27, ATC, SHANGHAI, DOUBLES }

/** Structured view model for the shared training UI. */
data class TrainingView(
    val targetLabel: String,
    val score: Int,
    val step: Int,
    val steps: Int,
    val finished: Boolean,
    val lowerIsBetter: Boolean = false,
    val shanghai: Boolean = false,
    val busted: Boolean = false,
)

interface TrainingGame {
    val type: TrainingType
    fun onDart(dart: Dart): TrainingGame
    fun onMiss(): TrainingGame = onDart(Dart.MISS)
    fun view(): TrainingView
    fun restart(): TrainingGame
}

fun newTrainingGame(type: TrainingType): TrainingGame = when (type) {
    TrainingType.CRICKET -> Cricket()
    TrainingType.BOBS27 -> Bobs27()
    TrainingType.ATC -> AroundTheClock()
    TrainingType.SHANGHAI -> Shanghai()
    TrainingType.DOUBLES -> DoublesDrill()
}

// -------- Around the Clock: 1..20 then bull, fewest darts -------------------
data class AroundTheClock(
    val target: Int = 1,       // 1..20, then 25 for bull
    val darts: Int = 0,
    val done: Boolean = false,
) : TrainingGame {
    override val type = TrainingType.ATC
    override fun onDart(dart: Dart): TrainingGame {
        if (done) return this
        val hit = if (target <= 20) {
            dart.segment == target && dart.ring in setOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE)
        } else {
            dart.ring == Ring.OUTER_BULL || dart.ring == Ring.BULL
        }
        val finished = hit && target == 25
        val next = when {
            !hit -> target
            target == 20 -> 25
            else -> target + 1
        }
        return copy(target = next, darts = darts + 1, done = finished)
    }
    override fun view() = TrainingView(
        targetLabel = if (target <= 20) target.toString() else "Bull",
        score = darts,
        step = if (target <= 20) target else 21,
        steps = 21,
        finished = done,
        lowerIsBetter = true,
    )
    override fun restart() = AroundTheClock()
}

// -------- Bob's 27: doubles D1..D20 then bull, start at 27 ------------------
data class Bobs27(
    val score: Int = 27,
    val idx: Int = 1,              // 1..20, then 21 = bull
    val dartsThisRound: Int = 0,
    val hitsThisRound: Int = 0,
    val darts: Int = 0,
    val done: Boolean = false,
    val busted: Boolean = false,
) : TrainingGame {
    override val type = TrainingType.BOBS27
    override fun onDart(dart: Dart): TrainingGame {
        if (done) return this
        val isHit = if (idx <= 20) dart.isDouble && dart.segment == idx
        else dart.ring == Ring.BULL || dart.ring == Ring.OUTER_BULL
        val hits = hitsThisRound + if (isHit) 1 else 0
        val dr = dartsThisRound + 1
        val total = darts + 1
        if (dr < 3) return copy(dartsThisRound = dr, hitsThisRound = hits, darts = total)
        val value = if (idx <= 20) idx * 2 else 50
        val newScore = score + if (hits > 0) hits * value else -value
        val bust = newScore < 0
        val finishedAll = idx >= 21
        return copy(
            score = newScore,
            idx = idx + 1,
            dartsThisRound = 0,
            hitsThisRound = 0,
            darts = total,
            done = bust || finishedAll,
            busted = bust,
        )
    }
    override fun view() = TrainingView(
        targetLabel = if (idx <= 20) "D$idx" else "Bull",
        score = score,
        step = minOf(idx, 21),
        steps = 21,
        finished = done,
        busted = busted,
    )
    override fun restart() = Bobs27()
}

// -------- Shanghai: 7 rounds, hit the round number -------------------------
data class Shanghai(
    val round: Int = 1,            // 1..7
    val score: Int = 0,
    val dartsThisRound: Int = 0,
    val hitSingle: Boolean = false,
    val hitDouble: Boolean = false,
    val hitTriple: Boolean = false,
    val darts: Int = 0,
    val done: Boolean = false,
    val shanghai: Boolean = false,
) : TrainingGame {
    override val type = TrainingType.SHANGHAI
    override fun onDart(dart: Dart): TrainingGame {
        if (done) return this
        val onNumber = dart.segment == round
        val s = hitSingle || (onNumber && dart.ring == Ring.SINGLE)
        val d = hitDouble || (onNumber && dart.ring == Ring.DOUBLE)
        val t = hitTriple || (onNumber && dart.ring == Ring.TRIPLE)
        val add = if (onNumber && dart.ring in setOf(Ring.SINGLE, Ring.DOUBLE, Ring.TRIPLE)) dart.value else 0
        val newScore = score + add
        val total = darts + 1
        if (s && d && t) {
            // Shanghai: single, double and triple of the number in one round -> instant win.
            return copy(score = newScore, darts = total, done = true, shanghai = true,
                hitSingle = s, hitDouble = d, hitTriple = t)
        }
        val dr = dartsThisRound + 1
        if (dr < 3) {
            return copy(
                score = newScore, dartsThisRound = dr, darts = total,
                hitSingle = s, hitDouble = d, hitTriple = t
            )
        }
        val finished = round >= 7
        return copy(
            round = round + 1,
            score = newScore,
            dartsThisRound = 0,
            hitSingle = false, hitDouble = false, hitTriple = false,
            darts = total,
            done = finished,
        )
    }
    override fun view() = TrainingView(
        targetLabel = round.toString(),
        score = score,
        step = minOf(round, 7),
        steps = 7,
        finished = done,
        shanghai = shanghai,
    )
    override fun restart() = Shanghai()
}

// -------- Doubles drill: D1..D20 then bull, 3 darts each -------------------
data class DoublesDrill(
    val idx: Int = 1,              // 1..20, then 21 = bull
    val dartsThisRound: Int = 0,
    val hitThisRound: Boolean = false,
    val targetsHit: Int = 0,
    val darts: Int = 0,
    val done: Boolean = false,
) : TrainingGame {
    override val type = TrainingType.DOUBLES
    override fun onDart(dart: Dart): TrainingGame {
        if (done) return this
        val isHit = if (idx <= 20) dart.isDouble && dart.segment == idx else dart.ring == Ring.BULL
        val hit = hitThisRound || isHit
        val dr = dartsThisRound + 1
        val total = darts + 1
        if (dr < 3) return copy(dartsThisRound = dr, hitThisRound = hit, darts = total)
        val finished = idx >= 21
        return copy(
            idx = idx + 1,
            dartsThisRound = 0,
            hitThisRound = false,
            targetsHit = targetsHit + if (hit) 1 else 0,
            darts = total,
            done = finished,
        )
    }
    override fun view() = TrainingView(
        targetLabel = if (idx <= 20) "D$idx" else "Bull",
        score = targetsHit,
        step = minOf(idx, 21),
        steps = 21,
        finished = done,
    )
    override fun restart() = DoublesDrill()
}

// -------- Cricket (solo): close 20..15 and bull, score on open numbers -----
data class Cricket(
    val marks: Map<Int, Int> = NUMBERS.associateWith { 0 },
    val points: Int = 0,
    val darts: Int = 0,
) : TrainingGame {
    override val type = TrainingType.CRICKET

    override fun onDart(dart: Dart): TrainingGame {
        val (number, add) = when (dart.ring) {
            Ring.TRIPLE -> dart.segment to 3
            Ring.DOUBLE -> dart.segment to 2
            Ring.SINGLE -> dart.segment to 1
            Ring.OUTER_BULL -> 25 to 1
            Ring.BULL -> 25 to 2
            Ring.MISS -> -1 to 0
        }
        val total = darts + 1
        if (number !in NUMBERS) return copy(darts = total)
        val current = marks.getValue(number)
        val toClose = (3 - current).coerceAtLeast(0)
        val closing = minOf(add, toClose)
        val extra = add - closing
        val value = if (number == 25) 25 else number
        val newMarks = marks.toMutableMap().apply { put(number, (current + closing).coerceAtMost(3)) }
        return copy(marks = newMarks, points = points + value * extra, darts = total)
    }

    val closed: Int get() = marks.count { it.value >= 3 }
    val done: Boolean get() = closed >= NUMBERS.size

    override fun view() = TrainingView(
        targetLabel = "20–15 · Bull",
        score = points,
        step = closed,
        steps = NUMBERS.size,
        finished = done,
    )
    override fun restart() = Cricket()

    companion object {
        val NUMBERS = listOf(20, 19, 18, 17, 16, 15, 25)
    }
}
