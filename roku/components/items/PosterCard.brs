sub init()
    m.art = m.top.findNode("art")
    m.title = m.top.findNode("title")
    m.favBox = m.top.findNode("favBox")
    m.fav = m.top.findNode("fav")
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    t = c.title
    isFav = (Left(t, 2) = "* ")
    if isFav then t = Mid(t, 3)
    m.title.text = t
    m.favBox.visible = isFav
    m.fav.visible = isFav
    m.art.uri = c.HDPosterUrl
end sub
