package com.example.mitvplayer.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * Lista ordenada de IDs guardada en el equipo (favoritos o historial).
 * Es observable: la pantalla se actualiza sola cuando cambia.
 */
class IdList(
    private val prefs: SharedPreferences,
    private val key: String,
    private val max: Int
) {
    var ids: List<String> by mutableStateOf(readStored())
        private set

    fun has(id: String): Boolean = ids.contains(id)

    /** Agrega si no estaba; quita si ya estaba. */
    fun toggle(id: String) {
        store(if (ids.contains(id)) ids.filter { it != id } else (listOf(id) + ids).take(max))
    }

    /** Lo pone al principio (lo más reciente primero). */
    fun touch(id: String) {
        if (ids.firstOrNull() == id) return
        store((listOf(id) + ids.filter { it != id }).take(max))
    }

    fun clear() = store(emptyList())

    private fun store(value: List<String>) {
        ids = value
        prefs.edit().putString(key, JSONArray(value).toString()).apply()
    }

    private fun readStored(): List<String> =
        try {
            val array = JSONArray(prefs.getString(key, "[]"))
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
}

/** Favoritos e historial de la cuenta que inició sesión. */
class Library(prefs: SharedPreferences, user: String) {
    val favLive = IdList(prefs, "$user:fav_live", 5000)
    val favMovies = IdList(prefs, "$user:fav_movies", 5000)
    val favSeries = IdList(prefs, "$user:fav_series", 5000)
    val recentLive = IdList(prefs, "$user:recent_live", 60)
    val recentMovies = IdList(prefs, "$user:recent_movies", 60)
    val recentSeries = IdList(prefs, "$user:recent_series", 60)

    fun favorites(kind: Kind): IdList = when (kind) {
        Kind.LIVE -> favLive
        Kind.MOVIES -> favMovies
        Kind.SERIES -> favSeries
    }

    fun recents(kind: Kind): IdList = when (kind) {
        Kind.LIVE -> recentLive
        Kind.MOVIES -> recentMovies
        Kind.SERIES -> recentSeries
    }

    fun clearFavorites() {
        favLive.clear()
        favMovies.clear()
        favSeries.clear()
    }

    fun clearHistory() {
        recentLive.clear()
        recentMovies.clear()
        recentSeries.clear()
    }
}
