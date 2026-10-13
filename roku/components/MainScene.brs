' Escena principal: guarda la sesión y maneja la pila de pantallas.

sub init()
    m.top.backgroundColor = "0x0A0C22FF"
    ' "cache" guarda las últimas listas abiertas para que volver a entrar sea instantáneo.
    m.global.addFields({ session: { parental: true }, cache: CreateObject("roSGNode", "Node") })
    m.stack = []
    m.pendingLink = invalid
    showLogin()
    ' Aviso a Roku de que la app ya está lista (lo exige la certificación).
    m.top.signalBeacon("AppLaunchComplete")
end sub

' ----------------------------------------------------------------- pila de pantallas

sub pushScreen(node as Object)
    if m.stack.Count() > 0
        m.stack[m.stack.Count() - 1].visible = false
    end if
    node.observeField("event", "onEvent")
    m.stack.Push(node)
    m.top.AppendChild(node)
    node.callFunc("focusScreen")
end sub

sub popScreen()
    if m.stack.Count() <= 1 then return
    top = m.stack.Pop()
    if top.subtype() = "SearchScreen" then top.callFunc("onClose")
    m.top.RemoveChild(top)
    prev = m.stack[m.stack.Count() - 1]
    prev.visible = true
    prev.callFunc("focusScreen")
end sub

sub clearStack()
    for each s in m.stack
        if s.subtype() = "SearchScreen" then s.callFunc("onClose")
        m.top.RemoveChild(s)
    end for
    m.stack = []
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if press and key = "back" and m.stack.Count() > 1
        popScreen()
        return true
    end if
    return false
end function

' ----------------------------------------------------------------- pantallas

sub showLogin()
    clearStack()
    pushScreen(CreateObject("roSGNode", "LoginScreen"))
end sub

sub showHome()
    clearStack()
    pushScreen(CreateObject("roSGNode", "HomeScreen"))
end sub

sub openBrowse(kind as String)
    s = CreateObject("roSGNode", "BrowseScreen")
    s.kind = kind
    pushScreen(s)
end sub

sub openMovie(item as Object)
    s = CreateObject("roSGNode", "MovieScreen")
    s.item = item
    pushScreen(s)
end sub

sub openSeries(item as Object)
    s = CreateObject("roSGNode", "SeriesScreen")
    s.item = item
    pushScreen(s)
end sub

sub openPlayer(params as Object)
    s = CreateObject("roSGNode", "PlayerScreen")
    pushScreen(s)
    s.params = params
end sub

' ----------------------------------------------------------------- eventos de las pantallas

sub onEvent(event as Object)
    e = event.getData()
    t = toS(e.type)
    if t = "loggedIn"
        onLoggedIn(e)
    else if t = "open"
        target = toS(e.target)
        if target = "live" or target = "movie" or target = "series"
            openBrowse(target)
        else if target = "search"
            pushScreen(CreateObject("roSGNode", "SearchScreen"))
        else if target = "continue"
            pushScreen(CreateObject("roSGNode", "ContinueScreen"))
        else if target = "settings"
            pushScreen(CreateObject("roSGNode", "SettingsScreen"))
        end if
    else if t = "openMovie"
        openMovie(e.item)
    else if t = "openSeries"
        openSeries(e.item)
    else if t = "playLive"
        openPlayer({ mode: "live", items: e.items, index: e.index, start: 0 })
    else if t = "play"
        openPlayer({ mode: e.mode, items: e.items, index: e.index, start: e.start })
    else if t = "playEntry"
        playEntry(e.entry)
    else if t = "logout"
        regDelete("user")
        regDelete("pass")
        m.global.session = { parental: true }
        showLogin()
    else if t = "back"
        popScreen()
    end if
end sub

sub onLoggedIn(e as Object)
    regWrite("user", toS(e.user))
    regWrite("pass", toS(e.pass))
    parental = (regRead("parental") <> "0")
    m.global.session = {
        base: portalBase()
        user: toS(e.user)
        pass: toS(e.pass)
        liveExt: toS(e.liveExt)
        parental: parental
        account: e.account
    }
    if m.pendingLink <> invalid
        applyDeepLink()
    else
        showHome()
    end if
end sub

' ----------------------------------------------------------------- enlaces directos (deep linking)
' mediaType: movie (contentId = id de la película), series (id de la serie), live (id del canal).

sub onDeepLink(event as Object)
    m.pendingLink = event.getData()
    applyDeepLink()
end sub

sub applyDeepLink()
    link = m.pendingLink
    if link = invalid then return
    s = m.global.session
    ' Si todavía no hay sesión, se aplica en cuanto entre.
    if s = invalid or s.user = invalid then return
    m.pendingLink = invalid
    id = toS(link.contentId)
    kind = LCase(toS(link.mediaType))
    showHome()
    if id = "" then return
    item = { id: id, name: "", icon: "", ext: "" }
    if kind = "movie"
        openMovie(item)
    else if kind = "series" or kind = "season" or kind = "episode"
        openSeries(item)
    else if kind = "live"
        openPlayer({ mode: "live", items: [item], index: 0, start: 0 })
    end if
end sub

' Seguir viendo algo de la lista "Continuar viendo".
sub playEntry(entry as Object)
    kind = toS(entry.kind)
    item = { id: toS(entry.id), name: toS(entry.name), icon: toS(entry.icon), ext: toS(entry.ext) }
    mode = "movie"
    if kind = "episode" then mode = "episode"
    openPlayer({ mode: mode, items: [item], index: 0, start: toI(entry.pos) })
end sub
