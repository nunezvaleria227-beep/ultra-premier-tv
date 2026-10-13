package com.example.mitvplayer.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Punto único de acceso a los datos.
 *
 * Cada sección (en vivo, películas, series) se descarga COMPLETA una vez, se guarda en el
 * equipo y se lee desde ahí; así las categorías muestran su cantidad y la búsqueda es instantánea.
 */
class Repository(
    private val api: XtreamApi,
    private val session: SessionStore,
    context: Context
) {
    private val appContext = context.applicationContext
    private val cacheDir: File = File(appContext.cacheDir, "catalogs").also { it.mkdirs() }

    var credentials: Credentials? = null
        private set
    var account: AccountInfo? = null
        private set

    /** Favoritos e historial de la cuenta actual (null antes de iniciar sesión). */
    var library: Library? = null
        private set

    private var liveMemory: Catalog<LiveChannel>? = null
    private var moviesMemory: Catalog<Movie>? = null
    private var seriesMemory: Catalog<SeriesItem>? = null

    /** Canales de En vivo agrupados por nombre base (quita HD/FHD/4K…), para prestar la guía entre versiones hermanas. */
    @Volatile
    private var siblingIndex: Map<String, List<String>>? = null

    /** Cachés con tope (los más usados recientemente se quedan): recorrer miles de canales no llena la memoria. */
    private class Lru(private val max: Int) : LinkedHashMap<String, Pair<Long, List<EpgEntry>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<EpgEntry>>>?): Boolean = size > max
    }

    private val epgCache = Lru(EPG_CACHE_MAX)
    private val tableCache = Lru(TABLE_CACHE_MAX)
    private val epgGate = Semaphore(3)

    /** Formato del video en vivo: "m3u8" (HLS) o "ts". */
    var liveFormat: String
        get() = session.liveFormat ?: defaultLiveFormat()
        set(value) {
            session.liveFormat = value
        }

    private fun defaultLiveFormat(): String {
        val allowed = account?.allowedFormats.orEmpty()
        return if (allowed.isEmpty() || allowed.any { it.equals("m3u8", ignoreCase = true) }) "m3u8" else "ts"
    }

    private fun creds(): Credentials = credentials ?: throw AuthException("Sesión no iniciada")

    // ------------------------------------------------------------------ sesión

    fun savedCredentials(): Credentials? = session.load()

    suspend fun login(input: Credentials, remember: Boolean): AccountInfo {
        val normalized = input.copy(server = api.normalizeServer(input.server))
        val info = withContext(Dispatchers.IO) { api.login(normalized) }
        credentials = normalized
        account = info
        library = Library(appContext.getSharedPreferences(LIBRARY_FILE, Context.MODE_PRIVATE), normalized.username)
        clearMemory()
        if (remember) session.save(normalized) else session.clear()
        return info
    }

    fun logout() {
        credentials = null
        account = null
        library = null
        clearMemory()
        session.clear()
    }

    private fun clearMemory() {
        liveMemory = null
        siblingIndex = null
        moviesMemory = null
        seriesMemory = null
        synchronized(epgCache) { epgCache.clear() }
        synchronized(tableCache) { tableCache.clear() }
    }

    /** Borra las listas guardadas en el equipo (se vuelven a descargar al entrar). */
    fun deleteDownloadedLists() {
        cacheDir.listFiles()?.forEach { it.delete() }
        clearMemory()
    }

    // ------------------------------------------------------------ listas completas

    suspend fun liveCatalog(force: Boolean = false): Catalog<LiveChannel> {
        if (!force) liveMemory?.let { return it }
        return loadCatalog(Kind.LIVE, force) { file, category -> api.parseLive(file, category) }
            .also {
                liveMemory = it
                siblingIndex = null
            }
    }

    suspend fun movieCatalog(force: Boolean = false): Catalog<Movie> {
        if (!force) moviesMemory?.let { return it }
        return loadCatalog(Kind.MOVIES, force) { file, category -> api.parseMovies(file, category) }
            .also { moviesMemory = it }
    }

    suspend fun seriesCatalog(force: Boolean = false): Catalog<SeriesItem> {
        if (!force) seriesMemory?.let { return it }
        return loadCatalog(Kind.SERIES, force) { file, category -> api.parseSeries(file, category) }
            .also { seriesMemory = it }
    }

    private suspend fun <T : Item> loadCatalog(
        kind: Kind,
        force: Boolean,
        parse: (File, String) -> List<T>
    ): Catalog<T> = withContext(Dispatchers.IO) {
        val c = creds()
        val key = cacheKey(c)
        val file = File(cacheDir, "${kind.cacheName}_$key.json")
        val categoriesFile = File(cacheDir, "${kind.cacheName}_categories_$key.json")
        val started = System.nanoTime()

        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS
        // Con la lista guardada al día también se usan las categorías guardadas: se abre sin esperar a la red.
        var categories: List<Category>? = if (!force && fresh) readCategories(categoriesFile) else null
        val categoriesFromDisk = categories != null
        if (categories == null) {
            categories = api.categories(c, kind.categoriesAction)
            saveCategories(categoriesFile, categories)
        }
        val afterCategories = System.nanoTime()

        var items: List<T>? = null
        var source = "caché"
        if (!force && fresh) {
            items = try {
                parse(file, "").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
        }
        if (items == null) {
            source = "red"
            api.downloadList(c, kind.streamsAction, file)
            var parsed = parse(file, "")
            if (parsed.isEmpty() && categories.isNotEmpty()) {
                // Hay servidores que no permiten pedir "todo"; se piden las categorías una por una.
                parsed = loadPerCategory(c, kind, categories, parse)
                file.setLastModified(System.currentTimeMillis())
            }
            items = parsed
        }
        PerfLog.log {
            "lista ${kind.cacheName}: categorías ${(afterCategories - started) / 1_000_000} ms (${if (categoriesFromDisk) "disco" else "red"}), " +
                "elementos ${PerfLog.ms(afterCategories)} ms ($source), ${items.size} elementos"
        }
        Catalog(categories, items, file.lastModified())
    }

    private fun readCategories(file: File): List<Category>? =
        try {
            if (!file.exists()) {
                null
            } else {
                val array = org.json.JSONArray(file.readText())
                val list = ArrayList<Category>(array.length())
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    list.add(Category(o.getString("i"), o.getString("n")))
                }
                list.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }

    private fun saveCategories(file: File, categories: List<Category>) {
        try {
            val array = org.json.JSONArray()
            for (category in categories) {
                array.put(org.json.JSONObject().put("i", category.id).put("n", category.name))
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            // Sin guardar: la próxima vez se piden de nuevo.
        }
    }

    private fun <T : Item> loadPerCategory(
        c: Credentials,
        kind: Kind,
        categories: List<Category>,
        parse: (File, String) -> List<T>
    ): List<T> {
        val out = ArrayList<T>()
        val temp = File(cacheDir, "temp_${kind.cacheName}.json")
        for (category in categories) {
            try {
                api.downloadList(c, kind.streamsAction, temp, mapOf("category_id" to category.id))
                out.addAll(parse(temp, category.id))
            } catch (e: IOException) {
                // Si una categoría falla se sigue con la siguiente.
            }
        }
        temp.delete()
        return out
    }

    private fun cacheKey(c: Credentials): String {
        val digest = MessageDigest.getInstance("SHA-1").digest("${c.server}|${c.username}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(10)
    }

    // ------------------------------------------------------------------ en vivo

    fun liveUrl(channelId: String): String = api.liveUrl(creds(), channelId, liveFormat)

    /** Siempre en MPEG-TS: es el formato que se puede grabar tal cual a un archivo. */
    fun recordUrl(channelId: String): String = api.liveUrl(creds(), channelId, "ts")

    /**
     * Igual que [recordUrl], pero sirve aunque la app no esté abierta (grabaciones programadas):
     * si no hay sesión en memoria usa la sesión guardada. Devuelve null si no hay ninguna.
     */
    fun recordUrlOffline(channelId: String): String? {
        val c = credentials ?: session.load() ?: return null
        return api.liveUrl(c, channelId, "ts")
    }

    suspend fun shortEpg(streamId: String): List<EpgEntry> =
        withContext(Dispatchers.IO) { api.shortEpg(creds(), streamId) }

    /** Otras versiones del mismo canal (mismo nombre sin HD/FHD/4K…), las que ya se sabe que tienen guía primero. */
    private fun siblingIds(streamId: String): List<String> {
        val live = liveMemory ?: return emptyList()
        val index = siblingIndex ?: live.items.groupBy({ epgBaseKey(it.name) }, { it.id })
            .filterKeys { it != null }
            .mapKeys { it.key!! }
            .also { siblingIndex = it }
        val key = live.byId[streamId]?.let { epgBaseKey(it.name) } ?: return emptyList()
        val others = index[key].orEmpty().filter { it != streamId }
        if (others.isEmpty()) return others
        val known = synchronized(epgCache) { others.filter { epgCache[it]?.second?.isNotEmpty() == true } }
        return (known + others.filter { it !in known }).take(MAX_SIBLINGS)
    }

    /** "Ahora y después" de UN canal (sin prestar de otros), con caché. Los vacíos se recuerdan más tiempo. */
    private suspend fun nowNextOwn(streamId: String): List<EpgEntry> {
        val now = System.currentTimeMillis()
        synchronized(epgCache) {
            val cached = epgCache[streamId]
            if (cached != null) {
                val maxAge = if (cached.second.isEmpty()) EMPTY_MAX_AGE_MS else EPG_MAX_AGE_MS
                if (now - cached.first < maxAge) return cached.second
            }
        }
        val list = epgGate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val c = creds()
                    val short = api.shortEpg(c, streamId, 4).sortedBy { it.start }
                    if (short.isNotEmpty()) {
                        short
                    } else {
                        // Algunos servidores no llenan "short" pero sí la tabla del día.
                        val nowSec = System.currentTimeMillis() / 1000
                        api.epgTable(c, streamId).sortedBy { it.start }.filter { it.end > nowSec }.take(4)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        synchronized(epgCache) { epgCache[streamId] = now to list }
        return list
    }

    /**
     * Programa actual y siguientes de un canal. Si el canal no tiene guía, se presta la de una
     * versión hermana (por ejemplo "ESPN FHD" para "ESPN HD"). Máximo 3 consultas a la vez.
     */
    suspend fun epgNowNext(streamId: String): List<EpgEntry> {
        val own = nowNextOwn(streamId)
        if (own.isNotEmpty()) return own
        for (sibling in siblingIds(streamId).take(2)) {
            val borrowed = nowNextOwn(sibling)
            if (borrowed.isNotEmpty()) return borrowed
        }
        return emptyList()
    }

    /** Guía completa de UN canal (sin prestar), con caché de 15 minutos. */
    private suspend fun tableOwn(streamId: String): List<EpgEntry> {
        val now = System.currentTimeMillis()
        synchronized(tableCache) {
            val cached = tableCache[streamId]
            if (cached != null) {
                val maxAge = if (cached.second.isEmpty()) EMPTY_MAX_AGE_MS else TABLE_MAX_AGE_MS
                if (now - cached.first < maxAge) return cached.second
            }
        }
        val list = epgGate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    api.epgTable(creds(), streamId).sortedBy { it.start }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        synchronized(tableCache) { tableCache[streamId] = now to list }
        return list
    }

    /**
     * Guía completa de un canal (para la guía en cuadrícula). Si el canal no tiene guía,
     * se presta la de una versión hermana. Si falla, devuelve vacío.
     */
    suspend fun epgTableCached(streamId: String): List<EpgEntry> {
        val own = tableOwn(streamId)
        if (own.isNotEmpty()) return own
        for (sibling in siblingIds(streamId)) {
            val borrowed = tableOwn(sibling)
            if (borrowed.isNotEmpty()) return borrowed
        }
        return emptyList()
    }

    /** Guía completa de un canal, para elegir un programa de Catch up. */
    suspend fun epgTable(streamId: String): List<EpgEntry> =
        withContext(Dispatchers.IO) { api.epgTable(creds(), streamId) }

    fun archiveUrl(channel: LiveChannel, entry: EpgEntry): String {
        val minutes = ((entry.end - entry.start) / 60).toInt().coerceAtLeast(1)
        return api.timeshiftUrl(creds(), channel.id, entry.start, minutes, account?.timezone)
    }

    // ---------------------------------------------------------------- películas

    suspend fun movieInfo(movieId: String): MovieInfo =
        withContext(Dispatchers.IO) { api.vodInfo(creds(), movieId) }

    fun movieUrl(movie: Movie): String = api.movieUrl(creds(), movie.id, movie.extension)

    fun movieUrlById(id: String, extension: String): String = api.movieUrl(creds(), id, extension)

    // ------------------------------------------------------------------- series

    suspend fun seriesDetail(seriesId: String): SeriesDetail =
        withContext(Dispatchers.IO) { api.seriesInfo(creds(), seriesId) }

    fun episodeUrl(episode: Episode): String = api.episodeUrl(creds(), episode.id, episode.extension)

    fun episodeUrlById(id: String, extension: String): String = api.episodeUrl(creds(), id, extension)

    private companion object {
        const val LIBRARY_FILE = "library"
        const val CACHE_MAX_AGE_MS = 12L * 60 * 60 * 1000
        const val EPG_MAX_AGE_MS = 5L * 60 * 1000
        const val TABLE_MAX_AGE_MS = 15L * 60 * 1000

        /** Los canales sin guía se vuelven a consultar con menos frecuencia. */
        const val EMPTY_MAX_AGE_MS = 20L * 60 * 1000
        const val MAX_SIBLINGS = 3
        const val EPG_CACHE_MAX = 600
        const val TABLE_CACHE_MAX = 120
    }
}
