package com.example.mitvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.mitvplayer.ui.AccountScreen
import com.example.mitvplayer.ui.AppViewModel
import com.example.mitvplayer.ui.ContinueScreen
import com.example.mitvplayer.ui.CatchUpChannelsScreen
import com.example.mitvplayer.ui.CatchUpProgramsScreen
import com.example.mitvplayer.ui.CatchUpScreen
import com.example.mitvplayer.ui.EpgCategoriesScreen
import com.example.mitvplayer.ui.EpgChannelsScreen
import com.example.mitvplayer.ui.EpgGridScreen
import com.example.mitvplayer.ui.HomeScreen
import com.example.mitvplayer.ui.LiveScreen
import com.example.mitvplayer.ui.LoginScreen
import com.example.mitvplayer.ui.MiTvTheme
import com.example.mitvplayer.ui.MovieDetailScreen
import com.example.mitvplayer.ui.MultiScreen
import com.example.mitvplayer.ui.NotificationsScreen
import com.example.mitvplayer.ui.PosterGridScreen
import com.example.mitvplayer.ui.RecordingsScreen
import com.example.mitvplayer.ui.Screen
import com.example.mitvplayer.ui.SearchScope
import com.example.mitvplayer.ui.SearchScreen
import com.example.mitvplayer.ui.SeriesDetailScreen
import com.example.mitvplayer.ui.SettingsScreen
import com.example.mitvplayer.data.Kind

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    // Atrás en cualquier pantalla abierta regresa a la anterior; en el inicio o el login cierra la app.
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    when (val top = vm.stack.last()) {
        Screen.Login -> LoginScreen(vm)
        Screen.Home -> HomeScreen(vm)
        Screen.Live -> LiveScreen(vm, vm.live, title = "Live")
        Screen.Radio -> LiveScreen(vm, vm.radio, title = "Radio", radio = true)
        Screen.Movies -> PosterGridScreen(
            vm = vm,
            state = vm.movies,
            kind = Kind.MOVIES,
            searchScope = SearchScope.MOVIES,
            noun = "películas",
            art = R.drawable.art_movies,
            posterOf = { it.poster },
            onOpen = { vm.openMovie(it) }
        )
        Screen.SeriesList -> PosterGridScreen(
            vm = vm,
            state = vm.series,
            kind = Kind.SERIES,
            searchScope = SearchScope.SERIES,
            noun = "series",
            art = R.drawable.art_series,
            posterOf = { it.cover },
            onOpen = { vm.openSeries(it) }
        )
        is Screen.MovieDetails -> MovieDetailScreen(vm, top.movie)
        is Screen.SeriesDetails -> SeriesDetailScreen(vm, top.series)
        Screen.Epg -> EpgCategoriesScreen(vm)
        is Screen.EpgChannels -> EpgChannelsScreen(vm, top.categoryId, top.title)
        is Screen.EpgGrid -> EpgGridScreen(vm, top.categoryId, top.title)
        Screen.CatchUp -> CatchUpScreen(vm)
        is Screen.CatchUpChannels -> CatchUpChannelsScreen(vm, top.categoryId, top.title)
        is Screen.CatchUpPrograms -> CatchUpProgramsScreen(vm, top.channel)
        Screen.Multi -> MultiScreen(vm)
        is Screen.Search -> SearchScreen(vm, top.scope)
        Screen.Account -> AccountScreen(vm)
        Screen.Notifications -> NotificationsScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Recordings -> RecordingsScreen(vm)
        Screen.Continue -> ContinueScreen(vm)
    }
}
