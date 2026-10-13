sub init()
    m.thumb = m.top.findNode("thumb")
    m.name = m.top.findNode("name")
    m.subtitle = m.top.findNode("subtitle")
end sub

sub onContent()
    c = m.top.itemContent
    if c = invalid then return
    m.name.text = c.title
    m.subtitle.text = c.description
    m.thumb.uri = c.HDPosterUrl
end sub
