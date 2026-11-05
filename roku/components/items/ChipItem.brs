sub init()
    m.chip = m.top.findNode("chip")
    m.bg = m.top.findNode("bg")
    m.name = m.top.findNode("name")
    m.text = ""
end sub

sub paint()
    c = m.top.itemContent
    if c = invalid then return
    if m.watched = invalid or not m.watched.isSameNode(c)
        m.watched = c
        c.observeFieldScoped("ShortDescriptionLine2", "paint")
    end if
    if c.title <> m.text
        m.text = c.title
        if c.id = "__fav__" then m.name.text = "★ " + upper(c.title) else m.name.text = upper(c.title)
    end if
    sel = (c.ShortDescriptionLine2 = "sel")
    on = m.top.rowListHasFocus and m.top.focusPercent > 0.5
    if on
        m.bg.uri = "pkg:/images/chip_white.9.png"
        m.name.color = "0x1446D8FF"
        m.chip.scale = [1.06, 1.06]
    else if sel
        m.bg.uri = "pkg:/images/chip_blue.9.png"
        m.name.color = "0xFFFFFFFF"
        m.chip.scale = [1, 1]
    else
        m.bg.uri = "pkg:/images/chip_glass.9.png"
        m.name.color = "0xB8C6ECFF"
        m.chip.scale = [1, 1]
    end if
end sub
