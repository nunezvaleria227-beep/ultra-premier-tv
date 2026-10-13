package com.example.mitvplayer.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Una película o episodio que se dejó a medias.
 * No se guarda la dirección de reproducción (lleva usuario y contraseña): se vuelve a armar con [type], [id] y [ext].
 */
class ResumeEntry(
    /** "movie" o "episode". */
    val type: String,
    val id: String,
    val ext: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Categoría de la película o de la serie (para esconderla con el control parental). */
    val category: String = ""
) {
    val key: String get() = "$type:$id"

    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** "Continuar viendo": posición de cada película o episodio, guardada en el equipo y observable. */
class ProgressStore(
    private val prefs: SharedPreferences,
    private val storeKey: String
) {
    var entries: List<ResumeEntry> by mutableStateOf(readStored())
        private set

    fun find(type: String, id: String): ResumeEntry? = entries.firstOrNull { it.type == type && it.id == id }

    fun save(entry: ResumeEntry) {
        store((listOf(entry) + entries.filter { it.key != entry.key }).take(MAX_ENTRIES))
    }

    fun remove(type: String, id: String) {
        if (entries.any { it.type == type && it.id == id }) {
            store(entries.filter { !(it.type == type && it.id == id) })
        }
    }

    fun clear() = store(emptyList())

    private fun store(value: List<ResumeEntry>) {
        entries = value
        val array = JSONArray()
        for (e in value) {
            array.put(
                JSONObject()
                    .put("t", e.type)
                    .put("i", e.id)
                    .put("x", e.ext)
                    .put("n", e.title)
                    .put("s", e.subtitle ?: "")
                    .put("m", e.image ?: "")
                    .put("p", e.positionMs)
                    .put("d", e.durationMs)
                    .put("u", e.updatedAt)
                    .put("c", e.category)
            )
        }
        prefs.edit().putString(storeKey, array.toString()).apply()
    }

    private fun readStored(): List<ResumeEntry> =
        try {
            val array = JSONArray(prefs.getString(storeKey, "[]"))
            (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                ResumeEntry(
                    type = o.getString("t"),
                    id = o.getString("i"),
                    ext = o.optString("x", "mp4"),
                    title = o.optString("n"),
                    subtitle = o.optString("s").ifEmpty { null },
                    image = o.optString("m").ifEmpty { null },
                    positionMs = o.optLong("p"),
                    durationMs = o.optLong("d"),
                    updatedAt = o.optLong("u"),
                    category = o.optString("c")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

    private companion object {
        const val MAX_ENTRIES = 40
    }
}
