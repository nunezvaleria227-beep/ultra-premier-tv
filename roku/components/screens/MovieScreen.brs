' Ficha de película. Zonas del foco: "buttons" (Ver ahora, Mi lista…) y "tabs" (barra de arriba).

sub init()
    m.cover = m.top.findNode("cover")
    m.backdrop = m.top.findNode("backdrop")
    m.kicker = m.top.findNode("kicker")
    m.name = m.top.findNode("name")
    m.meta = m.top.findNode("meta")
    m.hdBox = m.top.findNode("hdBox")
    m.hdText = m.top.findNode("hdText")
    m.plot = m.top.findNode("plot")
    m.extra = m.top.findNode("extra")
    m.actions = m.top.findNode("actions")
    m.progBg = m.top.findNode("progBg")
    m.progBar = m.top.findNode("progBar")
    m.progLabel = m.top.findNode("progLabel")
    m.status = m.top.findNode("status")
    m.idTag = m.top.findNode("idTag")
    m.topBar = m.top.findNode("topBar")
    m.autoplayDone = false
    m.tasks = []
    m.ext = "mp4"
    m.title = ""
    m.actionIds = []
    m.pane = "buttons"
    m.hdBox.visible = false
    m.hdText.visible = false
    m.actions.observeField("itemSelected", "onAction")
    m.topBar.observeField("selected", "onTopBar")
end sub

sub focusScreen()
    buildActions()
    applyFocus()
end sub

sub applyFocus()
    if m.pane = "tabs" then m.topBar.setFocus(true) else m.actions.setFocus(true)
end sub

sub onTopBar()
    m.top.event = { type: "nav", target: m.topBar.selected }
end sub

sub onItem()
    it = m.top.item
    m.title = toS(it.name)
    m.name.text = upper(m.title)
    m.cover.uri = toS(it.icon)
    ' Número de identificación de la película en el portal (sirve para registrar enlaces directos y para soporte).
    m.idTag.text = "ID de la película: " + toS(it.id)
    m.ext = toS(it.ext)
    if m.ext = "" then m.ext = "mp4"
    m.meta.text = ""
    m.plot.text = ""
    m.extra.text = ""
    m.status.text = "Cargando información…"
    buildActions()
    layout()
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "vodinfo", id: toS(it.id), tag: toS(it.id) }
    t.observeField("response", "onInfo")
    m.tasks.Push(t)
    t.control = "RUN"
end sub

sub onInfo(event as Object)
    resp = event.getData()
    m.status.text = ""
    if resp.ok <> true
        m.plot.text = "No se pudo cargar la descripción, pero la película se puede reproducir."
        layout()
        maybeAutoplay()
        return
    end if
    info = resp.info
    if m.title = "" and info.name <> ""
        m.title = info.name
        m.name.text = upper(m.title)
    end if
    parts = []
    if info.year <> "" then parts.Push(info.year)
    if info.duration <> "" then parts.Push(info.duration)
    genre = ""
    if info.genre <> ""
        g = info.genre.Split(",")
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
    m.meta.text = joinParts(parts)
    if genre <> ""
        first = genre.Split(",")
        m.kicker.text = "PELÍCULA  ·  " + upper(first[0].Trim())
    end if
    plot = info.plot
    if plot = "" then plot = "Sin descripción disponible."
    m.plot.text = plot
    credits = []
    if info.cast <> "" then credits.Push("Reparto: " + info.cast)
    if info.director <> "" then credits.Push("Dirección: " + info.director)
    m.extra.text = joinParts(credits)
    if toS(info.backdrop) <> "" then m.backdrop.uri = toS(info.backdrop)
    if info.cover <> "" and toS(m.top.item.icon) = "" then m.cover.uri = info.cover
    if info.ext <> "" then m.ext = info.ext
    layout()
    maybeAutoplay()
end sub

' Si se llegó por un enlace directo, la película empieza sola en cuanto se sabe su formato.
sub maybeAutoplay()
    if m.autoplayDone then return
    it = m.top.item
    if it = invalid then return
    ' Solo las películas abiertas desde un enlace directo traen esta marca; las demás no la tienen.
    if not it.DoesExist("autoplay") then return
    if it.autoplay <> true then return
    m.autoplayDone = true
    item = { id: toS(it.id), name: m.title, icon: toS(it.icon), ext: m.ext }
    saved = findContinue("movie", item.id)
    secs = 0
    if saved <> invalid and toI(saved.pos) >= 5 then secs = toI(saved.pos)
    m.top.event = { type: "play", mode: "movie", items: [item], index: 0, start: secs }
end sub

' Acomoda datos, sinopsis, créditos y botones debajo del título según cuánto ocupa cada texto.
sub layout()
    nameH = Int(m.name.boundingRect().height)
    if nameH < 44 then nameH = 44
    y = 101 + nameH + 2
    m.meta.translation = [283, y]
    m.status.translation = [283, y]
    showHd = (m.meta.text <> "")
    m.hdBox.visible = showHd
    m.hdText.visible = showHd
    if showHd
        hx = 283 + Int(m.meta.boundingRect().width) + 10
        m.hdBox.translation = [hx, y + 2]
        m.hdText.translation = [hx, y + 2]
    end if
    y = y + 30
    m.plot.translation = [283, y]
    if m.plot.text <> "" then y = y + Int(m.plot.boundingRect().height) + 8
    m.extra.translation = [283, y]
    if m.extra.text <> "" then y = y + Int(m.extra.boundingRect().height) + 6
    y = y + 13
    if y < 300 then y = 300
    if y > 500 then y = 500
    m.actions.translation = [283, y]
    m.progBg.translation = [283, y + 63]
    m.progBar.translation = [283, y + 63]
    m.progLabel.translation = [494, y + 56]
end sub

function joinParts(parts as Object) as String
    out = ""
    for each p in parts
        if out <> "" then out = out + "  ·  "
        out = out + p
    end for
    return out
end function

sub buildActions()
    it = m.top.item
    if it = invalid then return
    root = CreateObject("roSGNode", "ContentNode")
    m.actionIds = []
    saved = findContinue("movie", toS(it.id))
    if saved <> invalid and toI(saved.pos) >= 5
        addAction(root, "resume", "Continuar " + fmtClock(toI(saved.pos)), "ic_play")
        addAction(root, "start", "Desde el inicio", "ic_refresh")
        dur = toI(saved.dur)
        if dur > 0
            w = Int(200 * toI(saved.pos) / dur)
            if w < 4 then w = 4
            if w > 200 then w = 200
            m.progBar.width = w
            m.progBg.visible = true
            m.progBar.visible = true
            m.progLabel.text = "Te quedaste en " + fmtClock(toI(saved.pos))
            m.progLabel.visible = true
        end if
    else
        addAction(root, "start", "Ver ahora", "ic_play")
        m.progBg.visible = false
        m.progBar.visible = false
        m.progLabel.visible = false
    end if
    if isFavorite("movie", toS(it.id))
        addAction(root, "fav", "En mi lista", "ic_check")
    else
        addAction(root, "fav", "Mi lista", "ic_plus")
    end if
    m.actions.content = root
end sub

sub addAction(root as Object, id as String, title as String, icon as String)
    n = root.CreateChild("ContentNode")
    n.title = title
    if icon <> "" then n.HDPosterUrl = "pkg:/images/" + icon + ".png"
    m.actionIds.Push(id)
end sub

sub onAction(event as Object)
    index = event.getData()
    if index < 0 or index >= m.actionIds.Count() then return
    action = m.actionIds[index]
    it = m.top.item
    item = { id: toS(it.id), name: m.title, icon: toS(it.icon), ext: m.ext }
    if action = "start"
        m.top.event = { type: "play", mode: "movie", items: [item], index: 0, start: 0 }
    else if action = "resume"
        saved = findContinue("movie", item.id)
        secs = 0
        if saved <> invalid then secs = toI(saved.pos)
        m.top.event = { type: "play", mode: "movie", items: [item], index: 0, start: secs }
    else if action = "fav"
        toggleFavorite("movie", item)
        buildActions()
        m.actions.jumpToItem = index
        m.actions.setFocus(true)
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "up" and m.pane = "buttons"
        m.pane = "tabs"
        applyFocus()
        return true
    end if
    if key = "down" and m.pane = "tabs"
        m.pane = "buttons"
        applyFocus()
        return true
    end if
    if key = "back" and m.pane = "tabs"
        m.pane = "buttons"
        applyFocus()
        return true
    end if
    if key = "left" or key = "right" then return true
    return false
end function
