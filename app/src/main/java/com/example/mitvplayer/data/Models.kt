package com.example.mitvplayer.data

import java.text.Normalizer

/**
 * Texto sin acentos y en minúsculas, para comparar al buscar.
 * Se calcula para cada canal, película y serie al leer las listas (decenas de miles): el texto sin acentos
 * (lo más común) no pasa por el normalizador, y los acentos se quitan sin expresiones regulares.
 */
fun searchKey(text: String): String {
    var ascii = true
    for (ch in text) {
        if (ch.code >= 128) {
            ascii = false
            break
        }
    }
    if (ascii) return text.lowercase()
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val out = StringBuilder(decomposed.length)
    for (ch in decomposed) {
        val type = Character.getType(ch)
        if (type != Character.NON_SPACING_MARK.toInt() &&
            type != Character.COMBINING_SPACING_MARK.toInt() &&
            type != Character.ENCLOSING_MARK.toInt()
        ) {
            out.append(ch)
        }
    }
    return out.toString().lowercase()
}

/** Las tres secciones que se descargan completas del servidor. */
enum class Kind(val streamsAction: String, val categoriesAction: String, val cacheName: String) {
    LIVE("get_live_streams", "get_live_categories", "live"),
    MOVIES("get_vod_streams", "get_vod_categories", "movies"),
    SERIES("get_series", "get_series_categories", "series")
}

/** Algo que se puede listar: canal, película o serie. */
interface Item {
    val id: String
    val name: String
    val categoryId: String

    /** Fecha de alta en segundos desde epoch (0 si no se conoce). */
    val added: Long

    /** Calificación de 0 a 10 (0 si no hay). */
    val rating10: Float

    /** Nombre sin acentos ni mayúsculas, para buscar. */
    val key: String
}

data class Credentials(
    val server: String,
    val username: String,
    val password: String
)

data class AccountInfo(
    val username: String,
    val status: String,
    /** Fecha de vencimiento en segundos desde epoch; null = sin vencimiento. */
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedFormats: List<String>,
    /** Zona horaria del servidor (necesaria para pedir el archivo "Catch up"). */
    val timezone: String? = null,
    /** Mensaje que el servidor manda al iniciar sesión, si hay. */
    val message: String? = null
)

data class Category(
    val id: String,
    val name: String
)

data class LiveChannel(
    override val id: String,
    override val name: String,
    val icon: String?,
    override val categoryId: String,
    val number: Int,
    /** Días de archivo (Catch up) que guarda el servidor para este canal; 0 = sin archivo. */
    val archiveDays: Int = 0,
    override val added: Long = 0L
) : Item {
    override val rating10: Float get() = 0f
    override val key: String = searchKey(name)
}

data class Movie(
    override val id: String,
    override val name: String,
    val poster: String?,
    override val categoryId: String,
    val extension: String,
    val rating: String?,
    override val added: Long = 0L
) : Item {
    override val rating10: Float
        get() = rating?.toFloatOrNull()?.coerceIn(0f, 10f) ?: 0f
    override val key: String = searchKey(name)
}

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val duration: String?,
    val cast: String?,
    val director: String?,
    /** Imagen grande de fondo. */
    val backdrop: String? = null,
    /** Dirección del tráiler (YouTube). */
    val trailer: String? = null,
    /** Datos para encontrar sus subtítulos en internet. */
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val originalName: String? = null
)

data class SeriesItem(
    override val id: String,
    override val name: String,
    val cover: String?,
    override val categoryId: String,
    val plot: String?,
    val rating: String?,
    override val added: Long = 0L
) : Item {
    override val rating10: Float
        get() = rating?.toFloatOrNull()?.coerceIn(0f, 10f) ?: 0f
    override val key: String = searchKey(name)
}

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val extension: String,
    val plot: String?,
    val image: String?,
    val durationSecs: Int?
)

data class SeriesDetail(
    val plot: String?,
    val cover: String?,
    val genre: String?,
    /** Temporadas ordenadas; cada lista de episodios también va ordenada. */
    val seasons: Map<Int, List<Episode>>,
    val backdrop: String? = null,
    val trailer: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val releaseDate: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null
)

data class EpgEntry(
    val title: String,
    val description: String,
    /** Segundos desde epoch. */
    val start: Long,
    val end: Long,
    /** El servidor tiene grabado este programa (Catch up). */
    val hasArchive: Boolean = false,
    /** Bloque de relleno ("En vivo") para un canal que no manda guía; no es un programa real. */
    val placeholder: Boolean = false
)

/** Una sección completa ya descargada: categorías y todos sus elementos. */
class Catalog<T : Item>(
    val categories: List<Category>,
    val items: List<T>,
    /** Milisegundos desde epoch en que se descargó del servidor. */
    val updatedAt: Long
) {
    val byId: Map<String, T> by lazy { items.associateBy { it.id } }
    val counts: Map<String, Int> by lazy { items.groupingBy { it.categoryId }.eachCount() }
}
