' Botones o pastillas inclinadas en una fila. Izquierda / derecha mueve el foco y OK avisa en "itemSelected".
' En las orillas (izquierda en el primero, derecha en el último) la tecla sigue a la pantalla, para que decida.

sub init()
    m.strip = m.top.findNode("strip")
    m.items = []
    m.idx = 0
    m.top.observeField("focusedChild", "onFocus")
end sub

sub build()
    m.strip.removeChildrenIndex(m.strip.getChildCount(), 0)
    m.items = []
    c = m.top.content
    if c = invalid then return
    small = m.top.small
    ' Medidas de Fire TV pasadas a la pantalla de Roku (1280x720).
    h = 43
    margin = 14
    padL = 24
    padR = 27
    gap = 11
    fontName = "BlackItalic"
    fontSize = 15
    if small
        h = 32
        margin = 10
        padL = 17
        padR = 17
        gap = 7
        fontName = "ExtraBoldItalic"
        fontSize = 12
    end if
    x = 0
    for i = 0 to c.getChildCount() - 1
        item = c.getChild(i)
        g = m.strip.createChild("Group")
        bg = g.createChild("Poster")
        icon = invalid
        lx = padL
        if toS(item.HDPosterUrl) <> ""
            icon = g.createChild("Poster")
            icon.uri = item.HDPosterUrl
            icon.width = 20
            icon.height = 20
            icon.translation = [padL - 3, Int((h - 20) / 2)]
            lx = padL + 25
        end if
        lb = g.createChild("Label")
        lb.font = uFont(fontName, fontSize)
        lb.text = upper(toS(item.title))
        lb.height = h
        lb.vertAlign = "center"
        lb.translation = [lx, 0]
        w = lx + Int(lb.boundingRect().width) + padR
        bg.translation = [0 - margin, 0 - margin]
        bg.width = w + margin * 2
        bg.height = h + margin * 2
        g.translation = [x, 0]
        g.scaleRotateCenter = [w / 2, h / 2]
        m.items.Push({ g: g, bg: bg, lb: lb, icon: icon, w: w, x: x })
        x = x + w + gap
    end for
    if x > 0 then x = x - gap
    m.top.rowWidth = x
    if m.idx >= m.items.Count() then m.idx = 0
    paint()
end sub

sub onFocus()
    paint()
end sub

sub onJump()
    j = m.top.jumpToItem
    if j >= 0 and j < m.items.Count()
        m.idx = j
        m.top.itemFocused = j
        paint()
    end if
end sub

sub paint()
    focused = m.top.hasFocus()
    small = m.top.small
    for i = 0 to m.items.Count() - 1
        p = m.items[i]
        isOn = focused and i = m.idx
        if small
            textColor = "0xB8C6ECFF"
            if isOn
                p.bg.uri = "pkg:/images/chip_white.9.png"
                textColor = "0x1446D8FF"
            else if i = m.top.selectedIndex
                p.bg.uri = "pkg:/images/chip_blue.9.png"
                textColor = "0xFFFFFFFF"
            else
                p.bg.uri = "pkg:/images/chip_glass.9.png"
            end if
            if isOn then p.g.scale = [1.06, 1.06] else p.g.scale = [1, 1]
        else
            filled = isOn or i < m.top.primaryCount
            if isOn
                p.bg.uri = "pkg:/images/btn_white_on.9.png"
            else if filled
                p.bg.uri = "pkg:/images/btn_white.9.png"
            else
                p.bg.uri = "pkg:/images/btn_glass.9.png"
            end if
            if filled then textColor = "0x1446D8FF" else textColor = "0xFFFFFFFF"
            if isOn then p.g.scale = [1.07, 1.07] else p.g.scale = [1, 1]
        end if
        p.lb.color = textColor
        if p.icon <> invalid then p.icon.blendColor = textColor
    end for
    ' Si la fila es más ancha que el espacio, se corre para que se vea el elegido.
    if m.items.Count() > 0 and m.top.rowWidth > m.top.maxWidth
        p = m.items[m.idx]
        shift = 0
        if p.x + p.w > m.top.maxWidth then shift = m.top.maxWidth - (p.x + p.w)
        m.strip.translation = [shift, 0]
    else
        m.strip.translation = [0, 0]
    end if
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "left"
        if m.idx > 0
            m.idx = m.idx - 1
            m.top.itemFocused = m.idx
            paint()
            return true
        end if
        return false
    end if
    if key = "right"
        if m.idx < m.items.Count() - 1
            m.idx = m.idx + 1
            m.top.itemFocused = m.idx
            paint()
            return true
        end if
        return false
    end if
    if key = "OK"
        if m.items.Count() > 0 then m.top.itemSelected = m.idx
        return true
    end if
    return false
end function
