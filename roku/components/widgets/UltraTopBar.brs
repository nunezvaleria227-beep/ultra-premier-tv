' Barra de arriba. Izquierda / derecha recorre las pestañas; OK avisa cuál se eligió en "selected"
' (home, live, movie, series, search o more). Abajo lo maneja la pantalla que la contiene.

sub init()
    m.defs = [
        { id: "home", label: "INICIO" }
        { id: "live", label: "EN VIVO" }
        { id: "movie", label: "PELÍCULAS" }
        { id: "series", label: "SERIES" }
        { id: "search", label: "BUSCAR" }
        { id: "more", label: "MÁS" }
    ]
    m.tabs = m.top.findNode("tabs")
    m.clock = m.top.findNode("clock")
    m.dateLabel = m.top.findNode("dateLabel")
    m.nodes = []
    x = 0
    for each d in m.defs
        g = m.tabs.createChild("Group")
        bg = g.createChild("Poster")
        bg.visible = false
        lb = g.createChild("Label")
        lb.font = uFont("BlackItalic", 15)
        lb.text = d.label
        lb.height = 33
        lb.vertAlign = "center"
        lb.translation = [20, 0]
        w = Int(lb.boundingRect().width) + 40
        ' La imagen inclinada trae 12 px de margen alrededor para el brillo.
        bg.translation = [-12, -12]
        bg.width = w + 24
        bg.height = 57
        g.translation = [x, 0]
        m.nodes.Push({ g: g, bg: bg, lb: lb })
        x = x + w + 4
    end for
    m.idx = 0
    m.activeIdx = 0
    m.hadFocus = false
    m.top.observeField("focusedChild", "onFocus")
    m.timer = m.top.findNode("clockTimer")
    m.timer.observeField("fire", "refreshClock")
    m.timer.control = "start"
    refreshClock()
    paint()
end sub

sub refreshClock()
    now = nowSecs()
    m.clock.text = fmtHM(now)
    m.dateLabel.text = fmtBarDate(now)
end sub

sub onActive()
    m.activeIdx = -1
    for i = 0 to m.defs.Count() - 1
        if m.defs[i].id = m.top.active then m.activeIdx = i
    end for
    if m.activeIdx >= 0 then m.idx = m.activeIdx
    paint()
end sub

' Al subir a la barra, el foco empieza en la pestaña de la pantalla actual.
sub onFocus()
    has = m.top.hasFocus()
    if has and not m.hadFocus
        if m.activeIdx >= 0 then m.idx = m.activeIdx else m.idx = 0
        refreshClock()
    end if
    m.hadFocus = has
    paint()
end sub

sub paint()
    focused = m.top.hasFocus()
    for i = 0 to m.nodes.Count() - 1
        n = m.nodes[i]
        if focused and i = m.idx
            n.bg.uri = "pkg:/images/tab_focus.9.png"
            n.bg.visible = true
            n.lb.color = "0x1446D8FF"
        else if i = m.activeIdx
            n.bg.uri = "pkg:/images/tab_active.9.png"
            n.bg.visible = true
            n.lb.color = "0xFFFFFFFF"
        else
            n.bg.visible = false
            n.lb.color = "0x8FA3D6FF"
        end if
    end for
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "left"
        if m.idx > 0 then m.idx = m.idx - 1
        paint()
        return true
    end if
    if key = "right"
        if m.idx < m.defs.Count() - 1 then m.idx = m.idx + 1
        paint()
        return true
    end if
    if key = "OK"
        m.top.selected = m.defs[m.idx].id
        return true
    end if
    return false
end function
