package com.example.mitvplayer.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mitvplayer.Config
import com.example.mitvplayer.MiTvApp
import com.example.mitvplayer.PlayItem
import com.example.mitvplayer.PlayerActivity
import com.example.mitvplayer.data.AccountInfo
import com.example.mitvplayer.data.Credentials
import com.example.mitvplayer.data.Episode
import com.example.mitvplayer.data.LiveChannel
import com.example.mitvplayer.data.Movie
import com.example.mitvplayer.data.MovieInfo
import com.example.mitvplayer.data.SeriesDetail
import com.example.mitvplayer.data.SeriesItem
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class MovieDetails(val movie: Movie) : Screen
    data class SeriesDetails(val series: SeriesItem) : Screen
}

enum class Tab(val label: String) {
    LIVE("En vivo"),
    MOVIES("Películas"),
    SERIES("Series"),
    ACCOUNT("Cuenta")
}

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as MiTvApp).repository

    // -------------------------------------------------------------- navegación

    /** Pila de pantallas. El primer elemento es Login o Home; lo demás son detalles encima de Home. */
    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Login))
        private set

    var tab by mutableStateOf(Tab.LIVE)

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

    // --------------------------------------------------------------- contenido

    val live = BrowseState<LiveChannel>(
        scope = viewModelScope,
        fetchCategories = { repo.liveCategories() },
        fetchItems = { categoryId -> repo.liveChannels(categoryId) }
    )
    val movies = BrowseState<Movie>(
        scope = viewModelScope,
        fetchCategories = { repo.movieCategories() },
        fetchItems = { categoryId -> repo.movies(categoryId) }
    )
    val series = BrowseState<SeriesItem>(
        scope = viewModelScope,
        fetchCategories = { repo.seriesCategories() },
        fetchItems = { categoryId -> repo.series(categoryId) }
    )

    init {
        val saved = repo.savedCredentials()
        if (saved != null) {
            server = saved.server
            username = saved.username
            password = saved.password
            viewModelScope.launch { doLogin(saved, remember = true) }
        }
    }

    fun activeBrowse(): BrowseState<*>? = when (tab) {
        Tab.LIVE -> live
        Tab.MOVIES -> movies
        Tab.SERIES -> series
        Tab.ACCOUNT -> null
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
            tab = Tab.LIVE
            stack = listOf(Screen.Home)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loginState = LoginUiState(error = e.userMessage())
        }
    }

    fun logout() {
        repo.logout()
        resetBrowsing()
        password = ""
        loginState = LoginUiState()
        stack = listOf(Screen.Login)
    }

    private fun resetBrowsing() {
        live.reset()
        movies.reset()
        series.reset()
    }

    // --------------------------------------------------------------- navegación

    fun openMovie(movie: Movie) {
        movies.restoring = true
        stack = stack + Screen.MovieDetails(movie)
    }

    fun openSeries(item: SeriesItem) {
        series.restoring = true
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

    // ------------------------------------------------------------ reproducción

    fun playLive(context: Context, channels: List<LiveChannel>, index: Int) {
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

    fun playMovie(context: Context, movie: Movie) {
        val item = PlayItem(
            title = movie.name,
            subtitle = null,
            url = repo.movieUrl(movie),
            isLive = false
        )
        PlayerActivity.start(context, listOf(item), 0)
    }

    /** Reproduce el episodio elegido y, al terminar, sigue con los siguientes (también de otras temporadas). */
    fun playEpisode(context: Context, show: SeriesItem, detail: SeriesDetail, episode: Episode) {
        val all = detail.seasons.values.flatten()
        val start = all.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        val playItems = all.map { e ->
            PlayItem(
                title = show.name,
                subtitle = "T${e.season} · E${e.number} — ${e.title}",
                url = repo.episodeUrl(e),
                isLive = false
            )
        }
        PlayerActivity.start(context, playItems, start)
    }
}
