sub init()
    m.video = m.top.findNode("video")
    m.banner = m.top.findNode("banner")
    m.bannerTitle = m.top.findNode("bannerTitle")
    m.bannerId = m.top.findNode("bannerId")
    m.bannerNow = m.top.findNode("bannerNow")
    m.bannerNext = m.top.findNode("bannerNext")
    m.bannerTimer = m.top.findNode("bannerTimer")
    m.saveTimer = m.top.findNode("saveTimer")
    m.watchTimer = m.top.findNode("watchTimer")
    m.retryTimer = m.top.findNode("retryTimer")
    m.zapTimer = m.top.findNode("zapTimer")
    m.notice = m.top.findNode("notice")
    m.noticeText = m.top.findNode("noticeText")
    m.tasks = []
    m.items = []
    m.index = 0
    m.mode = "movie"
    m.tsTried = false
    m.autoSubsDone = false
    m.lastStart = 0
    m.leaving = false
    m.lastRecentId = ""

    ' Recuperación automática de la señal (cortes, señal atorada, reconexión del canal en vivo).
    m.stopwatch = CreateObject("roTimespan")
    m.retries = 0              ' intentos seguidos de recuperar la señal
    m.everPlayed = false       ' si esta reproducción ya llegó a verse alguna vez
    m.bufferSince = -1         ' milisegundos en que empezó a "cargar" (-1 si no está cargando)
    m.playingSince = -1        ' milisegundos en que empezó a reproducirse (-1 si no se reproduce)
    m.lastPos = 0              ' última posición vista (para retomar película o serie donde iba)
    m.lastCutPos = -100        ' posición del último corte detectado antes del final
    m.resumedFromCut = false
    m.retryExt = ""
    m.errorOpen = false
    m.liveExt = "m3u8"

    m.video.observeField("state", "onState")
    m.video.observeField("availableSubtitleTracks", "onSubTracks")
    m.bannerTimer.observeField("fire", "hideBanner")
    m.saveTimer.observeField("fire", "saveProgress")
    m.watchTimer.observeField("fire", "onWatch")
    m.retryTimer.observeField("fire", "onRetryTimer")
    m.zapTimer.observeField("fire", "onZapTimer")
    m.watchTimer.control = "start"
end sub

sub focusScreen()
    m.video.setFocus(true)
end sub

sub onParams()
    p = m.top.params
    m.mode = toS(p.mode)
    m.items = p.items
    m.index = p.index
    m.lastStart = toI(p.start)
    m.tsTried = false
    m.retries = 0
    m.lastCutPos = -100
    m.resumedFromCut = false
    playCurrent(m.lastStart, "")
    m.video.setFocus(true)
end sub

function displayTitle(it as Object) as String
    series = toS(it.seriesName)
    if series = "" then return toS(it.name)
    t = series + "  ·  T" + toI(it.season).ToStr() + " E" + toI(it.num).ToStr()
    if toS(it.name) <> "" then t = t + "  ·  " + toS(it.name)
    return t
end function

' Empieza (o cambia a) el elemento actual. forceExt solo se usa para probar .ts si .m3u8 falla.
sub playCurrent(startPos as Integer, forceExt as String)
    it = m.items[m.index]
    id = toS(it.id)
    m.autoSubsDone = false
    m.everPlayed = false
    m.bufferSince = -1
    m.playingSince = -1
    m.retryTimer.control = "stop"
    setNotice("")
    m.lastPos = 0
    if m.mode <> "live" then m.lastPos = startPos
    content = CreateObject("roSGNode", "ContentNode")
    if m.mode = "live"
        ext = toS(m.global.session.liveExt)
        if forceExt <> "" then ext = forceExt
        if ext = "" then ext = "m3u8"
        content.url = streamUrl("live", id, ext)
        content.streamFormat = formatForExt(ext)
        content.live = true
        m.liveExt = ext
    else
        ext = toS(it.ext)
        if ext = "" then ext = "mp4"
        kind = "movie"
        if m.mode = "episode" then kind = "series"
        content.url = streamUrl(kind, id, ext)
        content.streamFormat = formatForExt(ext)
        if startPos > 0 then content.PlayStart = startPos
    end if
    content.title = displayTitle(it)
    m.video.control = "stop"
    m.video.content = content
    m.video.control = "play"
    m.saveTimer.control = "stop"
    m.saveTimer.control = "start"
    showBanner(it, true)
end sub

' ----------------------------------------------------------------- aviso con el nombre (y la guía en vivo)

' withEpg: false al cambiar de canal rápido (solo se pone el nombre; la guía se pide cuando ya se quedó en el canal).
sub showBanner(it as Object, withEpg as Boolean)
    m.bannerTitle.text = displayTitle(it)
    m.bannerNow.text = ""
    m.bannerNext.text = ""
    m.bannerId.text = ""
    m.banner.visible = true
    m.bannerTimer.control = "stop"
    m.bannerTimer.control = "start"
    if m.mode = "live"
        ' Número de identificación del canal en el portal (sirve para registrar enlaces directos y para soporte).
        m.bannerId.text = "ID del canal: " + toS(it.id)
        if not withEpg then return
        m.epgId = toS(it.id)
        t = CreateObject("roSGNode", "ApiTask")
        t.request = { action: "epg", id: m.epgId, tag: m.epgId }
        t.observeField("response", "onEpg")
        m.tasks.Push(t)
        if m.tasks.Count() > 10 then m.tasks.Shift()
        t.control = "RUN"
    end if
end sub

sub onEpg(event as Object)
    resp = event.getData()
    if resp.tag <> m.epgId then return
    if resp.ok = true and resp.nowEntry <> invalid
        e = resp.nowEntry
        m.bannerNow.text = "Ahora (" + fmtHM(e.start) + " - " + fmtHM(e.stop) + "):  " + e.title
    end if
    if resp.ok = true and resp.nextEntry <> invalid
        u = resp.nextEntry
        m.bannerNext.text = "Después (" + fmtHM(u.start) + "):  " + u.title
    end if
end sub

sub hideBanner()
    m.banner.visible = false
end sub

' ----------------------------------------------------------------- estados del video

sub onState()
    state = m.video.state
    now = m.stopwatch.TotalMilliseconds()
    ' Se anota desde cuándo está cargando o reproduciéndose; el vigilante usa esto para detectar señal atorada.
    if state = "buffering"
        if m.bufferSince < 0 then m.bufferSince = now
    else
        m.bufferSince = -1
    end if
    if state = "playing"
        if m.playingSince < 0 then m.playingSince = now
        m.everPlayed = true
        m.resumedFromCut = false
        setNotice("")
    else
        m.playingSince = -1
    end if
    if state = "error"
        handleError()
    else if state = "finished"
        handleFinished()
    else if state = "paused"
        saveProgress()
    end if
end sub

' ----------------------------------------------------------------- recuperación automática de la señal

' Cada 2 segundos revisa que la señal no se haya quedado atorada y recuerda dónde va la película o serie.
sub onWatch()
    if m.leaving or m.errorOpen or m.items.Count() = 0 then return
    state = m.video.state
    now = m.stopwatch.TotalMilliseconds()
    if state = "playing"
        if m.mode <> "live"
            p = Int(m.video.position)
            if p > 0 then m.lastPos = p
        else if m.playingSince >= 0 and now - m.playingSince > 5000
            ' El canal ya se ve: se recuerda en "Continuar viendo".
            it = m.items[m.index]
            if toS(it.id) <> m.lastRecentId
                m.lastRecentId = toS(it.id)
                touchRecentLive(it)
            end if
        end if
        ' Si lleva un rato viéndose bien, se borran los intentos de recuperación.
        if m.playingSince >= 0 and now - m.playingSince > 20000 then m.retries = 0
    else if state = "buffering" and m.bufferSince >= 0
        ' Cargando demasiado tiempo = señal atorada. Al empezar se espera más que a media reproducción.
        limit = 60000
        if m.mode = "live" then limit = 30000
        if m.everPlayed
            limit = 40000
            if m.mode = "live" then limit = 15000
        end if
        if now - m.bufferSince > limit then recover()
    end if
end sub

' Intenta volver a pedir la señal sin molestar a la persona. Si no se logra después de varios intentos, muestra el aviso.
sub recover()
    if m.leaving then return
    limit = 2
    if m.everPlayed then limit = 4
    if m.retries >= limit
        showErrorDialog()
        return
    end if
    m.retries = m.retries + 1
    m.bufferSince = -1
    m.video.control = "stop"
    m.retryExt = ""
    if m.mode = "live" then m.retryExt = m.liveExt
    setNotice("Reconectando…")
    ' Una pausa corta deja que el servidor libere la conexión anterior (2, 3, 4… segundos).
    m.retryTimer.control = "stop"
    m.retryTimer.duration = 1 + m.retries
    m.retryTimer.control = "start"
end sub

sub onRetryTimer()
    if m.leaving or m.items.Count() = 0 then return
    pos = 0
    if m.mode <> "live" then pos = m.lastPos
    playCurrent(pos, m.retryExt)
end sub

sub setNotice(text as String)
    m.noticeText.text = text
    m.notice.visible = (text <> "")
end sub

sub handleError()
    ' En vivo: si .m3u8 falla al empezar, se prueba una vez con .ts.
    if m.mode = "live" and m.liveExt = "m3u8" and not m.tsTried and not m.everPlayed and m.retries = 0
        m.tsTried = true
        playCurrent(0, "ts")
        return
    end if
    ' Se retomó una película o serie justo donde se había cortado y ya no hay nada más: es el final de verdad.
    if m.mode <> "live" and m.resumedFromCut and not m.everPlayed
        finishPlayback()
        return
    end if
    recover()
end sub

sub showErrorDialog()
    setNotice("")
    detail = "Código " + toS(m.video.errorCode)
    if toS(m.video.errorMsg) <> "" then detail = detail + ": " + toS(m.video.errorMsg)
    m.errorOpen = true
    dlg = CreateObject("roSGNode", "Dialog")
    dlg.title = "No se pudo reproducir"
    dlg.message = "Revisa tu conexión o prueba con otro título." + Chr(10) + detail
    dlg.buttons = ["Reintentar", "Salir"]
    dlg.observeFieldScoped("buttonSelected", "onErrorChoice")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub onErrorChoice()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    dlg.close = true
    m.dialog = invalid
    m.errorOpen = false
    if choice = 0
        m.tsTried = false
        m.retries = 0
        pos = 0
        if m.mode <> "live" then pos = m.lastPos
        playCurrent(pos, "")
        m.video.setFocus(true)
    else
        leave()
    end if
end sub

sub handleFinished()
    if m.leaving then return
    if m.mode = "live"
        ' La transmisión en vivo no termina nunca: si "terminó", el servidor cortó la conexión y se vuelve a pedir.
        recover()
        return
    end if
    ' Película o episodio: si terminó muy antes del final, la conexión se cortó; se retoma donde iba.
    pos = m.lastPos
    vp = Int(m.video.position)
    if vp > pos then pos = vp
    dur = Int(m.video.duration)
    if dur > 120 and pos > 5 and pos < dur - 60 and Abs(pos - m.lastCutPos) > 20
        m.lastCutPos = pos
        m.lastPos = pos
        m.resumedFromCut = true
        recover()
        return
    end if
    finishPlayback()
end sub

' Terminó de verdad: se quita de "Continuar viendo" y se sigue con el siguiente episodio, o se sale.
sub finishPlayback()
    it = m.items[m.index]
    kind = "movie"
    if m.mode = "episode" then kind = "episode"
    updateContinue({ kind: kind, id: toS(it.id), pos: 1000000, dur: 1000000 })
    if m.mode = "episode" and m.index + 1 < m.items.Count()
        m.index = m.index + 1
        m.retries = 0
        m.lastCutPos = -100
        m.resumedFromCut = false
        playCurrent(0, "")
        return
    end if
    m.leaving = true
    leave()
end sub

' ----------------------------------------------------------------- continuar viendo

sub saveProgress()
    if m.leaving then return
    if m.items.Count() = 0 then return
    it = m.items[m.index]
    ' En vivo: se recuerda el canal en cuanto lleva unos segundos reproduciéndose.
    if m.mode = "live"
        id = toS(it.id)
        if id <> m.lastRecentId and m.video.state = "playing"
            m.lastRecentId = id
            touchRecentLive(it)
        end if
        return
    end if
    secs = Int(m.video.position)
    dur = Int(m.video.duration)
    if secs < 5 then return
    kind = "movie"
    if m.mode = "episode" then kind = "episode"
    updateContinue({ kind: kind, id: toS(it.id), name: displayTitle(it), icon: toS(it.icon), ext: toS(it.ext), pos: secs, dur: dur })
end sub

sub leave()
    m.saveTimer.control = "stop"
    m.watchTimer.control = "stop"
    m.retryTimer.control = "stop"
    m.zapTimer.control = "stop"
    saveProgress()
    m.leaving = true
    m.video.control = "stop"
    m.top.event = { type: "back" }
end sub

' ----------------------------------------------------------------- subtítulos y audio

function trackMatches(t as Object, code as String) as Boolean
    lang = LCase(toS(t.Language))
    desc = LCase(toS(t.Description))
    if code = "es"
        if lang = "es" or lang = "spa" or lang = "esl" or Left(lang, 3) = "spa" then return true
        if Instr(1, desc, "span") > 0 or Instr(1, desc, "espa") > 0 then return true
    else if code = "en"
        if lang = "en" or lang = "eng" or Left(lang, 3) = "eng" then return true
        if Instr(1, desc, "engl") > 0 or Instr(1, desc, "ingl") > 0 then return true
    end if
    return false
end function

function pickTrack(tracks as Object, code as String) as Dynamic
    for each t in tracks
        if Type(t) = "roAssociativeArray"
            if trackMatches(t, code) then return t
        end if
    end for
    return invalid
end function

sub applyTrack(t as Object)
    m.video.globalCaptionMode = "On"
    m.video.subtitleTrack = toS(t.TrackName)
end sub

' Al empezar: si el video trae subtítulos, se pone el idioma preferido (español; si no hay, inglés).
sub onSubTracks()
    if m.autoSubsDone then return
    tracks = m.video.availableSubtitleTracks
    if tracks = invalid or Type(tracks) <> "roArray" or tracks.Count() = 0 then return
    pref = regRead("subpref")
    if pref = "" then pref = "es"
    if pref = "off"
        m.autoSubsDone = true
        return
    end if
    t = pickTrack(tracks, pref)
    if t = invalid and pref = "es" then t = pickTrack(tracks, "en")
    if t <> invalid
        m.autoSubsDone = true
        applyTrack(t)
    end if
end sub

sub showDialog(title as String, message as String, buttons as Object, mode as String)
    dlg = CreateObject("roSGNode", "Dialog")
    dlg.title = title
    dlg.message = message
    dlg.buttons = buttons
    m.dialogButtons = buttons
    m.dialogMode = mode
    dlg.observeFieldScoped("buttonSelected", "onOptionChoice")
    m.dialog = dlg
    m.top.getScene().dialog = dlg
end sub

sub showOptions()
    buttons = ["Subtítulos"]
    audio = m.video.availableAudioTracks
    if audio <> invalid and Type(audio) = "roArray" and audio.Count() > 1 then buttons.Push("Audio")
    buttons.Push("Cerrar")
    showDialog("Opciones", "", buttons, "root")
end sub

sub showSubtitleMenu()
    tracks = m.video.availableSubtitleTracks
    m.subChoices = []
    labels = []
    if tracks <> invalid and Type(tracks) = "roArray"
        n = 0
        for each t in tracks
            if Type(t) = "roAssociativeArray" and n < 6
                label = toS(t.Description)
                if label = "" then label = toS(t.Language)
                if label = "" then label = "Subtítulos " + Str(n + 1).Trim()
                labels.Push(label)
                m.subChoices.Push(t)
                n = n + 1
            end if
        end for
    end if
    labels.Push("Sin subtítulos")
    msg = ""
    if m.subChoices.Count() = 0 then msg = "Este video no trae subtítulos."
    showDialog("Subtítulos", msg, labels, "subs")
end sub

sub showAudioMenu()
    m.audioChoices = []
    labels = []
    audio = m.video.availableAudioTracks
    if audio <> invalid and Type(audio) = "roArray"
        n = 0
        for each a in audio
            if Type(a) = "roAssociativeArray" and n < 6
                label = toS(a.Name)
                if label = "" then label = toS(a.Language)
                if label = "" then label = "Audio " + Str(n + 1).Trim()
                labels.Push(label)
                m.audioChoices.Push(a)
                n = n + 1
            end if
        end for
    end if
    labels.Push("Cancelar")
    showDialog("Audio", "", labels, "audio")
end sub

sub onOptionChoice()
    dlg = m.dialog
    if dlg = invalid then return
    choice = dlg.buttonSelected
    dlg.close = true
    m.dialog = invalid
    mode = m.dialogMode
    if mode = "root"
        label = m.dialogButtons[choice]
        if label = "Subtítulos"
            showSubtitleMenu()
            return
        else if label = "Audio"
            showAudioMenu()
            return
        end if
    else if mode = "subs"
        m.autoSubsDone = true
        if choice < m.subChoices.Count()
            t = m.subChoices[choice]
            applyTrack(t)
            if trackMatches(t, "es")
                regWrite("subpref", "es")
            else if trackMatches(t, "en")
                regWrite("subpref", "en")
            end if
        else
            m.video.globalCaptionMode = "Off"
            m.video.subtitleTrack = ""
            regWrite("subpref", "off")
        end if
    else if mode = "audio"
        if choice < m.audioChoices.Count()
            m.video.audioTrack = toS(m.audioChoices[choice].Track)
        end if
    end if
    m.video.setFocus(true)
end sub

' ----------------------------------------------------------------- control remoto

sub zap(delta as Integer)
    n = m.items.Count()
    if n < 2 then return
    m.index = (m.index + delta + n) mod n
    m.tsTried = false
    m.retries = 0
    m.playingSince = -1
    m.retryTimer.control = "stop"
    ' Se muestra el nombre del canal al instante, pero la señal se pide cuando la persona deja de cambiar
    ' (si pasa canales muy rápido no se abren conexiones de más, que el servidor cuenta como pantallas).
    showBanner(m.items[m.index], false)
    m.zapTimer.control = "stop"
    m.zapTimer.control = "start"
end sub

sub onZapTimer()
    if m.leaving then return
    playCurrent(0, "")
end sub

function onKeyEvent(key as String, press as Boolean) as Boolean
    if not press then return false
    if key = "back"
        leave()
        return true
    end if
    if key = "options"
        showOptions()
        return true
    end if
    if m.mode = "live"
        if key = "down"
            zap(1)
            return true
        else if key = "up"
            zap(-1)
            return true
        else if key = "OK"
            showBanner(m.items[m.index], true)
            return true
        end if
    end if
    return false
end function
