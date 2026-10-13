sub init()
    m.title = m.top.findNode("title")
    m.filterLabel = m.top.findNode("filterLabel")
    m.cats = m.top.findNode("cats")
    m.chans = m.top.findNode("chans")
    m.grid = m.top.findNode("grid")
    m.info = m.top.findNode("infoPanel")
    m.chanLogo = m.top.findNode("chanLogo")
    m.chanName = m.top.findNode("chanName")
    m.nowLabel = m.top.findNode("nowLabel")
    m.nextLabel = m.top.findNode("nextLabel")
    m.status = m.top.findNode("status")
    m.hint = m.top.findNode("hint")
    m.epgTimer = m.top.findNode("epgTimer")

    m.tasks = []
    m.allItems = invalid
    m.shown = invalid
    m.filterText = ""
    m.catId = ""
    m.catTitle = ""
    m.pane = "cats"
    m.epgId = ""
    m.pendingLogo = ""

    m.cats.observeField("itemSelected", "onCatSelected")
    m.chans.observeField("itemSelected", "onChanSelected")
    m.chans.observeField("itemFocused", "onChanFocused")
    m.grid.observeField("itemSelected", "onGridSelected")
    m.epgTimer.observeField("fire", "onEpgTimer")
end sub

sub onKind()
    kind = m.top.kind
    if kind = "live"
        m.title.text = "TV en directo"
    else if kind = "movie"
        m.title.text = "Películas"
    else
        m.title.text = "Series"
    end if
    m.hint.text = "OK: abrir      Play/Pausa: favorito      Asterisco (*): buscar en la lista"
    showStatus("Cargando categorías…")
    runApi({ action: "categories", type: kind }, "onCategories")
end sub

sub focusScreen()
    focusPane(m.pane)
end sub

sub runApi(req as Object, callback as String)
    t = CreateObject("roSGNode", "ApiTask")
    t.request = req
    t.observeField("response", callback)
    m.tasks.Push(t)
    if m.tasks.Count() > 24 then m.tasks.Shift()
    t.control = "RUN"
end sub

sub showStatus(text as String)
    m.status.text = text
    m.status.visible = (text <> "")
end sub

sub focusPane(p as String)
    m.pane = p
    if p = "items" and m.shown <> invalid
        if m.top.kind = "live"
            m.chans.setFocus(true)
        else
            m.grid.setFocus(true)
        end if
    else
        m.pane = "cats"
        m.cats.setFocus(true)
    end if
end sub

sub hideItems()
    m.chans.visible = false
    m.grid.visible = false
    m.info.visible = false
end sub

' ----------------------------------------------------------------- categorías

sub onCategories(event as Object)
    resp = event.getData()
    if resp.ok <> true
        showStatus(resp.error + "  Pulsa Atrás para volver.")
        return
    end if
    root = resp.content
    fav = CreateObject("roSGNode", "ContentNode")
    fav.id = "__fav__"
    fav.title = "Favoritos"
    root.InsertChild(fav, 0)
    m.cats.content = root
    showStatus("Elige una categoría con OK")
    focusPane("cats")
end sub

sub onCatSelected(event as Object)
    index = event.getData()
    node = m.cats.content.getChild(index)
    if node = invalid then return
    m.catId = node.id
    m.catTitle = node.title
    m.filterText = ""
    m.filterLabel.text = ""
    m.allItems = invalid
    m.shown = invalid
    hideItems()
    if m.catId = "__fav__"
        buildFavorites()
    else
        cached = cacheGet(m.top.kind + "_" + m.catId, 600)
        if cached <> invalid and cached.getChildCount() > 0
            m.allItems = cached
            showItems(cached)
        else
            showStatus("Cargando " + node.title + "…")
            runApi({ action: "items", type: m.top.kind, categoryId: m.catId, tag: m.catId }, "onItems")
        end if
    end if
end sub

sub buildFavorites()
    kind = m.top.kind
    root = CreateObject("roSGNode", "ContentNode")
    for each f in loadList(favKey(kind))
        node = root.CreateChild("ContentNode")
        title = "* " + toS(f.name)
        small = toS(f.icon)
        if kind <> "live" then small = thumbUrl(small)
        node.setFields({ id: toS(f.id), title: title, HDPosterUrl: small, FHDPosterUrl: toS(f.icon), ShortDescriptionLine1: title, StreamFormat: toS(f.ext) })
    end for
    if root.getChildCount() = 0
        m.allItems = invalid
        m.shown = invalid
        hideItems()
        showStatus("Todavía no tienes favoritos." + Chr(10) + "Entra a una categoría y pulsa Play/Pausa sobre un elemento.")
        focusPane("cats")
        return
    end if
    m.allItems = root
    showItems(root)
end sub

sub onItems(event as Object)
    resp = event.getData()
    if resp.tag <> m.catId then return
    if resp.ok <> true
        showStatus(resp.error)
        return
    end if
    if resp.content.getChildCount() = 0
        showStatus("Esta categoría está vacía.")
        return
    end if
    m.allItems = resp.content
    cachePut(m.top.kind + "_" + m.catId, resp.content)
    showItems(resp.content)
end sub

sub showItems(content as Object)
    m.shown = content
    showStatus("")
    if m.top.kind = "live"
        m.chans.content = content
        m.chans.visible = true
        m.info.visible = true
        m.grid.visible = false
    else
        m.grid.content = content
        m.grid.visible = true
        m.chans.visible = false
        m.info.visible = false
    end if
    n = content.getChildCount()
    label = m.catTitle + "  ·  " + n.ToStr() + " elementos"
    if m.filterText <> "" then label = m.catTitle + "  ·  filtro «" + m.filterText + "»  ·  " + n.ToStr() + " resultados"
    m.filterLabel.text = label
    focusPane("items")
end sub

' ----------------------------------------------------------------- abrir

sub onChanSelected(event as Object)
    index = event.getData()
    if m.shown = invalid then return
    total = m.shown.getChildCount()
    first = index - 150
    if first < 0 then first = 0
    last = first + 299
    if last > total - 1
        last = total - 1
        first = last - 299
        if first < 0 then first = 0
    end if
    items = []
    for i = first to last
        c = m.shown.getChild(i)
        items.Push({ id: c.id, name: rawName(c.title), icon: c.HDPosterUrl, ext: "" })
    end for
    m.top.event = { type: "playLive", items: items, index: index - first }
end sub

sub onGridSelected(event as Object)
    index = event.getData()
    if m.shown = invalid then return
    c = m.shown.getChild(index)
    if c = invalid then return
    big = c.FHDPosterUrl
    if big = "" then big = c.HDPosterUrl
    item = { id: c.id, name: rawName(c.title), icon: big, ext: c.StreamFormat }
    if m.top.kind = "movie"
        m.top.event = { type: "openMovie", item: item }
    else
        m.top.event = { type: "openSeries", item: item }
    end if
end sub

' ----------------------------------------------------------------- guía del canal enfocado

sub onChanFocused(event as Object)
    index = event.getData()
    if m.shown = invalid or index < 0 then return
    c = m.shown.getChild(index)
    if c = invalid then return
    m.chanName.text = rawName(c.title)
    m.chanLogo.uri = ""
    m.pendingLogo = c.HDPosterUrl
    m.nowLabel.text = ""
    m.nextLabel.text = ""
    m.epgId = c.id
    m.epgTimer.control = "stop"
    m.epgTimer.control = "start"
end sub

sub onEpgTimer()
    if m.epgId = "" then return
    if m.pendingLogo <> invalid then m.chanLogo.uri = m.pendingLogo
    m.nowLabel.text = "Cargando la guía…"
    runApi({ action: "epg", id: m.epgId, tag: m.epgId }, "onEpg")
end sub

sub onEpg(event as Object)
    resp = event.getData()
    if resp.tag <> m.epgId then return
    if resp.ok = true and resp.nowEntry <> invalid
        e = resp.nowEntry
        m.nowLabel.text = "Ahora (" + fmtHM(e.start) + " - " + fmtHM(e.stop) + "):" + Chr(10) + e.title
    else
        m.nowLabel.text = "En vivo"
    end if
    if resp.ok = true and resp.nextEntry <> invalid
        u = resp.nextEntry
        m.nextLabel.text = "Después (" + fmtHM(u.start) + "):" + Chr(10) + u.title
    else
        m.nextLabel.text = ""
    end if
end sub

' ----------------------------------------------------------------- favoritos

sub toggleFav()
    if m.shown = invalid then return
    kind = m.top.kind
    idx = -1
    if kind = "live"
        idx = m.chans.itemFocused
    else
        idx = m.grid.itemFocused
    end if
    if idx < 0 then return
    node = m.shown.getChild(idx)
    if node = invalid then return
    name = rawName(node.title)
    item = { id: node.id, name: name, icon: node.HDPosterUrl, ext: node.StreamFormat }
    isFav = toggleFavorite(kind, item)
    newTitle = name
    if isFav then newTitle = "* " + name
    node.title = newTitle
    node.ShortDescriptionLine1 = newTitle
    ' Si se está viendo una lista filtrada, la marca también se actualiza en la lista completa.
    if m.allItems <> invalid and not m.shown.isSameNode(m.allItems)
        n = m.allItems.getChildCount()
        for i = 0 to n - 1
            c = m.allItems.getChild(i)
            if c.id = node.id
                c.title = newTitle
                c.ShortDescriptionLine1 = newTitle
                exit for
            end if
        end for
    end if
    if m.catId = "__fav__" and not isFav
        cacheClear()
        buildFavorites()
    end if
end sub

' ----------------------------------------------------------------- filtro por texto

sub askFilter()
    dlg = CreateObject("roSGNode", "StandardKeyboardDialog")
    dlg.title = "Buscar en esta lista"
    dlg.text = m.filterText
    dlg.buttons = ["Buscar", "Quitar filtro", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onFilterDone")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onFilterDone()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    text = dlg.text
    dlg.close = true
    m.dialog = invalid
    if choice = 0
        applyFilter(text)
    else if choice = 1
        applyFilter("")
    else
        focusPane(m.pane)
    end if
end sub

sub applyFilter(text as String)
    if m.allItems = invalid then return
    m.filterText = text.Trim()
    if m.filterText = ""
        showItems(m.allItems)
        return
    end if
    words = queryWords(m.filterText)
    root = CreateObject("roSGNode", "ContentNode")
    n = m.allItems.getChildCount()
    for i = 0 to n - 1
        c = m.allItems.getChild(i)
        if matchesWords(rawName(c.title), words)
            node = root.CreateChild("ContentNode")
            node.setFields({ id: c.id, title: c.title, HDPosterUrl: c.HDPosterUrl, FHDPosterUrl: c.FHDPosterUrl, ShortDescriptionLine1: c.ShortDescriptionLine1, StreamFormat: c.StreamFormat })
            if root.getChildCount() >= 600 then exit for
        end if
    end for
    if root.getChildCount() = 0
        m.shown = invalid
        hideItems()
        m.filterLabel.text = m.catTitle + "  ·  filtro «" + m.filterText + "»"
        showStatus("No hay nada que coincida con «" + m.filterText + "»." + Chr(10) + "Pulsa * para cambiar o quitar el filtro.")
        focusPane("cats")
        return
    end if
    showItems(root)
end sub

' ----------------------------------------------------------------- control remoto

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "back"
        if m.pane = "items"
            focusPane("cats")
            return true
        end if
        return false
    end if
    if key = "left"
        if m.pane = "items"
            focusPane("cats")
            return true
        end if
        return false
    end if
    if key = "right"
        if m.pane = "cats" and m.shown <> invalid
            focusPane("items")
            return true
        end if
        return false
    end if
    if key = "play"
        if m.pane = "items" then toggleFav()
        return true
    end if
    if key = "options"
        if m.allItems <> invalid then askFilter()
        return true
    end if
    return false
end function
