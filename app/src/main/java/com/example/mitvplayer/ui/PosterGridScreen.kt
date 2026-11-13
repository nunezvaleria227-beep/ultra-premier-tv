package com.example.mitvplayer.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.data.Item
import com.example.mitvplayer.data.Kind

/**
 * Películas o Series con el diseño nuevo: encabezado con logo y reloj, las categorías como
 * pastillas arriba, la cuadrícula de pósters y la barra de menú flotante abajo.
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
    val chipFR = remember { FocusRequester() }
    var chipsFocused by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

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
        if (state.catalog != null) {
            withFrameNanos { }
            com.example.mitvplayer.QaProbe.ready(noun, "${state.items.size} elementos, ${state.rows.size} filas")
        }
        if (state.catalog != null && !state.restoring) {
            withFrameNanos { }
            withFrameNanos { }
            chipFR.tryRequestFocus()
        }
    }

    // "Atrás" desde los pósters o la barra sube primero a las categorías; desde ahí vuelve al inicio.
    BackHandler(enabled = !chipsFocused && state.catalog != null) {
        try {
            chipFR.requestFocus()
        } catch (e: Exception) {
            vm.back()
        }
    }

    val emptyText = when (state.selectedId) {
        ROW_FAV -> "Aún no tienes favoritos.\nMantén pulsado un título, o pulsa la tecla Menú del control, para añadirlo."
        ROW_RECENT -> "Todavía no has visto nada."
        else -> "No hay $noun en esta categoría"
    }
    val isSeries = kind == Kind.SERIES

    AppBackground(art = art, dim = 0.6f) {
        Column(Modifier.fillMaxSize()) {
            UltraSectionHeader(
                vm = vm,
                active = if (isSeries) UltraTab.SERIES else UltraTab.MOVIES,
                onActive = { chipFR.tryRequestFocus() },
                title = if (isSeries) "Series" else "Películas",
                icon = { t -> GlyphIcon(if (isSeries) Glyph.SERIES else Glyph.CLAPPER, Modifier.size(17.dp), t) }
            ) {
                SlantChip(
                    text = "Buscar",
                    selected = false,
                    onClick = { vm.open(Screen.Search(searchScope)) }
                )
                SlantChip(
                    text = "Orden: ${state.sort.label}",
                    selected = false,
                    onClick = { sortOpen = true }
                )
            }
            Spacer(Modifier.height(4.dp))
            if (state.catalog != null) {
                CategoryChips(
                    rows = state.rows,
                    selectedId = state.selectedId,
                    onSelect = { state.select(it) },
                    selectedRequester = chipFR,
                    listState = state.categoryListState,
                    modifier = Modifier.onFocusChanged { chipsFocused = it.hasFocus }
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SectionStatus(state, emptyText) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        state = state.gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = 8.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(state.items) { index, item ->
                            UltraPosterCard(
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
            Spacer(Modifier.height(12.dp))
        }
    }

    if (sortOpen) {
        val modes = listOf(SortMode.DEFAULT, SortMode.NEWEST, SortMode.AZ, SortMode.ZA, SortMode.RATING)
        OptionsDialog(
            title = "Ordenar",
            options = modes.map { mode ->
                Opt(if (state.sort == mode) "✓  ${mode.label}" else mode.label) { state.sort = mode }
            } + Opt("Actualizar lista") { state.refresh() },
            onDismiss = { sortOpen = false }
        )
    }
}
