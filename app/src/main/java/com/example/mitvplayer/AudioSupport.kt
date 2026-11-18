package com.example.mitvplayer

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Sonido en todos los canales.
 *
 * Por qué había canales mudos: el reproductor solo usaba los decodificadores de audio del aparato. Si un canal
 * traía el audio en un formato que el Fire TV no sabe leer (MP2, muy común en canales de TV; DTS…), se veía la
 * imagen sin sonido. Además el Dolby (AC3 / E-AC3) se mandaba "en bruto" a la tele, y si la tele o la barra de
 * sonido no lo aceptaban, el canal también quedaba mudo.
 *
 * Ahora:
 *  - El audio siempre se convierte en sonido normal dentro de la app (sin "passthrough"): suena en cualquier tele.
 *  - Lo que el aparato no sabe leer lo lee FFmpeg, que va dentro de la app (androidx.media3.decoder.ffmpeg:
 *    el código es el de Media3 1.4.1 y la parte nativa, libffmpegJNI.so, sale del paquete de Jellyfin; ver build.gradle.kts).
 *  - MP2, DTS y TrueHD van directo a FFmpeg: con esos formatos los decodificadores de los aparatos fallan seguido.
 *  - Si el decodificador de audio del aparato falla con algún canal, desde ese momento todo el audio lo lee FFmpeg.
 */
@OptIn(UnstableApi::class)
object AudioSupport {

    /** true = todo el audio que FFmpeg sabe leer se lee con FFmpeg (se activa solo si el del aparato falla). */
    @Volatile
    private var softwareOnly = false

    /** Formatos que siempre lee FFmpeg (si está disponible en el aparato). */
    private val ALWAYS_FFMPEG = setOf(
        MimeTypes.AUDIO_MPEG_L1,
        MimeTypes.AUDIO_MPEG_L2,
        MimeTypes.AUDIO_DTS,
        MimeTypes.AUDIO_DTS_HD,
        MimeTypes.AUDIO_TRUEHD
    )

    private fun ffmpegReads(mimeType: String?): Boolean =
        mimeType != null && try {
            FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mimeType)
        } catch (t: Throwable) {
            false
        }

    /** Decodificadores del aparato para el audio; vacío = ese formato lo lee FFmpeg. */
    private val audioCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val viaFfmpeg = MimeTypes.isAudio(mimeType) &&
            (softwareOnly || mimeType in ALWAYS_FFMPEG) &&
            ffmpegReads(mimeType)
        if (viaFfmpeg) {
            emptyList<MediaCodecInfo>()
        } else {
            MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        }
    }

    /** Para ExoPlayer.Builder(context, AudioSupport.renderersFactory(context)). El video no cambia. */
    fun renderersFactory(context: Context): RenderersFactory = Factory(context.applicationContext)

    /**
     * Si el error vino del decodificador de audio del aparato, pasa a leer el audio con FFmpeg.
     * true = vale la pena reintentar el canal en seguida (solo pasa una vez).
     */
    fun switchToSoftware(error: PlaybackException): Boolean {
        if (softwareOnly) return false
        val e = error as? ExoPlaybackException ?: return false
        if (e.type != ExoPlaybackException.TYPE_RENDERER) return false
        if (e.rendererName?.contains("Ffmpeg") == true) return false
        val mimeType = e.rendererFormat?.sampleMimeType
        if (!MimeTypes.isAudio(mimeType) || !ffmpegReads(mimeType)) return false
        softwareOnly = true
        return true
    }

    private class Factory(context: Context) : DefaultRenderersFactory(context) {

        /**
         * Sin passthrough: el audio sale siempre como sonido normal (PCM), que acepta cualquier tele o barra.
         * (Sin Context, DefaultAudioSink no manda el Dolby/DTS "en bruto" a la tele: lo decodifica la app.)
         */
        @Suppress("DEPRECATION")
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink = DefaultAudioSink.Builder()
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()

        /** Primero el decodificador del aparato y, para lo que no sepa leer, FFmpeg. */
        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: Handler,
            eventListener: AudioRendererEventListener,
            out: ArrayList<Renderer>
        ) {
            super.buildAudioRenderers(
                context,
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF,
                AudioSupport.audioCodecSelector,
                /* enableDecoderFallback= */ true,
                audioSink,
                eventHandler,
                eventListener,
                out
            )
            out.add(FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
        }
    }
}
