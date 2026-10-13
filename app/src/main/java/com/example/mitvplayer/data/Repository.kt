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

    private val epgCache = HashMap<String, Pair<Long, List<EpgEntry>>>()
    private val tableCache = HashMap<String, Pair<Long, List<EpgEntry>>>()
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
            .also { liveMemory = it }
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
        val categories = api.categories(c, kind.categoriesAction)
        val file = File(cacheDir, "${kind.cacheName}_${cacheKey(c)}.json")

        var items: List<T>? = null
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS
        if (!force && fresh) {
            items = try {
                parse(file, "").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
        }
        if (items == null) {
            api.downloadList(c, kind.streamsAction, file)
            var parsed = parse(file, "")
            if (parsed.isEmpty() && categories.isNotEmpty()) {
                // Hay servidores que no permiten pedir "todo"; se piden las categorías una por una.
                parsed = loadPerCategory(c, kind, categories, parse)
                file.setLastModified(System.currentTimeMillis())
            }
            items = parsed
        }
        Catalog(categories, items, file.lastModified())
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

    /** Programa actual y siguientes de un canal, con caché de 5 minutos y como máximo 3 consultas a la vez. */
    suspend fun epgNowNext(streamId: String): List<EpgEntry> {
        val now = System.currentTimeMillis()
        synchronized(epgCache) {
            val cached = epgCache[streamId]
            if (cached != null && now - cached.first < EPG_MAX_AGE_MS) return cached.second
        }
        val list = epgGate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    api.shortEpg(creds(), streamId, 4).sortedBy { it.start }
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

    /** Guía completa de un canal con caché de 15 minutos (para la guía en cuadrícula). Si falla, devuelve vacío. */
    suspend fun epgTableCached(streamId: String): List<EpgEntry> {
        val now = System.currentTimeMillis()
        synchronized(tableCache) {
            val cached = tableCache[streamId]
            if (cached != null && now - cached.first < TABLE_MAX_AGE_MS) return cached.second
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
    }
}
