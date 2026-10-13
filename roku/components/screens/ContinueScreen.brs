sub init()
    m.list = m.top.findNode("list")
    m.status = m.top.findNode("status")
    m.entries = []
    m.list.observeField("itemSelected", "onSelected")
end sub

sub focusScreen()
    rebuild()
    if m.list.visible
        m.list.setFocus(true)
    else
        m.top.setFocus(true)
    end if
end sub

' Primero lo que se dejó a medias (películas y episodios) y luego los últimos canales vistos.
sub rebuild()
    m.entries = []
    for each e in loadList("continue")
        m.entries.Push(e)
    end for
    for each e in recentLive()
        m.entries.Push(e)
    end for
    root = CreateObject("roSGNode", "ContentNode")
    for each e in m.entries
        node = root.CreateChild("ContentNode")
        node.title = toS(e.name)
        if toS(e.kind) = "live"
            node.description = "Canal en vivo"
            node.HDPosterUrl = toS(e.icon)
        else
            kind = "Película"
            if toS(e.kind) = "episode" then kind = "Episodio"
            line = kind + "  ·  vas en " + fmtClock(toI(e.pos))
            if toI(e.dur) > 0 then line = line + " de " + fmtClock(toI(e.dur))
            node.description = line
            node.HDPosterUrl = thumbUrl(toS(e.icon))
        end if
    end for
    previous = m.list.itemFocused
    m.list.content = root
    if previous > 0 and previous < m.entries.Count() then m.list.jumpToItem = previous
    if m.entries.Count() = 0
        m.status.text = "Todavía no hay nada para continuar." + Chr(10) + "Aquí aparece lo que dejes a medias en películas y series, y los últimos canales que veas."
        m.list.visible = false
    else
        m.status.text = ""
        m.list.visible = true
    end if
end sub

sub onSelected(event as Object)
    index = event.getData()
    if index < 0 or index >= m.entries.Count() then return
    e = m.entries[index]
    if toS(e.kind) = "live"
        items = []
        livePos = 0
        for each x in m.entries
            if toS(x.kind) = "live"
                if toS(x.id) = toS(e.id) then livePos = items.Count()
                items.Push({ id: toS(x.id), name: toS(x.name), icon: toS(x.icon), ext: "" })
            end if
        end for
        m.top.event = { type: "playLive", items: items, index: livePos }
    else
        m.top.event = { type: "playEntry", entry: e }
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "options" and m.entries.Count() > 0
        idx = m.list.itemFocused
        if idx >= 0 and idx < m.entries.Count()
            e = m.entries[idx]
            if toS(e.kind) = "live"
                removeRecentLive(toS(e.id))
            else
                updateContinue({ kind: toS(e.kind), id: toS(e.id), pos: 1000000, dur: 1000000 })
            end if
            rebuild()
            focusScreen()
        end if
        return true
    end if
    return false
end function
