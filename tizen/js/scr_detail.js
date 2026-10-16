/* Ficha de una película o de una serie. */

/** Película: ficha con sinopsis, reparto y botones para ver, continuar y guardar en favoritos. */
UP.screens.movieDetail = function (m) {
    var el = UP.el("div", "screen detail");
    el.appendChild(UP.header("Película"));

    var body = UP.el("div", "detail-body");
    var art = UP.el("div", "detail-poster");
    art.appendChild(UP.img(m.poster, "detail-poster-img"));
    body.appendChild(art);

    var col = UP.el("div", "detail-col");
    col.appendChild(UP.el("div", "detail-title", m.title));
    var meta = UP.el("div", "detail-meta", "");
    var plot = UP.el("div", "detail-plot", "Cargando información…");
    var extra = UP.el("div", "detail-extra", "");
    var btns = UP.el("div", "detail-btns");
    col.appendChild(meta); col.appendChild(plot); col.appendChild(extra); col.appendChild(btns);
    body.appendChild(col);
    el.appendChild(body);

    var info = null;

    function metaLine() {
        var parts = [];
        var year = m.year || (info && info.releaseDate ? parseInt(info.releaseDate, 10) : null);
        if (year) parts.push(String(year));
        if (info && info.duration) parts.push(info.duration);
        if (info && info.genre) parts.push(info.genre);
        var r = parseFloat((info && info.rating) || m.rating);
        if (r > 0) parts.push("★ " + (Math.round(r * 10) / 10));
        return parts.join("   •   ");
    }

    function startPlay(startMs) {
        var creds = UP.session.creds;
        var item = {
            type: "movie", id: m.id, title: m.title, subtitle: "", startMs: startMs || 0,
            url: UP.api.movieUrl(creds, m.id, m.ext),
            resume: { type: "movie", id: m.id, ext: m.ext, title: m.title, subtitle: "", image: m.poster || "", cat: m.cat }
        };
        UP.player.open([item], 0, { onExit: function () { buildButtons(); } });
    }

    var firstBtn = null;
    function buildButtons() {
        UP.clear(btns);
        var saved = UP.progress.find("movie", m.id);
        var resumeable = saved && saved.pos >= 5000 && !(saved.dur > 0 && saved.pos > saved.dur * 0.95);
        if (resumeable) {
            firstBtn = UP.btn("▶  Continuar desde " + UP.fmtDuration(saved.pos), function () { startPlay(saved.pos); }, "primary");
            btns.appendChild(firstBtn);
            btns.appendChild(UP.btn("Desde el principio", function () { startPlay(0); }));
        } else {
            firstBtn = UP.btn("▶  Reproducir", function () { startPlay(0); }, "primary");
            btns.appendChild(firstBtn);
        }
        var fav = UP.btn(UP.fav.has("movie", m.id) ? "★  Quitar de favoritos" : "☆  Añadir a favoritos", function () {
            var on = UP.fav.toggle("movie", m.id);
            fav.textContent = on ? "★  Quitar de favoritos" : "☆  Añadir a favoritos";
            UP.toast(on ? "Agregada a favoritos" : "Quitada de favoritos", 1800);
        });
        btns.appendChild(fav);
        if (UP.nav.current === null || !document.body.contains(UP.nav.current)) UP.nav.focus(firstBtn);
    }

    buildButtons();

    UP.api.vodInfo(UP.session.creds, m.id, function (err, i) {
        if (!el.parentNode) return;       // ya se salió de la pantalla
        info = err ? null : i;
        meta.textContent = metaLine();
        plot.textContent = info && info.plot ? info.plot : (err ? "No se pudo cargar la sinopsis." : "Sin sinopsis.");
        var lines = [];
        if (info && info.director) lines.push("Director: " + info.director);
        if (info && info.cast) lines.push("Reparto: " + info.cast);
        extra.textContent = lines.join("\n");
    });
    meta.textContent = metaLine();

    return {
        name: "movieDetail",
        el: el,
        firstFocus: function () { return firstBtn; },
        onShow: function (back) { if (back) buildButtons(); }
    };
};

/**
 * Serie: temporadas arriba, episodios abajo. Al terminar un episodio sigue el siguiente.
 * opts.auto = { episodeId, posMs } abre el reproductor en cuanto carga la serie (desde "Continuar viendo").
 */
UP.screens.seriesDetail = function (s, opts) {
    opts = opts || {};
    var el = UP.el("div", "screen detail series");
    el.appendChild(UP.header("Serie"));

    var body = UP.el("div", "detail-body");
    var art = UP.el("div", "detail-poster small");
    art.appendChild(UP.img(s.cover, "detail-poster-img"));
    body.appendChild(art);

    var col = UP.el("div", "detail-col");
    col.appendChild(UP.el("div", "detail-title", s.title));
    var meta = UP.el("div", "detail-meta", "");
    var plot = UP.el("div", "detail-plot short", "Cargando temporadas y episodios…");
    var btns = UP.el("div", "detail-btns");
    var seasonBar = UP.el("div", "season-bar scroller hscroll");
    var epList = UP.el("div", "ep-list scroller");
    col.appendChild(meta); col.appendChild(plot); col.appendChild(btns); col.appendChild(seasonBar); col.appendChild(epList);
    body.appendChild(col);
    el.appendChild(body);

    var detail = null;
    var flat = [];            // todos los episodios en orden
    var seasonIdx = 0;
    var seasonEls = [];
    var firstBtn = null;

    function infoLine() {
        var parts = [];
        var y = s.year || (detail && detail.releaseDate ? parseInt(detail.releaseDate, 10) : null);
        if (y) parts.push(String(y));
        if (detail && detail.genre) parts.push(detail.genre);
        var r = parseFloat((detail && detail.rating) || s.rating);
        if (r > 0) parts.push("★ " + (Math.round(r * 10) / 10));
        return parts.join("   •   ");
    }

    function epLabel(ep) {
        return "T" + ep.season + " · E" + ep.number + " — " + ep.title;
    }

    /** Arma la lista para el reproductor y empieza en el episodio "at". */
    function playFrom(at, startMs) {
        var creds = UP.session.creds;
        var queue = [];
        UP.each(flat, function (ep) {
            queue.push({
                type: "episode", id: ep.id, title: s.title, subtitle: epLabel(ep),
                url: UP.api.episodeUrl(creds, ep.id, ep.ext), startMs: 0,
                resume: { type: "episode", id: ep.id, ext: ep.ext, title: s.title, subtitle: epLabel(ep), image: ep.image || s.cover || "", cat: s.cat, sid: s.id }
            });
        });
        queue[at].startMs = startMs || 0;
        UP.player.open(queue, at, { onExit: function () {
            buildButtons();
            renderEpisodes();
            if (firstBtn && (!UP.nav.current || !document.body.contains(UP.nav.current))) UP.nav.focus(firstBtn);
        } });
    }

    function savedFor(ep) {
        var e = UP.progress.find("episode", ep.id);
        return e && e.pos >= 5000 && !(e.dur > 0 && e.pos > e.dur * 0.95) ? e : null;
    }

    function lastWatched() {
        // El episodio de esta serie que se vio más recientemente y quedó a medias.
        var best = null;
        UP.each(UP.progress.entries(), function (e) {
            if (e.type !== "episode" || String(e.sid) !== String(s.id)) return;
            if (!best || e.ts > best.ts) best = e;
        });
        if (!best) return null;
        for (var i = 0; i < flat.length; i++) if (String(flat[i].id) === String(best.id)) return { at: i, entry: best };
        return null;
    }

    function buildButtons() {
        UP.clear(btns);
        if (!flat.length) { firstBtn = null; return; }
        var lw = lastWatched();
        var okResume = lw && lw.entry.pos >= 5000 && !(lw.entry.dur > 0 && lw.entry.pos > lw.entry.dur * 0.95);
        if (okResume) {
            firstBtn = UP.btn("▶  Continuar: " + epLabel(flat[lw.at]) + " (" + UP.fmtDuration(lw.entry.pos) + ")", function () { playFrom(lw.at, lw.entry.pos); }, "primary");
        } else {
            firstBtn = UP.btn("▶  Ver desde el primer episodio", function () { playFrom(0, 0); }, "primary");
        }
        btns.appendChild(firstBtn);
        var fav = UP.btn(UP.fav.has("series", s.id) ? "★  Quitar de favoritos" : "☆  Añadir a favoritos", function () {
            var on = UP.fav.toggle("series", s.id);
            fav.textContent = on ? "★  Quitar de favoritos" : "☆  Añadir a favoritos";
            UP.toast(on ? "Agregada a favoritos" : "Quitada de favoritos", 1800);
        });
        btns.appendChild(fav);
        if (!UP.nav.current || !document.body.contains(UP.nav.current)) UP.nav.focus(firstBtn);
    }

    function buildSeasons() {
        UP.clear(seasonBar);
        seasonEls = [];
        UP.each(detail.seasons, function (se, i) {
            var chip = UP.f(UP.el("div", "chip" + (i === seasonIdx ? " sel" : ""), "Temporada " + se.number), function () {
                if (UP.nav.current && epList.firstChild && epList.firstChild._ep) UP.nav.focus(epList.firstChild);
            });
            chip._onfocus = function () { if (i !== seasonIdx) { seasonIdx = i; markSeasons(); renderEpisodes(); } };
            seasonEls.push(chip);
            seasonBar.appendChild(chip);
        });
    }

    function markSeasons() {
        UP.each(seasonEls, function (c, i) { c.className = c.className.replace(" sel", "") + (i === seasonIdx ? " sel" : ""); });
    }

    function renderEpisodes() {
        UP.clear(epList);
        epList.scrollTop = 0;
        var se = detail && detail.seasons[seasonIdx];
        if (!se) return;
        UP.each(se.episodes, function (ep) {
            var at = 0;
            for (var k = 0; k < flat.length; k++) if (flat[k] === ep) { at = k; break; }
            var row = UP.f(UP.el("div", "eprow"), function () {
                var sv = savedFor(ep);
                playFrom(at, sv ? sv.pos : 0);
            });
            row._ep = ep;
            var head = UP.el("div", "ep-head");
            head.appendChild(UP.el("span", "ep-num", ep.number + "."));
            head.appendChild(UP.el("span", "ep-title", ep.title));
            if (ep.durationSecs) head.appendChild(UP.el("span", "ep-dur", Math.max(1, Math.round(ep.durationSecs / 60)) + " min"));
            row.appendChild(head);
            if (ep.plot) row.appendChild(UP.el("div", "ep-plot", UP.trunc(ep.plot, 160)));
            var sv = savedFor(ep);
            if (sv && sv.dur > 0) row.appendChild(UP.bar(sv.pos / sv.dur, "ep-bar"));
            row._nav = {
                up: function (cur) {
                    var prev = cur.previousSibling;
                    if (prev && prev._ep) return prev;
                    return seasonEls[seasonIdx] || null;
                }
            };
            epList.appendChild(row);
        });
    }

    UP.api.seriesInfo(UP.session.creds, s.id, function (err, d) {
        if (!el.parentNode) return;
        if (err) {
            plot.textContent = err.message + "  Pulsa Atrás para volver.";
            return;
        }
        detail = d;
        flat = [];
        UP.each(d.seasons, function (se) { UP.each(se.episodes, function (ep) { flat.push(ep); }); });
        meta.textContent = infoLine();
        plot.textContent = d.plot || (flat.length ? "" : "Esta serie no tiene episodios.");
        if (d.cover && !s.cover) { UP.clear(art); art.appendChild(UP.img(d.cover, "detail-poster-img")); }
        buildSeasons();
        renderEpisodes();
        buildButtons();
        if (opts.auto) {
            var found = -1;
            for (var i = 0; i < flat.length; i++) if (String(flat[i].id) === String(opts.auto.episodeId)) { found = i; break; }
            var auto = opts.auto;
            opts.auto = null;
            if (found >= 0) playFrom(found, auto.posMs || 0);
        }
    });
    meta.textContent = infoLine();

    return {
        name: "seriesDetail",
        el: el,
        firstFocus: function () { return firstBtn; },
        onShow: function (back) { if (back && detail) { buildButtons(); renderEpisodes(); } }
    };
};
