/* Pantalla de inicio. */
UP.screens = UP.screens || {};

UP.screens.home = function () {
    var el = UP.el("div", "screen home");

    // ---------------------------------------------------------------- barra superior
    var bar = UP.el("div", "home-bar");
    var logo = UP.el("img", "home-logo");
    logo.src = "images/logo.png";
    bar.appendChild(logo);
    var right = UP.el("div", "home-right");
    var searchBtn = UP.btn("Búsqueda maestra", function () { UP.nav.push(UP.screens.search("all")); }, "pill");
    var accountBtn = UP.btn("Mi cuenta", function () { UP.nav.push(UP.screens.account()); }, "pill");
    var settingsBtn = UP.btn("Ajustes", function () { UP.nav.push(UP.screens.settings()); }, "pill");
    right.appendChild(searchBtn);
    right.appendChild(accountBtn);
    right.appendChild(settingsBtn);
    var clock = UP.el("div", "clock");
    clock.appendChild(UP.el("div", "clock-time", ""));
    clock.appendChild(UP.el("div", "clock-date", ""));
    right.appendChild(clock);
    bar.appendChild(right);
    el.appendChild(bar);

    var greeting = UP.el("div", "home-greeting");
    el.appendChild(greeting);

    // ----------------------------------------------------------------------- tarjetas
    var tiles = UP.el("div", "tiles");
    var tileInfo = [
        { id: "live", title: "TV EN DIRECTO", img: "tile_live.jpg", go: function () { UP.nav.push(UP.screens.live()); } },
        { id: "movie", title: "CINE", img: "tile_movies.jpg", go: function () { UP.nav.push(UP.screens.browse("movie")); } },
        { id: "series", title: "SERIES", img: "tile_series.jpg", go: function () { UP.nav.push(UP.screens.browse("series")); } },
        { id: "continue", title: "CONTINUAR VIENDO", img: "tile_continue.jpg", go: function () { UP.nav.push(UP.screens.continueWatching()); } }
    ];
    var subs = {};
    UP.each(tileInfo, function (t) {
        var tile = UP.f(UP.el("div", "tile"), t.go);
        var img = UP.el("img", "tile-img");
        img.src = "images/" + t.img;
        tile.appendChild(img);
        tile.appendChild(UP.el("div", "tile-title", t.title));
        subs[t.id] = UP.el("div", "tile-sub", "");
        tile.appendChild(subs[t.id]);
        tiles.appendChild(tile);
    });
    el.appendChild(tiles);

    var updated = UP.el("div", "home-updated");
    el.appendChild(updated);
    el.appendChild(UP.el("div", "home-hint", "Usa las flechas del control y OK para elegir."));

    // ------------------------------------------------------------------ contenido vivo
    function plural(n, one, many) { return n + " " + (n === 1 ? one : many); }

    function refresh() {
        var acc = UP.lib.account || {};
        var text = "Hola, " + (acc.username || (UP.session.creds && UP.session.creds.user) || "");
        if (acc.expiresAt) text += "   ·   EXPIRACIÓN: " + UP.fmtDate(new Date(acc.expiresAt * 1000));
        greeting.textContent = text;
        subs.live.textContent = plural(UP.lib.count("live"), "canal", "canales");
        subs.movie.textContent = plural(UP.lib.count("movie"), "película", "películas");
        subs.series.textContent = plural(UP.lib.count("series"), "serie", "series");
        var pending = UP.filter(UP.progress.entries(), function (e) { return !UP.lib.blocked(e.type === "movie" ? "movie" : "series", e.cat); }).length;
        subs.continue.textContent = pending ? plural(pending, "pendiente", "pendientes") : "Nada pendiente";
        var secs = Math.max(0, Math.round((new Date().getTime() - UP.lib.loadedAt) / 1000));
        updated.textContent = "Última actualización: " + (secs < 60 ? "hace " + secs + " seg" : "hace " + Math.round(secs / 60) + " min");
    }

    var timer = null;
    return {
        name: "home",
        el: el,
        onShow: function () {
            refresh();
            UP.tickClock();
            clearInterval(timer);
            timer = setInterval(refresh, 30000);
        },
        onHide: function () { clearInterval(timer); },
        destroy: function () { clearInterval(timer); },
        firstFocus: function () { return tiles.firstChild; },
        onBack: function () { UP.app.askExit(); return true; }
    };
};
