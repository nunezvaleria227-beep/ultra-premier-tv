sub init()
    m.video = m.top.findNode("video")
    m.banner = m.top.findNode("banner")
    m.bannerTitle = m.top.findNode("bannerTitle")
    m.bannerNow = m.top.findNode("bannerNow")
    m.bannerNext = m.top.findNode("bannerNext")
    m.bannerTimer = m.top.findNode("bannerTimer")
    m.saveTimer = m.top.findNode("saveTimer")
    m.tasks = []
    m.items = []
    m.index = 0
    m.mode = "movie"
    m.tsTried = false
    m.autoSubsDone = false
    m.lastStart = 0
    m.leaving = false
    m.lastRecentId = ""

    m.video.observeField("state", "onState")
    m.video.observeField("availableSubtitleTracks", "onSubTracks")
    m.bannerTimer.observeField("fire", "hideBanner")
    m.saveTimer.observeField("fire", "saveProgress")
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
    showBanner(it)
end sub

' ----------------------------------------------------------------- aviso con el nombre (y la guía en vivo)

sub showBanner(it as Object)
    m.bannerTitle.text = displayTitle(it)
    m.bannerNow.text = ""
    m.bannerNext.text = ""
    m.banner.visible = true
    m.bannerTimer.control = "stop"
    m.bannerTimer.control = "start"
    if m.mode = "live"
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
    if state = "error"
        handleError()
    else if state = "finished"
        handleFinished()
    else if state = "paused"
        saveProgress()
    end if
end sub

sub handleError()
    ' En vivo: si .m3u8 falla, se prueba una vez con .ts.
    if m.mode = "live" and m.liveExt = "m3u8" and not m.tsTried
        m.tsTried = true
        playCurrent(0, "ts")
        return
    end if
    detail = "Código " + toS(m.video.errorCode)
    if toS(m.video.errorMsg) <> "" then detail = detail + ": " + toS(m.video.errorMsg)
    m.lastPos = 0
    if m.mode <> "live" then m.lastPos = Int(m.video.position)
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
    if choice = 0
        m.tsTried = false
        playCurrent(m.lastPos, "")
        m.video.setFocus(true)
    else
        leave()
    end if
end sub

sub handleFinished()
    if m.mode = "live"
        ' La transmisión se cortó: se vuelve a pedir.
        playCurrent(0, "")
        return
    end if
    ' Terminó: se quita de "Continuar viendo".
    it = m.items[m.index]
    kind = "movie"
    if m.mode = "episode" then kind = "episode"
    updateContinue({ kind: kind, id: toS(it.id), pos: 1000000, dur: 1000000 })
    if m.mode = "episode" and m.index + 1 < m.items.Count()
        m.index = m.index + 1
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
            showBanner(m.items[m.index])
            return true
        end if
    end if
    return false
end function
