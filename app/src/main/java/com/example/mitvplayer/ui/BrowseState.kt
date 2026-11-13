package com.example.mitvplayer.ui

import android.os.SystemClock
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import com.example.mitvplayer.QaProbe
import com.example.mitvplayer.data.Catalog
import com.example.mitvplayer.data.Category
import com.example.mitvplayer.data.IdList
import com.example.mitvplayer.data.Item
import com.example.mitvplayer.data.Kind
import com.example.mitvplayer.data.Repository
import com.example.mitvplayer.data.searchKey
import com.example.mitvplayer.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filas especiales que van antes de las categorías reales. */
const val ROW_ALL = "__all"
const val ROW_FAV = "__fav"
const val ROW_RECENT = "__recent"
const val ROW_ADDED = "__added"

private const val ADDED_LIMIT = 30
private const val HOME_LIMIT = 30

/** Listas más largas que esto se ordenan en segundo plano (la pantalla nunca se congela). */
private const val SORT_INLINE_MAX = 2500

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
 *
 * Todo lo pesado (separar por categorías, contar, lo más nuevo) ya viene calculado en el [Catalog], así que
 * aquí cambiar de categoría es instantáneo; ordenar listas grandes se hace en segundo plano.
 */
class BrowseState<T : Item>(
    private val kind: Kind,
    private val scope: CoroutineScope,
    private val fetch: suspend (force: Boolean, onProgress: (String) -> Unit) -> Catalog<T>,
    private val favorites: () -> IdList?,
    private val recents: () -> IdList?,
    private val categoryFilter: ((Category) -> Boolean)? = null,
    /** Verdadero mientras hay que esconder lo de adultos (control parental). Puede leer estado de Compose. */
    private val hideAdult: (() -> Boolean)? = null
) {
    var catalog by mutableStateOf<Catalog<T>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Se está actualizando en segundo plano; lo que se ve sigue funcionando mientras tanto. */
    var refreshing by mutableStateOf(false)
        private set

    /** Cómo va la descarga ("Descargando… 12.5 MB"), para mostrarlo mientras carga. */
    var progress by mutableStateOf<String?>(null)
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

    /** Identificadores de las categorías escondidas por el control parental. */
    private val blockedIds: Set<String> by derivedStateOf {
        val cat = catalog
        if (cat == null || hideAdult?.invoke() != true) emptySet() else cat.adultCategoryIds
    }

    /** Todo lo de la sección sin lo escondido (para el buscador, la guía y los conteos). */
    val visibleItems: List<T> by derivedStateOf {
        val cat = catalog
        when {
            cat == null -> emptyList()
            blockedIds.isEmpty() -> cat.items
            else -> cat.itemsWithoutAdult
        }
    }

    /** Categorías de la sección sin las escondidas. */
    val visibleCategories: List<Category> by derivedStateOf {
        val cat = catalog
        val hide = blockedIds
        when {
            cat == null -> emptyList()
            hide.isEmpty() -> cat.categories
            else -> cat.categories.filter { it.id !in hide }
        }
    }

    /** Las categorías que entran en esta sección (la Radio solo usa algunas) y no están escondidas. */
    private val allowedCategories: List<Category> by derivedStateOf {
        val filter = categoryFilter
        if (filter == null) visibleCategories else visibleCategories.filter(filter)
    }

    private val allowedIds: Set<String> by derivedStateOf { allowedCategories.mapTo(HashSet()) { it.id } }

    /** Todo lo que entra en esta sección. */
    private val pool: List<T> by derivedStateOf {
        val cat = catalog
        when {
            cat == null -> emptyList()
            categoryFilter == null -> visibleItems
            else -> {
                // Pocas categorías (la radio): se juntan sus listas, que ya vienen separadas.
                val seen = HashSet<String>()
                val out = ArrayList<T>()
                for (category in allowedCategories) {
                    for (item in cat.byCategory[category.id].orEmpty()) if (seen.add(item.id)) out.add(item)
                }
                out
            }
        }
    }

    /** Los favoritos de la sección (para "Mi lista" del inicio). */
    val favoriteList: List<T> by derivedStateOf {
        val cat = catalog
        if (cat == null) emptyList() else favoriteItems(cat)
    }

    /** Lo último que se vio de la sección (para el canal en vivo del inicio). */
    val recentList: List<T> by derivedStateOf {
        val cat = catalog
        if (cat == null) emptyList() else recentItems(cat)
    }

    /** Barra lateral: TODO, FAVORITOS, recientes y las categorías con su cantidad. */
    val rows: List<CategoryRow> by derivedStateOf {
        val cat = catalog
        if (cat == null) emptyList() else buildRows(cat)
    }

    /** Contenido de la fila elegida, sin ordenar. */
    private val baseItems: List<T> by derivedStateOf {
        val cat = catalog
        if (cat == null) emptyList() else itemsFor(selectedId, cat)
    }

    private var sortedFor by mutableStateOf<Pair<List<T>, SortMode>?>(null)
    private var sortedList by mutableStateOf<List<T>>(emptyList())

    /** Contenido de la fila elegida, ya ordenado. */
    val items: List<T> by derivedStateOf {
        val base = baseItems
        val mode = sort
        when {
            mode == SortMode.DEFAULT || base.size < 2 -> base
            base.size <= SORT_INLINE_MAX -> sorted(base, mode)
            else -> {
                val done = sortedFor
                if (done != null && done.first === base && done.second == mode) sortedList else base
            }
        }
    }

    /** Para el inicio: lo más nuevo, sin lo escondido. */
    val newestVisible: List<T> by derivedStateOf {
        val cat = catalog ?: return@derivedStateOf emptyList()
        val hide = blockedIds
        cat.newest.asSequence().filter { it.categoryId !in hide }.take(HOME_LIMIT).toList()
    }

    /** Para el inicio: lo mejor calificado (7 o más), sin lo escondido. */
    val topRatedVisible: List<T> by derivedStateOf {
        val cat = catalog ?: return@derivedStateOf emptyList()
        val hide = blockedIds
        cat.topRated.asSequence().filter { it.rating10 >= 7f && it.categoryId !in hide }.take(HOME_LIMIT).toList()
    }

    init {
        // Las listas grandes se ordenan fuera de la pantalla; mientras tanto se ve la lista sin ordenar.
        scope.launch {
            snapshotFlow { baseItems to sort }.collectLatest { (base, mode) ->
                if (mode == SortMode.DEFAULT || base.size <= SORT_INLINE_MAX) return@collectLatest
                val result = withContext(Dispatchers.Default) { sorted(base, mode) }
                sortedList = result
                sortedFor = base to mode
            }
        }
    }

    /** ¿Esta categoría está escondida por el control parental? */
    fun isBlocked(categoryId: String): Boolean = categoryId.isNotEmpty() && categoryId in blockedIds

    /** Si la categoría elegida quedó escondida (se bloqueó de nuevo), vuelve a TODO. */
    fun ensureSelectionVisible() {
        if (selectedId.startsWith("__")) return
        if (selectedId in blockedIds) select(ROW_ALL)
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
        ROW_ADDED -> {
            val hide = blockedIds
            cat.newest.asSequence().filter { it.categoryId !in hide }.take(ADDED_LIMIT).toList()
        }
        else -> if (id in allowedIds) cat.byCategory[id].orEmpty() else emptyList()
    }

    private fun favoriteItems(cat: Catalog<T>): List<T> {
        val hide = blockedIds
        return favorites()?.ids.orEmpty().mapNotNull { cat.byId[it] }.filter { it.categoryId !in hide }
    }

    private fun recentItems(cat: Catalog<T>): List<T> {
        val hide = blockedIds
        return recents()?.ids.orEmpty().mapNotNull { cat.byId[it] }.filter { it.categoryId !in hide }
    }

    private fun sorted(list: List<T>, mode: SortMode): List<T> = when (mode) {
        SortMode.DEFAULT -> list
        SortMode.AZ -> list.sortedBy { it.key }
        SortMode.ZA -> list.sortedByDescending { it.key }
        SortMode.NEWEST -> list.sortedByDescending { it.added }
        SortMode.RATING -> list.sortedByDescending { it.rating10 }
    }

    private fun buildRows(cat: Catalog<T>): List<CategoryRow> {
        val rows = ArrayList<CategoryRow>(allowedCategories.size + 4)
        rows += CategoryRow(ROW_ALL, "TODO", pool.size)
        rows += CategoryRow(ROW_FAV, "FAVORITOS", favoriteItems(cat).size)
        rows += CategoryRow(
            ROW_RECENT,
            if (kind == Kind.LIVE) "HISTORIAL DE CANALES" else "RECIENTEMENTE MIRADA",
            recentItems(cat).size
        )
        if (kind != Kind.LIVE) {
            rows += CategoryRow(ROW_ADDED, "RECIENTEMENTE AÑADIDO", minOf(ADDED_LIMIT, cat.newest.size))
        }
        for (category in allowedCategories) {
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

    /**
     * Carga la sección si todavía no está (no hace nada si ya está o se está cargando).
     * Con [force] y algo ya en pantalla, actualiza en segundo plano sin quitar lo que se ve.
     */
    suspend fun loadNow(force: Boolean = false) {
        if (loading || refreshing) return
        if (catalog != null && !force) return
        val id = generation
        val background = force && catalog != null
        if (background) refreshing = true else loading = true
        error = null
        progress = null
        var refreshAfter = false
        try {
            val started = SystemClock.uptimeMillis()
            val loaded = fetch(force) { text -> if (id == generation && !background) progress = text }
            if (id != generation) return
            catalog = loaded
            updatedAt = loaded.updatedAt
            val known = selectedId.startsWith("__") || loaded.counts.containsKey(selectedId)
            if (!known) selectedId = ROW_ALL
            QaProbe.log(
                "CATALOGO ${kind.name} ${loaded.items.size} elementos, ${loaded.categories.size} categorías en " +
                    "${SystemClock.uptimeMillis() - started} ms${if (background) " (actualización)" else ""}"
            )
            // La copia guardada es de hace muchas horas: ya se ve, y se actualiza sola en segundo plano.
            refreshAfter = !force && System.currentTimeMillis() - loaded.updatedAt > Repository.CACHE_MAX_AGE_MS
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            if (id == generation && !background) error = "No hay memoria suficiente para cargar esta lista."
        } catch (e: Exception) {
            if (id == generation && !background) error = e.userMessage()
        } finally {
            if (id == generation) {
                loading = false
                refreshing = false
                progress = null
            }
        }
        if (refreshAfter && id == generation) refresh()
    }

    fun ensureLoaded() {
        if (catalog == null && !loading) scope.launch { loadNow() }
    }

    fun refresh() {
        if (!loading && !refreshing) scope.launch { loadNow(force = true) }
    }

    fun retry() {
        if (!loading) scope.launch { loadNow(force = catalog == null) }
    }

    fun reset() {
        generation++
        catalog = null
        loading = false
        refreshing = false
        progress = null
        error = null
        updatedAt = 0L
        selectedId = ROW_ALL
        categoryQuery = ""
        sort = SortMode.DEFAULT
        sortedFor = null
        sortedList = emptyList()
        lastFocusedIndex = 0
        restoring = false
    }
}
