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
    else if action = "home"
        res = doHome(req, res)
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
    ' Se prueba el servidor principal y luego los de respaldo, con el mismo usuario y contraseña.
    servers = serverList()
    authRes = invalid
    netRes = invalid
    for each base in servers
        r = loginAt(base, req, 12000)
        if r.ok = true then return copyLogin(r, res)
        if r.authFailed = true
            if authRes = invalid then authRes = r
        else if netRes = invalid
            netRes = r
        end if
    end for
    if authRes = invalid
        ' Ningún servidor contestó: una última vez el principal, esperando más.
        Sleep(500)
        r = loginAt(servers[0], req, 18000)
        if r.ok = true then return copyLogin(r, res)
        if r.authFailed = true then authRes = r
    end if
    pick = authRes
    if pick = invalid then pick = netRes
    return copyLogin(pick, res)
end function

function copyLogin(src as Object, res as Object) as Object
    for each k in src
        res[k] = src[k]
    end for
    return res
end function

function loginAt(base as String, req as Object, timeoutMs as Integer) as Object
    res = { base: base }
    url = base + "/player_api.php?username=" + urlEscape(toS(req.user)) + "&password=" + urlEscape(toS(req.pass))
    ' Para entrar se espera menos que en las demás consultas: si el servidor no contesta, la persona lo sabe pronto.
    info = httpGetInfo(url, timeoutMs)
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
                rate = ""
                if kind <> "live" then rate = ratingBadge(s.rating)
                list.Push({ id: id, title: title, HDPosterUrl: small, FHDPosterUrl: icon, ShortDescriptionLine1: title, StreamFormat: ext, Rating: rate })
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
        backdrop: firstUrl(info.backdrop_path)
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
        backdrop: firstUrl(info.backdrop_path)
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

' ----------------------------------------------------------------- inicio: lo más nuevo de películas, series y un grupo de canales

function firstUrl(v as Dynamic) as String
    if v = invalid then return ""
    if Type(v) = "roArray"
        for each u in v
            t = toS(u)
            if t <> "" then return t
        end for
        return ""
    end if
    return toS(v)
end function

' Pide varias direcciones a la vez. Devuelve los textos en el mismo orden (invalid donde falló).
function fetchAll(urls as Object, timeoutMs as Integer) as Object
    port = CreateObject("roMessagePort")
    xfers = []
    pendingIds = {}
    out = []
    for i = 0 to urls.Count() - 1
        x = CreateObject("roUrlTransfer")
        x.SetPort(port)
        x.SetUrl(urls[i])
        x.EnableEncodings(true)
        x.AddHeader("User-Agent", "Mozilla/5.0 (UltraPremierTV Roku)")
        if x.AsyncGetToString() then pendingIds[x.GetIdentity().ToStr()] = i
        xfers.Push(x)
        out.Push(invalid)
    end for
    clock = CreateObject("roTimespan")
    while pendingIds.Count() > 0 and clock.TotalMilliseconds() < timeoutMs
        msg = Wait(400, port)
        if Type(msg) = "roUrlEvent"
            key = msg.GetSourceIdentity().ToStr()
            if pendingIds.DoesExist(key)
                code = msg.GetResponseCode()
                if code >= 200 and code < 300 then out[pendingIds[key]] = msg.GetString()
                pendingIds.Delete(key)
            end if
        end if
    end while
    for each x in xfers
        x.AsyncCancel()
    end for
    return out
end function

' Las primeras categorías (sin las de adultos si el control parental está activo).
function firstCats(act as String, n as Integer) as Object
    found = []
    json = getJson(apiUrl(act, ""))
    if json = invalid or Type(json) <> "roArray" then return found
    hideAdult = parentalOn()
    for each c in json
        if Type(c) = "roAssociativeArray"
            name = toS(c.category_name)
            id = toS(c.category_id)
            if id <> "" and not (hideAdult and isAdultName(name)) then found.Push({ id: id, name: name })
        end if
        if found.Count() >= n then exit for
    end for
    return found
end function

function homeEntry(s as Object, kind as String, catName as String) as Dynamic
    name = toS(s.name)
    if name = "" then return invalid
    e = { kind: kind, id: "", name: name, icon: "", backdrop: "", plot: "", year: "", genre: "", rating: "", score: 0.0, ext: "", cat: catName, added: 0 }
    if kind = "movie"
        e.id = toS(s.stream_id)
        e.icon = toS(s.stream_icon)
        e.ext = toS(s.container_extension)
        if e.ext = "" then e.ext = "mp4"
        e.rating = toS(s.rating)
        e.score = ratingNum(s.rating)
        e.added = toI(s.added)
    else if kind = "series"
        e.id = toS(s.series_id)
        e.icon = toS(s.cover)
        e.backdrop = firstUrl(s.backdrop_path)
        e.plot = toS(s.plot)
        e.genre = toS(s.genre)
        y = toS(s.releaseDate)
        if Len(y) >= 4 then y = Left(y, 4)
        e.year = y
        e.rating = toS(s.rating)
        e.score = ratingNum(s.rating)
        e.added = toI(s.last_modified)
    else
        e.id = toS(s.stream_id)
        e.icon = toS(s.stream_icon)
    end if
    if e.id = "" then return invalid
    return e
end function

sub addHomeItems(txt as Dynamic, kind as String, catName as String, out as Object, seen as Object, limit as Integer)
    if txt = invalid then return
    json = ParseJson(txt)
    if json = invalid or Type(json) <> "roArray" then return
    hideAdult = parentalOn()
    n = 0
    for each s in json
        if Type(s) = "roAssociativeArray"
            e = homeEntry(s, kind, catName)
            if e <> invalid
                key = kind + e.id
                if not seen.DoesExist(key) and not (hideAdult and isAdultName(e.name))
                    seen[key] = true
                    out.Push(e)
                    n = n + 1
                    if n >= limit then exit for
                end if
            end if
        end if
    end for
end sub

function doHome(req as Object, res as Object) as Object
    movieCats = firstCats("get_vod_categories", 3)
    seriesCats = firstCats("get_series_categories", 2)
    liveCats = firstCats("get_live_categories", 1)
    urls = []
    for each c in movieCats
        urls.Push(apiUrl("get_vod_streams", "&category_id=" + urlEscape(c.id)))
    end for
    for each c in seriesCats
        urls.Push(apiUrl("get_series", "&category_id=" + urlEscape(c.id)))
    end for
    for each c in liveCats
        urls.Push(apiUrl("get_live_streams", "&category_id=" + urlEscape(c.id)))
    end for
    texts = fetchAll(urls, 30000)
    movies = []
    series = []
    live = []
    seen = {}
    i = 0
    for each c in movieCats
        addHomeItems(texts[i], "movie", c.name, movies, seen, 4000)
        texts[i] = invalid
        i = i + 1
    end for
    for each c in seriesCats
        addHomeItems(texts[i], "series", c.name, series, seen, 4000)
        texts[i] = invalid
        i = i + 1
    end for
    liveName = ""
    for each c in liveCats
        addHomeItems(texts[i], "live", c.name, live, seen, 20)
        texts[i] = invalid
        liveName = c.name
        i = i + 1
    end for
    if movies.Count() = 0 and series.Count() = 0 and live.Count() = 0
        res.error = "No se pudo cargar el inicio. Revisa tu internet e inténtalo otra vez."
        return res
    end if
    ' "Mejor calificadas": películas con 7 o más, de la mejor a la peor.
    top = []
    for each e in movies
        if e.score >= 7 then top.Push(e)
    end for
    top.SortBy("score", "r")
    while top.Count() > 20
        top.Pop()
    end while
    movies.SortBy("added", "r")
    series.SortBy("added", "r")
    while movies.Count() > 20
        movies.Pop()
    end while
    while series.Count() > 20
        series.Pop()
    end while
    res.ok = true
    res.movies = movies
    res.top = top
    res.series = series
    res.live = live
    res.liveName = liveName
    return res
end function
