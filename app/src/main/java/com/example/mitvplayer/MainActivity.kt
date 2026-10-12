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
import com.example.mitvplayer.ui.AppViewModel
import com.example.mitvplayer.ui.HomeScreen
import com.example.mitvplayer.ui.LoginScreen
import com.example.mitvplayer.ui.MiTvTheme
import com.example.mitvplayer.ui.MovieDetailScreen
import com.example.mitvplayer.ui.Screen
import com.example.mitvplayer.ui.SeriesDetailScreen

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
    // Atrás en un detalle regresa a Home; en Home o Login cierra la app.
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }

    when (val top = vm.stack.last()) {
        Screen.Login -> LoginScreen(vm)
        Screen.Home -> HomeScreen(vm)
        is Screen.MovieDetails -> MovieDetailScreen(vm, top.movie)
        is Screen.SeriesDetails -> SeriesDetailScreen(vm, top.series)
    }
}
