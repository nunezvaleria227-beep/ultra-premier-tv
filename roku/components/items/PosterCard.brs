sub init()
    m.card = m.top.findNode("card")
    m.art = m.top.findNode("art")
    m.title = m.top.findNode("title")
    m.initial = m.top.findNode("initial")
    m.ring = m.top.findNode("ring")
    m.rateBox = m.top.findNode("rateBox")
    m.rate = m.top.findNode("rate")
    m.fav = m.top.findNode("fav")
end sub

' title (con "* " delante si es favorito), HDPosterUrl, Rating (calificación redondeada) y
' ShortDescriptionLine2 = "live" para los canales (se ve el logo completo).
sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    t = c.title
    isFav = (Left(t, 2) = "* ")
    if isFav then t = Mid(t, 3)
    m.title.text = upper(t)
    m.initial.text = upper(Left(t, 1))
    m.fav.visible = isFav
    r = c.Rating
    hasRate = (r <> invalid and r <> "")
    m.rateBox.visible = hasRate
    m.rate.visible = hasRate
    if hasRate then m.rate.text = r
    if c.ShortDescriptionLine2 = "live"
        m.art.loadDisplayMode = "scaleToFit"
        m.art.translation = [14, 50]
        m.art.width = 100
        m.art.height = 92
    else
        m.art.loadDisplayMode = "scaleToZoom"
        m.art.translation = [0, 0]
        m.art.width = 128
        m.art.height = 192
    end if
    m.art.uri = c.HDPosterUrl
    paint()
end sub

sub paint()
    p = 0.0
    if (m.top.gridHasFocus or m.top.rowListHasFocus) and m.top.rowFocusPercent > 0.5 then p = m.top.focusPercent
    s = 1 + 0.07 * p
    m.card.scale = [s, s]
    on = p > 0.5
    m.ring.visible = on
    if on then m.title.color = "0xFFFFFFFF" else m.title.color = "0xB8C6ECFF"
end sub
