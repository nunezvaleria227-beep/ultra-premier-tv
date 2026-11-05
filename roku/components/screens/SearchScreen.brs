' Buscar con el teclado en pantalla. Busca por nombre en canales, películas y series (sin acentos ni mayúsculas).
' Zonas del foco: "tabs" (barra de arriba), "kb" (teclado), "btn" (botón Buscar), "filters" y "grid".

sub init()
    m.kb = m.top.findNode("kb")
    m.searchBtn = m.top.findNode("searchBtn")
    m.filters = m.top.findNode("filters")
    m.grid = m.top.findNode("grid")
    m.status = m.top.findNode("status")
    m.topBar = m.top.findNode("topBar")
    m.query = ""
    m.results = []
    m.shown = []
    m.filterKinds = ["all", "movie", "series", "live"]
    m.filterIndex = 0
    m.task = invalid
    m.running = false
    m.pane = "kb"

    btn = CreateObject("roSGNode", "ContentNode")
    b = btn.CreateChild("ContentNode")
    b.title = "Buscar"
    b.HDPosterUrl = "pkg:/images/ic_search.png"
    m.searchBtn.content = btn
    paintFilters()
    m.status.text = "Escribe el nombre con el teclado y elige Buscar."

    m.kb.observeField("text", "onText")
    m.searchBtn.observeField("itemSelected", "startSearch")
    m.filters.observeField("itemSelected", "onFilter")
    m.grid.observeField("itemSelected", "onResultSelected")
    m.topBar.observeField("selected", "onTopBar")
end sub

sub focusScreen()
    applyFocus()
end sub

sub applyFocus()
    p = m.pane
    if p = "grid" and m.shown.Count() > 0
        m.grid.setFocus(true)
    else if p = "filters"
        m.filters.setFocus(true)
    else if p = "btn"
        m.searchBtn.setFocus(true)
    else if p = "tabs"
        m.topBar.setFocus(true)
    else
        m.pane = "kb"
        m.kb.setFocus(true)
    end if
end sub

sub onClose()
    cancelSearch()
end sub

sub cancelSearch()
    if m.task <> invalid then m.task.cancel = true
    m.running = false
end sub

sub onText()
    m.query = m.kb.text
end sub

sub onTopBar()
    target = m.topBar.selected
    if target = "search"
        m.pane = "kb"
        applyFocus()
        return
    end if
    m.top.event = { type: "nav", target: target }
end sub

' ----------------------------------------------------------------- búsqueda

sub startSearch()
    q = m.query.Trim()
    if Len(q) < 2
        m.status.text = "Escribe al menos 2 letras."
        return
    end if
    cancelSearch()
    m.results = []
    showResults()
    m.status.text = "Buscando…"
    t = CreateObject("roSGNode", "SearchTask")
    t.query = q
    t.scope = "all"
    t.observeField("results", "onResults")
    t.observeField("progress", "onProgress")
    t.observeField("finished", "onFinished")
    m.task = t
    m.running = true
    t.control = "RUN"
end sub

sub onProgress(event as Object)
    if m.running and m.shown.Count() = 0 then m.status.text = event.getData()
end sub

sub onFinished()
    m.running = false
    if m.shown.Count() = 0
        if m.results.Count() = 0
            m.status.text = "Sin resultados para «" + m.query.Trim() + "»."
        else
            m.status.text = "No hay resultados en este filtro."
        end if
    end if
end sub

sub onResults(event as Object)
    arr = event.getData()
    if arr = invalid then return
    m.results = arr
    showResults()
end sub

function countKind(kind as String) as Integer
    n = 0
    for each e in m.results
        if e.kind = kind then n = n + 1
    end for
    return n
end function

sub paintFilters()
    root = CreateObject("roSGNode", "ContentNode")
    labels = ["Todo (" + m.results.Count().ToStr() + ")", "Películas (" + countKind("movie").ToStr() + ")", "Series (" + countKind("series").ToStr() + ")", "Canales (" + countKind("live").ToStr() + ")"]
    for each l in labels
        n = root.CreateChild("ContentNode")
        n.title = l
    end for
    m.filters.content = root
    m.filters.selectedIndex = m.filterIndex
end sub

sub onFilter(event as Object)
    m.filterIndex = event.getData()
    showResults()
end sub

sub showResults()
    paintFilters()
    kind = m.filterKinds[m.filterIndex]
    m.shown = []
    root = CreateObject("roSGNode", "ContentNode")
    for each e in m.results
        if kind = "all" or e.kind = kind
            m.shown.Push(e)
            node = root.CreateChild("ContentNode")
            node.title = toS(e.name)
            if e.kind <> "live" then node.Rating = ratingBadge(e.rating)
            if e.kind = "live"
                node.HDPosterUrl = toS(e.icon)
                node.ShortDescriptionLine2 = "live"
            else
                node.HDPosterUrl = thumbUrl(toS(e.icon))
            end if
        end if
    end for
    m.grid.content = root
    m.grid.visible = (m.shown.Count() > 0)
    if m.shown.Count() > 0 then m.status.text = ""
end sub

sub onResultSelected(event as Object)
    index = event.getData()
    if index < 0 or index >= m.shown.Count() then return
    e = m.shown[index]
    item = { id: e.id, name: e.name, icon: e.icon, ext: e.ext }
    if e.kind = "live"
        m.top.event = { type: "playLive", items: [item], index: 0 }
    else if e.kind = "movie"
        m.top.event = { type: "openMovie", item: item }
    else
        m.top.event = { type: "openSeries", item: item }
    end if
end sub

' ----------------------------------------------------------------- control remoto

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    p = m.pane
    if key = "back"
        if p <> "kb"
            m.pane = "kb"
            applyFocus()
            return true
        end if
        return false
    end if
    if key = "play"
        startSearch()
        return true
    end if
    target = ""
    if p = "tabs"
        if key = "down" then target = "kb"
    else if p = "kb"
        if key = "right" then target = "filters"
        if key = "down" then target = "btn"
        if key = "up" then target = "tabs"
    else if p = "btn"
        if key = "up" then target = "kb"
        if key = "right" then target = "filters"
    else if p = "filters"
        if key = "down" and m.shown.Count() > 0 then target = "grid"
        if key = "left" then target = "kb"
        if key = "up" then target = "tabs"
    else if p = "grid"
        if key = "up" then target = "filters"
        if key = "left" then target = "kb"
    end if
    if target <> ""
        m.pane = target
        applyFocus()
        return true
    end if
    return false
end function
