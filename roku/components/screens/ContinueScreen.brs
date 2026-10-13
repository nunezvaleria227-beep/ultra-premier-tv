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

sub rebuild()
    m.entries = loadList("continue")
    root = CreateObject("roSGNode", "ContentNode")
    for each e in m.entries
        kind = "Película"
        if toS(e.kind) = "episode" then kind = "Episodio"
        label = kind + "  ·  " + toS(e.name) + "   —   " + fmtClock(toI(e.pos))
        if toI(e.dur) > 0 then label = label + " de " + fmtClock(toI(e.dur))
        root.CreateChild("ContentNode").title = label
    end for
    previous = m.list.itemFocused
    m.list.content = root
    if previous > 0 and previous < m.entries.Count() then m.list.jumpToItem = previous
    if m.entries.Count() = 0
        m.status.text = "Todavía no hay nada para continuar." + Chr(10) + "Lo que dejes a medias en películas y series aparece aquí."
        m.list.visible = false
    else
        m.status.text = ""
        m.list.visible = true
    end if
end sub

sub onSelected(event as Object)
    index = event.getData()
    if index < 0 or index >= m.entries.Count() then return
    m.top.event = { type: "playEntry", entry: m.entries[index] }
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "options" and m.entries.Count() > 0
        idx = m.list.itemFocused
        if idx >= 0 and idx < m.entries.Count()
            e = m.entries[idx]
            updateContinue({ kind: toS(e.kind), id: toS(e.id), pos: 1000000, dur: 1000000 })
            rebuild()
            focusScreen()
        end if
        return true
    end if
    return false
end function
