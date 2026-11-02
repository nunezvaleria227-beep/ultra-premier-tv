package com.example.mitvplayer.data

import android.util.Log

/** Mediciones de tiempo para las pruebas: solo escribe en el registro cuando la app es de depuración. */
object PerfLog {
    @Volatile
    var enabled = false

    inline fun log(message: () -> String) {
        if (enabled) Log.d("UPTV", message())
    }

    fun ms(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000
}
