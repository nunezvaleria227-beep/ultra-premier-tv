' Utilidades compartidas. Cada componente que las necesita incluye este archivo.

function appVersion() as String
    return "1.0"
end function

function portalBase() as String
    return "http://gazytv.vip:8880"
end function

' ----------------------------------------------------------------- texto y números

' Convierte un valor simple (texto, número, booleano) a texto. Lo demás queda vacío.
function toS(v as Dynamic) as String
    if v = invalid then return ""
    t = Type(v)
    if t = "String" or t = "roString" then return v
    if t = "Integer" or t = "roInt" or t = "LongInteger" or t = "roLongInteger" then return v.ToStr()
    if t = "Float" or t = "roFloat" or t = "Double" or t = "roDouble" then return Int(v).ToStr()
    if t = "Boolean" or t = "roBoolean"
        if v then return "true"
        return "false"
    end if
    return ""
end function

function toI(v as Dynamic) as Integer
    s = toS(v)
    if s = "" then return 0
    return s.ToInt()
end function

function two(n as Integer) as String
    if n < 10 then return "0" + n.ToStr()
    return n.ToStr()
end function

' 75 -> "01:15", 3725 -> "1:02:05"
function fmtClock(totalSeconds as Integer) as String
    if totalSeconds < 0 then totalSeconds = 0
    h = totalSeconds \ 3600
    mi = (totalSeconds mod 3600) \ 60
    se = totalSeconds mod 60
    if h > 0 then return h.ToStr() + ":" + two(mi) + ":" + two(se)
    return two(mi) + ":" + two(se)
end function

' Hora local "HH:MM" de una marca de tiempo (segundos desde 1970).
function fmtHM(epoch as Integer) as String
    dt = CreateObject("roDateTime")
    dt.FromSeconds(epoch)
    dt.ToLocalTime()
    return two(dt.GetHours()) + ":" + two(dt.GetMinutes())
end function

function fmtDateEs(epoch as Integer) as String
    months = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]
    dt = CreateObject("roDateTime")
    dt.FromSeconds(epoch)
    dt.ToLocalTime()
    return dt.GetDayOfMonth().ToStr() + " de " + months[dt.GetMonth() - 1] + " de " + dt.GetYear().ToStr()
end function

function nowSecs() as Integer
    return CreateObject("roDateTime").AsSeconds()
end function

function urlEscape(s as String) as String
    return CreateObject("roUrlTransfer").Escape(s)
end function

' Decodifica base64 (así vienen los títulos de la guía). Si no parece base64 válido, devuelve el texto tal cual.
function decodeB64(s as String) as String
    if s = "" then return ""
    if Len(s) mod 4 <> 0 then return s
    ba = CreateObject("roByteArray")
    ba.FromBase64String(s)
    out = ba.ToAsciiString()
    if out = "" then return s
    for i = 1 to Len(out)
        c = Asc(Mid(out, i, 1))
        if (c < 32 and c <> 10 and c <> 13 and c <> 9) or c = 65533 then return s
    end for
    return out
end function

' Minúsculas y sin acentos, para buscar sin importar acentos ni mayúsculas.
function normText(s as String) as String
    t = LCase(s)
    if m.reNonAscii = invalid then m.reNonAscii = CreateObject("roRegex", "[^ -~]", "")
    if not m.reNonAscii.IsMatch(t) then return t
    pairs = ["á", "a", "à", "a", "ä", "a", "â", "a", "é", "e", "è", "e", "ë", "e", "ê", "e", "í", "i", "ì", "i", "ï", "i", "î", "i", "ó", "o", "ò", "o", "ö", "o", "ô", "o", "ú", "u", "ù", "u", "ü", "u", "û", "u", "ñ", "n", "ç", "c", "Á", "a", "É", "e", "Í", "i", "Ó", "o", "Ú", "u", "Ñ", "n"]
    i = 0
    while i < pairs.Count()
        if Instr(1, t, pairs[i]) > 0 then t = t.Replace(pairs[i], pairs[i + 1])
        i = i + 2
    end while
    return t
end function

' Palabras de una búsqueda ya normalizadas.
function queryWords(q as String) as Object
    words = []
    for each w in normText(q).Split(" ")
        if w <> "" then words.Push(w)
    end for
    return words
end function

function matchesWords(name as String, words as Object) as Boolean
    if words.Count() = 0 then return true
    n = normText(name)
    for each w in words
        if Instr(1, n, w) = 0 then return false
    end for
    return true
end function

' Nombres de categorías para adultos (se ocultan con el control parental).
function isAdultName(name as String) as Boolean
    n = LCase(name)
    keys = ["xxx", "adult", "+18", "18+", "porn", "erotic", "erótic"]
    for each k in keys
        if Instr(1, n, k) > 0 then return true
    end for
    return false
end function

' Los favoritos se marcan con "* " delante del nombre.
function rawName(title as String) as String
    if Left(title, 2) = "* " then return Mid(title, 3)
    return title
end function

function formatForExt(ext as String) as String
    e = LCase(ext)
    if e = "m3u8" then return "hls"
    if e = "mkv" then return "mkv"
    if e = "ts" then return "ts"
    return "mp4"
end function

' ----------------------------------------------------------------- memoria del canal (registro)

function regRead(key as String) as String
    sec = CreateObject("roRegistrySection", "UltraPremierTV")
    if sec.Exists(key) then return sec.Read(key)
    return ""
end function

sub regWrite(key as String, value as String)
    sec = CreateObject("roRegistrySection", "UltraPremierTV")
    sec.Write(key, value)
    sec.Flush()
end sub

sub regDelete(key as String)
    sec = CreateObject("roRegistrySection", "UltraPremierTV")
    sec.Delete(key)
    sec.Flush()
end sub

function loadList(key as String) as Object
    raw = regRead(key)
    if raw = "" then return []
    arr = ParseJson(raw)
    if arr = invalid then return []
    if Type(arr) <> "roArray" then return []
    return arr
end function

sub saveList(key as String, arr as Object)
    regWrite(key, FormatJson(arr))
end sub

' ----------------------------------------------------------------- favoritos

function favKey(kind as String) as String
    return "fav_" + kind
end function

function favoriteIds(kind as String) as Object
    ids = {}
    for each f in loadList(favKey(kind))
        ids[toS(f.id)] = true
    end for
    return ids
end function

function isFavorite(kind as String, id as String) as Boolean
    for each f in loadList(favKey(kind))
        if toS(f.id) = id then return true
    end for
    return false
end function

' Agrega o quita un favorito. Devuelve true si quedó como favorito.
function toggleFavorite(kind as String, item as Object) as Boolean
    id = toS(item.id)
    list = loadList(favKey(kind))
    kept = []
    found = false
    for each f in list
        if toS(f.id) = id
            found = true
        else
            kept.Push(f)
        end if
    end for
    if found
        saveList(favKey(kind), kept)
        return false
    end if
    entry = { id: id, name: toS(item.name), icon: toS(item.icon), ext: toS(item.ext) }
    kept.Unshift(entry)
    while kept.Count() > 80
        kept.Pop()
    end while
    saveList(favKey(kind), kept)
    return true
end function

' ----------------------------------------------------------------- continuar viendo

function findContinue(kind as String, id as String) as Dynamic
    for each e in loadList("continue")
        if toS(e.kind) = kind and toS(e.id) = id then return e
    end for
    return invalid
end function

' Guarda dónde se quedó una película o episodio. Si ya casi terminó, lo quita de la lista.
sub updateContinue(entry as Object)
    kind = toS(entry.kind)
    id = toS(entry.id)
    kept = []
    for each e in loadList("continue")
        if not (toS(e.kind) = kind and toS(e.id) = id) then kept.Push(e)
    end for
    secs = toI(entry.pos)
    dur = toI(entry.dur)
    finished = (dur > 0 and (secs >= dur * 0.95 or dur - secs < 45))
    if secs >= 10 and not finished then kept.Unshift(entry)
    while kept.Count() > 20
        kept.Pop()
    end while
    saveList("continue", kept)
end sub

' ----------------------------------------------------------------- direcciones de reproducción

' kind: "live", "movie" o "series"
function streamUrl(kind as String, id as String, ext as String) as String
    s = m.global.session
    return s.base + "/" + kind + "/" + s.user + "/" + s.pass + "/" + id + "." + ext
end function

' ----------------------------------------------------------------- red

' Pide una dirección y devuelve el texto, o invalid si no se pudo (sin internet, tiempo agotado, error del servidor).
function httpGet(url as String, timeoutMs = 25000 as Integer) as Dynamic
    http = CreateObject("roUrlTransfer")
    port = CreateObject("roMessagePort")
    http.SetPort(port)
    http.SetUrl(url)
    http.EnableEncodings(true)
    http.RetainBodyOnError(true)
    http.AddHeader("User-Agent", "Mozilla/5.0 (UltraPremierTV Roku)")
    if Left(url, 5) = "https"
        http.SetCertificatesFile("common:/certs/ca-bundle.crt")
        http.InitClientCertificates()
    end if
    if http.AsyncGetToString()
        msg = Wait(timeoutMs, port)
        if Type(msg) = "roUrlEvent"
            code = msg.GetResponseCode()
            if code >= 200 and code < 300 then return msg.GetString()
            return invalid
        end if
        http.AsyncCancel()
    end if
    return invalid
end function

' Llamada al servidor de la cuenta ya iniciada.
function apiUrl(action as String, extra as String) as String
    s = m.global.session
    u = s.base + "/player_api.php?username=" + urlEscape(s.user) + "&password=" + urlEscape(s.pass)
    if action <> "" then u = u + "&action=" + action
    return u + extra
end function
