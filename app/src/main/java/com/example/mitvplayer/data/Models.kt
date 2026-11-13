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

/**
 * Calificación de 0 a 10 a partir del texto del servidor ("7.5", "7,5", "8", "N/A", vacío...).
 * Se hace a mano: el toFloatOrNull() de Kotlin usa una expresión regular enorme y, llamado miles de veces
 * (al ordenar por calificación), dejaba la pantalla congelada varios segundos.
 */
fun parseRating(text: String?): Float {
    if (text.isNullOrEmpty()) return 0f
    var whole = 0f
    var frac = 0f
    var scale = 0.1f
    var seenDigit = false
    var afterPoint = false
    for (ch in text) {
        when {
            ch in '0'..'9' -> {
                seenDigit = true
                if (afterPoint) {
                    frac += (ch - '0') * scale
                    scale /= 10f
                } else {
                    whole = whole * 10f + (ch - '0')
                }
            }
            (ch == '.' || ch == ',') && !afterPoint -> afterPoint = true
            ch == ' ' && !seenDigit -> Unit
            else -> break
        }
    }
    if (!seenDigit) return 0f
    return (whole + frac).coerceIn(0f, 10f)
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
    // Se calcula la primera vez que se busca u ordena (no al cargar la lista: así abre más rápido).
    private var keyCache: String? = null
    override val key: String
        get() = keyCache ?: searchKey(name).also { keyCache = it }
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
    override val rating10: Float = parseRating(rating)
    // Se calcula la primera vez que se busca u ordena (no al cargar la lista: así abre más rápido).
    private var keyCache: String? = null
    override val key: String
        get() = keyCache ?: searchKey(name).also { keyCache = it }
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
    override val rating10: Float = parseRating(rating)
    // Se calcula la primera vez que se busca u ordena (no al cargar la lista: así abre más rápido).
    private var keyCache: String? = null
    override val key: String
        get() = keyCache ?: searchKey(name).also { keyCache = it }
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
    raw: List<T>,
    /** Milisegundos desde epoch en que se descargó del servidor. */
    val updatedAt: Long
) {
    /** Todo, sin repetidos (si un elemento viene en dos categorías, aquí aparece una sola vez). */
    val items: List<T>

    val byId: Map<String, T>

    /** Lo de cada categoría, ya separado (sin repetidos dentro de la misma categoría). */
    val byCategory: Map<String, List<T>>

    val counts: Map<String, Int>

    /** Categorías para adultos (XXX, +18…), las que esconde el control parental. */
    val adultCategoryIds: Set<String>

    /** [items] sin lo de las categorías para adultos. */
    val itemsWithoutAdult: List<T>

    /** Lo más nuevo primero (para "Recién añadido" y el inicio). */
    val newest: List<T>

    /** Lo mejor calificado primero. */
    val topRated: List<T>

    init {
        // Todo esto se calcula una sola vez, al cargar la lista y fuera de la pantalla:
        // así cambiar de categoría o de sección es instantáneo aunque haya cientos de miles de elementos.
        val ids = HashMap<String, T>(raw.size * 4 / 3 + 16)
        val unique = ArrayList<T>(raw.size)
        val groups = HashMap<String, ArrayList<T>>()
        var repeatedIn: HashMap<String, MutableSet<String>>? = null
        for (item in raw) {
            val previous = ids.putIfAbsent(item.id, item)
            if (previous == null) {
                unique.add(item)
                groups.getOrPut(item.categoryId) { ArrayList() }.add(item)
            } else if (previous.categoryId != item.categoryId) {
                val map = repeatedIn ?: HashMap<String, MutableSet<String>>().also { repeatedIn = it }
                val seen = map.getOrPut(item.id) { HashSet<String>(4).apply { add(previous.categoryId) } }
                if (seen.add(item.categoryId)) groups.getOrPut(item.categoryId) { ArrayList() }.add(item)
            }
        }
        items = unique
        byId = ids
        byCategory = groups
        counts = HashMap<String, Int>(groups.size * 2).apply { groups.forEach { (k, v) -> put(k, v.size) } }
        adultCategoryIds = categories.filter { isAdultCategoryName(it.name) }.mapTo(HashSet()) { it.id }
        itemsWithoutAdult = if (adultCategoryIds.isEmpty()) unique else unique.filter { it.categoryId !in adultCategoryIds }
        newest = topBy(unique, TOP_LIMIT) { it.added.toDouble() }.ifEmpty { unique.asReversed().take(TOP_LIMIT) }
        topRated = topBy(unique, TOP_LIMIT) { it.rating10.toDouble() }
    }

    private companion object {
        const val TOP_LIMIT = 400

        /** Los [limit] con el valor más alto (sin ordenar toda la lista); solo los que tienen valor mayor a 0. */
        fun <T> topBy(items: List<T>, limit: Int, value: (T) -> Double): List<T> {
            val heap = java.util.PriorityQueue<Pair<Double, Int>>(limit + 1, compareBy({ it.first }, { -it.second }))
            for (i in items.indices) {
                val v = value(items[i])
                if (v <= 0.0) continue
                if (heap.size < limit) {
                    heap.add(v to i)
                } else if (v > heap.peek()!!.first) {
                    heap.poll()
                    heap.add(v to i)
                }
            }
            val out = ArrayList<Pair<Double, Int>>(heap)
            out.sortWith(compareBy<Pair<Double, Int>>({ -it.first }, { it.second }))
            return out.map { items[it.second] }
        }
    }
}

private val ADULT_NAME = Regex(
    "(\\bxxx\\b|\\badult(?!\\s*swim)\\w*|\\bporn\\w*|\\berotic\\w*|\\bhentai\\b|\\bplayboy\\b|" +
        "\\bsexo\\b|\\bsexy\\b|\\bsex\\b|\\bxvideos\\b|\\bonlyfans\\b|\\bpara mayores\\b|\\bmayores de 18\\b|" +
        "(^|\\s|\\[|\\()\\+\\s?18|(^|[^0-9])18\\s?\\+)"
)

/** Nombre de categoría de contenido para adultos (sin acentos ni mayúsculas, como en la búsqueda). */
fun isAdultCategoryName(name: String): Boolean = ADULT_NAME.containsMatchIn(searchKey(name))
