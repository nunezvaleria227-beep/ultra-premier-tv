package com.example.mitvplayer

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * Arranque rápido del video: empieza a reproducir con 1 segundo guardado (en vez de 2.5 que trae ExoPlayer),
 * así cambiar de canal en vivo o abrir una película se siente inmediato. Si la red se corta, espera un poco
 * más antes de seguir para no volver a pararse enseguida.
 */
@OptIn(UnstableApi::class)
object FastStart {
    fun loadControl(): LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // mínimo que se intenta tener guardado
            50_000, // máximo
            1_000, // para empezar a reproducir
            2_500 // para seguir después de quedarse sin datos
        )
        .build()
}
