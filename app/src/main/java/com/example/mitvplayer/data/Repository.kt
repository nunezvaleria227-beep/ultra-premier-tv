package com.example.mitvplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Punto único de acceso a los datos. Mantiene la sesión en memoria y una caché simple
 * por categoría para no volver a pedir al servidor lo que ya se cargó.
 */
class Repository(
    private val api: XtreamApi,
    private val session: SessionStore
) {
    var credentials: Credentials? = null
        private set
    var account: AccountInfo? = null
        private set

    private var liveCategoriesCache: List<Category>? = null
    private var movieCategoriesCache: List<Category>? = null
    private var seriesCategoriesCache: List<Category>? = null
    private val liveCache = HashMap<String, List<LiveChannel>>()
    private val movieCache = HashMap<String, List<Movie>>()
    private val seriesCache = HashMap<String, List<SeriesItem>>()

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
        clearCaches()
        if (remember) session.save(normalized) else session.clear()
        return info
    }

    fun logout() {
        credentials = null
        account = null
        clearCaches()
        session.clear()
    }

    private fun clearCaches() {
        liveCategoriesCache = null
        movieCategoriesCache = null
        seriesCategoriesCache = null
        liveCache.clear()
        movieCache.clear()
        seriesCache.clear()
    }

    // ------------------------------------------------------------------ en vivo

    suspend fun liveCategories(): List<Category> {
        liveCategoriesCache?.let { return it }
        return withContext(Dispatchers.IO) { api.categories(creds(), "get_live_categories") }
            .also { liveCategoriesCache = it }
    }

    suspend fun liveChannels(categoryId: String): List<LiveChannel> =
        liveCache[categoryId]
            ?: withContext(Dispatchers.IO) { api.liveStreams(creds(), categoryId) }
                .also { liveCache[categoryId] = it }

    suspend fun shortEpg(streamId: String): List<EpgEntry> =
        withContext(Dispatchers.IO) { api.shortEpg(creds(), streamId) }

    fun liveUrl(channelId: String): String = api.liveUrl(creds(), channelId, liveFormat)

    // ---------------------------------------------------------------- películas

    suspend fun movieCategories(): List<Category> {
        movieCategoriesCache?.let { return it }
        return withContext(Dispatchers.IO) { api.categories(creds(), "get_vod_categories") }
            .also { movieCategoriesCache = it }
    }

    suspend fun movies(categoryId: String): List<Movie> =
        movieCache[categoryId]
            ?: withContext(Dispatchers.IO) { api.vodStreams(creds(), categoryId) }
                .also { movieCache[categoryId] = it }

    suspend fun movieInfo(movieId: String): MovieInfo =
        withContext(Dispatchers.IO) { api.vodInfo(creds(), movieId) }

    fun movieUrl(movie: Movie): String = api.movieUrl(creds(), movie.id, movie.extension)

    // ------------------------------------------------------------------- series

    suspend fun seriesCategories(): List<Category> {
        seriesCategoriesCache?.let { return it }
        return withContext(Dispatchers.IO) { api.categories(creds(), "get_series_categories") }
            .also { seriesCategoriesCache = it }
    }

    suspend fun series(categoryId: String): List<SeriesItem> =
        seriesCache[categoryId]
            ?: withContext(Dispatchers.IO) { api.seriesList(creds(), categoryId) }
                .also { seriesCache[categoryId] = it }

    suspend fun seriesDetail(seriesId: String): SeriesDetail =
        withContext(Dispatchers.IO) { api.seriesInfo(creds(), seriesId) }

    fun episodeUrl(episode: Episode): String = api.episodeUrl(creds(), episode.id, episode.extension)
}
