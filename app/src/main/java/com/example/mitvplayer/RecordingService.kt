package com.example.mitvplayer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Grabación en curso. Siempre tiene hora de fin: nunca pasa de [RecordingService.MAX_MINUTES]. */
data class ActiveRecording(
    val channelName: String,
    val startedAt: Long,
    val bytes: Long,
    val endsAt: Long
)

/** Una grabación ya guardada en la memoria del dispositivo. */
class SavedRecording(
    val title: String,
    /** Dirección para reproducirla (content:// o file://). */
    val url: String,
    val size: Long,
    val dateMs: Long,
    internal val uri: Uri?,
    internal val file: File?,
    /** "ts" (como se graba) o "mp4" (ya convertida). */
    val ext: String = "ts"
)

/** Estado de la grabación en curso, visible desde las pantallas. */
object RecordingManager {
    var current by mutableStateOf<ActiveRecording?>(null)

    internal var stopAction: (() -> Unit)? = null

    fun stop() {
        stopAction?.invoke()
    }
}

/**
 * Graba un canal en vivo (MPEG-TS) como máximo [MAX_MINUTES] minutos y lo guarda directamente
 * en la memoria del dispositivo (carpeta Películas/Ultra Premier TV, visible en la app de Archivos).
 * Corre como servicio en primer plano para que no se corte al salir de la app.
 */
class RecordingService : Service() {

    @Volatile
    private var stopRequested = false

    @Volatile
    private var call: Call? = null
    private var worker: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            if (worker?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "Canal"
        val minutes = (intent?.getIntExtra(EXTRA_MINUTES, MAX_MINUTES) ?: MAX_MINUTES).coerceIn(1, MAX_MINUTES)
        if (url == null || worker?.isAlive == true) {
            if (worker?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Grabando $name (máximo 2 horas)"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        stopRequested = false
        RecordingManager.stopAction = { requestStop() }
        worker = Thread { record(url, name, minutes) }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun requestStop() {
        stopRequested = true
        call?.cancel()
    }

    override fun onDestroy() {
        requestStop()
        RecordingManager.stopAction = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ grabación

    private fun record(url: String, name: String, minutes: Int) {
        val startedAt = System.currentTimeMillis()
        val endsAt = startedAt + minutes * 60_000L
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(startedAt))
        var sink: Sink? = null
        var total = 0L
        var reason = REASON_STOPPED

        try {
            val target = openSink(name, stamp)
            sink = target
            publish(ActiveRecording(name, startedAt, 0L, endsAt))

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).header("User-Agent", Config.USER_AGENT).build()
            val buffer = ByteArray(64 * 1024)
            val storage = StatFs((getExternalFilesDir(null) ?: filesDir).path)
            var lastPublish = 0L
            var lastSpaceCheck = 0L
            var failures = 0

            while (!stopRequested && reason == REASON_STOPPED) {
                if (System.currentTimeMillis() >= endsAt) {
                    reason = REASON_LIMIT
                    break
                }
                try {
                    val newCall = client.newCall(request)
                    call = newCall
                    newCall.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        val body = response.body ?: throw IOException("Respuesta vacía")
                        body.byteStream().use { input ->
                            while (!stopRequested) {
                                val now = System.currentTimeMillis()
                                if (now >= endsAt) {
                                    reason = REASON_LIMIT
                                    break
                                }
                                val read = input.read(buffer)
                                if (read < 0) break
                                target.out.write(buffer, 0, read)
                                total += read
                                failures = 0
                                if (now - lastPublish >= 1000) {
                                    lastPublish = now
                                    publish(ActiveRecording(name, startedAt, total, endsAt))
                                }
                                if (now - lastSpaceCheck >= 10_000) {
                                    lastSpaceCheck = now
                                    storage.restat((getExternalFilesDir(null) ?: filesDir).path)
                                    if (storage.availableBytes < MIN_FREE_BYTES) {
                                        reason = REASON_SPACE
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    // Se cortó la señal o se canceló: abajo se decide si se reintenta.
                }
                if (stopRequested || reason != REASON_STOPPED) break
                // La señal se cayó: se reintenta unas cuantas veces antes de rendirse.
                failures++
                if (failures > MAX_RETRIES) {
                    reason = REASON_LOST
                    break
                }
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        } catch (e: Exception) {
            reason = REASON_LOST
        } finally {
            val where = sink?.where ?: ""
            sink?.close(keep = total > 0)
            val saved = total > 0
            main.post {
                RecordingManager.current = null
                finishMessage(saved, reason, where, minutes)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun finishMessage(saved: Boolean, reason: String, where: String, minutes: Int) {
        val text = when {
            !saved -> "No se pudo grabar este canal. Si tu cuenta solo permite una conexión, cierra la vista previa e inténtalo otra vez."
            reason == REASON_LIMIT && minutes >= MAX_MINUTES -> "Se cumplieron las 2 horas. La grabación se guardó en $where."
            reason == REASON_LIMIT -> "Terminó la grabación programada. Se guardó en $where."
            reason == REASON_SPACE -> "Casi no queda espacio. Lo grabado se guardó en $where."
            reason == REASON_LOST -> "Se perdió la señal. Lo grabado se guardó en $where."
            else -> "Grabación guardada en $where."
        }
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            ensureChannel(manager)
            manager.notify(
                NOTIFICATION_DONE_ID,
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("Ultra Premier TV")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: Exception) {
            // Sin permiso de avisos: ya se mostró el mensaje en pantalla.
        }
    }

    /** Dónde se escribe la grabación: la memoria pública del equipo o, si no se puede, la carpeta de la app. */
    private class Sink(
        val out: OutputStream,
        val where: String,
        private val onClose: (keep: Boolean) -> Unit
    ) {
        fun close(keep: Boolean) {
            try { out.flush() } catch (e: Exception) { }
            try { out.close() } catch (e: Exception) { }
            try { onClose(keep) } catch (e: Exception) { }
        }
    }

    private fun openSink(name: String, stamp: String): Sink {
        val display = "${safeName(name)}_$stamp.ts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            for (mime in listOf("video/mp2t", null)) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, display)
                        if (mime != null) put(MediaStore.Video.Media.MIME_TYPE, mime)
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$PUBLIC_FOLDER")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(collection, values) ?: continue
                    val out = resolver.openOutputStream(uri, "w")
                    if (out == null) {
                        resolver.delete(uri, null, null)
                        continue
                    }
                    return Sink(out, WHERE_PUBLIC) { keep ->
                        if (keep) {
                            resolver.update(
                                uri,
                                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                                null,
                                null
                            )
                        } else {
                            resolver.delete(uri, null, null)
                        }
                    }
                } catch (e: Exception) {
                    // Se prueba sin tipo MIME y, si tampoco, en la carpeta de la app.
                }
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), PUBLIC_FOLDER)
                if (dir.isDirectory || dir.mkdirs()) {
                    val file = File(dir, display)
                    val out = file.outputStream()
                    return Sink(out, WHERE_PUBLIC) { keep ->
                        if (keep) {
                            MediaScannerConnection.scanFile(applicationContext, arrayOf(file.path), arrayOf("video/mp2t"), null)
                        } else {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                // Se usa la carpeta de la app.
            }
        }

        val file = File(appRecordingsDir(this), display)
        return Sink(file.outputStream(), WHERE_APP) { keep -> if (!keep) file.delete() }
    }

    private fun publish(recording: ActiveRecording) {
        main.post { RecordingManager.current = recording }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Grabaciones", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel(getSystemService(NotificationManager::class.java))
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Ultra Premier TV")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "Detener", stop)
            .build()
    }

    companion object {
        /** Tope de cada grabación. Al cumplirse se detiene sola y queda guardada. */
        const val MAX_MINUTES = 120

        const val PUBLIC_FOLDER = "Ultra Premier TV"

        private const val WHERE_PUBLIC = "Películas/Ultra Premier TV"
        private const val WHERE_APP = "la carpeta de la app (Grabaciones)"
        private const val MIN_FREE_BYTES = 150L * 1024 * 1024
        private const val MAX_RETRIES = 6
        private const val REASON_STOPPED = "stopped"
        private const val REASON_LIMIT = "limit"
        private const val REASON_SPACE = "space"
        private const val REASON_LOST = "lost"

        private const val ACTION_STOP = "com.ultrapremier.tv.STOP_RECORDING"
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_MINUTES = "minutes"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 77
        private const val NOTIFICATION_DONE_ID = 78

        /** [minutes] = null graba el máximo permitido ([MAX_MINUTES]). */
        fun start(context: Context, url: String, name: String, minutes: Int? = null) {
            if (RecordingManager.current != null) return
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_MINUTES, (minutes ?: MAX_MINUTES).coerceIn(1, MAX_MINUTES))
            ContextCompat.startForegroundService(context, intent)
        }

        /** Carpeta propia de la app: solo se usa si no se puede escribir en la memoria pública. */
        fun appRecordingsDir(context: Context): File {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            return File(base, "Grabaciones").also { it.mkdirs() }
        }

        /** Todas las grabaciones guardadas, las más nuevas primero. */
        fun listSaved(context: Context): List<SavedRecording> {
            val result = ArrayList<SavedRecording>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val projection = arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DATE_ADDED
                    )
                    context.contentResolver.query(
                        collection,
                        projection,
                        "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?",
                        arrayOf("${Environment.DIRECTORY_MOVIES}/$PUBLIC_FOLDER/%"),
                        "${MediaStore.Video.Media.DATE_ADDED} DESC"
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            val uri = Uri.withAppendedPath(collection, id.toString())
                            result.add(
                                SavedRecording(
                                    title = c.getString(1).orEmpty().substringBeforeLast('.'),
                                    url = uri.toString(),
                                    size = c.getLong(2),
                                    dateMs = c.getLong(3) * 1000,
                                    uri = uri,
                                    file = null,
                                    ext = c.getString(1).orEmpty().substringAfterLast('.', "ts").lowercase()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Sin acceso a la lista: se muestran solo las de la carpeta de la app.
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), PUBLIC_FOLDER)
                addFiles(result, dir)
            }
            addFiles(result, appRecordingsDir(context))
            return result.sortedByDescending { it.dateMs }
        }

        private fun addFiles(into: MutableList<SavedRecording>, dir: File) {
            dir.listFiles { f -> f.isFile && (f.extension == "ts" || f.extension == "mp4") }?.forEach { f ->
                into.add(
                    SavedRecording(
                        title = f.nameWithoutExtension,
                        url = Uri.fromFile(f).toString(),
                        size = f.length(),
                        dateMs = f.lastModified(),
                        uri = null,
                        file = f,
                        ext = f.extension.lowercase()
                    )
                )
            }
        }

        fun delete(context: Context, recording: SavedRecording): Boolean = try {
            val uri = recording.uri
            if (uri != null) context.contentResolver.delete(uri, null, null) > 0 else recording.file?.delete() == true
        } catch (e: Exception) {
            false
        }

        private fun safeName(name: String): String =
            name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(40).ifEmpty { "canal" }
    }
}
