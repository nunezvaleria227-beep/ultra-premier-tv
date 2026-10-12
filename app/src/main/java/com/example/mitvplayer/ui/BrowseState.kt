package com.example.mitvplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.example.mitvplayer.data.Catalog
import com.example.mitvplayer.data.Category
import com.example.mitvplayer.data.IdList
import com.example.mitvplayer.data.Item
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.searchKey
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Filas especiales que van antes de las categorías reales. */
const val ROW_ALL = "__all"
const val ROW_FAV = "__fav"
const val ROW_RECENT = "__recent"
const val ROW_ADDED = "__added"

private const val ADDED_LIMIT = 30

class CategoryRow(val id: String, val name: String, val count: Int)

enum class SortMode(val label: String) {
    DEFAULT("Predeterminado"),
    AZ("A – Z"),
    ZA("Z – A"),
    NEWEST("Más recientes"),
    RATING("Mejor calificación")
}

/**
 * Estado de una sección con categorías a un lado y contenido al otro (En vivo, Películas, Series, Radio).
 *
 * Vive en el ViewModel, así que al abrir un detalle y volver, la categoría elegida,
 * el scroll y el último elemento enfocado se conservan.
 */
class BrowseState<T : Item>(
    private val kind: Kind,
    private val scope: CoroutineScope,
    private val fetch: suspend (force: Boolean) -> Catalog<T>,
    private val favorites: () -> IdList?,
    private val recents: () -> IdList?,
    private val categoryFilter: ((Category) -> Boolean)? = null
) {
    var catalog by mutableStateOf<Catalog<T>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Milisegundos desde epoch de la última descarga (0 si aún no hay). */
    var updatedAt by mutableStateOf(0L)
        private set

    var selectedId by mutableStateOf(ROW_ALL)
        private set
    var categoryQuery by mutableStateOf("")
    var sort by mutableStateOf(SortMode.DEFAULT)
    var sidebarOpen by mutableStateOf(true)

    val categoryListState = LazyListState()
    val itemsListState = LazyListState()
    val gridState = LazyGridState()

    /** Para devolver el foco al elemento que se abrió cuando se regresa de un detalle. */
    val restoreRequester = FocusRequester()
    var lastFocusedIndex: Int = 0
    var restoring by mutableStateOf(false)

    private var generation = 0

    // ------------------------------------------------------------- lo que se muestra

    /** Todo lo que entra en esta sección (con el filtro de categorías aplicado). */
    private val pool: List<T> by derivedStateOf {
        val cat = catalog
        val filter = categoryFilter
        when {
            cat == null -> emptyList()
            filter == null -> cat.items
            else -> {
                val ids = cat.categories.filter(filter).map { it.id }.toSet()
                cat.items.filter { it.categoryId in ids }
            }
        }
    }

    /** Barra lateral: TODO, FAVORITOS, recientes y las categorías con su cantidad. */
    val rows: List<CategoryRow> by derivedStateOf {
        val cat = catalog
        if (cat == null) emptyList() else buildRows(cat)
    }

    /** Contenido de la fila elegida, ya ordenado. */
    val items: List<T> by derivedStateOf {
        val cat = catalog
        if (cat == null) emptyList() else sorted(itemsFor(selectedId, cat))
    }

    val selectedName: String
        get() = when (selectedId) {
            ROW_ALL -> "TODO"
            ROW_FAV -> "FAVORITOS"
            ROW_RECENT -> if (kind == Kind.LIVE) "HISTORIAL DE CANALES" else "RECIENTEMENTE MIRADA"
            ROW_ADDED -> "RECIENTEMENTE AÑADIDO"
            else -> (catalog?.categories?.firstOrNull { it.id == selectedId }?.name ?: "TODO").trim().uppercase()
        }

    /** Elementos de una fila cualquiera, sin ordenar (para las pantallas de guía y Catch up). */
    fun itemsFor(id: String): List<T> {
        val cat = catalog ?: return emptyList()
        return itemsFor(id, cat)
    }

    private fun itemsFor(id: String, cat: Catalog<T>): List<T> = when (id) {
        ROW_ALL -> pool
        ROW_FAV -> favoriteItems(cat)
        ROW_RECENT -> recentItems(cat)
        ROW_ADDED -> pool.sortedByDescending { it.added }.take(ADDED_LIMIT)
        else -> pool.filter { it.categoryId == id }
    }

    private fun favoriteItems(cat: Catalog<T>): List<T> =
        favorites()?.ids.orEmpty().mapNotNull { cat.byId[it] }

    private fun recentItems(cat: Catalog<T>): List<T> =
        recents()?.ids.orEmpty().mapNotNull { cat.byId[it] }

    private fun sorted(list: List<T>): List<T> = when (sort) {
        SortMode.DEFAULT -> list
        SortMode.AZ -> list.sortedBy { it.key }
        SortMode.ZA -> list.sortedByDescending { it.key }
        SortMode.NEWEST -> list.sortedByDescending { it.added }
        SortMode.RATING -> list.sortedByDescending { it.rating10 }
    }

    private fun buildRows(cat: Catalog<T>): List<CategoryRow> {
        val rows = ArrayList<CategoryRow>()
        rows += CategoryRow(ROW_ALL, "TODO", pool.size)
        rows += CategoryRow(ROW_FAV, "FAVORITOS", favoriteItems(cat).size)
        rows += CategoryRow(
            ROW_RECENT,
            if (kind == Kind.LIVE) "HISTORIAL DE CANALES" else "RECIENTEMENTE MIRADA",
            recentItems(cat).size
        )
        if (kind != Kind.LIVE) {
            rows += CategoryRow(ROW_ADDED, "RECIENTEMENTE AÑADIDO", minOf(ADDED_LIMIT, pool.size))
        }
        val filter = categoryFilter
        for (category in cat.categories) {
            if (filter != null && !filter(category)) continue
            val count = cat.counts[category.id] ?: 0
            if (count > 0) rows += CategoryRow(category.id, category.name.trim(), count)
        }
        val query = searchKey(categoryQuery).trim()
        return if (query.isEmpty()) rows else rows.filter { searchKey(it.name).contains(query) }
    }

    // ------------------------------------------------------------------- acciones

    fun select(id: String) {
        selectedId = id
        lastFocusedIndex = 0
        itemsListState.requestScrollToItem(0)
        gridState.requestScrollToItem(0)
    }

    /** Descarga la sección si todavía no está (no hace nada si ya está o se está descargando). */
    suspend fun loadNow(force: Boolean = false) {
        if (loading) return
        if (catalog != null && !force) return
        val id = generation
        loading = true
        error = null
        try {
            val loaded = fetch(force)
            if (id != generation) return
            catalog = loaded
            updatedAt = loaded.updatedAt
            val known = selectedId.startsWith("__") || loaded.counts.containsKey(selectedId)
            if (!known) selectedId = ROW_ALL
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            if (id == generation) error = "No hay memoria suficiente para cargar esta lista."
        } catch (e: Exception) {
            if (id == generation) error = e.userMessage()
        } finally {
            if (id == generation) loading = false
        }
    }

    fun ensureLoaded() {
        if (catalog == null && !loading) scope.launch { loadNow() }
    }

    fun refresh() {
        if (!loading) scope.launch { loadNow(force = true) }
    }

    fun retry() {
        if (!loading) scope.launch { loadNow(force = catalog == null) }
    }

    fun reset() {
        generation++
        catalog = null
        loading = false
        error = null
        updatedAt = 0L
        selectedId = ROW_ALL
        categoryQuery = ""
        sort = SortMode.DEFAULT
        lastFocusedIndex = 0
        restoring = false
    }
}
