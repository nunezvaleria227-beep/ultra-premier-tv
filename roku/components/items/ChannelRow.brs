sub init()
    m.logo = m.top.findNode("logo")
    m.name = m.top.findNode("name")
    m.favBox = m.top.findNode("favBox")
    m.fav = m.top.findNode("fav")
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    t = c.title
    isFav = (Left(t, 2) = "* ")
    if isFav then t = Mid(t, 3)
    m.name.text = t
    m.favBox.visible = isFav
    m.fav.visible = isFav
    m.logo.uri = c.HDPosterUrl
end sub
