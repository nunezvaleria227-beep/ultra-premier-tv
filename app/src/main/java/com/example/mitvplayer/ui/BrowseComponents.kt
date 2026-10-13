package com.example.mitvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.data.Item

/** Una fila de la barra lateral: nombre en mayúsculas y cantidad a la derecha. */
@Composable
fun SidebarRow(
    row: CategoryRow,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> AccentColor.copy(alpha = 0.55f)
        selected -> RowSelectedColor
        else -> Color.Transparent
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.name.uppercase(),
            color = TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.count.toString(),
            color = TextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** Buscador de categorías y la lista de categorías con sus cantidades. */
@Composable
fun <T : Item> CategorySidebar(
    state: BrowseState<T>,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    firstRowRequester: FocusRequester? = null,
    onSelected: () -> Unit = {}
) {
    Column(modifier.background(SidebarColor.copy(alpha = 0.80f))) {
        header()
        SearchField(
            value = state.categoryQuery,
            onValueChange = { state.categoryQuery = it },
            placeholder = "Buscar en categorías",
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        LazyColumn(
            state = state.categoryListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(state.rows) { index, row ->
                SidebarRow(
                    row = row,
                    selected = state.selectedId == row.id,
                    onClick = {
                        state.select(row.id)
                        onSelected()
                    },
                    modifier = if (index == 0 && firstRowRequester != null) {
                        Modifier.focusRequester(firstRowRequester)
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/** Opciones de orden y de actualización para el menú de tres puntos de una sección. */
fun <T : Item> sectionMenu(
    state: BrowseState<T>,
    modes: List<SortMode>,
    extra: List<MenuEntry> = emptyList()
): List<MenuEntry> {
    val entries = ArrayList<MenuEntry>()
    modes.forEach { mode ->
        entries += MenuEntry(label = "Ordenar: ${mode.label}", checked = state.sort == mode) { state.sort = mode }
    }
    entries += MenuEntry(label = "Actualizar lista") { state.refresh() }
    entries.addAll(extra)
    return entries
}

/** Mensaje de carga / error / vacío común a las pantallas de una sección. */
@Composable
fun <T : Item> SectionStatus(
    state: BrowseState<T>,
    emptyText: String,
    content: @Composable () -> Unit
) {
    val error = state.error
    when {
        state.catalog == null && error != null -> ErrorWithRetry(message = error, onRetry = { state.retry() })
        state.catalog == null -> CenterLoading(
            text = "Cargando la lista completa…\nLa primera vez puede tardar un poco."
        )
        state.items.isEmpty() -> CenterMessage(emptyText)
        else -> content()
    }
}

@Composable
fun FullSize(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) { content() }
}
