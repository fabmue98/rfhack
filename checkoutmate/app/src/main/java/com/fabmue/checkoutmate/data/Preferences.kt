package com.fabmue.checkoutmate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "checkoutmate")

/** User-facing settings snapshot. */
data class Settings(
    val language: String = "system",       // "system" | "en" | "de"
    val prefDouble: Int = 0,                // 0 = shortest path, 1..20 = D n, 25 = Bull
    val defaultDoubleOut: Boolean = true,
    val defaultDoubleIn: Boolean = false,
    val theme: String = "dark",             // "dark" | "amoled" | "felt"
    val voice: Boolean = false,
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = true,
    val scoreboardMode: Boolean = false,
    val devUnlock: Boolean = false,
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val PREF_DOUBLE = intPreferencesKey("pref_double")
        val DEFAULT_DOUBLE_OUT = booleanPreferencesKey("default_double_out")
        val DEFAULT_DOUBLE_IN = booleanPreferencesKey("default_double_in")
        val THEME = stringPreferencesKey("theme")
        val VOICE = booleanPreferencesKey("voice")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SCOREBOARD_MODE = booleanPreferencesKey("scoreboard_mode")
        val OWNED = stringSetPreferencesKey("owned")
        val DEV_UNLOCK = booleanPreferencesKey("dev_unlock")
        val SAVED_MATCH = stringPreferencesKey("saved_match")
        val LAST_PLAYERS = stringPreferencesKey("last_players")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            language = p[Keys.LANGUAGE] ?: "system",
            prefDouble = p[Keys.PREF_DOUBLE] ?: 0,
            defaultDoubleOut = p[Keys.DEFAULT_DOUBLE_OUT] ?: true,
            defaultDoubleIn = p[Keys.DEFAULT_DOUBLE_IN] ?: false,
            theme = p[Keys.THEME] ?: "dark",
            voice = p[Keys.VOICE] ?: false,
            haptics = p[Keys.HAPTICS] ?: true,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: true,
            scoreboardMode = p[Keys.SCOREBOARD_MODE] ?: false,
            devUnlock = p[Keys.DEV_UNLOCK] ?: false,
        )
    }

    val owned: Flow<Set<String>> = context.dataStore.data.map { it[Keys.OWNED] ?: emptySet() }
    val devUnlock: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEV_UNLOCK] ?: false }
    val savedMatch: Flow<String?> = context.dataStore.data.map { it[Keys.SAVED_MATCH] }
    val lastPlayers: Flow<List<String>> = context.dataStore.data.map { p ->
        (p[Keys.LAST_PLAYERS] ?: "").split("\u0001").filter { it.isNotBlank() }
    }

    fun languageBlocking(): String = try {
        runBlocking { context.dataStore.data.map { it[Keys.LANGUAGE] ?: "system" }.first() }
    } catch (e: Exception) {
        "system"
    }

    suspend fun setLanguage(v: String) = edit { it[Keys.LANGUAGE] = v }
    suspend fun setPrefDouble(v: Int) = edit { it[Keys.PREF_DOUBLE] = v }
    suspend fun setDefaultDoubleOut(v: Boolean) = edit { it[Keys.DEFAULT_DOUBLE_OUT] = v }
    suspend fun setDefaultDoubleIn(v: Boolean) = edit { it[Keys.DEFAULT_DOUBLE_IN] = v }
    suspend fun setTheme(v: String) = edit { it[Keys.THEME] = v }
    suspend fun setVoice(v: Boolean) = edit { it[Keys.VOICE] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.HAPTICS] = v }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setScoreboardMode(v: Boolean) = edit { it[Keys.SCOREBOARD_MODE] = v }
    suspend fun setDevUnlock(v: Boolean) = edit { it[Keys.DEV_UNLOCK] = v }

    suspend fun setOwned(set: Set<String>) = edit { it[Keys.OWNED] = set }

    suspend fun setSavedMatch(json: String?) = edit {
        if (json == null) it.remove(Keys.SAVED_MATCH) else it[Keys.SAVED_MATCH] = json
    }

    suspend fun setLastPlayers(names: List<String>) = edit {
        it[Keys.LAST_PLAYERS] = names.joinToString("\u0001")
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
