package com.fabmue.checkoutmate.data

import com.fabmue.checkoutmate.domain.X01Config
import com.fabmue.checkoutmate.domain.X01Match
import com.fabmue.checkoutmate.domain.X01Player
import org.json.JSONArray
import org.json.JSONObject

/** Persists an in-progress match to JSON for save-and-resume. */
object MatchSerializer {

    fun toJson(m: X01Match): String {
        val root = JSONObject()
        val c = m.config
        root.put("start", c.start)
        root.put("legsToWin", c.legsToWin)
        root.put("setsToWin", c.setsToWin)
        root.put("doubleIn", c.doubleIn)
        root.put("doubleOut", c.doubleOut)
        root.put("players", JSONArray(c.players))
        root.put("current", m.current)
        root.put("legNumber", m.legNumber)
        root.put("setNumber", m.setNumber)
        root.put("legStarter", m.legStarter)
        root.put("finished", m.finished)
        root.put("winner", m.winner ?: -1)
        val arr = JSONArray()
        m.players.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("remaining", p.remaining)
                    put("legs", p.legs)
                    put("sets", p.sets)
                    put("dartsThrown", p.dartsThrown)
                    put("pointsScored", p.pointsScored)
                    put("first9Points", p.first9Points)
                    put("first9Darts", p.first9Darts)
                    put("doubleInNeeded", p.doubleInNeeded)
                    put("checkoutChances", p.checkoutChances)
                    put("checkoutHits", p.checkoutHits)
                    put("count180", p.count180)
                    put("highestFinish", p.highestFinish)
                    put("bestLegDarts", p.bestLegDarts)
                    put("legDarts", p.legDarts)
                }
            )
        }
        root.put("playerStates", arr)
        return root.toString()
    }

    fun fromJson(s: String): X01Match? = try {
        val root = JSONObject(s)
        val playersArr = root.getJSONArray("players")
        val names = (0 until playersArr.length()).map { playersArr.getString(it) }
        val config = X01Config(
            players = names,
            start = root.getInt("start"),
            legsToWin = root.getInt("legsToWin"),
            setsToWin = root.getInt("setsToWin"),
            doubleIn = root.getBoolean("doubleIn"),
            doubleOut = root.getBoolean("doubleOut"),
        )
        val states = root.getJSONArray("playerStates")
        val players = (0 until states.length()).map { i ->
            val o = states.getJSONObject(i)
            X01Player(
                name = o.getString("name"),
                remaining = o.getInt("remaining"),
                legs = o.getInt("legs"),
                sets = o.getInt("sets"),
                dartsThrown = o.getInt("dartsThrown"),
                pointsScored = o.getInt("pointsScored"),
                first9Points = o.getInt("first9Points"),
                first9Darts = o.getInt("first9Darts"),
                doubleInNeeded = o.getBoolean("doubleInNeeded"),
                checkoutChances = o.getInt("checkoutChances"),
                checkoutHits = o.getInt("checkoutHits"),
                count180 = o.getInt("count180"),
                highestFinish = o.getInt("highestFinish"),
                bestLegDarts = o.getInt("bestLegDarts"),
                legDarts = o.getInt("legDarts"),
            )
        }
        val winner = root.getInt("winner").let { if (it < 0) null else it }
        X01Match(
            config = config,
            players = players,
            current = root.getInt("current"),
            legNumber = root.getInt("legNumber"),
            setNumber = root.getInt("setNumber"),
            legStarter = root.getInt("legStarter"),
            finished = root.getBoolean("finished"),
            winner = winner,
            lastEvent = null,
        )
    } catch (e: Exception) {
        null
    }
}
