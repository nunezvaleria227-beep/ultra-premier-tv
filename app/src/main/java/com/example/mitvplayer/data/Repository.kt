package com.example.mitvplayer.data

import android.content.Context
import com.example.mitvplayer.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    private val networkPrefs = appContext.getSharedPreferences(NETWORK_FILE, Context.MODE_PRIVATE)

    init {
        // La identificación (User-Agent) que el servidor aceptó la última vez se usa desde el arranque.
        networkPrefs.getString(KEY_USER_AGENT, null)?.takeIf { it.isNotBlank() }?.let { Config.userAgent = it }
    }

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

    /** Si hay una sesión guardada, sin abrir el almacén cifrado (null = no se sabe todavía). */
    fun hasSavedSessionHint(): Boolean? = session.hasSavedHint

    /**
     * Si el servidor rechaza la identificación actual con 401/403, prueba las otras de [Config.userAgentOptions]
     * y se queda con la primera que funcione.
     */
    private fun <T> withAnyAgent(block: () -> T): T {
        try {
            return block()
        } catch (first: AccessDeniedException) {
            val original = Config.userAgent
            for (agent in Config.userAgentOptions()) {
                if (agent == original) continue
                Config.userAgent = agent
                try {
                    val result = block()
                    rememberUserAgent(agent)
                    return result
                } catch (e: AccessDeniedException) {
                    // Esta tampoco: se prueba la siguiente.
                }
            }
            Config.userAgent = original
            throw first
        }
    }

    /** Guarda la identificación (User-Agent) que el servidor aceptó para reproducir. */
    fun rememberUserAgent(agent: String) {
        networkPrefs.edit().putString(KEY_USER_AGENT, agent).apply()
    }

    suspend fun login(input: Credentials, remember: Boolean): AccountInfo {
        val normalized = input.copy(server = api.normalizeServer(input.server))
        val info = withContext(Dispatchers.IO) { loginTryingAgents(normalized) }
        credentials = normalized
        account = info
        library = Library(appContext.getSharedPreferences(LIBRARY_FILE, Context.MODE_PRIVATE), normalized.username)
        clearMemory()
        if (remember) session.save(normalized) else session.clear()
        return info
    }

    /**
     * Inicia sesión con la identificación (User-Agent) actual. Si el servidor la rechaza con 401/403,
     * prueba las otras de [Config.userAgentOptions] y guarda la primera que funcione.
     */
    private fun loginTryingAgents(c: Credentials): AccountInfo {
        val candidates = (listOf(Config.userAgent) + Config.userAgentOptions()).distinct()
        var denied: AccessDeniedException? = null
        for (agent in candidates) {
            Config.userAgent = agent
            try {
                val info = api.login(c)
                networkPrefs.edit().putString(KEY_USER_AGENT, agent).apply()
                return info
            } catch (e: AccessDeniedException) {
                denied = e
                // Si el servidor sí acepta esta identificación (sin datos contesta 401 o 200), el 403 es por el
                // usuario o la contraseña: no se insiste con otras identificaciones, porque muchos intentos
                // fallidos seguidos pueden hacer que el servidor bloquee la conexión.
                val probe = api.probe(c.server)
                if (probe == 401 || probe == 200) {
                    Config.userAgent = candidates.first()
                    throw WrongCredentialsException()
                }
            }
        }
        Config.userAgent = candidates.first()
        throw denied ?: AuthException("Acceso denegado por el servidor")
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

    /** Un solo cargador por sección: si dos pantallas piden la misma lista a la vez, se descarga una vez. */
    private val loadLocks = Kind.values().associateWith { Mutex() }

    suspend fun liveCatalog(force: Boolean = false, onProgress: (String) -> Unit = {}): Catalog<LiveChannel> =
        loadLocks.getValue(Kind.LIVE).withLock {
            if (!force) liveMemory?.let { return@withLock it }
            loadCatalog(
                Kind.LIVE, force, { file, category -> api.parseLive(file, category) },
                CatalogStore::readLive, CatalogStore::writeLive, onProgress
            ).also {
                liveMemory = it
                siblingIndex = null
                buildSiblingIndex(it)
            }
        }

    suspend fun movieCatalog(force: Boolean = false, onProgress: (String) -> Unit = {}): Catalog<Movie> =
        loadLocks.getValue(Kind.MOVIES).withLock {
            if (!force) moviesMemory?.let { return@withLock it }
            loadCatalog(
                Kind.MOVIES, force, { file, category -> api.parseMovies(file, category) },
                CatalogStore::readMovies, CatalogStore::writeMovies, onProgress
            ).also { moviesMemory = it }
        }

    suspend fun seriesCatalog(force: Boolean = false, onProgress: (String) -> Unit = {}): Catalog<SeriesItem> =
        loadLocks.getValue(Kind.SERIES).withLock {
            if (!force) seriesMemory?.let { return@withLock it }
            loadCatalog(
                Kind.SERIES, force, { file, category -> api.parseSeries(file, category) },
                CatalogStore::readSeries, CatalogStore::writeSeries, onProgress
            ).also { seriesMemory = it }
        }

    /**
     * Si ya hay una copia guardada en el equipo se abre de inmediato (aunque sea de ayer: la pantalla avisa y se
     * actualiza sola en segundo plano). Si no, se descarga del servidor, se lee y se guarda la copia compacta.
     */
    private suspend fun <T : Item> loadCatalog(
        kind: Kind,
        force: Boolean,
        parse: (File, String) -> List<T>,
        readStored: (File) -> List<T>?,
        writeStored: (File, List<T>) -> Unit,
        onProgress: (String) -> Unit
    ): Catalog<T> = withContext(Dispatchers.IO) {
        val c = creds()
        val key = cacheKey(c)
        val stored = File(cacheDir, "${kind.cacheName}_$key.bin")
        val oldJson = File(cacheDir, "${kind.cacheName}_$key.json")
        val categoriesFile = File(cacheDir, "${kind.cacheName}_categories_$key.json")
        val started = System.nanoTime()

        if (!force) {
            val categories = readCategories(categoriesFile)
            if (categories != null) {
                var source = "copia guardada"
                var items = readStored(stored)
                if (items == null && oldJson.exists()) {
                    // Lista que guardó la versión anterior de la app: se convierte una sola vez.
                    source = "lista de la versión anterior"
                    items = try {
                        parse(oldJson, "").takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    if (items != null) {
                        writeStored(stored, items)
                        stored.setLastModified(oldJson.lastModified())
                    }
                    oldJson.delete()
                }
                val found: List<T>? = items
                if (!found.isNullOrEmpty()) {
                    val catalog = Catalog(categories, found, stored.lastModified())
                    val count = found.size
                    PerfLog.log { "lista ${kind.cacheName}: $count elementos de la $source en ${PerfLog.ms(started)} ms" }
                    return@withContext catalog
                }
            }
        }

        onProgress("Conectando con el servidor…")
        val categories = withAnyAgent { api.categories(c, kind.categoriesAction) }
        saveCategories(categoriesFile, categories)
        val afterCategories = System.nanoTime()
        val download = File(cacheDir, "${kind.cacheName}_$key.download")
        try {
            withAnyAgent {
                api.downloadList(c, kind.streamsAction, download) { bytes ->
                    onProgress("Descargando… ${"%.1f".format(bytes / 1_048_576.0)} MB")
                }
            }
            val afterDownload = System.nanoTime()
            onProgress("Preparando la lista…")
            val all = parse(download, "")
            // Hay servidores que no permiten pedir "todo"; entonces se piden las categorías una por una.
            val items = if (all.isEmpty() && categories.isNotEmpty()) loadPerCategory(c, kind, categories, parse, onProgress) else all
            if (items.isNotEmpty()) writeStored(stored, items)
            val catalog = Catalog(categories, items, System.currentTimeMillis())
            PerfLog.log {
                "lista ${kind.cacheName}: categorías ${(afterCategories - started) / 1_000_000} ms, " +
                    "descarga ${(afterDownload - afterCategories) / 1_000_000} ms (${download.length() / 1024} KB), " +
                    "lectura e índices ${PerfLog.ms(afterDownload)} ms, ${items.size} elementos"
            }
            removeOldCopies(kind, key)
            catalog
        } finally {
            download.delete()
        }
    }

    /** Borra listas guardadas de otros servidores o cuentas, y las de versiones anteriores de la app. */
    private fun removeOldCopies(kind: Kind, key: String) {
        val prefix = "${kind.cacheName}_"
        cacheDir.listFiles()?.forEach { file ->
            val name = file.name
            if (!name.startsWith(prefix) || name.startsWith("${prefix}categories_")) return@forEach
            if (name.contains(key) && name.endsWith(".bin")) return@forEach
            file.delete()
        }
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
        parse: (File, String) -> List<T>,
        onProgress: (String) -> Unit
    ): List<T> {
        val out = ArrayList<T>()
        val temp = File(cacheDir, "temp_${kind.cacheName}.json")
        categories.forEachIndexed { index, category ->
            onProgress("Descargando categorías… ${index + 1} de ${categories.size}")
            try {
                withAnyAgent { api.downloadList(c, kind.streamsAction, temp, mapOf("category_id" to category.id)) }
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
        val index = siblingIndex
        if (index == null) {
            // Agrupar miles de canales por nombre tarda: se hace aparte, sin trabar la pantalla.
            buildSiblingIndex(live)
            return emptyList()
        }
        val key = live.byId[streamId]?.let { epgBaseKey(it.name) } ?: return emptyList()
        val others = index[key].orEmpty().filter { it != streamId }
        if (others.isEmpty()) return others
        val known = synchronized(epgCache) { others.filter { epgCache[it]?.second?.isNotEmpty() == true } }
        return (known + others.filter { it !in known }).take(MAX_SIBLINGS)
    }

    @Volatile
    private var siblingIndexBuilding = false

    private fun buildSiblingIndex(live: Catalog<LiveChannel>) {
        synchronized(this) {
            if (siblingIndexBuilding) return
            siblingIndexBuilding = true
        }
        Thread({
            try {
                val built = live.items.groupBy({ epgBaseKey(it.name) }, { it.id })
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }
                if (liveMemory === live) siblingIndex = built
            } catch (e: Throwable) {
                // Sin índice: simplemente no se presta la guía entre canales hermanos.
            } finally {
                siblingIndexBuilding = false
            }
        }, "epg-hermanos").apply { priority = Thread.MIN_PRIORITY }.start()
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

    companion object {
        const val LIBRARY_FILE = "library"
        const val NETWORK_FILE = "network"
        const val KEY_USER_AGENT = "user_agent"
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
