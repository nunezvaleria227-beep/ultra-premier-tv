/* Servidor de ejemplo dentro del navegador, para la vista de demostración. Todos los nombres son inventados.
   Usuario y contraseña de ejemplo: demo / demo. NO va dentro del paquete de la TV. */
(function () {
    var BASE = "http://demo.ultrapremier.tv";
    UP.CONFIG.PORTAL_URL = BASE;

    var NOW = Math.floor(new Date().getTime() / 1000);

    function b64(s) { return btoa(unescape(encodeURIComponent(s))); }
    function hash(s) { var h = 0; for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0; return Math.abs(h); }
    var PALETTE = [["#2F7BFF", "#1B2056"], ["#FF6B6B", "#4A1530"], ["#FFC93C", "#4B3A0E"], ["#3DDC97", "#0D3B2E"], ["#B388FF", "#2C1B5E"], ["#FF8A3D", "#4A2208"], ["#38C6F4", "#0B3550"]];

    function esc(s) { return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }

    /** Cartel o logo dibujado con SVG (una imagen en línea, sin descargar nada). */
    function art(title, w, h, sub, big) {
        var p = PALETTE[hash(title) % PALETTE.length];
        var words = String(title).split(" "), lines = [], cur = "";
        for (var i = 0; i < words.length; i++) {
            if ((cur + " " + words[i]).length > (big ? 11 : 16) && cur) { lines.push(cur); cur = words[i]; } else cur = cur ? cur + " " + words[i] : words[i];
        }
        if (cur) lines.push(cur);
        var fs = Math.round(w / (big ? 7 : 9)), y0 = h / 2 - ((lines.length - 1) * fs * 0.6);
        var tx = "";
        for (var k = 0; k < lines.length; k++) tx += '<text x="' + (w / 2) + '" y="' + (y0 + k * fs * 1.2) + '" text-anchor="middle" font-family="Arial,sans-serif" font-weight="700" font-size="' + fs + '" fill="#fff">' + esc(lines[k]) + '</text>';
        if (sub) tx += '<text x="' + (w / 2) + '" y="' + (h - fs * 0.8) + '" text-anchor="middle" font-family="Arial,sans-serif" font-size="' + Math.round(fs * 0.7) + '" fill="rgba(255,255,255,.75)">' + esc(sub) + '</text>';
        var svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + w + '" height="' + h + '" viewBox="0 0 ' + w + ' ' + h + '">' +
            '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="' + p[0] + '"/><stop offset="1" stop-color="' + p[1] + '"/></linearGradient></defs>' +
            '<rect width="100%" height="100%" fill="url(#g)"/>' + tx + '</svg>';
        return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
    }

    // ------------------------------------------------------------------ canales
    var LIVE_CATS = [["1", "MX | Noticias"], ["2", "MX | Deportes"], ["3", "MX | Cine"], ["4", "MX | Infantiles"], ["5", "MX | Música"], ["6", "XXX Adultos"]];
    var LIVE = [
        ["101", "MX | Canal Uno HD", "1", 1], ["102", "MX | Canal Uno FHD", "1", 0], ["103", "MX | Canal Uno 4K", "1", 0],
        ["104", "MX | Noticias 24 HD", "1", 1], ["105", "MX | Noticiero Capital", "1", 1], ["106", "MX | Mundo Hoy", "1", 1],
        ["201", "MX | Deportes Total HD", "2", 1], ["202", "MX | Deportes Total FHD", "2", 0], ["203", "MX | Liga Plus", "2", 1],
        ["204", "MX | Motor Sport", "2", 1], ["205", "MX | Ring Side", "2", 0],
        ["301", "MX | Cine Estelar HD", "3", 1], ["302", "MX | Cine Clásico", "3", 1], ["303", "MX | Cine Familiar", "3", 1],
        ["304", "MX | Estrenos 24h", "3", 1], ["305", "MX | Cine de Acción", "3", 1],
        ["401", "MX | Kids Planeta", "4", 1], ["402", "MX | Pipo TV", "4", 1], ["403", "MX | Aventuras Junior", "4", 0],
        ["501", "MX | Top Latino", "5", 1], ["502", "MX | Concierto en Vivo", "5", 1], ["503", "MX | Retro Hits", "5", 1],
        ["601", "XXX Canal Adulto", "6", 0]
    ];
    var EPG_TITLES = {
        "1": ["Noticiero de la mañana", "Hoy en la ciudad", "Mesa de análisis", "Noticiero estelar", "Edición nocturna", "Reportaje especial"],
        "2": ["Liga en vivo", "Resumen deportivo", "Pasión de domingo", "Lo mejor de la semana", "Mundo motor", "Box estelar"],
        "3": ["Cine de acción", "Cine de comedia", "Estreno de la semana", "Clásicos del cine", "Cine familiar", "Cine de misterio"],
        "4": ["Las aventuras de Pipo", "Los exploradores", "Doña Nube y sus amigos", "Súper Granja", "Detective Bigotes", "Cuentos de la abuela"],
        "5": ["Top 20 latino", "Conciertos en vivo", "Videos del recuerdo", "Éxitos del año", "Acústico", "Noche de baile"],
        "6": ["Programación para adultos"]
    };

    // ---------------------------------------------------------------- películas
    var VOD_CATS = [["1", "Acción"], ["2", "Comedia"], ["3", "Drama"], ["4", "Familiar"], ["5", "+18 Adultos"]];
    var MOVIE_TITLES = [
        ["La Última Frontera (2021)", "1"], ["Carrera Contra el Tiempo (2020)", "1"], ["Operación Halcón (2019)", "1"], ["Tormenta de Acero (2022)", "1"], ["Persecución en la Ciudad [4K] (2023)", "1"], ["ES - El Rescate Imposible (2018) FHD", "1"],
        ["Risas en el Barrio (2019)", "2"], ["Tres Tíos y un Bebé Grande (2017)", "2"], ["Vacaciones Desastrosas (2021)", "2"], ["La Boda Equivocada (2020)", "2"], ["Oficina de Locos (2016)", "2"],
        ["Sombras del Pasado (2018)", "3"], ["Camino a Casa (2022)", "3"], ["El Último Verano (2015)", "3"], ["Cartas al Viento (2019)", "3"], ["Un Lugar en el Mundo (2021)", "3"],
        ["El Dragón de Papel (2020)", "4"], ["Aventura en el Bosque Mágico (2018)", "4"], ["Pipo y el Tesoro Perdido (2022)", "4"], ["La Granja de Súper Animales (2017)", "4"], ["Viaje a las Estrellas Junior (2023)", "4"],
        ["Película Para Adultos (2022)", "5"]
    ];
    var GENRES = { "1": "Acción, Aventura", "2": "Comedia", "3": "Drama", "4": "Familia, Animación", "5": "Adultos" };
    var CAST = "Ana Gómez, Luis Pérez, Marta Ruiz, Carlos Prueba";
    var PLOT = "Una historia de ejemplo para la demostración de Ultra Premier TV. Sirve para ver cómo se acomoda la sinopsis en la ficha de la película, con varias líneas de texto, acentos y la letra ñ: canción, niño, pingüino.";

    var MOVIES = [];
    (function () {
        for (var i = 0; i < MOVIE_TITLES.length; i++) {
            var m = MOVIE_TITLES[i];
            MOVIES.push({ id: String(500 + i), name: m[0], cat: m[1], rating: String(5 + ((hash(m[0]) % 40) / 10)), added: NOW - i * 86400 });
        }
    })();

    // ------------------------------------------------------------------- series
    var SERIES_CATS = [["1", "Drama"], ["2", "Aventura"], ["3", "Comedia"]];
    var SERIES_TITLES = [
        ["La Casa del Lago (2020)", "1"], ["Secretos de Familia (2019)", "1"], ["Turno de Noche (2021)", "1"], ["Ciudad de Cristal (2018)", "1"],
        ["Aventuras Sin Fin (2017)", "2"], ["Los Exploradores del Sur (2022)", "2"], ["Mundo Perdido (2016)", "2"],
        ["Vecinos Imposibles (2021)", "3"], ["La Pandilla del Barrio (2019)", "3"], ["Chef por Accidente (2023)", "3"]
    ];
    var SERIES = [];
    (function () {
        for (var i = 0; i < SERIES_TITLES.length; i++) {
            var s = SERIES_TITLES[i];
            SERIES.push({ id: String(700 + i), name: s[0], cat: s[1], rating: String(6 + ((hash(s[0]) % 30) / 10)), index: i, seasons: 2 + (i % 2), eps: 4 + (i % 4) });
        }
    })();

    function episodeId(si, season, ep) { return String(9000 + si * 100 + season * 20 + ep); }

    // Nombres para el reproductor de mentira.
    UP.fakeNames = UP.fakeNames || {};
    UP.each(LIVE, function (c) { UP.fakeNames["live:" + c[0]] = c[1].replace(/^MX \| /, ""); });
    UP.each(MOVIES, function (m) { UP.fakeNames["movie:" + m.id] = UP.cleanTitle(m.name); });
    UP.each(SERIES, function (s) {
        for (var se = 1; se <= s.seasons; se++) for (var e = 1; e <= s.eps; e++) {
            UP.fakeNames["series:" + episodeId(s.index, se, e)] = UP.cleanTitle(s.name) + "  T" + se + " E" + e;
        }
    });

    // ------------------------------------------------------------- respuestas
    function parse(url) {
        var q = {}, at = url.indexOf("?");
        if (at < 0) return q;
        var parts = url.substring(at + 1).split("&");
        for (var i = 0; i < parts.length; i++) {
            var kv = parts[i].split("=");
            q[decodeURIComponent(kv[0])] = decodeURIComponent((kv[1] || "").replace(/\+/g, " "));
        }
        return q;
    }

    function userInfo() {
        return {
            user_info: {
                username: "demo", password: "demo", message: "Bienvenido a la demostración", auth: 1, status: "Active",
                exp_date: String(NOW + 89 * 86400), is_trial: "0", active_cons: "1", created_at: String(NOW - 200 * 86400),
                max_connections: "2", allowed_output_formats: ["m3u8", "ts"]
            },
            server_info: { url: "demo.ultrapremier.tv", port: "80" }
        };
    }

    function epgFor(id, limit) {
        var ch = null;
        for (var i = 0; i < LIVE.length; i++) if (LIVE[i][0] === id) ch = LIVE[i];
        if (!ch || !ch[3]) return { epg_listings: [] };
        var titles = EPG_TITLES[ch[2]], slot = 1800, start = Math.floor(NOW / slot) * slot, out = [];
        for (var n = 0; n < (limit || 2); n++) {
            var idx = (hash(id) + Math.floor(start / slot) + n) % titles.length;
            out.push({
                id: String(n), epg_id: id, title: b64(titles[idx]), lang: "es", start: "", end: "",
                description: b64("Descripción de ejemplo del programa «" + titles[idx] + "»."),
                start_timestamp: String(start + n * slot), stop_timestamp: String(start + (n + 1) * slot), channel_id: id
            });
        }
        return { epg_listings: out };
    }

    function respond(url) {
        var q = parse(url), a = q.action || "";
        if (q.username !== "demo" || q.password !== "demo") return { user_info: { auth: 0 } };
        if (!a) return userInfo();
        var i, out;
        if (a === "get_live_categories") { out = []; for (i = 0; i < LIVE_CATS.length; i++) out.push({ category_id: LIVE_CATS[i][0], category_name: LIVE_CATS[i][1], parent_id: 0 }); return out; }
        if (a === "get_vod_categories") { out = []; for (i = 0; i < VOD_CATS.length; i++) out.push({ category_id: VOD_CATS[i][0], category_name: VOD_CATS[i][1], parent_id: 0 }); return out; }
        if (a === "get_series_categories") { out = []; for (i = 0; i < SERIES_CATS.length; i++) out.push({ category_id: SERIES_CATS[i][0], category_name: SERIES_CATS[i][1], parent_id: 0 }); return out; }
        if (a === "get_live_streams") {
            out = [];
            for (i = 0; i < LIVE.length; i++) {
                var c = LIVE[i];
                out.push({ num: i + 1, name: c[1], stream_type: "live", stream_id: Number(c[0]), stream_icon: art(c[1].replace(/^MX \| /, ""), 240, 144, "", true), category_id: c[2], added: String(NOW) });
            }
            return out;
        }
        if (a === "get_vod_streams") {
            out = [];
            for (i = 0; i < MOVIES.length; i++) {
                var m = MOVIES[i];
                out.push({ num: i + 1, name: m.name, stream_type: "movie", stream_id: Number(m.id), stream_icon: art(UP.cleanTitle(m.name), 300, 450, String(UP.yearOf(m.name) || "")), rating: m.rating, added: String(m.added), category_id: m.cat, container_extension: "mp4" });
            }
            return out;
        }
        if (a === "get_series") {
            out = [];
            for (i = 0; i < SERIES.length; i++) {
                var s = SERIES[i];
                out.push({ num: i + 1, name: s.name, series_id: Number(s.id), cover: art(UP.cleanTitle(s.name), 300, 450, String(UP.yearOf(s.name) || "")), plot: PLOT, rating: s.rating, last_modified: String(NOW - i * 3600), category_id: s.cat });
            }
            return out;
        }
        if (a === "get_short_epg") return epgFor(q.stream_id, parseInt(q.limit, 10) || 2);
        if (a === "get_vod_info") {
            var mv = null;
            for (i = 0; i < MOVIES.length; i++) if (MOVIES[i].id === q.vod_id) mv = MOVIES[i];
            if (!mv) return { info: {} };
            return { info: { plot: PLOT, genre: GENRES[mv.cat], releasedate: String(UP.yearOf(mv.name) || "") + "-01-01", duration: "01:35:00", cast: CAST, director: "Carlos Prueba", rating: mv.rating, backdrop_path: [] } };
        }
        if (a === "get_series_info") {
            var sr = null;
            for (i = 0; i < SERIES.length; i++) if (SERIES[i].id === q.series_id) sr = SERIES[i];
            if (!sr) return { info: {}, episodes: {} };
            var eps = {};
            for (var se = 1; se <= sr.seasons; se++) {
                eps[String(se)] = [];
                for (var e = 1; e <= sr.eps; e++) {
                    eps[String(se)].push({
                        id: episodeId(sr.index, se, e), episode_num: e, title: "Episodio " + e + " de la temporada " + se, container_extension: "mp4", season: se,
                        info: { plot: "Resumen del episodio " + e + ": pasa algo inesperado y todo cambia para los protagonistas.", movie_image: art("T" + se + " E" + e, 320, 180, ""), duration_secs: 2520 }
                    });
                }
            }
            return { info: { name: sr.name, cover: art(UP.cleanTitle(sr.name), 300, 450, ""), plot: PLOT, genre: "Drama", cast: CAST, director: "Director Prueba", releaseDate: "2020-01-01", rating: sr.rating, backdrop_path: [] }, episodes: eps };
        }
        return [];
    }

    var realRequest = UP.api.request;
    UP.api.request = function (url, timeoutMs, cb) {
        if (url.indexOf(BASE) !== 0) return realRequest(url, timeoutMs, cb);
        var body;
        try { body = JSON.stringify(respond(url)); } catch (e) { setTimeout(function () { cb({ message: "Error en la demostración", auth: false }); }, 0); return null; }
        setTimeout(function () { cb(null, body); }, 60 + Math.floor(Math.random() * 60));
        return null;
    };

    // La pantalla de entrar ya trae escritos el usuario y la contraseña de ejemplo.
    var origLogin = UP.screens.login;
    UP.screens.login = function (opts) {
        var scr = origLogin(opts);
        var inputs = scr.el.querySelectorAll("input");
        if (inputs.length >= 2 && !inputs[0].value) { inputs[0].value = "demo"; inputs[1].value = "demo"; }
        var sub = scr.el.querySelector(".login-sub");
        if (sub) sub.textContent = "Demostración con datos de ejemplo. El usuario y la contraseña ya están escritos: baja hasta Entrar.";
        return scr;
    };
})();
