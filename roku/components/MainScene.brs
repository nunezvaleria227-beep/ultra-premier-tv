' Escena principal: guarda la sesión y maneja la pila de pantallas.

sub init()
    m.top.backgroundColor = "0x0A0C22FF"
    ' "cache" guarda las últimas listas abiertas para que volver a entrar sea instantáneo.
    m.global.addFields({ session: { parental: true }, cache: CreateObject("roSGNode", "Node") })
    m.stack = []
    m.pendingLink = invalid
    ' Avisos de arranque a Roku (lo exige la certificación): "AppLaunchComplete" cuando el inicio ya se ve y,
    ' si antes hay que escribir usuario y contraseña, "AppDialogInitiate" / "AppDialogComplete" alrededor de esa espera.
    m.launchDone = false
    m.dialogOpen = false
    ' Con un enlace directo a una película o canal, "AppLaunchComplete" se manda cuando empieza la reproducción;
    ' si no empieza a tiempo, este reloj lo manda igual para no dejar el arranque sin aviso.
    m.launchTimer = CreateObject("roSGNode", "Timer")
    m.launchTimer.duration = 10
    m.launchTimer.repeat = false
    m.launchTimer.observeField("fire", "onLaunchTimer")
    m.prefetch = []
    m.prefetchUser = ""
    ' Tienda de Roku: solo se usa para la pantalla oficial "Solicitud de información" al iniciar sesión.
    m.store = CreateObject("roSGNode", "ChannelStore")
    m.store.observeField("userData", "onRokuUserData")
    showLogin()
end sub

' La persona tiene que escribir sus datos antes de ver el inicio: esa espera no cuenta como tiempo de arranque de la app.
sub beginDialog()
    if m.launchDone or m.dialogOpen then return
    m.dialogOpen = true
    m.top.signalBeacon("AppDialogInitiate")
end sub

' El inicio ya se muestra: se cierra la espera (si la hubo) y se avisa que la app está lista.
sub endDialog()
    if m.dialogOpen
        m.dialogOpen = false
        m.top.signalBeacon("AppDialogComplete")
    end if
end sub

sub finishLaunch()
    if m.launchDone then return
    endDialog()
    m.launchDone = true
    m.top.signalBeacon("AppLaunchComplete")
end sub

sub onLaunchTimer()
    finishLaunch()
end sub

' ¿Este enlace directo hace que empiece a reproducirse algo? (Roku pide reproducir directo en todos los tipos.)
function linkPlays(link as Dynamic) as Boolean
    if link = invalid then return false
    if Type(link) <> "roAssociativeArray" then return false
    if toS(link.contentId) = "" then return false
    kind = LCase(toS(link.mediaType))
    if kind = "movie" or kind = "tvspecial" or kind = "shortformvideo" then return true
    if kind = "series" or kind = "season" or kind = "episode" then return true
    if kind = "live" or kind = "livefeed" or kind = "sportsevent" then return true
    return false
end function

' Roku avisa que la app usa mucha memoria: se sueltan las listas guardadas para volver a pedirlas cuando se necesiten.
sub onMemoryWarning()
    cacheClear()
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
    ' Sin sesión guardada, la persona debe escribir usuario y contraseña antes de ver el inicio.
    if regRead("user") = "" or regRead("pass") = "" then beginDialog()
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
    else if t = "loginFailed"
        ' La entrada automática no funcionó: ahora la persona debe corregir sus datos.
        beginDialog()
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
        regDelete("account")
        regDelete("liveExt")
        m.prefetchUser = ""
        cacheClear()
        m.global.session = { parental: true }
        showLogin()
    else if t = "back"
        popScreen()
    else if t = "started"
        ' Ya se ve la primera imagen del video.
        m.launchTimer.control = "stop"
        finishLaunch()
    end if
end sub

sub onLoggedIn(e as Object)
    regWrite("user", toS(e.user))
    regWrite("pass", toS(e.pass))
    regWrite("liveExt", toS(e.liveExt))
    if e.account <> invalid then regWrite("account", FormatJson(e.account))
    parental = (regRead("parental") <> "0")
    m.global.session = {
        base: portalBase()
        user: toS(e.user)
        pass: toS(e.pass)
        liveExt: toS(e.liveExt)
        parental: parental
        account: e.account
    }
    holdBeacon = linkPlays(m.pendingLink)
    if m.pendingLink <> invalid
        applyDeepLink()
    else
        showHome()
    end if
    if holdBeacon and not m.launchDone
        ' La espera de usuario y contraseña (si la hubo) termina ahora; el aviso de "listo" sale cuando empieza
        ' la reproducción (evento "started") o a los 10 segundos.
        endDialog()
        m.launchTimer.control = "start"
    else
        finishLaunch()
    end if
    ' Roku exige que las apps con inicio de sesión muestren su pantalla de "Solicitud de información"
    ' (getUserData) al entrar. Solo cuando la persona inicia sesión a mano, no en la entrada automática.
    if e.manual = true then askRokuEmail()
    prefetchCategories(toS(e.user))
    ' Si se abrió con la sesión guardada, la cuenta se comprueba ahora en segundo plano.
    if e.restored = true then checkSession(toS(e.user), toS(e.pass))
end sub

' Vuelve a comprobar usuario y contraseña sin hacer esperar a nadie. Si el servidor ya no los acepta, se pide entrar de nuevo.
sub checkSession(user as String, pass as String)
    t = CreateObject("roSGNode", "ApiTask")
    t.request = { action: "login", user: user, pass: pass, tag: user }
    t.observeField("response", "onSessionChecked")
    m.checkTask = t
    t.control = "RUN"
end sub

sub onSessionChecked(event as Object)
    resp = event.getData()
    s = m.global.session
    if resp = invalid or s = invalid or s.user = invalid then return
    if toS(resp.tag) <> toS(s.user) then return
    if resp.ok = true
        regWrite("liveExt", toS(resp.liveExt))
        if resp.account <> invalid then regWrite("account", FormatJson(resp.account))
        fresh = {}
        for each k in s
            fresh[k] = s[k]
        end for
        fresh.liveExt = toS(resp.liveExt)
        fresh.account = resp.account
        m.global.session = fresh
    else if resp.authFailed = true
        regDelete("user")
        regDelete("pass")
        regDelete("account")
        regDelete("liveExt")
        regWrite("lastTry", "Tu sesión ya no es válida. Escribe tu usuario y contraseña otra vez.")
        m.prefetchUser = ""
        cacheClear()
        m.global.session = { parental: true }
        showLogin()
    end if
end sub

' Mientras la persona mira el inicio, se bajan en segundo plano las categorías de canales, películas y series
' para que esas pantallas abran al instante.
sub prefetchCategories(user as String)
    m.prefetchUser = user
    m.prefetch = []
    for each kind in ["live", "movie", "series"]
        t = CreateObject("roSGNode", "ApiTask")
        t.request = { action: "categories", type: kind, tag: kind }
        t.observeField("response", "onPrefetched")
        m.prefetch.Push(t)
        t.control = "RUN"
    end for
end sub

sub onPrefetched(event as Object)
    resp = event.getData()
    if resp = invalid or resp.ok <> true or resp.content = invalid then return
    ' Si entretanto se cerró la sesión o entró otra cuenta, esa respuesta ya no sirve.
    s = m.global.session
    if s = invalid or s.user = invalid then return
    if toS(s.user) <> m.prefetchUser then return
    cachePut("cats_" + toS(resp.tag), resp.content)
end sub

' Muestra la pantalla de Roku que pide compartir el correo de la cuenta Roku. Es opcional para la persona:
' si la rechaza o no hay respuesta, la app sigue igual. La app no guarda ni envía ese correo a ningún lado.
sub askRokuEmail()
    info = CreateObject("roSGNode", "ContentNode")
    info.addFields({ context: "signin" })
    m.store.requestedUserDataInfo = info
    m.store.requestedUserData = "email"
    m.store.command = "getUserData"
end sub

sub onRokuUserData()
    ' Nada que hacer: la app no usa el correo de la cuenta Roku.
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
    if kind = "movie" or kind = "tvspecial" or kind = "shortformvideo"
        ' Roku espera que un enlace directo a una película empiece a reproducirla.
        item.autoplay = true
        openMovie(item)
    else if kind = "series" or kind = "season"
        ' A una serie se entra reproduciendo un episodio (donde se quedó la persona, o el primero).
        item.autoplay = true
        openSeries(item)
    else if kind = "episode"
        saved = findContinue("episode", id)
        secs = 0
        if saved <> invalid
            if toS(saved.ext) <> "" then item.ext = toS(saved.ext)
            if toI(saved.pos) >= 5 then secs = toI(saved.pos)
        end if
        if item.ext = "" then item.ext = "mp4"
        item.name = "Episodio"
        openPlayer({ mode: "episode", items: [item], index: 0, start: secs })
    else if kind = "live" or kind = "livefeed" or kind = "sportsevent"
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
