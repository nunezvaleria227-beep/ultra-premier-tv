/* Cine y Series: categorías a la izquierda y carteles a la derecha. kind = "movie" o "series". */
UP.screens.browse = function (kind) {
    var isMovie = kind === "movie";
    var el = UP.el("div", "screen browse");
    el.appendChild(UP.header(isMovie ? "Cine" : "Series"));

    var body = UP.el("div", "browse-body");
    var catsPane = UP.el("div", "pane cats scroller");
    var gridPane = UP.el("div", "grid scroller");
    body.appendChild(catsPane);
    body.appendChild(gridPane);
    el.appendChild(body);

    var CHUNK = 36;
    var folder = "all";
    var items = [];
    var cards = [];
    var catEls = {};
    var selectTimer = null;

    function posterOf(it) { return isMovie ? it.poster : it.cover; }

    // ------------------------------------------------------------------------ categorías
    function folders() {
        var list = [
            { id: "all", label: "TODO", count: UP.lib.count(kind) },
            { id: "fav", label: "FAVORITOS", count: UP.lib.items(kind, "fav").length },
            { id: "added", label: "RECIENTEMENTE AÑADIDO", count: null }
        ];
        var counts = {};
        UP.each(UP.lib.visibleItems(kind), function (c) { counts[c.cat] = (counts[c.cat] || 0) + 1; });
        UP.each(UP.lib.visibleCats(kind), function (c) { list.push({ id: c.id, label: c.name, count: counts[c.id] || 0 }); });
        return list;
    }

    function buildCats() {
        UP.clear(catsPane);
        catEls = {};
        var search = UP.listRow("🔍  Buscar", null, function () { UP.nav.push(UP.screens.search(kind)); }, "search");
        search._nav = { right: function () { return cards.length ? cards[0] : false; } };
        catsPane.appendChild(search);
        UP.each(folders(), function (f) {
            var row = UP.listRow(f.label, f.count, function () { goToGrid(f.id); });
            row._folder = f.id;
            row._onfocus = function () { scheduleSelect(f.id); };
            row._nav = {
                right: function () {
                    clearTimeout(selectTimer);
                    if (f.id !== folder) selectFolder(f.id);
                    return cards.length ? cards[0] : false;
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
        if (id === folder && cards.length) return;
        clearTimeout(selectTimer);
        selectTimer = setTimeout(function () { selectFolder(id); }, 160);
    }

    function selectFolder(id) {
        folder = id;
        items = UP.lib.items(kind, id);
        markSelected();
        renderGrid();
    }

    function goToGrid(id) {
        clearTimeout(selectTimer);
        if (id !== folder) selectFolder(id);
        if (cards.length) UP.nav.focus(cards[0]);
    }

    // ----------------------------------------------------------------------- carteles
    function emptyText() {
        if (folder === "fav") return isMovie ? "Aún no tienes películas favoritas." : "Aún no tienes series favoritas.";
        return isMovie ? "No hay películas en esta categoría." : "No hay series en esta categoría.";
    }

    function renderGrid() {
        UP.clear(gridPane);
        cards = [];
        gridPane.scrollTop = 0;
        if (!items.length) {
            gridPane.appendChild(UP.el("div", "empty", emptyText()));
            return;
        }
        appendChunk();
    }

    function appendChunk() {
        var from = cards.length, to = Math.min(items.length, from + CHUNK);
        for (var i = from; i < to; i++) {
            var c = card(items[i], i);
            cards.push(c);
            gridPane.appendChild(c);
        }
    }

    function card(it, i) {
        var c = UP.f(UP.el("div", "card"), function () { open(it); });
        var art = UP.el("div", "poster");
        art.appendChild(UP.img(posterOf(it), "poster-img"));
        c.appendChild(art);
        c.appendChild(UP.el("div", "card-title", it.title));
        var meta = [];
        if (it.year) meta.push(String(it.year));
        var r = parseFloat(it.rating);
        if (r > 0) meta.push("★ " + (Math.round(r * 10) / 10));
        c.appendChild(UP.el("div", "card-meta", meta.join("  ·  ")));
        c._nav = {
            left: function (cur) {
                // En la primera columna, ◀ vuelve a la categoría elegida.
                var r0 = cur.getBoundingClientRect(), g = gridPane.getBoundingClientRect();
                if (r0.left - g.left < 40) return catEls[folder] || null;
                return null;
            }
        };
        c._onfocus = function () {
            if (i >= cards.length - 12 && cards.length < items.length) appendChunk();
        };
        return c;
    }

    function open(it) {
        UP.nav.push(isMovie ? UP.screens.movieDetail(it) : UP.screens.seriesDetail(it));
    }

    function refreshCounts() {
        UP.each(folders(), function (x) {
            var row = catEls[x.id];
            if (row && row.lastChild && x.count !== null) row.lastChild.textContent = String(x.count);
        });
    }

    buildCats();
    selectFolder("all");

    return {
        name: kind,
        el: el,
        firstFocus: function () { return catEls.all; },
        onShow: function (back) {
            if (!back) return;
            refreshCounts();
            if (folder === "fav") selectFolder("fav");
        },
        destroy: function () { clearTimeout(selectTimer); }
    };
};
