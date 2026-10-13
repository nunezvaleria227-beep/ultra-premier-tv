package com.example.mitvplayer

import android.content.Context

/** Preferencias del reproductor que se recuerdan entre videos. */
object PlayerPrefs {
    private const val FILE = "player_prefs"

    /** Tamaños de imagen disponibles (máximo tres). */
    const val SIZE_FIT = 0
    const val SIZE_FILL = 1
    const val SIZE_ZOOM = 2

    /** [kind]: "live", "movie" o "episode". Cada tipo recuerda su propio tamaño. */
    fun sizeMode(context: Context, kind: String): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("size_$kind", SIZE_FIT).coerceIn(0, 2)

    fun setSizeMode(context: Context, kind: String, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("size_$kind", mode.coerceIn(0, 2)).apply()
    }
}
