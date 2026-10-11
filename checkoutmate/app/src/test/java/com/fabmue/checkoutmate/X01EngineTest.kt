package com.fabmue.checkoutmate

import com.fabmue.checkoutmate.domain.Dart
import com.fabmue.checkoutmate.domain.X01Config
import com.fabmue.checkoutmate.domain.X01Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X01EngineTest {

    private fun config(
        start: Int = 501,
        legsToWin: Int = 3,
        setsToWin: Int = 1,
        doubleIn: Boolean = false,
        doubleOut: Boolean = true,
    ) = X01Config(listOf("A"), start, legsToWin, setsToWin, doubleIn, doubleOut)

    @Test
    fun threeTrebleTwentiesScore180() {
        var m = X01Engine.start(config())
        m = X01Engine.applyDarts(m, listOf(Dart.triple(20), Dart.triple(20), Dart.triple(20)))
        assertEquals(321, m.players[0].remaining)
        assertEquals(180, m.players[0].pointsScored)
        assertEquals(1, m.players[0].count180)
        assertEquals(3, m.players[0].dartsThrown)
        assertEquals(180.0, m.players[0].threeDartAverage, 0.001)
        assertFalse(m.finished)
    }

    @Test
    fun bustRevertsVisit() {
        var m = X01Engine.start(config(start = 100))
        m = X01Engine.applyTotal(m, 60) // -> 40
        assertEquals(40, m.players[0].remaining)
        m = X01Engine.applyDarts(m, listOf(Dart.triple(20))) // 40 - 60 < 0 -> bust
        assertEquals(40, m.players[0].remaining)
    }

    @Test
    fun doubleOutRequiresDouble() {
        var m = X01Engine.start(config(start = 40))
        // single 20 then single 20 would reach 0 without a double -> bust
        m = X01Engine.applyDarts(m, listOf(Dart.single(20), Dart.single(20)))
        assertEquals(40, m.players[0].remaining)
    }

    @Test
    fun checkoutOnDoubleWinsLeg() {
        var m = X01Engine.start(config(start = 40, legsToWin = 3))
        m = X01Engine.applyDarts(m, listOf(Dart.double(20)))
        assertEquals(1, m.players[0].legs)
        assertEquals(1, m.players[0].checkoutHits)
        assertEquals(40, m.players[0].highestFinish)
        assertEquals(1, m.players[0].bestLegDarts)
        // new leg started, score reset
        assertEquals(40, m.players[0].remaining)
        assertFalse(m.finished)
    }

    @Test
    fun matchFinishesWhenLastLegWon() {
        var m = X01Engine.start(config(start = 2, legsToWin = 1, setsToWin = 1))
        m = X01Engine.applyDarts(m, listOf(Dart.double(1)))
        assertTrue(m.finished)
        assertEquals(0, m.winner)
    }

    @Test
    fun doubleInBlocksScoringUntilDouble() {
        var m = X01Engine.start(config(start = 40, doubleIn = true))
        m = X01Engine.applyDarts(m, listOf(Dart.single(20))) // not a double -> scores nothing
        assertEquals(40, m.players[0].remaining)
        assertTrue(m.players[0].doubleInNeeded)
        m = X01Engine.applyDarts(m, listOf(Dart.double(20))) // opens and finishes
        assertEquals(1, m.players[0].legs)
    }

    @Test
    fun setsProgressResetsLegs() {
        // First to 1 leg per set, first to 2 sets.
        var m = X01Engine.start(config(start = 2, legsToWin = 1, setsToWin = 2))
        m = X01Engine.applyDarts(m, listOf(Dart.double(1))) // wins leg -> wins set 1
        assertEquals(1, m.players[0].sets)
        assertEquals(0, m.players[0].legs)
        assertFalse(m.finished)
        m = X01Engine.applyDarts(m, listOf(Dart.double(1))) // wins set 2 -> match
        assertTrue(m.finished)
        assertEquals(0, m.winner)
    }

    @Test
    fun previewDetectsClosedVisit() {
        val m = X01Engine.start(config(start = 60))
        val p = X01Engine.preview(m, listOf(Dart.single(20), Dart.double(20)))
        assertTrue(p.legWon)
        assertTrue(p.closed)
        assertEquals(0, p.remaining)
    }
}
