' Inicio de Ultra Premier TV ("Mosaico"): lo destacado en grande con sus botones, el canal en vivo,
' "Continuar viendo" y las filas (Estrenos, Series nuevas, Mejor calificadas y Mi lista).
' Al recorrer las filas, lo destacado cambia a lo que se está viendo (como en Fire TV).
' Zonas del foco: "tabs" (barra de arriba), "hero" (botones de lo destacado), "live", "cont" y "rows".

sub init()
    m.hero = m.top.findNode("hero")
    m.heroArt = m.top.findNode("heroArt")
    m.heroBorder = m.top.findNode("heroBorder")
    m.heroRing = m.top.findNode("heroRing")
    m.heroBadge = m.top.findNode("heroBadge")
    m.heroBadgeBg = m.top.findNode("heroBadgeBg")
    m.heroBadgeText = m.top.findNode("heroBadgeText")
    m.heroTitle = m.top.findNode("heroTitle")
    m.heroMeta = m.top.findNode("heroMeta")
    m.heroPlot = m.top.findNode("heroPlot")
    m.heroStatus = m.top.findNode("heroStatus")
    m.heroBtns = m.top.findNode("heroBtns")

    m.liveTile = m.top.findNode("liveTile")
    m.liveIconBig = m.top.findNode("liveIconBig")
    m.liveBorder = m.top.findNode("liveBorder")
    m.liveBadgeBg = m.top.findNode("liveBadgeBg")
    m.liveBadgeText = m.top.findNode("liveBadgeText")
    m.liveLogo = m.top.findNode("liveLogo")
    m.liveName = m.top.findNode("liveName")
    m.liveProg = m.top.findNode("liveProg")
    m.liveBarBg = m.top.findNode("liveBarBg")
    m.liveBar = m.top.findNode("liveBar")
    m.liveRing = m.top.findNode("liveRing")

    m.contTile = m.top.findNode("contTile")
    m.contArt = m.top.findNode("contArt")
    m.contBorder = m.top.findNode("contBorder")
    m.contKicker = m.top.findNode("contKicker")
    m.contTitle = m.top.findNode("contTitle")
    m.contSub = m.top.findNode("contSub")
    m.contBarBg = m.top.findNode("contBarBg")
    m.contBar = m.top.findNode("contBar")
    m.contRing = m.top.findNode("contRing")

    m.rowBar = m.top.findNode("rowBar")
    m.rowTitle = m.top.findNode("rowTitle")
    m.rowSub = m.top.findNode("rowSub")
    m.rowHint = m.top.findNode("rowHint")
    m.rows = m.top.findNode("rows")
    m.note = m.top.findNode("note")
    m.topBar = m.top.findNode("topBar")
    m.infoTimer = m.top.findNode("infoTimer")
    m.noteTimer = m.top.findNode("noteTimer")

    m.zone = "hero"
    m.server = invalid
    m.loading = false
    m.loadError = invalid
    m.homeTask = invalid
    m.shelves = []
    m.rowSig = ""
    m.rowIndex = 0
    m.picked = invalid
    m.shown = invalid
    m.heroSig = ""
    m.btnIds = []
    m.infoCache = {}
    m.infoTasks = {}
    m.pendingInfo = invalid
    m.liveItems = []
    m.epgId = ""
    m.epgAt = 0
    m.epgTask = invalid
    m.contMode = ""
    m.contEntry = invalid

    m.heroBtns.observeField("itemSelected", "onHeroButton")
    m.rows.observeField("rowItemFocused", "onRowFocused")
    m.rows.observeField("rowItemSelected", "onRowSelected")
    m.topBar.observeField("selected", "onTopBar")
    m.infoTimer.observeField("fire", "onInfoTimer")
    m.noteTimer.observeField("fire", "onNoteTimer")
    buildAll()
    loadHome(false)
end sub

sub focusScreen()
    ' Al volver de otra pantalla se actualizan "Continuar viendo", "Mi lista" y el último canal visto.
    buildAll()
    applyFocus()
end sub

sub buildAll()
    buildRows()
    showHero()
    showLive()
    showContinue()
end sub

' La barra de arriba de otra pantalla (o "Más") pidió "Mi lista": se baja a esa fila.
sub onGoShelf()
    key = m.top.goShelf
    if key = "" then return
    buildRows()
    for i = 0 to m.shelves.Count() - 1
        if m.shelves[i].key = key
            m.rows.jumpToRowItem = [i, 0]
            m.rowIndex = i
            setZone("rows")
            return
        end if
    end for
    if key = "mine" then showNote("Tu lista está vacía. En una película o serie elige «Mi lista» para guardarla aquí.")
    setZone("hero")
end sub

sub showNote(text as String)
    m.note.text = text
    m.note.visible = true
    m.noteTimer.control = "stop"
    m.noteTimer.control = "start"
end sub

sub onNoteTimer()
    m.note.text = ""
end sub

' ----------------------------------------------------------------- datos del servidor

sub loadHome(force as Boolean)
    if not force
        cached = cacheGet("home_rows", 900)
        if cached <> invalid
            onHomeData(cached.data)
            return
        end if
    end if
    m.loading = true
    m.loadError = invalid
    buildAll()
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "home", tag: "home" }
    t.observeField("response", "onHomeLoaded")
    m.homeTask = t
    t.control = "RUN"
end sub

sub onHomeLoaded(event as Object)
    resp = event.getData()
    m.homeTask = invalid
    m.loading = false
    if resp = invalid or resp.ok <> true
        msg = "No se pudo cargar el inicio. Revisa tu internet e inténtalo otra vez."
        if resp <> invalid and toS(resp.error) <> "" then msg = toS(resp.error)
        m.loadError = msg
        buildAll()
        return
    end if
    top = resp.top
    if top = invalid then top = []
    data = { movies: resp.movies, series: resp.series, live: resp.live, liveName: toS(resp.liveName), top: top }
    holder = CreateObject("roSGNode", "Node")
    holder.addFields({ data: data })
    cachePut("home_rows", holder)
    onHomeData(data)
end sub

sub onHomeData(data as Object)
    m.loading = false
    m.loadError = invalid
    m.server = data
    buildAll()
    if m.zone = "hero" then applyFocus()
end sub

' ----------------------------------------------------------------- filas

sub buildRows()
    shelves = []
    if m.server <> invalid
        if m.server.movies.Count() > 0 then shelves.Push({ key: "new", title: "Estrenos", extra: "lo más nuevo", items: m.server.movies })
        if m.server.series.Count() > 0 then shelves.Push({ key: "series", title: "Series nuevas", extra: "", items: m.server.series })
        top = m.server.top
        if top <> invalid and top.Count() > 0 then shelves.Push({ key: "top", title: "Mejor calificadas", extra: "", items: top })
    end if
    mine = []
    for each f in loadList(favKey("movie"))
        mine.Push({ kind: "movie", id: toS(f.id), name: toS(f.name), icon: toS(f.icon), ext: toS(f.ext) })
    end for
    for each f in loadList(favKey("series"))
        mine.Push({ kind: "series", id: toS(f.id), name: toS(f.name), icon: toS(f.icon), ext: "" })
    end for
    if mine.Count() > 0 then shelves.Push({ key: "mine", title: "Mi lista", extra: "", items: mine })
    m.shelves = shelves

    ' Solo se vuelve a armar si cambió algo, así no se pierde el lugar en las filas.
    sig = ""
    for each s in shelves
        sig = sig + s.key + ":" + s.items.Count().ToStr() + ":"
        if s.items.Count() > 0 then sig = sig + toS(s.items[0].id) + "-" + toS(s.items[s.items.Count() - 1].id) + ","
    end for
    if sig <> m.rowSig
        m.rowSig = sig
        keepRow = m.rowIndex
        root = CreateObject("roSGNode", "ContentNode")
        for each s in shelves
            row = root.CreateChild("ContentNode")
            row.title = s.title
            for each e in s.items
                node = row.CreateChild("ContentNode")
                node.title = toS(e.name)
                node.HDPosterUrl = thumbUrl(toS(e.icon))
                node.Rating = ratingBadge(e.rating)
            end for
        end for
        m.rows.content = root
        if keepRow >= shelves.Count() then keepRow = shelves.Count() - 1
        if keepRow < 0 then keepRow = 0
        m.rowIndex = keepRow
        if keepRow > 0 then m.rows.jumpToRowItem = [keepRow, 0]
    end if
    m.rows.visible = (shelves.Count() > 0)
    paintRowTitle()
end sub

sub paintRowTitle()
    n = m.shelves.Count()
    if n = 0
        m.rowBar.visible = false
        m.rowTitle.text = ""
        m.rowSub.text = ""
        m.rowHint.text = ""
        return
    end if
    ri = m.rowIndex
    if ri < 0 or ri >= n then ri = 0
    s = m.shelves[ri]
    m.rowBar.visible = true
    m.rowTitle.text = upper(s.title)
    w = Int(m.rowTitle.boundingRect().width)
    m.rowSub.translation = [62 + w + 10, 453]
    if s.extra <> "" then m.rowSub.text = "·  " + s.extra else m.rowSub.text = ""
    hint = ""
    if ri > 0 then hint = "▲ " + m.shelves[ri - 1].title
    if ri < n - 1
        if hint <> "" then hint = hint + "      "
        hint = hint + "▼ " + m.shelves[ri + 1].title
    end if
    m.rowHint.text = hint
end sub

function shelfItem(r as Integer, c as Integer) as Dynamic
    if r < 0 or r >= m.shelves.Count() then return invalid
    items = m.shelves[r].items
    if c < 0 or c >= items.Count() then return invalid
    return items[c]
end function

sub onRowFocused(event as Object)
    at = event.getData()
    if at = invalid or at.Count() < 2 then return
    m.rowIndex = at[0]
    paintRowTitle()
    if m.zone = "rows" then pickAt(at[0], at[1])
end sub

' Lo enfocado en las filas pasa a ser lo destacado.
sub pickAt(r as Integer, c as Integer)
    e = shelfItem(r, c)
    if e = invalid then return
    m.picked = e
    showHero()
end sub

sub onRowSelected(event as Object)
    at = event.getData()
    if at = invalid or at.Count() < 2 then return
    e = shelfItem(at[0], at[1])
    if e = invalid then return
    if e.kind = "series"
        m.top.event = { type: "openSeries", item: itemOf(e) }
    else
        m.top.event = { type: "openMovie", item: itemOf(e) }
    end if
end sub

' ----------------------------------------------------------------- lo destacado

function heroItem() as Dynamic
    if m.picked <> invalid then return m.picked
    if m.server <> invalid
        if m.server.movies.Count() > 0 then return m.server.movies[0]
        if m.server.series.Count() > 0 then return m.server.series[0]
    end if
    return invalid
end function

function isNewest(e as Object) as Boolean
    if m.server = invalid or e.kind <> "movie" then return false
    if m.server.movies.Count() = 0 then return false
    return toS(m.server.movies[0].id) = toS(e.id)
end function

sub showHero()
    e = heroItem()
    m.shown = e
    if e = invalid
        m.heroBadge.visible = false
        m.heroTitle.text = ""
        m.heroMeta.text = ""
        m.heroPlot.text = ""
        m.heroArt.uri = ""
        if m.loading
            m.heroStatus.text = "Cargando lo nuevo…"
        else if m.loadError <> invalid
            m.heroStatus.text = m.loadError
        else
            m.heroStatus.text = "Aún no hay películas ni series."
        end if
        buildHeroButtons(invalid)
        layoutHero()
        return
    end if
    m.heroStatus.text = ""
    isSeries = (e.kind = "series")
    if isSeries
        badge = "SERIE"
    else if isNewest(e)
        badge = "★ ESTRENO"
    else
        badge = "PELÍCULA"
    end if
    m.heroBadge.visible = true
    m.heroBadgeText.text = badge
    m.heroBadgeBg.width = Int(m.heroBadgeText.boundingRect().width) + 26
    m.heroTitle.text = upper(toS(e.name))
    info = invalid
    if not isSeries then info = m.infoCache[toS(e.id)]
    m.heroMeta.text = heroMeta(e, info)
    plot = ""
    if info <> invalid then plot = toS(info.plot)
    if plot = "" then plot = toS(e.plot)
    m.heroPlot.text = plot
    img = ""
    if info <> invalid then img = toS(info.backdrop)
    if img = "" then img = toS(e.backdrop)
    if img = "" then img = toS(e.icon)
    if m.heroArt.uri <> img then m.heroArt.uri = img
    buildHeroButtons(e)
    layoutHero()
    if not isSeries and info = invalid then requestLater(e)
end sub

function fmtScore(v as Dynamic) as String
    r = ratingNum(v)
    if r < 0.5 then return ""
    if r > 10 then r = 10
    n = Int(r * 10 + 0.5)
    whole = n \ 10
    tenth = n mod 10
    return whole.ToStr() + "." + tenth.ToStr()
end function

function heroMeta(e as Object, info as Dynamic) as String
    parts = []
    if e.kind = "series"
        parts.Push("Serie")
        y = toS(e.year)
        if y <> "" then parts.Push(y)
        sc = fmtScore(e.rating)
        if sc <> "" then parts.Push("★ " + sc)
    else
        year = toS(e.year)
        genre = toS(e.genre)
        dur = ""
        if info <> invalid
            if toS(info.year) <> "" then year = toS(info.year)
            if toS(info.genre) <> "" then genre = toS(info.genre)
            dur = toS(info.duration)
        end if
        if Len(year) > 4 then year = Left(year, 4)
        if year <> "" then parts.Push(year)
        if genre <> ""
            g = genre.Split(",")
            genre = g[0].Trim()
            if g.Count() > 1 then genre = genre + ", " + g[1].Trim()
            parts.Push(genre)
        end if
        if dur <> "" then parts.Push(dur)
        sc = fmtScore(e.rating)
        if sc <> "" then parts.Push("★ " + sc)
        parts.Push("HD")
    end if
    out = ""
    for each p in parts
        if out <> "" then out = out + "  ·  "
        out = out + p
    end for
    return out
end function

' Acomoda insignia, título, datos, sinopsis y botones, centrados a lo alto dentro del recuadro.
sub layoutHero()
    if m.shown = invalid
        sh = Int(m.heroStatus.boundingRect().height)
        y = Int((344 - (sh + 22 + 43)) / 2)
        m.heroStatus.translation = [32, y]
        m.heroBtns.translation = [32, y + sh + 22]
        return
    end if
    titleH = Int(m.heroTitle.boundingRect().height)
    if titleH < 44 then titleH = 44
    metaH = 0
    if m.heroMeta.text <> "" then metaH = 28
    plotH = 0
    if m.heroPlot.text <> "" then plotH = 9 + Int(m.heroPlot.boundingRect().height)
    total = 22 + 13 + titleH + metaH + plotH + 19 + 43
    y = Int((344 - total) / 2)
    if y < 22 then y = 22
    m.heroBadge.translation = [32, y]
    y = y + 22 + 13
    m.heroTitle.translation = [32, y - 4]
    y = y + titleH
    if metaH > 0
        m.heroMeta.translation = [32, y + 8]
        y = y + metaH
    end if
    if plotH > 0
        m.heroPlot.translation = [32, y + 9]
        y = y + plotH
    end if
    m.heroBtns.translation = [32, y + 19]
end sub

sub buildHeroButtons(e as Dynamic)
    sig = "none"
    if e <> invalid
        fav = isFavorite(e.kind, toS(e.id))
        sig = e.kind + toS(e.id)
        if fav then sig = sig + "*"
    end if
    if sig = m.heroSig then return
    m.heroSig = sig
    root = CreateObject("roSGNode", "ContentNode")
    m.btnIds = []
    if e = invalid
        addBtn(root, "refresh", "Actualizar", "ic_refresh")
    else
        if e.kind = "series"
            addBtn(root, "play", "Ver serie", "ic_play")
        else
            addBtn(root, "play", "Ver ahora", "ic_play")
        end if
        addBtn(root, "info", "Más info", "")
        if isFavorite(e.kind, toS(e.id))
            addBtn(root, "fav", "En mi lista", "ic_check")
        else
            addBtn(root, "fav", "Mi lista", "ic_plus")
        end if
    end if
    m.heroBtns.content = root
end sub

sub addBtn(root as Object, id as String, label as String, icon as String)
    n = root.CreateChild("ContentNode")
    n.title = label
    if icon <> "" then n.HDPosterUrl = "pkg:/images/" + icon + ".png"
    m.btnIds.Push(id)
end sub

function itemOf(e as Object) as Object
    return { id: toS(e.id), name: toS(e.name), icon: toS(e.icon), ext: toS(e.ext) }
end function

sub onHeroButton(event as Object)
    idx = event.getData()
    if idx < 0 or idx >= m.btnIds.Count() then return
    id = m.btnIds[idx]
    if id = "refresh"
        cacheClear()
        loadHome(true)
        return
    end if
    e = m.shown
    if e = invalid then return
    if id = "play"
        if e.kind = "series"
            m.top.event = { type: "openSeries", item: itemOf(e) }
        else
            playMovie(e)
        end if
    else if id = "info"
        if e.kind = "series"
            m.top.event = { type: "openSeries", item: itemOf(e) }
        else
            m.top.event = { type: "openMovie", item: itemOf(e) }
        end if
    else if id = "fav"
        toggleFavorite(e.kind, itemOf(e))
        buildRows()
        buildHeroButtons(e)
        m.heroBtns.jumpToItem = idx
        if m.zone = "hero" then m.heroBtns.setFocus(true)
    end if
end sub

sub playMovie(e as Object)
    item = itemOf(e)
    if item.ext = "" then item.ext = "mp4"
    cached = m.infoCache[item.id]
    if cached <> invalid and toS(cached.ext) <> "" then item.ext = toS(cached.ext)
    saved = findContinue("movie", item.id)
    secs = 0
    if saved <> invalid and toI(saved.pos) >= 5 then secs = toI(saved.pos)
    m.top.event = { type: "play", mode: "movie", items: [item], index: 0, start: secs }
end sub

' ----------------------------------------------------------------- ficha de la película destacada (año, género, imagen de fondo)

sub requestLater(e as Object)
    m.pendingInfo = e
    m.infoTimer.control = "stop"
    m.infoTimer.control = "start"
end sub

sub onInfoTimer()
    e = m.pendingInfo
    m.pendingInfo = invalid
    if e = invalid then return
    if m.shown = invalid or toS(m.shown.id) <> toS(e.id) then return
    id = toS(e.id)
    if id = "" or m.infoCache[id] <> invalid or m.infoTasks.DoesExist(id) then return
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "vodinfo", id: id, tag: id }
    t.observeField("response", "onInfo")
    m.infoTasks[id] = t
    t.control = "RUN"
end sub

sub onInfo(event as Object)
    resp = event.getData()
    if resp = invalid then return
    id = toS(resp.tag)
    m.infoTasks.Delete(id)
    if resp.ok <> true then return
    m.infoCache[id] = resp.info
    if m.shown <> invalid and toS(m.shown.id) = id and m.shown.kind = "movie" then showHero()
end sub

' ----------------------------------------------------------------- canal en vivo

sub showLive()
    items = []
    for each c in recentLive()
        items.Push({ id: toS(c.id), name: toS(c.name), icon: toS(c.icon), ext: "" })
    end for
    if items.Count() = 0 and m.server <> invalid and m.server.live <> invalid
        for each c in m.server.live
            items.Push({ id: toS(c.id), name: toS(c.name), icon: toS(c.icon), ext: "" })
        end for
    end if
    m.liveItems = items
    w = Int(m.liveBadgeText.boundingRect().width)
    m.liveBadgeBg.width = 18 + w + 8
    if items.Count() = 0
        if m.loading then m.liveName.text = "CARGANDO CANALES…" else m.liveName.text = "TV EN VIVO"
        m.liveProg.text = "Elige un canal en En vivo"
        m.liveLogo.uri = ""
        m.liveIconBig.uri = ""
        m.liveBarBg.visible = false
        m.liveBar.visible = false
        m.epgId = ""
        return
    end if
    c = items[0]
    m.liveName.text = upper(c.name)
    m.liveLogo.uri = c.icon
    m.liveIconBig.uri = c.icon
    now = nowSecs()
    if m.epgId <> c.id or now - m.epgAt > 120
        if m.epgId <> c.id
            m.liveProg.text = "En vivo ahora"
            m.liveBarBg.visible = false
            m.liveBar.visible = false
        end if
        m.epgId = c.id
        m.epgAt = now
        t = CreateObject("roSGNode", "ApiTask")
        t.request = { action: "epg", id: c.id, tag: c.id }
        t.observeField("response", "onLiveEpg")
        m.epgTask = t
        t.control = "RUN"
    end if
end sub

sub onLiveEpg(event as Object)
    resp = event.getData()
    if resp = invalid or toS(resp.tag) <> m.epgId then return
    if resp.ok = true and resp.nowEntry <> invalid
        e = resp.nowEntry
        title = toS(e.title)
        if title = "" then title = "En vivo ahora"
        m.liveProg.text = title
        span = toI(e.stop) - toI(e.start)
        if span > 0
            pct = (nowSecs() - toI(e.start)) / span
            if pct < 0.02 then pct = 0.02
            if pct > 1 then pct = 1
            m.liveBar.width = Int(366 * pct)
            m.liveBarBg.visible = true
            m.liveBar.visible = true
        end if
    else
        m.liveProg.text = "En vivo ahora"
    end if
end sub

' ----------------------------------------------------------------- continuar viendo

sub showContinue()
    list = loadList("continue")
    m.contBarBg.visible = false
    m.contBar.visible = false
    if list.Count() > 0
        c = list[0]
        m.contMode = "resume"
        m.contEntry = c
        m.contKicker.text = "CONTINUAR VIENDO"
        m.contTitle.text = upper(toS(c.name))
        m.contSub.text = "Te quedaste en " + fmtClock(toI(c.pos))
        m.contArt.uri = thumbUrl(toS(c.icon))
        if toI(c.dur) > 0
            pct = toI(c.pos) / toI(c.dur)
            if pct < 0.02 then pct = 0.02
            if pct > 1 then pct = 1
            m.contBar.width = Int(237 * pct)
            m.contBarBg.visible = true
            m.contBar.visible = true
        end if
    else if m.server <> invalid and m.server.series.Count() > 0
        s = m.server.series[0]
        m.contMode = "series"
        m.contEntry = s
        m.contKicker.text = "SERIE NUEVA"
        m.contTitle.text = upper(toS(s.name))
        m.contSub.text = "Temporadas y episodios"
        m.contArt.uri = thumbUrl(toS(s.icon))
    else
        m.contMode = "screen"
        m.contEntry = invalid
        m.contKicker.text = "CONTINUAR VIENDO"
        m.contTitle.text = "AQUÍ APARECE LO QUE DEJES A MEDIAS"
        m.contSub.text = "Películas y episodios"
        m.contArt.uri = ""
    end if
    layoutCont()
end sub

sub layoutCont()
    titleH = Int(m.contTitle.boundingRect().height)
    if titleH < 22 then titleH = 22
    total = 14 + 4 + titleH + 3 + 16
    if m.contBar.visible then total = total + 12
    y = Int((164 - total) / 2)
    m.contKicker.translation = [206, y]
    y = y + 18
    m.contTitle.translation = [206, y]
    y = y + titleH + 3
    m.contSub.translation = [206, y]
    y = y + 16 + 8
    m.contBarBg.translation = [206, y]
    m.contBar.translation = [206, y]
end sub

sub openContinue()
    if m.contMode = "resume" and m.contEntry <> invalid
        m.top.event = { type: "playEntry", entry: m.contEntry }
    else if m.contMode = "series" and m.contEntry <> invalid
        m.top.event = { type: "openSeries", item: itemOf(m.contEntry) }
    else
        m.top.event = { type: "open", target: "continue" }
    end if
end sub

' ----------------------------------------------------------------- barra de arriba

sub onTopBar()
    target = m.topBar.selected
    if target = "home"
        setZone("hero")
    else if target = "more"
        m.top.event = { type: "more" }
    else
        m.top.event = { type: "open", target: target }
    end if
end sub

' ----------------------------------------------------------------- foco y teclas

sub setZone(z as String)
    m.zone = z
    applyFocus()
end sub

sub applyFocus()
    if m.zone = "rows" and m.shelves.Count() = 0 then m.zone = "hero"
    z = m.zone
    if z = "tabs"
        m.topBar.setFocus(true)
    else if z = "hero"
        m.heroBtns.setFocus(true)
    else if z = "rows"
        m.rows.setFocus(true)
        at = m.rows.rowItemFocused
        if at <> invalid and at.Count() >= 2 then pickAt(at[0], at[1])
    else
        ' Canal en vivo y "Continuar viendo": las teclas las maneja esta pantalla.
        m.top.setFocus(true)
    end if
    paintZones()
end sub

sub paintZones()
    heroOn = (m.zone = "hero")
    m.heroRing.visible = heroOn
    m.heroBorder.visible = not heroOn
    liveOn = (m.zone = "live")
    m.liveRing.visible = liveOn
    m.liveBorder.visible = not liveOn
    if liveOn then m.liveTile.scale = [1.03, 1.03] else m.liveTile.scale = [1, 1]
    contOn = (m.zone = "cont")
    m.contRing.visible = contOn
    m.contBorder.visible = not contOn
    if contOn then m.contTile.scale = [1.03, 1.03] else m.contTile.scale = [1, 1]
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    z = m.zone
    if key = "back"
        if z <> "hero"
            setZone("hero")
            return true
        end if
        ' En lo destacado, Atrás cierra la app (como en el inicio de cualquier app de Roku).
        return false
    end if
    if key = "options"
        showOptions()
        return true
    end if
    if z = "tabs"
        if key = "down"
            setZone("hero")
            return true
        end if
        return false
    end if
    if z = "hero"
        if key = "up"
            setZone("tabs")
        else if key = "down"
            if m.shelves.Count() > 0 then setZone("rows")
        else if key = "right"
            ' Solo llega aquí desde el último botón.
            setZone("live")
        end if
        return true
    end if
    if z = "live"
        if key = "up"
            setZone("tabs")
        else if key = "down"
            setZone("cont")
        else if key = "left"
            m.heroBtns.jumpToItem = m.btnIds.Count() - 1
            setZone("hero")
        else if key = "OK"
            if m.liveItems.Count() > 0
                m.top.event = { type: "playLive", items: m.liveItems, index: 0 }
            else
                m.top.event = { type: "open", target: "live" }
            end if
        end if
        return true
    end if
    if z = "cont"
        if key = "up"
            setZone("live")
        else if key = "down"
            if m.shelves.Count() > 0 then setZone("rows")
        else if key = "left"
            m.heroBtns.jumpToItem = m.btnIds.Count() - 1
            setZone("hero")
        else if key = "OK"
            openContinue()
        end if
        return true
    end if
    if z = "rows"
        ' La lista de filas solo deja pasar "arriba" desde la primera fila.
        if key = "up"
            m.heroBtns.jumpToItem = 0
            setZone("hero")
        end if
        return true
    end if
    return false
end function

' ----------------------------------------------------------------- tecla * (opciones): cerrar sesión

sub showOptions()
    dlg = CreateObject("roSGNode", "Dialog")
    dlg.title = "Opciones"
    s = m.global.session
    if s <> invalid and s.user <> invalid then dlg.message = "Cuenta: " + toS(s.user)
    dlg.buttons = ["Cerrar sesión", "Cancelar"]
    dlg.observeFieldScoped("buttonSelected", "onOptionsChoice")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onOptionsChoice()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    dlg.close = true
    m.dialog = invalid
    applyFocus()
    if choice = 0 then m.top.event = { type: "logout" }
end sub
