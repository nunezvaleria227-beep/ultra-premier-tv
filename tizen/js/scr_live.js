/* TV en directo: categorías a la izquierda, canales en el centro, información del canal a la derecha. */
UP.screens.live = function () {
    var el = UP.el("div", "screen live");
    el.appendChild(UP.header("TV en directo"));

    var body = UP.el("div", "live-body");
    var catsPane = UP.el("div", "pane cats scroller");
    var listPane = UP.el("div", "pane chans scroller");
    var infoPane = UP.el("div", "pane info");
    body.appendChild(catsPane);
    body.appendChild(listPane);
    body.appendChild(infoPane);
    el.appendChild(body);

    var CHUNK = 40;
    var folder = "all";
    var channels = [];
    var rows = [];          // elementos de fila, alineados con "channels"
    var catEls = {};        // carpeta -> elemento de la lista de categorías
    var selectTimer = null;

    // ------------------------------------------------------------------- panel de información
    var iLogo = UP.el("div", "info-logo");
    var iName = UP.el("div", "info-name", "");
    var iCat = UP.el("div", "info-cat", "");
    var iNow = UP.el("div", "info-now", "");
    var iNext = UP.el("div", "info-next", "");
    var iHint = UP.el("div", "info-hint", "Elige un canal y pulsa OK");
    infoPane.appendChild(iLogo); infoPane.appendChild(iName); infoPane.appendChild(iCat);
    infoPane.appendChild(iNow); infoPane.appendChild(iNext); infoPane.appendChild(iHint);

    function showInfo(ch) {
        UP.clear(iLogo);
        if (ch.icon) iLogo.appendChild(UP.img(ch.icon, "info-img"));
        iName.textContent = ch.name;
        iCat.textContent = UP.lib.catName.live[ch.cat] || "";
        iNow.textContent = "";
        iNext.textContent = "";
        UP.epg.get(ch, function (list) {
            if (UP.nav.current && UP.nav.current._ch !== ch) return;
            var nn = UP.epg.nowNext(list);
            iNow.textContent = nn.now ? "Ahora  " + UP.fmtHm(nn.now.start) + " – " + UP.fmtHm(nn.now.end) + "\n" + nn.now.title : "En vivo";
            iNext.textContent = nn.next ? "Después  " + UP.fmtHm(nn.next.start) + "\n" + nn.next.title : "";
        });
    }

    // ------------------------------------------------------------------------ categorías
    function folders() {
        var list = [
            { id: "all", label: "TODO", count: UP.lib.count("live") },
            { id: "fav", label: "FAVORITOS", count: UP.lib.items("live", "fav").length },
            { id: "history", label: "HISTORIAL DE CANALES", count: UP.lib.items("live", "history").length }
        ];
        var counts = {};
        UP.each(UP.lib.visibleItems("live"), function (c) { counts[c.cat] = (counts[c.cat] || 0) + 1; });
        UP.each(UP.lib.visibleCats("live"), function (c) { list.push({ id: c.id, label: c.name, count: counts[c.id] || 0 }); });
        return list;
    }

    function buildCats() {
        UP.clear(catsPane);
        catEls = {};
        UP.each(folders(), function (f) {
            var row = UP.listRow(f.label, f.count, function () { goToList(); });
            row._folder = f.id;
            row._onfocus = function () { scheduleSelect(f.id); };
            row._nav = {
                right: function () {
                    clearTimeout(selectTimer);
                    if (f.id !== folder) selectFolder(f.id);
                    return rows.length ? rows[0] : false;
                }
            };
            catEls[f.id] = row;
            catsPane.appendChild(row);
        });
        markSelected();
    }

    function markSelected() {
        for (var id in catEls) if (catEls.hasOwnProperty(id)) {
            catEls[id].className = catEls[id].className.replace(" sel", "") + (id === folder ? " sel" : "");
        }
    }

    function scheduleSelect(id) {
        if (id === folder && rows.length) return;
        clearTimeout(selectTimer);
        selectTimer = setTimeout(function () { selectFolder(id); }, 140);
    }

    function selectFolder(id) {
        folder = id;
        channels = UP.lib.items("live", id);
        markSelected();
        renderList();
    }

    function goToList() {
        clearTimeout(selectTimer);
        if (folder !== (UP.nav.current && UP.nav.current._folder)) selectFolder(UP.nav.current._folder);
        if (rows.length) UP.nav.focus(rows[0]);
    }

    // -------------------------------------------------------------------------- canales
    function emptyText() {
        if (folder === "fav") return "Aún no tienes canales favoritos.\nEn un canal pulsa ▶ y luego OK sobre la estrella.";
        if (folder === "history") return "Aquí aparecen los últimos canales que veas.";
        return "No hay canales en esta categoría.";
    }

    function renderList() {
        UP.clear(listPane);
        rows = [];
        listPane.scrollTop = 0;
        if (!channels.length) {
            listPane.appendChild(UP.el("div", "empty", emptyText()));
            UP.clear(iLogo); iName.textContent = ""; iCat.textContent = ""; iNow.textContent = ""; iNext.textContent = "";
            return;
        }
        appendChunk();
        loadNow(0, 6);
    }

    function appendChunk() {
        var from = rows.length, to = Math.min(channels.length, from + CHUNK);
        for (var i = from; i < to; i++) {
            var r = chanRow(channels[i], i);
            rows.push(r);
            listPane.appendChild(r);
        }
    }

    function loadNow(from, count) {
        for (var i = Math.max(0, from); i < Math.min(rows.length, from + count); i++) {
            (function (row, ch) {
                if (row._epg) return;
                row._epg = true;
                UP.epg.get(ch, function (list) {
                    var nn = UP.epg.nowNext(list);
                    row._now.textContent = nn.now ? nn.now.title : "En vivo";
                });
            })(rows[i], channels[i]);
        }
    }

    function chanRow(ch, i) {
        var row = UP.f(UP.el("div", "crow"), function () { play(i); });
        row._ch = ch;
        var icon = UP.el("div", "cicon");
        if (ch.icon) icon.appendChild(UP.img(ch.icon, "cicon-img"));
        row.appendChild(icon);
        var txt = UP.el("div", "ctxt");
        txt.appendChild(UP.el("div", "cname", ch.name));
        var now = UP.el("div", "cnow", "");
        txt.appendChild(now);
        row._now = now;
        row.appendChild(txt);
        var star = UP.f(UP.el("div", "cstar", UP.fav.has("live", ch.id) ? "★" : "☆"), function () {
            var on = UP.fav.toggle("live", ch.id);
            star.textContent = on ? "★" : "☆";
            UP.toast(on ? "Canal agregado a favoritos" : "Canal quitado de favoritos", 1800);
            if (folder === "fav" && !on) {
                // En la carpeta de favoritos, el canal desaparece de la lista.
                var at = rows.indexOf(row);
                channels.splice(at, 1);
                rows.splice(at, 1);
                listPane.removeChild(row);
                var next = rows[at] || rows[at - 1];
                if (next) UP.nav.focus(next); else renderList();
                refreshCounts();
            } else {
                refreshCounts();
            }
        });
        star._ch = ch;
        star._nav = { left: function () { return row; }, right: function () { return false; } };
        row.appendChild(star);
        row._nav = {
            left: function () { return catEls[folder] || null; },
            right: function () { return star; }
        };
        row._onfocus = function () {
            showInfo(ch);
            loadNow(i - 1, 8);
            if (i >= rows.length - 8 && rows.length < channels.length) appendChunk();
        };
        return row;
    }

    function refreshCounts() {
        var f = folders();
        UP.each(f, function (x) {
            var row = catEls[x.id];
            if (row && row.lastChild) row.lastChild.textContent = String(x.count);
        });
    }

    // ------------------------------------------------------------------------ reproducir
    function play(i) {
        var catLabel = folder === "all" || folder === "fav" || folder === "history" ? null : (UP.lib.catName.live[folder] || null);
        var items = [];
        UP.each(channels, function (ch) {
            items.push({
                type: "live", id: ch.id, title: ch.name, subtitle: catLabel || UP.lib.catName.live[ch.cat] || "", ch: ch,
                url: function (fmt) { return UP.api.liveUrl(UP.session.creds, ch.id, fmt); }
            });
        });
        UP.player.open(items, i, { onExit: function () { refreshCounts(); if (folder === "history") selectFolder("history"); } });
    }

    // -------------------------------------------------------------------------- pantalla
    buildCats();
    selectFolder("all");

    return {
        name: "live",
        el: el,
        firstFocus: function () { return catEls.all; },
        onShow: function (back) { if (back) refreshCounts(); },
        destroy: function () { clearTimeout(selectTimer); }
    };
};
