package com.example.mitvplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.example.mitvplayer.data.Category
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado de una pestaña con categorías a la izquierda y contenido a la derecha
 * (En vivo, Películas o Series).
 *
 * Vive en el ViewModel, así que al abrir un detalle y volver, la categoría elegida,
 * la posición del scroll y el último elemento enfocado se conservan.
 */
class BrowseState<T>(
    private val scope: CoroutineScope,
    private val fetchCategories: suspend () -> List<Category>,
    private val fetchItems: suspend (categoryId: String) -> List<T>
) {
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var selected by mutableStateOf<Category?>(null)
        private set
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val categoryListState = LazyListState()
    val itemsListState = LazyListState()
    val gridState = LazyGridState()

    /** Para devolver el foco al elemento que se abrió cuando se regresa de un detalle. */
    val restoreRequester = FocusRequester()
    var lastFocusedIndex: Int = 0
    var restoring by mutableStateOf(false)

    private var requestId = 0
    private var started = false

    fun ensureLoaded() {
        if (!started) reload()
    }

    fun reload() {
        val id = ++requestId
        started = true
        loading = true
        error = null
        scope.launch {
            try {
                val loaded = fetchCategories()
                if (id != requestId) return@launch
                categories = loaded
                val first = loaded.firstOrNull()
                if (first != null) select(first) else loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == requestId) {
                    error = e.userMessage()
                    loading = false
                }
            }
        }
    }

    fun select(category: Category) {
        val id = ++requestId
        selected = category
        items = emptyList()
        loading = true
        error = null
        lastFocusedIndex = 0
        itemsListState.requestScrollToItem(0)
        gridState.requestScrollToItem(0)
        scope.launch {
            try {
                val loaded = fetchItems(category.id)
                if (id == requestId) items = loaded
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == requestId) error = e.userMessage()
            } finally {
                if (id == requestId) loading = false
            }
        }
    }

    fun retry() {
        val current = selected
        if (categories.isEmpty() || current == null) reload() else select(current)
    }

    fun reset() {
        requestId++
        started = false
        categories = emptyList()
        selected = null
        items = emptyList()
        loading = false
        error = null
        lastFocusedIndex = 0
        restoring = false
    }
}
