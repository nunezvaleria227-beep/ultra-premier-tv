package com.example.mitvplayer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/** Avance de la conversión que se muestra en la pantalla de Grabaciones. */
data class ConvertProgress(
    val title: String,
    val percent: Int,
    val outBytes: Long,
    val phase: String
)

/**
 * Convierte una grabación (.ts) a MP4, que se abre en cualquier celular o programa.
 *
 * Normalmente solo se cambia el "envoltorio": el video no se vuelve a comprimir, así que es rápido y no pierde calidad.
 * El audio se pasa a AAC si hace falta. Si el video no cabe en un MP4 tal cual, se reintenta recomprimiéndolo (más lento).
 * El MP4 se arma primero en una carpeta temporal y luego se guarda junto a las grabaciones (Películas / Ultra Premier TV).
 */
@androidx.annotation.OptIn(UnstableApi::class)
object RecordingConverter {

    var current by mutableStateOf<ConvertProgress?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var temp: File? = null

    fun start(context: Context, recording: SavedRecording, deleteOriginal: Boolean) {
        val app = context.applicationContext
        if (current != null) return
        if (RecordingManager.current != null) {
            toast(app, "Espera a que termine la grabación en curso para convertir.")
            return
        }
        val work = workDir(app)
        val needed = recording.size * 2 + recording.size / 10 + 64L * 1024 * 1024
        if (work.usableSpace < needed) {
            toast(
                app,
                "Falta espacio: para convertir esta grabación hacen falta unos ${formatGigas(needed)} libres. " +
                    "Borra algo o conviértela cuando tengas más espacio."
            )
            return
        }
        val out = File(work, "convert_${System.currentTimeMillis()}.mp4")
        temp = out
        current = ConvertProgress(recording.title, 0, 0, "Convirtiendo")
        attempt(app, recording, out, deleteOriginal, transcodeVideo = false)
    }

    fun cancel() {
        val t = transformer
        transformer = null
        try { t?.cancel() } catch (e: Exception) { }
        temp?.delete()
        temp = null
        current = null
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun attempt(app: Context, recording: SavedRecording, out: File, deleteOriginal: Boolean, transcodeVideo: Boolean) {
        out.delete()
        val builder = Transformer.Builder(app).setAudioMimeType(MimeTypes.AUDIO_AAC)
        if (transcodeVideo) builder.setVideoMimeType(MimeTypes.VIDEO_H264)
        lateinit var t: Transformer
        t = builder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (transformer !== t) return
                    transformer = null
                    publishInBackground(app, recording, out, deleteOriginal)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (transformer !== t) return
                    transformer = null
                    out.delete()
                    if (!transcodeVideo) {
                        current = current?.copy(phase = "Convirtiendo (recomprimiendo el video, tarda más)", percent = 0)
                        attempt(app, recording, out, deleteOriginal, transcodeVideo = true)
                    } else {
                        temp = null
                        current = null
                        toast(app, "No se pudo convertir esta grabación a MP4. Sigue guardada como estaba.")
                    }
                }
            })
            .build()
        transformer = t
        try {
            t.start(MediaItem.fromUri(recording.url), out.absolutePath)
        } catch (e: Exception) {
            transformer = null
            out.delete()
            temp = null
            current = null
            toast(app, "No se pudo empezar la conversión.")
            return
        }
        poll(t, out, recording.size)
    }

    /** Cada poco actualiza el porcentaje. Si el archivo no dice su duración, se calcula con el tamaño que va llevando. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun poll(t: Transformer, out: File, inputSize: Long) {
        val holder = ProgressHolder()
        val tick = object : Runnable {
            override fun run() {
                if (transformer !== t || current == null) return
                val state = try { t.getProgress(holder) } catch (e: Exception) { Transformer.PROGRESS_STATE_UNAVAILABLE }
                val written = out.length()
                val percent = if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    holder.progress
                } else if (inputSize > 0) {
                    (written * 100 / inputSize).toInt()
                } else {
                    0
                }
                current = current?.copy(percent = percent.coerceIn(0, 99), outBytes = written)
                main.postDelayed(this, 700)
            }
        }
        main.postDelayed(tick, 700)
    }

    private fun publishInBackground(app: Context, recording: SavedRecording, source: File, deleteOriginal: Boolean) {
        current = current?.copy(phase = "Guardando el MP4", percent = 99)
        Thread {
            val where = publish(app, source, recording.title)
            source.delete()
            var removed = false
            if (where != null && deleteOriginal) {
                removed = RecordingService.delete(app, recording)
            }
            main.post {
                temp = null
                current = null
                if (where == null) {
                    toast(app, "Se convirtió, pero no se pudo guardar el MP4 (¿poco espacio?). La grabación original sigue guardada.")
                } else {
                    val tail = if (deleteOriginal) {
                        if (removed) " La original se borró." else " No se pudo borrar la original."
                    } else {
                        ""
                    }
                    toast(app, "Listo: MP4 guardado en $where.$tail")
                }
            }
        }.start()
    }

    /** Guarda el MP4 junto a las grabaciones. Devuelve dónde quedó, o null si no se pudo. */
    private fun publish(app: Context, source: File, title: String): String? {
        val name = "$title.mp4"
        val folder = RecordingService.PUBLIC_FOLDER

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = app.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$folder")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    try {
                        val stream = resolver.openOutputStream(uri, "w")
                        if (stream != null) {
                            stream.use { output -> source.inputStream().use { it.copyTo(output, 1 shl 20) } }
                            resolver.update(
                                uri,
                                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                                null,
                                null
                            )
                            return "Películas/$folder"
                        }
                    } catch (e: Exception) {
                        // Se prueba en la carpeta de la app.
                    }
                    try { resolver.delete(uri, null, null) } catch (e: Exception) { }
                }
            } catch (e: Exception) {
                // Se prueba en la carpeta de la app.
            }
        } else if (ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), folder)
                if (dir.isDirectory || dir.mkdirs()) {
                    val target = File(dir, name)
                    source.copyTo(target, overwrite = true)
                    MediaScannerConnection.scanFile(app, arrayOf(target.path), arrayOf("video/mp4"), null)
                    return "Películas/$folder"
                }
            } catch (e: Exception) {
                // Se prueba en la carpeta de la app.
            }
        }

        return try {
            val target = File(RecordingService.appRecordingsDir(app), name)
            source.copyTo(target, overwrite = true)
            "la carpeta de la app (Grabaciones)"
        } catch (e: Exception) {
            null
        }
    }

    private fun workDir(app: Context): File {
        val base = app.getExternalFilesDir("convert") ?: File(app.cacheDir, "convert")
        base.mkdirs()
        // Restos de una conversión que se interrumpió.
        base.listFiles()?.forEach { if (it != temp) it.delete() }
        return base
    }

    private fun formatGigas(bytes: Long): String = "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)

    private fun toast(app: Context, text: String) {
        main.post { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
    }
}
