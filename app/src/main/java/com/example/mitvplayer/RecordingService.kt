package com.example.mitvplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ActiveRecording(
    val channelName: String,
    val file: File,
    val startedAt: Long,
    val bytes: Long,
    /** Hora de fin en milisegundos; null = graba hasta que se detenga. */
    val endsAt: Long?
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
 * Graba un canal en vivo (MPEG-TS) a un archivo del equipo.
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
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
        if (url == null || worker?.isAlive == true) {
            if (worker?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Grabando $name"),
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

    private fun record(url: String, name: String, minutes: Int) {
        val startedAt = System.currentTimeMillis()
        val endsAt = if (minutes > 0) startedAt + minutes * 60_000L else null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(startedAt))
        val file = File(recordingsDir(this), "${safeName(name)}_$stamp.ts")
        publish(ActiveRecording(name, file, startedAt, 0L, endsAt))

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        try {
            val newCall = client.newCall(
                Request.Builder().url(url).header("User-Agent", Config.USER_AGENT).build()
            )
            call = newCall
            newCall.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Respuesta vacía")
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var lastPublish = 0L
                        while (!stopRequested) {
                            val now = System.currentTimeMillis()
                            if (endsAt != null && now >= endsAt) break
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            total += read
                            if (now - lastPublish >= 1000) {
                                lastPublish = now
                                publish(ActiveRecording(name, file, startedAt, total, endsAt))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Corte de la señal o cancelación: se conserva lo que se alcanzó a grabar.
        } finally {
            if (file.exists() && file.length() == 0L) file.delete()
            main.post {
                RecordingManager.current = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun publish(recording: ActiveRecording) {
        main.post { RecordingManager.current = recording }
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Grabaciones", NotificationManager.IMPORTANCE_LOW)
            )
        }
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
        private const val ACTION_STOP = "com.ultrapremier.tv.STOP_RECORDING"
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_MINUTES = "minutes"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 77

        /** [minutes] = null graba hasta que se detenga. */
        fun start(context: Context, url: String, name: String, minutes: Int?) {
            if (RecordingManager.current != null) return
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_MINUTES, minutes ?: 0)
            ContextCompat.startForegroundService(context, intent)
        }

        fun recordingsDir(context: Context): File {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            return File(base, "Grabaciones").also { it.mkdirs() }
        }

        fun listRecordings(context: Context): List<File> =
            (recordingsDir(context).listFiles { f -> f.isFile && f.extension == "ts" }?.toList() ?: emptyList())
                .sortedByDescending { it.lastModified() }

        private fun safeName(name: String): String =
            name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(40).ifEmpty { "canal" }
    }
}
