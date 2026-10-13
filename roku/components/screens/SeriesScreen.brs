sub init()
    m.name = m.top.findNode("name")
    m.cover = m.top.findNode("cover")
    m.meta = m.top.findNode("meta")
    m.plot = m.top.findNode("plot")
    m.seasonList = m.top.findNode("seasonList")
    m.episodeList = m.top.findNode("episodeList")
    m.status = m.top.findNode("status")
    m.idTag = m.top.findNode("idTag")
    m.tasks = []
    m.seasons = []
    m.flat = []
    m.pane = "seasons"
    m.seasonList.visible = false
    m.episodeList.visible = false
    m.seasonList.observeField("itemFocused", "onSeasonFocused")
    m.episodeList.observeField("itemSelected", "onEpisodeSelected")
end sub

sub focusScreen()
    if m.pane = "episodes" and m.episodeList.visible
        m.episodeList.setFocus(true)
    else
        m.pane = "seasons"
        m.seasonList.setFocus(true)
    end if
end sub

sub onItem()
    it = m.top.item
    m.name.text = toS(it.name)
    m.cover.uri = toS(it.icon)
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
    if m.name.text = "" and info.name <> "" then m.name.text = info.name
    parts = ""
    if info.year <> "" then parts = info.year
    if info.rating <> "" and info.rating <> "0"
        if parts <> "" then parts = parts + "  ·  "
        parts = parts + "Puntuación " + info.rating
    end if
    m.meta.text = parts
    m.plot.text = info.plot
    if info.cover <> "" and toS(m.top.item.icon) = "" then m.cover.uri = info.cover

    m.seasons = resp.seasons
    m.flat = []
    seasonRoot = CreateObject("roSGNode", "ContentNode")
    for each s in m.seasons
        node = seasonRoot.CreateChild("ContentNode")
        node.title = "Temporada " + s.number.ToStr()
        for each e in s.episodes
            m.flat.Push({ id: e.id, title: e.title, ext: e.ext, season: s.number, num: e.num, image: e.image })
        end for
    end for
    m.seasonList.content = seasonRoot
    m.seasonList.visible = true
    m.episodeList.visible = true
    showSeason(0)
    m.pane = "seasons"
    m.seasonList.setFocus(true)
end sub

' Muestra los episodios de la temporada que está en la posición "seasonIndex".
sub showSeason(seasonIndex as Integer)
    if seasonIndex < 0 or seasonIndex >= m.seasons.Count() then return
    season = m.seasons[seasonIndex]
    ' Posición de su primer episodio dentro de la lista completa.
    offset = 0
    for i = 0 to seasonIndex - 1
        offset = offset + m.seasons[i].episodes.Count()
    end for
    root = CreateObject("roSGNode", "ContentNode")
    k = 0
    for each e in season.episodes
        node = root.CreateChild("ContentNode")
        node.id = Str(offset + k).Trim()
        label = "Episodio " + e.num.ToStr()
        if e.title <> "" then label = "E" + e.num.ToStr() + "  ·  " + e.title
        node.title = label
        k = k + 1
    end for
    m.episodeList.content = root
end sub

sub onSeasonFocused(event as Object)
    showSeason(event.getData())
end sub

sub onEpisodeSelected(event as Object)
    node = m.episodeList.content.getChild(event.getData())
    if node = invalid then return
    flatIndex = node.id.ToInt()
    it = m.top.item
    items = []
    for each e in m.flat
        image = e.image
        if image = "" then image = toS(it.icon)
        items.Push({ id: e.id, name: e.title, icon: image, ext: e.ext, seriesName: m.name.text, seriesId: toS(it.id), season: e.season, num: e.num })
    end for
    secs = 0
    saved = findContinue("episode", items[flatIndex].id)
    if saved <> invalid and toI(saved.pos) >= 5 then secs = toI(saved.pos)
    m.top.event = { type: "play", mode: "episode", items: items, index: flatIndex, start: secs }
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "right" and m.pane = "seasons" and m.episodeList.visible
        m.pane = "episodes"
        m.episodeList.setFocus(true)
        return true
    end if
    if (key = "left" or key = "back") and m.pane = "episodes"
        m.pane = "seasons"
        m.seasonList.setFocus(true)
        return true
    end if
    if key = "play"
        it = m.top.item
        item = { id: toS(it.id), name: toS(it.name), icon: toS(it.icon), ext: "" }
        if toggleFavorite("series", item)
            m.hintMsg = "Agregada a favoritos"
        else
            m.hintMsg = "Quitada de favoritos"
        end if
        m.top.findNode("hint").text = m.hintMsg + "      (Play/Pausa otra vez para cambiarlo)"
        return true
    end if
    return false
end function
