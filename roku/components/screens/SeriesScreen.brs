' Ficha de serie: botones (seguir viendo, Mi lista), temporadas como pastillas inclinadas y episodios en tarjetas.
' Zonas del foco: "tabs" (barra de arriba), "buttons", "seasons", "episodes".

sub init()
    m.name = m.top.findNode("name")
    m.kicker = m.top.findNode("kicker")
    m.backdrop = m.top.findNode("backdrop")
    m.topBar = m.top.findNode("topBar")
    m.title = ""
    m.meta = m.top.findNode("meta")
    m.plot = m.top.findNode("plot")
    m.btns = m.top.findNode("btns")
    m.seasonList = m.top.findNode("seasonList")
    m.episodeList = m.top.findNode("episodeList")
    m.status = m.top.findNode("status")
    m.idTag = m.top.findNode("idTag")
    m.tasks = []
    m.seasons = []
    m.flat = []
    m.seasonIndex = 0
    m.btnIds = []
    m.resumeIndex = -1
    m.pane = "buttons"
    m.autoplayDone = false
    m.seasonList.visible = false
    m.episodeList.visible = false
    m.btns.visible = false
    m.seasonList.observeField("itemFocused", "onSeasonFocused")
    m.seasonList.observeField("itemSelected", "onSeasonSelected")
    m.episodeList.observeField("rowItemSelected", "onEpisodeSelected")
    m.btns.observeField("itemSelected", "onButton")
    m.topBar.observeField("selected", "onTopBar")
end sub

sub onTopBar()
    m.top.event = { type: "nav", target: m.topBar.selected }
end sub

sub focusScreen()
    if m.flat.Count() > 0
        buildButtons()
        if m.seasonIndex < m.seasons.Count() then showSeason(m.seasonIndex)
    end if
    applyFocus()
end sub

sub applyFocus()
    if m.pane = "tabs"
        m.topBar.setFocus(true)
        return
    end if
    if m.flat.Count() = 0
        m.top.setFocus(true)
        return
    end if
    if m.pane = "episodes"
        m.episodeList.setFocus(true)
    else if m.pane = "seasons"
        m.seasonList.setFocus(true)
    else
        m.pane = "buttons"
        m.btns.setFocus(true)
    end if
end sub

sub onItem()
    it = m.top.item
    m.title = toS(it.name)
    m.name.text = upper(m.title)
    m.backdrop.uri = toS(it.icon)
    layout()
    ' Número de identificación de la serie en el portal (sirve para registrar enlaces directos y para soporte).
    m.idTag.text = "ID de la serie: " + toS(it.id)
    m.status.text = "Cargando temporadas y episodios…"
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "seriesinfo", id: toS(it.id), tag: toS(it.id) }
    t.observeField("response", "onInfo")
    m.tasks.Push(t)
    t.control = "RUN"
end sub

sub onInfo(event as Object)
    resp = event.getData()
    if resp.ok <> true
        m.status.text = resp.error + "  Pulsa Atrás para volver."
        return
    end if
    m.status.text = ""
    info = resp.info
    if m.title = "" and info.name <> ""
        m.title = info.name
        m.name.text = upper(m.title)
    end if
    parts = []
    if toS(info.year) <> "" then parts.Push(Left(toS(info.year), 4))
    if toS(info.genre) <> ""
        g = toS(info.genre).Split(",")
        genre = g[0].Trim()
        if g.Count() > 1 then genre = genre + ", " + g[1].Trim()
        parts.Push(genre)
    end if
    score = ratingNum(info.rating)
    if score >= 0.5
        n = Int(score * 10 + 0.5)
        whole = n \ 10
        tenth = n mod 10
        parts.Push("★ " + whole.ToStr() + "." + tenth.ToStr())
    end if
    line = ""
    for each p in parts
        if line <> "" then line = line + "  ·  "
        line = line + p
    end for
    m.meta.text = line
    m.plot.text = info.plot
    if toS(info.backdrop) <> ""
        m.backdrop.uri = toS(info.backdrop)
    else if info.cover <> "" and toS(m.top.item.icon) = ""
        m.backdrop.uri = info.cover
    end if

    m.seasons = resp.seasons
    m.flat = []
    seasonRoot = CreateObject("roSGNode", "ContentNode")
    for each s in m.seasons
        node = seasonRoot.CreateChild("ContentNode")
        if s.number = 0 then node.title = "Especiales" else node.title = "Temporada " + s.number.ToStr()
        for each e in s.episodes
            m.flat.Push({ id: e.id, title: e.title, ext: e.ext, season: s.number, num: e.num, image: e.image, dur: e.dur })
        end for
    end for
    n = m.seasons.Count()
    if n = 1 then m.kicker.text = "SERIE  ·  1 TEMPORADA" else m.kicker.text = "SERIE  ·  " + n.ToStr() + " TEMPORADAS"
    layout()
    m.seasonList.content = seasonRoot
    m.seasonList.visible = true
    m.episodeList.visible = true
    buildButtons()
    ' Se abre la temporada del episodio a medias (si hay uno).
    m.seasonIndex = 0
    if m.resumeIndex >= 0
        target = m.flat[m.resumeIndex].season
        for i = 0 to m.seasons.Count() - 1
            if m.seasons[i].number = target then m.seasonIndex = i
        end for
    end if
    m.seasonList.jumpToItem = m.seasonIndex
    showSeason(m.seasonIndex)
    m.pane = "buttons"
    applyFocus()
    maybeAutoplay()
end sub

' El episodio a medias más reciente de esta serie (posición en la lista completa), o -1.
function findResume() as Integer
    for each saved in loadList("continue")
        if toS(saved.kind) = "episode"
            for i = 0 to m.flat.Count() - 1
                if toS(m.flat[i].id) = toS(saved.id) then return i
            end for
        end if
    end for
    return -1
end function

sub buildButtons()
    m.resumeIndex = findResume()
    root = CreateObject("roSGNode", "ContentNode")
    m.btnIds = []
    if m.resumeIndex >= 0
        e = m.flat[m.resumeIndex]
        addBtn(root, "play", "Seguir con T" + e.season.ToStr() + " E" + e.num.ToStr(), "ic_play")
    else if m.flat.Count() > 0
        e = m.flat[0]
        addBtn(root, "play", "Ver T" + e.season.ToStr() + " E" + e.num.ToStr(), "ic_play")
    end if
    if isFavorite("series", toS(m.top.item.id))
        addBtn(root, "fav", "En mi lista", "ic_check")
    else
        addBtn(root, "fav", "Mi lista", "ic_plus")
    end if
    m.btns.content = root
    m.btns.visible = true
end sub

sub addBtn(root as Object, id as String, label as String, icon as String)
    n = root.CreateChild("ContentNode")
    n.title = label
    if icon <> "" then n.HDPosterUrl = "pkg:/images/" + icon + ".png"
    m.btnIds.Push(id)
end sub

sub onButton(event as Object)
    idx = event.getData()
    if idx < 0 or idx >= m.btnIds.Count() then return
    id = m.btnIds[idx]
    if id = "play"
        flatIndex = 0
        if m.resumeIndex >= 0 then flatIndex = m.resumeIndex
        playFrom(flatIndex)
    else if id = "fav"
        toggleSeriesFav()
        buildButtons()
        m.btns.jumpToItem = idx
        m.btns.setFocus(true)
    end if
end sub

sub toggleSeriesFav()
    it = m.top.item
    toggleFavorite("series", { id: toS(it.id), name: m.title, icon: toS(it.icon), ext: "" })
end sub

' Acomoda datos, sinopsis, botones, temporadas y episodios debajo del título (que puede ocupar una o dos líneas).
sub layout()
    nameH = Int(m.name.boundingRect().height)
    if nameH < 44 then nameH = 44
    y = 18 + nameH + 4
    m.meta.translation = [0, y]
    y = y + 26
    m.plot.translation = [0, y]
    if m.plot.text <> "" then y = y + Int(m.plot.boundingRect().height)
    y = 83 + y + 16
    m.btns.translation = [48, y]
    m.status.translation = [48, y + 64]
    m.seasonList.translation = [48, y + 64]
    m.episodeList.translation = [48, y + 116]
end sub

sub playFrom(flatIndex as Integer)
    items = episodeItems()
    if flatIndex < 0 or flatIndex >= items.Count() then return
    secs = 0
    saved = findContinue("episode", items[flatIndex].id)
    if saved <> invalid and toI(saved.pos) >= 5 then secs = toI(saved.pos)
    m.top.event = { type: "play", mode: "episode", items: items, index: flatIndex, start: secs }
end sub

' Si se llegó por un enlace directo a una serie, Roku exige reproducir un episodio sin mostrar pantallas intermedias:
' si la persona ya iba viendo la serie sigue donde se quedó; si no, empieza por el primer episodio.
sub maybeAutoplay()
    if m.autoplayDone then return
    it = m.top.item
    if it = invalid then return
    if not it.DoesExist("autoplay") then return
    if it.autoplay <> true then return
    if m.flat.Count() = 0 then return
    m.autoplayDone = true
    flatIndex = 0
    if m.resumeIndex >= 0 then flatIndex = m.resumeIndex
    playFrom(flatIndex)
end sub

' Todos los episodios de la serie, en orden, listos para el reproductor.
function episodeItems() as Object
    it = m.top.item
    items = []
    for each e in m.flat
        image = e.image
        if image = "" then image = toS(it.icon)
        items.Push({ id: e.id, name: e.title, icon: image, ext: e.ext, seriesName: m.title, seriesId: toS(it.id), season: e.season, num: e.num })
    end for
    return items
end function

' Muestra en tarjetas los episodios de la temporada que está en la posición "seasonIndex".
sub showSeason(seasonIndex as Integer)
    if seasonIndex < 0 or seasonIndex >= m.seasons.Count() then return
    m.seasonIndex = seasonIndex
    m.seasonList.selectedIndex = seasonIndex
    season = m.seasons[seasonIndex]
    ' Posición de su primer episodio dentro de la lista completa.
    offset = 0
    for i = 0 to seasonIndex - 1
        offset = offset + m.seasons[i].episodes.Count()
    end for
    it = m.top.item
    root = CreateObject("roSGNode", "ContentNode")
    row = root.CreateChild("ContentNode")
    k = 0
    for each e in season.episodes
        node = row.CreateChild("ContentNode")
        node.id = Str(offset + k).Trim()
        label = "Episodio " + e.num.ToStr()
        if e.title <> "" then label = label + ": " + rawName(e.title)
        node.title = label
        node.ShortDescriptionLine1 = "E" + e.num.ToStr()
        image = e.image
        if image = "" then image = toS(it.icon)
        node.HDPosterUrl = image
        parts = []
        if e.dur > 0 then parts.Push(Str(e.dur \ 60).Trim() + " min")
        saved = findContinue("episode", e.id)
        if saved <> invalid and toI(saved.dur) > 0
            pct = Int(100 * toI(saved.pos) / toI(saved.dur))
            node.Rating = pct.ToStr()
            remain = (toI(saved.dur) - toI(saved.pos)) \ 60
            if remain < 1 then remain = 1
            parts.Push("Te quedan " + remain.ToStr() + " min")
        else
            parts.Push("Nuevo")
        end if
        subText = ""
        for each p in parts
            if subText <> "" then subText = subText + "  ·  "
            subText = subText + p
        end for
        node.ShortDescriptionLine2 = subText
        k = k + 1
    end for
    m.episodeList.content = root
end sub

sub onSeasonFocused(event as Object)
    showSeason(event.getData())
end sub

sub onSeasonSelected(event as Object)
    showSeason(event.getData())
    m.pane = "episodes"
    applyFocus()
end sub

sub onEpisodeSelected(event as Object)
    at = event.getData()
    if at = invalid or at.Count() < 2 then return
    row = m.episodeList.content.getChild(0)
    node = row.getChild(at[1])
    if node = invalid then return
    playFrom(node.id.ToInt())
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "down"
        if m.pane = "tabs"
            m.pane = "buttons"
            applyFocus()
        else if m.pane = "buttons" and m.seasonList.visible
            m.pane = "seasons"
            applyFocus()
        else if m.pane = "seasons"
            m.pane = "episodes"
            applyFocus()
        end if
        return true
    end if
    if key = "up"
        if m.pane = "episodes"
            m.pane = "seasons"
            applyFocus()
        else if m.pane = "seasons"
            m.pane = "buttons"
            applyFocus()
        else if m.pane = "buttons"
            m.pane = "tabs"
            applyFocus()
        end if
        return true
    end if
    if key = "back" and m.pane <> "buttons" and m.flat.Count() > 0
        m.pane = "buttons"
        applyFocus()
        return true
    end if
    if key = "play"
        toggleSeriesFav()
        buildButtons()
        return true
    end if
    if key = "left" or key = "right" then return true
    return false
end function
