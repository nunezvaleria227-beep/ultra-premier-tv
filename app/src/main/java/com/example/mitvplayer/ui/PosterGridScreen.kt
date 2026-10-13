package com.example.mitvplayer.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.example.mitvplayer.data.Item
import com.example.mitvplayer.data.Kind

/**
 * Películas o Series: categorías con buscador a la izquierda y cuadrícula de pósters a la derecha.
 * La barra lateral se puede ocultar con la X para ver más pósters.
 */
@Composable
fun <T : Item> PosterGridScreen(
    vm: AppViewModel,
    state: BrowseState<T>,
    kind: Kind,
    searchScope: SearchScope,
    noun: String,
    @DrawableRes art: Int,
    posterOf: (T) -> String?,
    onOpen: (T) -> Unit
) {
    val firstRow = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        state.ensureLoaded()
        if (state.restoring) {
            // Vuelve el foco al póster que se abrió.
            withFrameNanos { }
            withFrameNanos { }
            try {
                state.restoreRequester.requestFocus()
            } catch (e: Exception) {
                // Si ya no está en pantalla se ignora.
            }
            state.restoring = false
        }
    }
    LaunchedEffect(state.catalog) {
        if (state.catalog != null && !state.restoring) {
            withFrameNanos { }
            withFrameNanos { }
            if (state.sidebarOpen) firstRow.tryRequestFocus()
        }
    }

    val emptyText = when (state.selectedId) {
        ROW_FAV -> "Aún no tienes favoritos.\nMantén pulsado un título, o pulsa la tecla Menú del control, para añadirlo."
        ROW_RECENT -> "Todavía no has visto nada."
        else -> "No hay $noun en esta categoría"
    }

    AppBackground(art = art, dim = 0.5f) {
        Row(Modifier.fillMaxSize()) {
            if (state.sidebarOpen) {
                CategorySidebar(
                    state = state,
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight(),
                    header = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppLogo(Modifier.height(34.dp))
                            Spacer(Modifier.weight(1f))
                            IconAction(onClick = { state.sidebarOpen = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Ocultar categorías", tint = TextPrimary)
                            }
                        }
                    },
                    firstRowRequester = firstRow
                )
            }
            Column(
                Modifier
                    .weight(if (state.sidebarOpen) 0.70f else 1f)
                    .fillMaxHeight()
            ) {
                TopBar(
                    title = state.selectedName,
                    centerTitle = true,
                    showLogo = !state.sidebarOpen,
                    onBack = { vm.back() },
                    onSearch = { vm.open(Screen.Search(searchScope)) },
                    menu = sectionMenu(
                        state,
                        listOf(SortMode.DEFAULT, SortMode.AZ, SortMode.ZA, SortMode.NEWEST, SortMode.RATING)
                    ),
                    leading = {
                        if (!state.sidebarOpen) {
                            IconAction(onClick = { state.sidebarOpen = true }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Mostrar categorías", tint = TextPrimary)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SectionStatus(state, emptyText) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(104.dp),
                            state = state.gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.items) { index, item ->
                                PosterCard(
                                    title = item.name,
                                    imageUrl = posterOf(item),
                                    rating10 = item.rating10,
                                    favorite = vm.isFavorite(kind, item.id),
                                    onClick = { onOpen(item) },
                                    onLongClick = { vm.toggleFavorite(kind, item.id) },
                                    modifier = Modifier
                                        .focusIf(state.restoring && index == state.lastFocusedIndex, state.restoreRequester)
                                        .onFocusChanged { if (it.isFocused) state.lastFocusedIndex = index }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
