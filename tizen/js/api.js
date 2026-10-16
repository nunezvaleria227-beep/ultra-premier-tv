/* Cliente de la API estándar de Xtream Codes (player_api.php). Las respuestas de estos servidores son poco
   consistentes (números como texto, arreglos vacíos que llegan como objetos...), por eso todo se lee de forma tolerante. */
(function () {
    function fail(message, auth) {
        return { message: message, auth: !!auth };
    }

    function cleanBody(body) {
        return String(body || "").replace(/^[﻿\s]+/, "").replace(/\s+$/, "");
    }

    function parseObject(body) {
        try {
            var v = JSON.parse(cleanBody(body));
            if (v && typeof v === "object") return v;
        } catch (e) { /* abajo */ }
        return null;
    }

    function parseArray(body) {
        var text = cleanBody(body);
        if (text.charAt(0) !== "[") return [];
        try { return JSON.parse(text); } catch (e) { return []; }
    }

    function enc(v) { return encodeURIComponent(v); }

    UP.api = {
        /** Limpia lo que se escriba: agrega http:// y quita /player_api.php, /get.php, etc. */
        normalizeServer: function (raw) {
            var text = String(raw || "").replace(/^\s+|\s+$/g, "");
            if (!/^https?:\/\//i.test(text)) text = "http://" + text;
            var m = /^(https?):\/\/([^\/:?#]+)(?::(\d+))?([^?#]*)/i.exec(text);
            if (!m) throw new Error("La URL del servidor no es válida");
            var scheme = m[1].toLowerCase(), host = m[2], port = m[3] || "", path = m[4] || "";
            var endings = ["/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php"];
            for (var i = 0; i < endings.length; i++) {
                var at = path.indexOf(endings[i]);
                if (at >= 0) path = path.substring(0, at);
            }
            path = path.replace(/\/+$/, "");
            var isDefault = (scheme === "http" && (port === "" || port === "80")) || (scheme === "https" && (port === "" || port === "443"));
            return scheme + "://" + host + (isDefault ? "" : ":" + port) + path;
        },

        /** GET con tiempo límite. cb(error, texto). */
        request: function (url, timeoutMs, cb) {
            var done = false;
            var x = new XMLHttpRequest();
            function finish(err, text) {
                if (done) return;
                done = true;
                cb(err, text);
            }
            try {
                x.open("GET", url, true);
                x.timeout = timeoutMs || 30000;
                x.setRequestHeader("Accept", "application/json");
            } catch (e) {
                finish(fail("No se pudo abrir la conexión con el servidor."));
                return null;
            }
            x.onload = function () {
                if (x.status === 401 || x.status === 403) return finish(fail("Acceso denegado por el servidor (" + x.status + ")", true));
                if (x.status < 200 || x.status >= 300) return finish(fail("El servidor respondió con error " + x.status));
                finish(null, x.responseText);
            };
            x.onerror = function () { finish(fail("No se pudo conectar con el servidor. Revisa tu conexión a internet.")); };
            x.ontimeout = function () { finish(fail("El servidor tardó demasiado en responder.")); };
            x.onabort = function () { finish(fail("Se canceló la conexión.")); };
            try { x.send(); } catch (e2) { finish(fail("No se pudo conectar con el servidor.")); }
            return x;
        },

        apiUrl: function (c, action, extra) {
            var url = c.server + "/player_api.php?username=" + enc(c.user) + "&password=" + enc(c.pass);
            if (action) url += "&action=" + enc(action);
            if (extra) for (var k in extra) if (extra.hasOwnProperty(k)) url += "&" + enc(k) + "=" + enc(extra[k]);
            return url;
        },

        // ---------------------------------------------------------------- login
        login: function (c, cb) {
            UP.api.request(UP.api.apiUrl(c, null), 30000, function (err, text) {
                if (err) return cb(err);
                var root = parseObject(text);
                if (!root) return cb(fail("El servidor no respondió como un portal Xtream Codes. Revisa la URL."));
                var u = root.user_info;
                if (!u || UP.int(u.auth, 0) !== 1) return cb(fail("Usuario o contraseña incorrectos", true));
                var status = UP.str(u.status) || "Active";
                if (status.toLowerCase() !== "active") return cb(fail("La cuenta no está activa (estado: " + status + ")", true));
                var formats = [];
                if (u.allowed_output_formats instanceof Array) formats = u.allowed_output_formats;
                var exp = UP.str(u.exp_date);
                cb(null, {
                    username: UP.str(u.username) || c.user,
                    status: status,
                    expiresAt: exp ? parseInt(exp, 10) : null,
                    maxConnections: UP.str(u.max_connections) ? UP.int(u.max_connections) : null,
                    activeConnections: UP.str(u.active_cons) ? UP.int(u.active_cons) : null,
                    allowedFormats: formats,
                    message: UP.str(u.message)
                });
            });
        },

        // ------------------------------------------------------------ categorías
        categories: function (c, action, cb) {
            UP.api.request(UP.api.apiUrl(c, action), 60000, function (err, text) {
                if (err) return cb(err);
                var out = [];
                var arr = parseArray(text);
                for (var i = 0; i < arr.length; i++) {
                    var o = arr[i];
                    if (!o) continue;
                    var id = UP.str(o.category_id);
                    if (id === null) continue;
                    out.push({ id: id, name: UP.str(o.category_name) || id });
                }
                cb(null, out);
            });
        },

        // ---------------------------------------------------------- listas completas
        liveStreams: function (c, cb) {
            UP.api.request(UP.api.apiUrl(c, "get_live_streams"), 180000, function (err, text) {
                if (err) return cb(err);
                var arr = parseArray(text), out = [];
                for (var i = 0; i < arr.length; i++) {
                    var o = arr[i];
                    var id = o ? UP.str(o.stream_id) : null;
                    if (id === null) continue;
                    var name = UP.str(o.name) || ("Canal " + id);
                    out.push({
                        id: id, name: name, icon: UP.str(o.stream_icon), cat: UP.str(o.category_id) || "0",
                        num: UP.int(o.num), key: UP.searchKey(name), base: UP.epgBaseKey(name)
                    });
                }
                cb(null, out);
            });
        },

        movies: function (c, cb) {
            UP.api.request(UP.api.apiUrl(c, "get_vod_streams"), 180000, function (err, text) {
                if (err) return cb(err);
                var arr = parseArray(text), out = [];
                for (var i = 0; i < arr.length; i++) {
                    var o = arr[i];
                    var id = o ? UP.str(o.stream_id) : null;
                    if (id === null) continue;
                    var name = UP.str(o.name) || ("Película " + id);
                    out.push({
                        id: id, name: name, title: UP.cleanTitle(name), year: UP.yearOf(name), poster: UP.str(o.stream_icon),
                        cat: UP.str(o.category_id) || "0", ext: UP.str(o.container_extension) || "mp4",
                        rating: UP.str(o.rating), added: UP.int(o.added), key: UP.searchKey(name)
                    });
                }
                cb(null, out);
            });
        },

        series: function (c, cb) {
            UP.api.request(UP.api.apiUrl(c, "get_series"), 180000, function (err, text) {
                if (err) return cb(err);
                var arr = parseArray(text), out = [];
                for (var i = 0; i < arr.length; i++) {
                    var o = arr[i];
                    var id = o ? UP.str(o.series_id) : null;
                    if (id === null) continue;
                    var name = UP.str(o.name) || ("Serie " + id);
                    out.push({
                        id: id, name: name, title: UP.cleanTitle(name), year: UP.yearOf(name), cover: UP.str(o.cover),
                        cat: UP.str(o.category_id) || "0", rating: UP.str(o.rating), added: UP.int(o.last_modified),
                        key: UP.searchKey(name)
                    });
                }
                cb(null, out);
            });
        },

        // ------------------------------------------------------------------ guía
        shortEpg: function (c, streamId, limit, cb) {
            UP.api.request(UP.api.apiUrl(c, "get_short_epg", { stream_id: streamId, limit: limit || 2 }), 20000, function (err, text) {
                if (err) return cb(err);
                var root = parseObject(text) || {};
                var list = root.epg_listings instanceof Array ? root.epg_listings : [];
                var out = [];
                for (var i = 0; i < list.length; i++) {
                    var o = list[i];
                    if (!o) continue;
                    var start = parseInt(UP.str(o.start_timestamp), 10), end = parseInt(UP.str(o.stop_timestamp), 10);
                    if (isNaN(start) || isNaN(end)) continue;
                    out.push({ title: UP.b64decode(UP.str(o.title)), description: UP.b64decode(UP.str(o.description)), start: start, end: end });
                }
                cb(null, out);
            });
        },

        vodInfo: function (c, vodId, cb) {
            UP.api.request(UP.api.apiUrl(c, "get_vod_info", { vod_id: vodId }), 30000, function (err, text) {
                if (err) return cb(err);
                var root = parseObject(text) || {};
                var i = root.info || {};
                cb(null, {
                    plot: UP.str(i.plot) || UP.str(i.description),
                    genre: UP.str(i.genre),
                    releaseDate: UP.str(i.releasedate) || UP.str(i.release_date),
                    duration: UP.str(i.duration),
                    cast: UP.str(i.cast) || UP.str(i.actors),
                    director: UP.str(i.director),
                    backdrop: backdrop(i),
                    rating: UP.str(i.rating)
                });
            });
        },

        seriesInfo: function (c, seriesId, cb) {
            UP.api.request(UP.api.apiUrl(c, "get_series_info", { series_id: seriesId }), 30000, function (err, text) {
                if (err) return cb(err);
                var root = parseObject(text) || {};
                var info = root.info || {};
                var episodes = [];

                function add(o, seasonFallback) {
                    var id = o ? UP.str(o.id) : null;
                    if (id === null) return;
                    var ei = o.info || {};
                    episodes.push({
                        id: id,
                        season: UP.str(o.season) ? UP.int(o.season, seasonFallback) : seasonFallback,
                        number: UP.int(o.episode_num, 0),
                        title: UP.str(o.title) || "Episodio",
                        ext: UP.str(o.container_extension) || "mp4",
                        plot: UP.str(ei.plot),
                        image: UP.str(ei.movie_image),
                        durationSecs: UP.str(ei.duration_secs) ? UP.int(ei.duration_secs) : null
                    });
                }

                var raw = root.episodes, k, j;
                if (raw instanceof Array) {
                    for (j = 0; j < raw.length; j++) {
                        if (raw[j] instanceof Array) for (k = 0; k < raw[j].length; k++) add(raw[j][k], j + 1);
                    }
                } else if (raw && typeof raw === "object") {
                    for (var key in raw) {
                        if (!raw.hasOwnProperty(key) || !(raw[key] instanceof Array)) continue;
                        for (k = 0; k < raw[key].length; k++) add(raw[key][k], UP.int(key, 0));
                    }
                }

                // Temporadas ordenadas, con sus episodios ordenados.
                var map = {}, nums = [];
                UP.each(episodes, function (e) {
                    if (!map[e.season]) { map[e.season] = []; nums.push(e.season); }
                    map[e.season].push(e);
                });
                nums.sort(function (a, b) { return a - b; });
                var seasons = [];
                UP.each(nums, function (n) {
                    map[n].sort(function (a, b) { return a.number - b.number; });
                    seasons.push({ number: n, episodes: map[n] });
                });

                cb(null, {
                    plot: UP.str(info.plot), cover: UP.str(info.cover), genre: UP.str(info.genre), cast: UP.str(info.cast),
                    director: UP.str(info.director),
                    releaseDate: UP.str(info.releaseDate) || UP.str(info.release_date) || UP.str(info.releasedate),
                    backdrop: backdrop(info), rating: UP.str(info.rating), seasons: seasons
                });
            });
        },

        // ------------------------------------------------------------ video
        liveUrl: function (c, id, format) { return c.server + "/live/" + enc(c.user) + "/" + enc(c.pass) + "/" + id + "." + format; },
        movieUrl: function (c, id, ext) { return c.server + "/movie/" + enc(c.user) + "/" + enc(c.pass) + "/" + id + "." + ext; },
        episodeUrl: function (c, id, ext) { return c.server + "/series/" + enc(c.user) + "/" + enc(c.pass) + "/" + id + "." + ext; }
    };

    /** Imagen de fondo: el servidor la manda como arreglo de direcciones o como una sola. */
    function backdrop(info) {
        var raw = info.backdrop_path;
        if (raw instanceof Array) {
            for (var i = 0; i < raw.length; i++) {
                var u = String(raw[i] || "").replace(/^\s+|\s+$/g, "");
                if (/^http/i.test(u)) return u;
            }
        } else if (typeof raw === "string" && /^http/i.test(raw.replace(/^\s+/, ""))) {
            return raw.replace(/^\s+|\s+$/g, "");
        }
        return null;
    }
})();
