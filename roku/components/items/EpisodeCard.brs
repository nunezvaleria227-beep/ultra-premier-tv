sub init()
    m.card = m.top.findNode("card")
    m.art = m.top.findNode("art")
    m.num = m.top.findNode("num")
    m.watch = m.top.findNode("watch")
    m.watchBox = m.top.findNode("watchBox")
    m.barBg = m.top.findNode("barBg")
    m.bar = m.top.findNode("bar")
    m.ring = m.top.findNode("ring")
    m.title = m.top.findNode("title")
    m.subLabel = m.top.findNode("subLabel")
end sub

' ShortDescriptionLine1 = "E3", ShortDescriptionLine2 = texto de abajo, Rating = avance de 0 a 100 (vacío si no se ha visto).
sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    m.title.text = upper(c.title)
    m.num.text = c.ShortDescriptionLine1
    m.subLabel.text = c.ShortDescriptionLine2
    m.art.uri = c.HDPosterUrl
    pct = -1
    if c.Rating <> "" then pct = c.Rating.ToInt()
    watching = (pct >= 0 and pct < 92)
    m.watch.visible = watching
    m.watchBox.visible = watching
    m.barBg.visible = (pct >= 0)
    m.bar.visible = (pct >= 0)
    if pct >= 0
        if pct < 2 then pct = 2
        if pct > 100 then pct = 100
        m.bar.width = Int(196 * pct / 100)
    end if
end sub

sub paint()
    p = 0.0
    if m.top.rowListHasFocus then p = m.top.focusPercent
    s = 1 + 0.05 * p
    m.card.scale = [s, s]
    on = p > 0.5
    m.ring.visible = on
    if on then m.title.color = "0xFFFFFFFF" else m.title.color = "0xF2F6FFFF"
end sub
