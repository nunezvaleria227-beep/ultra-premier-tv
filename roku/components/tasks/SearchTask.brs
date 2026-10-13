sub init()
    m.top.functionName = "doWork"
end sub

function getJson(url as String) as Dynamic
    txt = httpGet(url)
    if txt = invalid then return invalid
    return ParseJson(txt)
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
            for each catId in ids
                if m.top.cancel then exit for
                i = i + 1
                m.top.progress = "Buscando en " + labels[kind] + " (" + i.ToStr() + " de " + total.ToStr() + ")…"
                list = getJson(apiUrl(itemAct, "&category_id=" + urlEscape(catId)))
                if list <> invalid and Type(list) = "roArray"
                    added = false
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
                                    added = true
                                    if found.Count() >= 150
                                        full = true
                                        exit for
                                    end if
                                end if
                            end if
                        end if
                    end for
                    if added then m.top.results = found
                end if
                if full then exit for
            end for
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
