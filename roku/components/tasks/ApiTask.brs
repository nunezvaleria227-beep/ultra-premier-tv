sub init()
    m.top.functionName = "doWork"
end sub

sub doWork()
    req = m.top.request
    action = toS(req.action)
    res = { ok: false, action: action, tag: toS(req.tag), error: "", diag: "", authFailed: false }
    if action = "login"
        res = doLogin(req, res)
    else if action = "categories"
        res = doCategories(req, res)
    else if action = "items"
        res = doItems(req, res)
    else if action = "vodinfo"
        res = doVodInfo(req, res)
    else if action = "seriesinfo"
        res = doSeriesInfo(req, res)
    else if action = "epg"
        res = doEpg(req, res)
    else
        res.error = "Acción desconocida."
    end if
    m.top.response = res
end sub

function getJson(url as String) as Dynamic
    txt = httpGetRetry(url)
    if txt = invalid then return invalid
    return ParseJson(txt)
end function

function parentalOn() as Boolean
    s = m.global.session
    if s <> invalid and s.parental = true then return true
    return false
end function

' ----------------------------------------------------------------- inicio de sesión

function doLogin(req as Object, res as Object) as Object
    url = portalBase() + "/player_api.php?username=" + urlEscape(toS(req.user)) + "&password=" + urlEscape(toS(req.pass))
    ' Para entrar se espera menos que en las demás consultas: si el servidor no contesta, la persona lo sabe pronto.
    info = httpGetInfo(url, 12000)
    if info.body = invalid
        Sleep(500)
        info = httpGetInfo(url, 18000)
    end if
    json = invalid
    if info.body <> invalid then json = ParseJson(info.body)
    if json = invalid
        res.error = "No se pudo conectar con el servidor. Revisa tu internet e inténtalo otra vez."
        codeNum = info.code
        codeText = codeNum.ToStr()
        if info.body = invalid
            res.diag = "sin conexión con el servidor (código " + codeText + ", " + toS(info.why) + ")"
        else
            res.diag = "el servidor respondió algo que no se entiende"
        end if
        return res
    end if
    if Type(json) <> "roAssociativeArray"
        res.error = "Usuario o contraseña incorrectos."
        res.diag = "el servidor no aceptó el usuario o la contraseña"
        res.authFailed = true
        return res
    end if
    ui = json.user_info
    if ui = invalid or Type(ui) <> "roAssociativeArray"
        res.error = "Usuario o contraseña incorrectos."
        res.diag = "el servidor no aceptó el usuario o la contraseña"
        res.authFailed = true
        return res
    end if
    if toS(ui.auth) <> "1"
        res.error = "Usuario o contraseña incorrectos."
        res.diag = "el servidor no aceptó el usuario o la contraseña"
        res.authFailed = true
        return res
    end if
    status = toS(ui.status)
    if status <> "" and LCase(status) <> "active"
        res.error = "Tu cuenta no está activa (" + status + ")."
        res.diag = "cuenta no activa (" + status + ")"
        res.authFailed = true
        return res
    end if
    liveExt = "m3u8"
    formats = ui.allowed_output_formats
    if formats <> invalid and Type(formats) = "roArray" and formats.Count() > 0
        hasM3u8 = false
        hasTs = false
        for each f in formats
            lf = LCase(toS(f))
            if lf = "m3u8" then hasM3u8 = true
            if lf = "ts" then hasTs = true
        end for
        if not hasM3u8 and hasTs then liveExt = "ts"
    end if
    res.ok = true
    res.liveExt = liveExt
    res.account = {
        exp: toI(ui.exp_date)
        maxCon: toI(ui.max_connections)
        activeCon: toI(ui.active_cons)
        status: status
        user: toS(ui.username)
    }
    return res
end function

' ----------------------------------------------------------------- categorías

function doCategories(req as Object, res as Object) as Object
    kind = toS(req.type)
    act = "get_live_categories"
    if kind = "movie" then act = "get_vod_categories"
    if kind = "series" then act = "get_series_categories"
    json = getJson(apiUrl(act, ""))
    if json = invalid or Type(json) <> "roArray"
        res.error = "No se pudieron cargar las categorías. Inténtalo otra vez."
        return res
    end if
    hideAdult = parentalOn()
    root = CreateObject("roSGNode", "ContentNode")
    ' La fila "Favoritos" va siempre primero (así la lista se puede guardar tal cual en la memoria rápida).
    fav = root.CreateChild("ContentNode")
    fav.id = "__fav__"
    fav.title = "Favoritos"
    for each c in json
        if Type(c) = "roAssociativeArray"
            name = toS(c.category_name)
            if not (hideAdult and isAdultName(name))
                node = root.CreateChild("ContentNode")
                node.id = toS(c.category_id)
                node.title = name
            end if
        end if
    end for
    res.ok = true
    res.content = root
    return res
end function

' ----------------------------------------------------------------- canales, películas o series de una categoría

function doItems(req as Object, res as Object) as Object
    kind = toS(req.type)
    catId = toS(req.categoryId)
    act = "get_live_streams"
    if kind = "movie" then act = "get_vod_streams"
    if kind = "series" then act = "get_series"
    json = getJson(apiUrl(act, "&category_id=" + urlEscape(catId)))
    if json = invalid or Type(json) <> "roArray"
        res.error = "No se pudo cargar esta categoría. Inténtalo otra vez."
        return res
    end if
    favs = favoriteIds(kind)
    ' Se arma una lista simple y los nodos se crean de un solo golpe (mucho más rápido que uno por uno).
    list = []
    for each s in json
        if Type(s) = "roAssociativeArray"
            id = ""
            icon = ""
            ext = ""
            desc = ""
            if kind = "live"
                id = toS(s.stream_id)
                icon = toS(s.stream_icon)
            else if kind = "movie"
                id = toS(s.stream_id)
                icon = toS(s.stream_icon)
                ext = toS(s.container_extension)
                if ext = "" then ext = "mp4"
            else
                id = toS(s.series_id)
                icon = toS(s.cover)
            end if
            if id <> ""
                name = toS(s.name)
                if name = "" then name = "Sin nombre"
                title = name
                if favs.DoesExist(id) then title = "* " + name
                small = icon
                if kind <> "live" then small = thumbUrl(icon)
                list.Push({ id: id, title: title, HDPosterUrl: small, FHDPosterUrl: icon, ShortDescriptionLine1: title, StreamFormat: ext })
                if list.Count() >= 3000 then exit for
            end if
        end if
    end for
    root = CreateObject("roSGNode", "ContentNode")
    root.Update({ children: list }, true)
    res.ok = true
    res.content = root
    res.count = list.Count()
    return res
end function

' ----------------------------------------------------------------- ficha de una película

function doVodInfo(req as Object, res as Object) as Object
    json = getJson(apiUrl("get_vod_info", "&vod_id=" + urlEscape(toS(req.id))))
    if json = invalid or Type(json) <> "roAssociativeArray"
        res.error = "No se pudo cargar la información de la película."
        return res
    end if
    info = json.info
    if info = invalid or Type(info) <> "roAssociativeArray" then info = {}
    md = json.movie_data
    ext = ""
    if md <> invalid and Type(md) = "roAssociativeArray" then ext = toS(md.container_extension)
    plot = toS(info.plot)
    if plot = "" then plot = toS(info.description)
    year = toS(info.releasedate)
    if Len(year) >= 4 then year = Left(year, 4)
    res.ok = true
    name = toS(info.name)
    if name = "" and md <> invalid and Type(md) = "roAssociativeArray" then name = toS(md.name)
    res.info = {
        name: name
        plot: plot
        genre: toS(info.genre)
        cast: toS(info.cast)
        director: toS(info.director)
        year: year
        duration: toS(info.duration)
        rating: toS(info.rating)
        cover: toS(info.movie_image)
        ext: ext
    }
    return res
end function

' ----------------------------------------------------------------- ficha de una serie (temporadas y episodios)

sub addSeason(seasons as Object, num as Integer, list as Dynamic)
    if list = invalid or Type(list) <> "roArray" then return
    episodes = []
    for each e in list
        if Type(e) = "roAssociativeArray"
            id = toS(e.id)
            if id <> ""
                ext = toS(e.container_extension)
                if ext = "" then ext = "mp4"
                plot = ""
                image = ""
                dur = 0
                ei = e.info
                if ei <> invalid and Type(ei) = "roAssociativeArray"
                    plot = toS(ei.plot)
                    image = toS(ei.movie_image)
                    dur = toI(ei.duration_secs)
                end if
                title = toS(e.title)
                episodes.Push({ id: id, title: title, num: toI(e.episode_num), ext: ext, plot: plot, image: image, dur: dur })
            end if
        end if
    end for
    if episodes.Count() = 0 then return
    episodes.SortBy("num")
    seasons.Push({ number: num, episodes: episodes })
end sub

function doSeriesInfo(req as Object, res as Object) as Object
    json = getJson(apiUrl("get_series_info", "&series_id=" + urlEscape(toS(req.id))))
    if json = invalid or Type(json) <> "roAssociativeArray"
        res.error = "No se pudo cargar la información de la serie."
        return res
    end if
    info = json.info
    if info = invalid or Type(info) <> "roAssociativeArray" then info = {}
    seasons = []
    eps = json.episodes
    if eps <> invalid
        if Type(eps) = "roAssociativeArray"
            for each key in eps
                addSeason(seasons, key.ToInt(), eps[key])
            end for
        else if Type(eps) = "roArray"
            n = 0
            for each list in eps
                n = n + 1
                addSeason(seasons, n, list)
            end for
        end if
    end if
    if seasons.Count() = 0
        res.error = "Esta serie todavía no tiene episodios."
        return res
    end if
    seasons.SortBy("number")
    year = toS(info.releaseDate)
    if Len(year) >= 4 then year = Left(year, 4)
    res.ok = true
    res.info = {
        name: toS(info.name)
        plot: toS(info.plot)
        genre: toS(info.genre)
        cast: toS(info.cast)
        year: year
        rating: toS(info.rating)
        cover: toS(info.cover)
    }
    res.seasons = seasons
    return res
end function

' ----------------------------------------------------------------- guía (ahora / después)

function doEpg(req as Object, res as Object) as Object
    json = getJson(apiUrl("get_short_epg", "&stream_id=" + urlEscape(toS(req.id)) + "&limit=4"))
    res.ok = true
    res.nowEntry = invalid
    res.nextEntry = invalid
    if json = invalid or Type(json) <> "roAssociativeArray" then return res
    list = json.epg_listings
    if list = invalid or Type(list) <> "roArray" then return res
    t = nowSecs()
    for each l in list
        if Type(l) = "roAssociativeArray"
            st = toI(l.start_timestamp)
            en = toI(l.stop_timestamp)
            entry = { title: decodeB64(toS(l.title)), desc: decodeB64(toS(l.description)), start: st, stop: en }
            if st <= t and t < en
                if res.nowEntry = invalid then res.nowEntry = entry
            else if st >= t
                if res.nextEntry = invalid then res.nextEntry = entry
            end if
        end if
    end for
    return res
end function
