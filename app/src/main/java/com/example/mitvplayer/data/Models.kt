package com.example.mitvplayer.data

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{M}+")

/** Texto sin acentos y en minúsculas, para comparar al buscar. */
fun searchKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

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
    val director: String?
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
    val seasons: Map<Int, List<Episode>>
)

data class EpgEntry(
    val title: String,
    val description: String,
    /** Segundos desde epoch. */
    val start: Long,
    val end: Long,
    /** El servidor tiene grabado este programa (Catch up). */
    val hasArchive: Boolean = false
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
