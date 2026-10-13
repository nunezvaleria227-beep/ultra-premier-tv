sub init()
    m.cover = m.top.findNode("cover")
    m.name = m.top.findNode("name")
    m.meta = m.top.findNode("meta")
    m.plot = m.top.findNode("plot")
    m.extra = m.top.findNode("extra")
    m.actions = m.top.findNode("actions")
    m.status = m.top.findNode("status")
    m.idTag = m.top.findNode("idTag")
    m.tasks = []
    m.ext = "mp4"
    m.actionIds = []
    m.actions.observeField("itemSelected", "onAction")
end sub

sub focusScreen()
    buildActions()
    m.actions.setFocus(true)
end sub

sub onItem()
    it = m.top.item
    m.name.text = toS(it.name)
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
        return
    end if
    info = resp.info
    if m.name.text = "" and info.name <> "" then m.name.text = info.name
    parts = []
    if info.year <> "" then parts.Push(info.year)
    if info.duration <> "" then parts.Push(info.duration)
    if info.rating <> "" and info.rating <> "0" then parts.Push("Puntuación " + info.rating)
    if info.genre <> "" then parts.Push(info.genre)
    m.meta.text = joinParts(parts)
    m.plot.text = info.plot
    extra = ""
    if info.director <> "" then extra = "Dirección: " + info.director
    if info.cast <> ""
        if extra <> "" then extra = extra + Chr(10)
        extra = extra + "Reparto: " + info.cast
    end if
    m.extra.text = extra
    if info.cover <> "" and toS(m.top.item.icon) = "" then m.cover.uri = info.cover
    if info.ext <> "" then m.ext = info.ext
end sub

function joinParts(parts as Object) as String
    out = ""
    for each p in parts
        if out <> "" then out = out + "   ·   "
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
        addAction(root, "resume", "Continuar (" + fmtClock(toI(saved.pos)) + ")")
        addAction(root, "start", "Desde el inicio")
    else
        addAction(root, "start", "Reproducir")
    end if
    if isFavorite("movie", toS(it.id))
        addAction(root, "fav", "Quitar de favoritos")
    else
        addAction(root, "fav", "Agregar a favoritos")
    end if
    m.actions.content = root
end sub

sub addAction(root as Object, id as String, title as String)
    n = root.CreateChild("ContentNode")
    n.title = title
    m.actionIds.Push(id)
end sub

sub onAction(event as Object)
    index = event.getData()
    action = m.actionIds[index]
    it = m.top.item
    item = { id: toS(it.id), name: m.name.text, icon: toS(it.icon), ext: m.ext }
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
        m.actions.setFocus(true)
    end if
end sub
