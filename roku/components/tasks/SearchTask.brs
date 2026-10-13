sub init()
    m.top.functionName = "doWork"
end sub

function getJson(url as String) as Dynamic
    txt = httpGetRetry(url)
    if txt = invalid then return invalid
    return ParseJson(txt)
end function

' Pide varias direcciones a la vez (mucho más rápido que una por una).
' Devuelve los textos en el mismo orden que las direcciones; queda invalid donde algo falló o se acabó el tiempo.
function fetchMany(urls as Object, timeoutMs as Integer) as Object
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
    while pendingIds.Count() > 0 and clock.TotalMilliseconds() < timeoutMs and not m.top.cancel
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
    ' Lo que no alcanzó a llegar se cancela.
    for each x in xfers
        x.AsyncCancel()
    end for
    return out
end function

' Agrega a "found" lo que coincide con la búsqueda. Devuelve true si ya se llegó al máximo de resultados.
function collectMatches(list as Dynamic, kind as String, words as Object, found as Object) as Boolean
    if list = invalid or Type(list) <> "roArray" then return false
    for each it in list
        if Type(it) = "roAssociativeArray"
            name = toS(it.name)
            if matchesWords(name, words)
                entry = { kind: kind, name: name, id: "", icon: "", ext: "" }
                if kind = "live"
                    entry.id = toS(it.stream_id)
                    entry.icon = toS(it.stream_icon)
                else if kind = "movie"
                    entry.id = toS(it.stream_id)
                    entry.icon = toS(it.stream_icon)
                    entry.ext = toS(it.container_extension)
                    if entry.ext = "" then entry.ext = "mp4"
                else
                    entry.id = toS(it.series_id)
                    entry.icon = toS(it.cover)
                end if
                if entry.id <> ""
                    found.Push(entry)
                    if found.Count() >= 150 then return true
                end if
            end if
        end if
    end for
    return false
end function

sub doWork()
    words = queryWords(m.top.query)
    scope = m.top.scope
    kinds = []
    if scope = "all" or scope = "live" then kinds.Push("live")
    if scope = "all" or scope = "movie" then kinds.Push("movie")
    if scope = "all" or scope = "series" then kinds.Push("series")
    labels = { live: "canales", movie: "películas", series: "series" }
    hideAdult = false
    s = m.global.session
    if s <> invalid and s.parental = true then hideAdult = true

    found = []
    full = false
    batchSize = 5
    for each kind in kinds
        if m.top.cancel or full then exit for
        catAct = "get_live_categories"
        itemAct = "get_live_streams"
        if kind = "movie"
            catAct = "get_vod_categories"
            itemAct = "get_vod_streams"
        else if kind = "series"
            catAct = "get_series_categories"
            itemAct = "get_series"
        end if
        cats = getJson(apiUrl(catAct, ""))
        if cats <> invalid and Type(cats) = "roArray"
            ids = []
            for each c in cats
                if Type(c) = "roAssociativeArray"
                    if not (hideAdult and isAdultName(toS(c.category_name))) then ids.Push(toS(c.category_id))
                end if
            end for
            total = ids.Count()
            i = 0
            ' Las categorías se piden de 5 en 5 al mismo tiempo.
            while i < total and not m.top.cancel and not full
                last = i + batchSize - 1
                if last > total - 1 then last = total - 1
                urls = []
                for j = i to last
                    urls.Push(apiUrl(itemAct, "&category_id=" + urlEscape(ids[j])))
                end for
                upTo = last + 1
                m.top.progress = "Buscando en " + labels[kind] + " (" + upTo.ToStr() + " de " + total.ToStr() + ")…"
                bodies = fetchMany(urls, 40000)
                before = found.Count()
                for each body in bodies
                    if body <> invalid and not full
                        full = collectMatches(ParseJson(body), kind, words, found)
                    end if
                end for
                if found.Count() > before then m.top.results = found
                i = last + 1
            end while
        end if
    end for

    if m.top.cancel
        m.top.progress = "Búsqueda detenida."
    else if found.Count() = 0
        m.top.progress = "No se encontró nada con ese texto."
    else if full
        m.top.progress = "Se muestran los primeros 150 resultados."
    else
        m.top.progress = found.Count().ToStr() + " resultados."
    end if
    m.top.finished = true
end sub
