package com.example.mitvplayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Una grabación programada desde la guía. No guarda usuario ni contraseña: la dirección se arma al grabar. */
class ScheduledRecording(
    val id: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val startSec: Long,
    val endSec: Long
)

/** Lista de grabaciones programadas, guardada en el equipo y observable. */
class ScheduleStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scheduled_recordings", Context.MODE_PRIVATE)

    var items: List<ScheduledRecording> by mutableStateOf(read())
        private set

    fun find(id: String): ScheduledRecording? = items.firstOrNull { it.id == id }

    fun has(channelId: String, startSec: Long): Boolean = items.any { it.channelId == channelId && it.startSec == startSec }

    fun idFor(channelId: String, startSec: Long): String = "$channelId@$startSec"

    fun add(item: ScheduledRecording) {
        store(items.filter { it.id != item.id } + item)
    }

    fun remove(id: String) {
        store(items.filter { it.id != id })
    }

    private fun store(value: List<ScheduledRecording>) {
        items = value.sortedBy { it.startSec }
        val array = JSONArray()
        for (r in items) {
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("c", r.channelId)
                    .put("n", r.channelName)
                    .put("t", r.title)
                    .put("s", r.startSec)
                    .put("e", r.endSec)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun read(): List<ScheduledRecording> =
        try {
            val array = JSONArray(prefs.getString("items", "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ScheduledRecording(
                    id = o.getString("id"),
                    channelId = o.getString("c"),
                    channelName = o.optString("n"),
                    title = o.optString("t"),
                    startSec = o.getLong("s"),
                    endSec = o.getLong("e")
                )
            }.sortedBy { it.startSec }
        } catch (e: Exception) {
            emptyList()
        }
}

/** Pone y quita las alarmas que arrancan las grabaciones programadas. */
object RecordingScheduler {
    const val ACTION_FIRE = "com.ultrapremier.tv.FIRE_RECORDING"
    const val EXTRA_ID = "id"

    private var cached: ScheduleStore? = null

    @Synchronized
    fun store(context: Context): ScheduleStore =
        cached ?: ScheduleStore(context.applicationContext).also { cached = it }

    private fun pendingIntent(context: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Guarda la grabación y programa su alarma. */
    fun schedule(context: Context, item: ScheduledRecording) {
        store(context).add(item)
        setAlarm(context, item)
    }

    fun cancel(context: Context, id: String) {
        store(context).remove(id)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingIntent(context, id))
    }

    /** Después de reiniciar el equipo las alarmas se pierden: se vuelven a poner las que siguen pendientes. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis() / 1000
        val list = store(context).items
        for (item in list) {
            if (item.endSec <= now) store(context).remove(item.id) else setAlarm(context, item)
        }
    }

    private fun setAlarm(context: Context, item: ScheduledRecording) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = maxOf(item.startSec * 1000, System.currentTimeMillis() + 2_000L)
        val pending = pendingIntent(context, item.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}

/** Llega la hora de un programa: empieza a grabar el canal. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RecordingScheduler.EXTRA_ID) ?: return
        val store = RecordingScheduler.store(context)
        val item = store.find(id) ?: return
        store.remove(id)

        val nowSec = System.currentTimeMillis() / 1000
        if (item.endSec <= nowSec) return

        val repository = (context.applicationContext as MiTvApp).repository
        val url = repository.recordUrlOffline(item.channelId)
        if (url == null) {
            Toast.makeText(
                context,
                "No se pudo grabar ${item.title}: abre Ultra Premier TV e inicia sesión con \"Mantener la sesión iniciada\".",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // Hasta el final del programa y 2 minutos más (los programas suelen alargarse), sin pasar del máximo.
        val minutes = (((item.endSec - nowSec) / 60).toInt() + 3).coerceIn(1, RecordingService.MAX_MINUTES)
        try {
            RecordingService.start(context, url, item.channelName, minutes)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo iniciar la grabación programada de ${item.title}.", Toast.LENGTH_LONG).show()
        }
    }
}

/** Al encender el equipo se vuelven a poner las alarmas de las grabaciones programadas. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RecordingScheduler.rescheduleAll(context)
        }
    }
}
