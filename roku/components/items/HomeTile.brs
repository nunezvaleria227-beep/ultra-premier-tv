sub init()
    m.art = m.top.findNode("art")
    m.title = m.top.findNode("title")
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    m.title.text = c.title
    m.art.uri = c.HDPosterUrl
end sub
