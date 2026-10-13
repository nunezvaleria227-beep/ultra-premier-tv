package com.example.mitvplayer.ui

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mitvplayer.Config
import com.example.mitvplayer.MiTvApp
import com.example.mitvplayer.PlayItem
import com.example.mitvplayer.PlayerActivity
import com.example.mitvplayer.ResumeTag
import com.example.mitvplayer.RecordingManager
import com.example.mitvplayer.RecordingScheduler
import com.example.mitvplayer.RecordingService
import com.example.mitvplayer.ScheduledRecording
import com.example.mitvplayer.UpdateManager
import com.example.mitvplayer.data.AccountInfo
import com.example.mitvplayer.data.Credentials
import com.example.mitvplayer.data.EpgEntry
import com.example.mitvplayer.data.Episode
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.Library
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.MovieInfo
import com.example.mitvplayer.data.ParentalControl
import com.example.mitvplayer.data.ResumeEntry
import com.example.mitvplayer.data.SeriesDetail
import com.example.mitvplayer.data.SeriesItem
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class SearchScope(val label: String) {
    ALL("Todo"),
    LIVE("En vivo"),
    MOVIES("Películas"),
    SERIES("Series")
}

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data object Live : Screen
    data object Radio : Screen
    data object Movies : Screen
    data object SeriesList : Screen
    data class MovieDetails(val movie: Movie) : Screen
    data class SeriesDetails(val series: SeriesItem) : Screen
    data object Epg : Screen
    data class EpgChannels(val categoryId: String, val title: String) : Screen
    data object CatchUp : Screen
    data class CatchUpChannels(val categoryId: String, val title: String) : Screen
    data class CatchUpPrograms(val channel: LiveChannel) : Screen
    data object Multi : Screen
    data class Search(val scope: SearchScope) : Screen
    data object Account : Screen
    data object Notifications : Screen
    data object Settings : Screen
    data object Recordings : Screen
    data object Continue : Screen
    data class EpgGrid(val categoryId: String, val title: String) : Screen
}

/** Estado de la búsqueda de versiones nuevas. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val version: Int) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
}

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as MiTvApp).repository

    /** Control parental: PIN y bloqueo de las categorías para adultos. */
    val parental = ParentalControl(application)

    // -------------------------------------------------------------- navegación

    /** Pila de pantallas. El primer elemento es Login o Home; lo demás se abre encima. */
    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Login))
        private set

    /** Último botón enfocado del inicio, para volver a él al regresar. */
    var homeFocus: Int = 0

    // ------------------------------------------------------- actualizaciones de la app

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** true = ya se le avisó y dijo "más tarde" (no se vuelve a mostrar hasta que abra la app otra vez). */
    var updateDismissed by mutableStateOf(false)

    private var updateFile: java.io.File? = null

    /** Busca una versión nueva. [manual] = true muestra mensajes aunque no haya nada nuevo. */
    fun checkForUpdate(manual: Boolean = false) {
        if (update is UpdateState.Downloading) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val latest = UpdateManager.latestVersion()
            val current = UpdateManager.currentVersion(app)
            when {
                latest == null -> if (manual) {
                    Toast.makeText(app, "No se pudo buscar actualizaciones. Revisa tu conexión.", Toast.LENGTH_LONG).show()
                }
                latest > current -> {
                    update = UpdateState.Available(latest)
                    updateDismissed = false
                }
                else -> if (manual) {
                    Toast.makeText(app, "Ya tienes la última versión (${UpdateManager.currentName(app)}).", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Descarga la versión nueva y abre el instalador de Android. */
    fun startUpdate() {
        val app = getApplication<Application>()
        val version = (update as? UpdateState.Available)?.version ?: return
        viewModelScope.launch {
            try {
                val cached = updateFile
                val file = if (cached != null && cached.exists()) {
                    cached
                } else {
                    update = UpdateState.Downloading(0)
                    UpdateManager.download(app) { percent -> update = UpdateState.Downloading(percent) }
                        .also { updateFile = it }
                }
                val opened = UpdateManager.install(app, file)
                update = UpdateState.Available(version)
                updateDismissed = true
                if (!opened) {
                    Toast.makeText(
                        app,
                        "Activa \"Permitir desde esta fuente\" para Ultra Premier TV y vuelve a pulsar Actualizar en Ajustes.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update = UpdateState.Available(version)
                updateDismissed = true
                Toast.makeText(app, "No se pudo descargar la actualización. Inténtalo de nuevo en Ajustes.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------------- login

    var server by mutableStateOf(Config.PORTAL_URL)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberMe by mutableStateOf(true)
    var loginState by mutableStateOf(LoginUiState())
        private set

    var liveFormat by mutableStateOf("m3u8")
        private set

    val account: AccountInfo? get() = repo.account
    val library: Library? get() = repo.library

    // --------------------------------------------------------------- contenido

    val live = BrowseState<LiveChannel>(
        kind = Kind.LIVE,
        scope = viewModelScope,
        fetch = { force -> repo.liveCatalog(force) },
        favorites = { repo.library?.favLive },
        recents = { repo.library?.recentLive },
        blocked = { parental.blocks(it.name) }
    )

    /** La radio son las categorías de En vivo cuyo nombre dice "radio". */
    val radio = BrowseState<LiveChannel>(
        kind = Kind.LIVE,
        scope = viewModelScope,
        fetch = { _ -> repo.liveCatalog(false) },
        favorites = { repo.library?.favLive },
        recents = { repo.library?.recentLive },
        categoryFilter = { it.name.contains("radio", ignoreCase = true) },
        blocked = { parental.blocks(it.name) }
    )

    val movies = BrowseState<Movie>(
        kind = Kind.MOVIES,
        scope = viewModelScope,
        fetch = { force -> repo.movieCatalog(force) },
        favorites = { repo.library?.favMovies },
        recents = { repo.library?.recentMovies },
        blocked = { parental.blocks(it.name) }
    )

    val series = BrowseState<SeriesItem>(
        kind = Kind.SERIES,
        scope = viewModelScope,
        fetch = { force -> repo.seriesCatalog(force) },
        favorites = { repo.library?.favSeries },
        recents = { repo.library?.recentSeries },
        blocked = { parental.blocks(it.name) }
    )

    // ----------------------------------------------------------- pantalla múltiple

    /** Índice del diseño elegido en [MultiLayouts]; null = aún no se elige. */
    var multiLayout by mutableStateOf<Int?>(null)
        private set
    val multiChannels = mutableStateListOf<LiveChannel?>()
    var multiActive by mutableStateOf(0)

    init {
        val saved = repo.savedCredentials()
        if (saved != null) {
            server = saved.server
            username = saved.username
            password = saved.password
            viewModelScope.launch { doLogin(saved, remember = true) }
        }
    }

    // ------------------------------------------------------------------- login

    fun submitLogin() {
        if (loginState.loading) return
        val serverValue = if (Config.PORTAL_URL.isNotBlank()) Config.PORTAL_URL else server
        if (serverValue.isBlank()) {
            loginState = LoginUiState(error = "Escribe la URL del servidor")
            return
        }
        if (username.isBlank() || password.isEmpty()) {
            loginState = LoginUiState(error = "Escribe tu usuario y contraseña")
            return
        }
        val credentials = Credentials(serverValue.trim(), username.trim(), password)
        viewModelScope.launch { doLogin(credentials, rememberMe) }
    }

    private suspend fun doLogin(credentials: Credentials, remember: Boolean) {
        loginState = LoginUiState(loading = true)
        try {
            repo.login(credentials, remember)
            resetBrowsing()
            liveFormat = repo.liveFormat
            loginState = LoginUiState()
            homeFocus = 0
            stack = listOf(Screen.Home)
            preload()
            checkForUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loginState = LoginUiState(error = e.userMessage())
        }
    }

    /** Descarga las tres listas en segundo plano, una tras otra, para que ya estén al abrir cada sección. */
    private fun preload() {
        viewModelScope.launch {
            live.loadNow()
            movies.loadNow()
            series.loadNow()
        }
    }

    /** Vuelve a esconder el contenido para adultos. */
    fun lockParental() {
        parental.lock()
        live.ensureSelectionVisible()
        radio.ensureSelectionVisible()
        movies.ensureSelectionVisible()
        series.ensureSelectionVisible()
    }

    fun logout() {
        lockParental()
        repo.logout()
        resetBrowsing()
        password = ""
        loginState = LoginUiState()
        stack = listOf(Screen.Login)
    }

    private fun resetBrowsing() {
        live.reset()
        radio.reset()
        movies.reset()
        series.reset()
        multiLayout = null
        multiChannels.clear()
        multiActive = 0
    }

    // --------------------------------------------------------------- navegación

    fun open(screen: Screen) {
        stack = stack + screen
    }

    fun openMovie(movie: Movie, restoreFocus: Boolean = true) {
        movies.restoring = restoreFocus
        repo.library?.recentMovies?.touch(movie.id)
        stack = stack + Screen.MovieDetails(movie)
    }

    fun openSeries(item: SeriesItem, restoreFocus: Boolean = true) {
        series.restoring = restoreFocus
        repo.library?.recentSeries?.touch(item.id)
        stack = stack + Screen.SeriesDetails(item)
    }

    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    // ------------------------------------------------------------------ ajustes

    fun toggleLiveFormat() {
        val next = if (liveFormat == "m3u8") "ts" else "m3u8"
        repo.liveFormat = next
        liveFormat = next
    }

    /** Vuelve a descargar las tres listas. */
    fun refreshAll() {
        viewModelScope.launch {
            live.loadNow(force = true)
            radio.loadNow(force = true)
            movies.loadNow(force = true)
            series.loadNow(force = true)
        }
    }

    fun clearFavorites() {
        repo.library?.clearFavorites()
    }

    fun clearHistory() {
        repo.library?.clearHistory()
    }

    // ---------------------------------------------------------------- favoritos

    fun isFavorite(kind: Kind, id: String): Boolean = repo.library?.favorites(kind)?.has(id) ?: false

    fun toggleFavorite(kind: Kind, id: String) {
        repo.library?.favorites(kind)?.toggle(id)
    }

    // ----------------------------------------------------------------- detalles

    suspend fun loadMovieInfo(movie: Movie): MovieInfo? =
        try {
            repo.movieInfo(movie.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    suspend fun loadSeriesDetail(item: SeriesItem): SeriesDetail = repo.seriesDetail(item.id)

    // ---------------------------------------------------------------------- guía

    suspend fun epgFor(channelId: String): List<EpgEntry> =
        try {
            repo.epgNowNext(channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    suspend fun epgTable(channelId: String): List<EpgEntry> = repo.epgTable(channelId)

    /** Guía completa de un canal con caché (para la cuadrícula); si falla, devuelve vacío. */
    suspend fun epgTableFor(channelId: String): List<EpgEntry> =
        try {
            repo.epgTableCached(channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    // ------------------------------------------------------------ reproducción

    fun liveUrl(channel: LiveChannel): String = repo.liveUrl(channel.id)

    fun markWatched(channel: LiveChannel) {
        repo.library?.recentLive?.touch(channel.id)
    }

    fun playLive(context: Context, channels: List<LiveChannel>, index: Int) {
        channels.getOrNull(index)?.let { markWatched(it) }
        val playItems = channels.map { channel ->
            PlayItem(
                title = if (channel.number > 0) "${channel.number}  ${channel.name}" else channel.name,
                subtitle = null,
                url = repo.liveUrl(channel.id),
                isLive = true,
                streamId = channel.id
            )
        }
        PlayerActivity.start(context, playItems, index)
    }

    /** [fromStart] = true empieza desde el principio aunque haya un minuto guardado. */
    fun playMovie(context: Context, movie: Movie, fromStart: Boolean = false) {
        repo.library?.recentMovies?.touch(movie.id)
        val item = PlayItem(
            title = movie.name,
            subtitle = null,
            url = repo.movieUrl(movie),
            isLive = false,
            resume = ResumeTag("movie", movie.id, movie.extension, movie.poster, movie.categoryId),
            fromStart = fromStart
        )
        PlayerActivity.start(context, listOf(item), 0)
    }

    /** Minuto guardado de un episodio, si se dejó a medias. */
    fun savedEpisode(episodeId: String): ResumeEntry? = repo.library?.progress?.find("episode", episodeId)

    /** Abre el tráiler en YouTube (o en el navegador). */
    fun openTrailer(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "No hay ninguna app para abrir el tráiler. Instala YouTube o un navegador.", Toast.LENGTH_LONG).show()
        }
    }

    /** Minuto guardado de una película, si se dejó a medias. */
    fun savedMovie(movie: Movie): ResumeEntry? = repo.library?.progress?.find("movie", movie.id)

    /** Continúa algo que se dejó a medias (desde "Continuar viendo"). */
    fun playResume(context: Context, entry: ResumeEntry) {
        val url = if (entry.type == "movie") repo.movieUrlById(entry.id, entry.ext) else repo.episodeUrlById(entry.id, entry.ext)
        val item = PlayItem(
            title = entry.title,
            subtitle = entry.subtitle,
            url = url,
            isLive = false,
            resume = ResumeTag(entry.type, entry.id, entry.ext, entry.image, entry.category)
        )
        PlayerActivity.start(context, listOf(item), 0)
    }

    fun removeResume(entry: ResumeEntry) {
        repo.library?.progress?.remove(entry.type, entry.id)
    }

    /** "Continuar viendo" sin lo que el control parental esconde. */
    val resumeEntries: List<ResumeEntry>
        get() = repo.library?.progress?.entries.orEmpty().filter { entry ->
            val state = if (entry.type == "movie") movies else series
            !state.isBlocked(entry.category)
        }

    /** Reproduce el episodio elegido y, al terminar, sigue con los siguientes (también de otras temporadas). */
    fun playEpisode(context: Context, show: SeriesItem, detail: SeriesDetail, episode: Episode) {
        repo.library?.recentSeries?.touch(show.id)
        val all = detail.seasons.values.flatten()
        val start = all.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        val playItems = all.map { e ->
            PlayItem(
                title = show.name,
                subtitle = "T${e.season} · E${e.number} — ${e.title}",
                url = repo.episodeUrl(e),
                isLive = false,
                resume = ResumeTag("episode", e.id, e.extension, e.image ?: show.cover, show.categoryId)
            )
        }
        PlayerActivity.start(context, playItems, start)
    }

    /** Reproduce un programa ya emitido (Catch up). */
    fun playArchive(context: Context, channel: LiveChannel, entry: EpgEntry) {
        val item = PlayItem(
            title = channel.name,
            subtitle = "${formatHm(entry.start)} – ${formatHm(entry.end)}  ${entry.title}",
            url = repo.archiveUrl(channel, entry),
            isLive = false
        )
        PlayerActivity.start(context, listOf(item), 0)
    }

    fun playFile(context: Context, title: String, fileUrl: String) {
        PlayerActivity.start(context, listOf(PlayItem(title, null, fileUrl, isLive = false)), 0)
    }

    // ---------------------------------------------------------------- grabación

    /** Graba el canal (máximo 2 horas) y lo guarda en la memoria del dispositivo. [minutes] = null graba el máximo. */
    fun startRecording(context: Context, channel: LiveChannel, minutes: Int? = null) {
        if (RecordingManager.current != null) {
            Toast.makeText(context, "Ya hay una grabación en curso. Puedes detenerla en Grabaciones.", Toast.LENGTH_LONG).show()
            return
        }
        val activity = context.findActivity()
        if (activity != null) {
            val needed = ArrayList<String>()
            // Android 13+: sin este permiso la grabación funciona, pero no se ve el aviso con el botón "Detener".
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.POST_NOTIFICATIONS)
            // Android 9 o menos: hace falta para guardar en la carpeta Películas.
            if (Build.VERSION.SDK_INT < 29 &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (needed.isNotEmpty()) activity.requestPermissions(needed.toTypedArray(), 77)
        }
        RecordingService.start(context, repo.recordUrl(channel.id), channel.name, minutes)
        Toast.makeText(
            context,
            "Grabando ${channel.name}. Máximo 2 horas; se guarda sola en Películas/Ultra Premier TV.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------------------------------------------------- grabaciones programadas

    /** Grabaciones programadas desde la guía (observable). */
    val scheduledRecordings: List<ScheduledRecording>
        get() = RecordingScheduler.store(getApplication()).items

    fun isScheduled(channelId: String, startSec: Long): Boolean =
        RecordingScheduler.store(getApplication()).has(channelId, startSec)

    /** Programa que el canal grabe este programa cuando empiece. */
    fun scheduleRecording(context: Context, channel: LiveChannel, entry: EpgEntry) {
        val store = RecordingScheduler.store(context)
        RecordingScheduler.schedule(
            context,
            ScheduledRecording(
                id = store.idFor(channel.id, entry.start),
                channelId = channel.id,
                channelName = channel.name,
                title = entry.title.ifBlank { channel.name },
                startSec = entry.start,
                endSec = entry.end
            )
        )
        // Para que la grabación funcione con la app cerrada hace falta que la sesión esté guardada y el permiso de avisos.
        val activity = context.findActivity()
        if (activity != null && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
        }
        val tail = if (rememberMe) "" else " Activa \"Mantener la sesión iniciada\" al entrar para que también funcione con la app cerrada."
        Toast.makeText(
            context,
            "Listo: se grabará ${entry.title.ifBlank { channel.name }} a las ${formatHm(entry.start)}.$tail",
            Toast.LENGTH_LONG
        ).show()
    }

    fun cancelScheduled(context: Context, id: String) {
        RecordingScheduler.cancel(context, id)
    }

    // ----------------------------------------------------------- pantalla múltiple

    fun chooseMultiLayout(index: Int) {
        val cells = MultiLayouts.getOrNull(index) ?: return
        multiLayout = index
        multiActive = 0
        // Los canales ya elegidos se conservan mientras quepan en el diseño nuevo.
        val previous = multiChannels.toList()
        multiChannels.clear()
        repeat(cells.size) { multiChannels.add(previous.getOrNull(it)) }
    }
}
