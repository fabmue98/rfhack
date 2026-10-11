package com.fabmue.checkoutmate.data

import android.content.Context
import org.json.JSONObject

/** A recommended finish, e.g. ["T20", "T20", "D20"]. */
data class CheckoutSuggestion(val path: List<String>) {
    val darts: Int get() = path.size
    val display: String get() = path.joinToString(" ")
}

/**
 * Loads the pre-computed, verified checkout table from assets (checkouts.json)
 * and serves finish suggestions. Fully offline; parsed once, lazily.
 */
class CheckoutRepository(private val context: Context) {

    private val bogey = HashSet<Int>()
    private val primary = HashMap<Int, List<String>>()
    private val byFinish = HashMap<Int, Map<String, List<String>>>()

    @Volatile
    private var loaded = false

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        val text = context.assets.open("checkouts.json").bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val bogeyArr = root.getJSONArray("bogey")
        for (i in 0 until bogeyArr.length()) bogey.add(bogeyArr.getInt(i))
        val checkouts = root.getJSONObject("checkouts")
        val keys = checkouts.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val score = key.toInt()
            val entry = checkouts.getJSONObject(key)
            primary[score] = entry.getJSONArray("p").toStringList()
            val f = entry.getJSONObject("f")
            val fKeys = f.keys()
            val map = HashMap<String, List<String>>()
            while (fKeys.hasNext()) {
                val fk = fKeys.next()
                map[fk] = f.getJSONArray(fk).toStringList()
            }
            byFinish[score] = map
        }
        loaded = true
    }

    fun isBogey(remaining: Int): Boolean {
        ensureLoaded()
        return remaining in bogey
    }

    /**
     * Suggested finish for [remaining], honouring a [preferredDouble] label
     * ("D16", "Bull", ...) when a minimal path ending on it exists.
     * Returns null when no checkout is possible (out of range or a bogey number).
     */
    fun suggestion(remaining: Int, preferredDouble: String? = null): CheckoutSuggestion? {
        ensureLoaded()
        if (remaining !in 2..170 || remaining in bogey) return null
        val base = primary[remaining] ?: return null
        val path = if (preferredDouble != null) {
            byFinish[remaining]?.get(preferredDouble) ?: base
        } else base
        return CheckoutSuggestion(path)
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
