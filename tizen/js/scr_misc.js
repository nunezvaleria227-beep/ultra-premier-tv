/* Entrar, cargando, buscar, continuar viendo, mi cuenta y ajustes. */
UP.screens = UP.screens || {};

(function () {
    function trim(s) { return String(s === undefined || s === null ? "" : s).replace(/^\s+|\s+$/g, ""); }

    /** Cuadro de texto enfocable con el control remoto. */
    function textField(label, type, value, placeholder) {
        var wrap = UP.el("div", "field");
        wrap.appendChild(UP.el("div", "field-label", label));
        var inp = document.createElement("input");
        inp.type = type;
        inp.value = value || "";
        inp.className = "f";
        inp.setAttribute("autocomplete", "off");
        inp.setAttribute("autocapitalize", "off");
        inp.setAttribute("autocorrect", "off");
        inp.spellcheck = false;
        if (placeholder) inp.placeholder = placeholder;
        wrap.appendChild(inp);
        return { wrap: wrap, input: inp };
    }

    // ================================================================== entrar
    /** opts: { user, error } */
    UP.screens.login = function (opts) {
        opts = opts || {};
        var needsServer = !UP.CONFIG.PORTAL_URL;
        var el = UP.el("div", "screen login");
        var wrap = UP.el("div", "login-wrap");

        var brand = UP.el("div", "login-brand");
        var big = UP.el("img", "logo-big");
        big.src = "images/logo.png";
        brand.appendChild(big);
        brand.appendChild(UP.el("div", "login-tag", "TV en vivo, películas y series"));
        brand.appendChild(UP.el("div", "login-tag2", "todo en un solo lugar"));
        wrap.appendChild(brand);

        var card = UP.el("div", "login-card");
        card.appendChild(UP.el("div", "login-title", "¡Bienvenido!"));
        card.appendChild(UP.el("div", "login-sub", "Entra con tu usuario y contraseña."));

        var server = null;
        if (needsServer) {
            server = textField("URL del servidor", "text", UP.store.get("last_server", ""), "http://servidor.com:8080");
            card.appendChild(server.wrap);
        }
        var user = textField("Usuario", "text", opts.user || "");
        var pass = textField("Contraseña", "password", "");
        var eye = UP.f(UP.el("div", "eye", "VER"), function () {
            var show = pass.input.type === "password";
            pass.input.type = show ? "text" : "password";
            eye.textContent = show ? "OCULTAR" : "VER";
        });
        pass.wrap.appendChild(eye);
        card.appendChild(user.wrap);
        card.appendChild(pass.wrap);

        var error = UP.el("div", "login-error", opts.error || "");
        card.appendChild(error);

        var busy = false;
        var go = UP.btn("Entrar", function () { submit(); }, "primary");
        card.appendChild(go);
        wrap.appendChild(card);
        el.appendChild(wrap);

        function setBusy(b) {
            busy = b;
            go.textContent = b ? "Entrando… un momento" : "Entrar";
        }

        function submit() {
            if (busy) return;
            var u = trim(user.input.value), p = pass.input.value;
            var s = needsServer ? trim(server.input.value) : UP.CONFIG.PORTAL_URL;
            if (needsServer && !s) { error.textContent = "Escribe la URL del servidor."; UP.nav.focus(server.input); return; }
            if (!u || !p) { error.textContent = "Escribe tu usuario y tu contraseña."; UP.nav.focus(u ? pass.input : user.input); return; }
            try { s = UP.api.normalizeServer(s); } catch (e) { error.textContent = e.message; return; }
            error.textContent = "";
            setBusy(true);
            if (needsServer) UP.store.set("last_server", s);
            UP.app.signIn({ server: s, user: u, pass: p }, function (err) {
                // Solo se llega aquí si falló (si salió bien, esta pantalla ya no existe).
                setBusy(false);
                error.textContent = err && err.message ? err.message : "No se pudo entrar.";
            });
        }

        // En una computadora, Enter dentro de un cuadro pasa al siguiente (en la TV, Enter abre el teclado de la TV).
        function onEnterNext(inp, nextEl) {
            inp.onkeydown = function (e) {
                if (e.keyCode === UP.keys.ENTER && !(window.webapis && window.webapis.avplay)) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (nextEl === go) submit(); else UP.nav.focus(nextEl);
                }
            };
        }
        if (server) onEnterNext(server.input, user.input);
        onEnterNext(user.input, pass.input);
        onEnterNext(pass.input, go);

        return {
            name: "login",
            el: el,
            firstFocus: function () { return opts.user ? pass.input : (server ? server.input : user.input); }
        };
    };

    // ================================================================ cargando
    UP.screens.loading = function (text) {
        var el = UP.el("div", "screen loading");
        var wrap = UP.el("div", "loading-wrap");
        var big = UP.el("img", "logo-big");
        big.src = "images/logo.png";
        wrap.appendChild(big);
        var label = UP.el("div", "loading-text", text || "Cargando…");
        wrap.appendChild(label);
        var bar = UP.bar(0.05);
        wrap.appendChild(bar);
        el.appendChild(wrap);
        return {
            name: "loading",
            el: el,
            onBack: function () { return true; },     // no se puede salir mientras carga
            /** Texto y avance (de 0 a 1). */
            set: function (t, fraction) {
                if (t) label.textContent = t;
                if (fraction !== undefined) bar.firstChild.style.width = Math.round(Math.max(0.05, Math.min(1, fraction)) * 100) + "%";
            }
        };
    };

    // ================================================================= buscar
    /** kind: "all" (búsqueda maestra), "live", "movie" o "series". */
    UP.screens.search = function (kind) {
        var titles = { all: "Búsqueda maestra", live: "Buscar canales", movie: "Buscar películas", series: "Buscar series" };
        var kinds = kind === "all" ? ["live", "movie", "series"] : [kind];
        var heads = { live: "CANALES", movie: "PELÍCULAS", series: "SERIES" };
        var el = UP.el("div", "screen search");
        el.appendChild(UP.header(titles[kind] || "Buscar"));

        var top = UP.el("div", "sr-top");
        var inp = document.createElement("input");
        inp.type = "text";
        inp.className = "f";
        inp.placeholder = "Escribe al menos 2 letras";
        inp.setAttribute("autocomplete", "off");
        inp.setAttribute("autocorrect", "off");
        inp.spellcheck = false;
        top.appendChild(inp);
        el.appendChild(top);

        var list = UP.el("div", "sr-list scroller");
        el.appendChild(list);

        var timer = null;
        var LIMIT = { live: 80, movie: 60, series: 60 };

        function rowFor(k, it, liveQueue, liveIndex) {
            var row = UP.f(UP.el("div", "srow"));
            var imgBox = UP.el("div", "srow-img" + (k === "live" ? "" : " tall"));
            var src = k === "live" ? it.icon : (k === "movie" ? it.poster : it.cover);
            if (src) imgBox.appendChild(UP.img(src));
            row.appendChild(imgBox);
            var txt = UP.el("div", "srow-txt");
            var name = k === "live" ? it.name : it.title + (it.year ? " (" + it.year + ")" : "");
            txt.appendChild(UP.el("div", "srow-title", name));
            var meta = [];
            meta.push(UP.lib.catName[k][it.cat] || "");
            var r = parseFloat(it.rating);
            if (k !== "live" && r > 0) meta.push("★ " + (Math.round(r * 10) / 10));
            txt.appendChild(UP.el("div", "srow-meta", UP.filter(meta, function (m) { return !!m; }).join("   ·   ")));
            row.appendChild(txt);
            row.onclick = function () {
                if (k === "live") {
                    var items = [];
                    UP.each(liveQueue, function (ch) {
                        items.push({
                            type: "live", id: ch.id, title: ch.name, subtitle: UP.lib.catName.live[ch.cat] || "", ch: ch,
                            url: function (fmt) { return UP.api.liveUrl(UP.session.creds, ch.id, fmt); }
                        });
                    });
                    UP.player.open(items, liveIndex, {});
                } else if (k === "movie") {
                    UP.nav.push(UP.screens.movieDetail(it));
                } else {
                    UP.nav.push(UP.screens.seriesDetail(it));
                }
            };
            return row;
        }

        function run() {
            var q = UP.searchKey(inp.value).replace(/^\s+|\s+$/g, "");
            UP.clear(list);
            list.scrollTop = 0;
            if (q.length < 2) {
                list.appendChild(UP.el("div", "empty", "Escribe al menos 2 letras para buscar.\nSe ignoran mayúsculas y acentos."));
                return;
            }
            var found = 0;
            UP.each(kinds, function (k) {
                var matches = [];
                var all = UP.lib.visibleItems(k);
                for (var i = 0; i < all.length && matches.length < LIMIT[k]; i++) {
                    if (all[i].key.indexOf(q) >= 0) matches.push(all[i]);
                }
                if (!matches.length) return;
                found += matches.length;
                if (kinds.length > 1) list.appendChild(UP.el("div", "sr-head", heads[k] + "  (" + matches.length + ")"));
                UP.each(matches, function (it, idx) { list.appendChild(rowFor(k, it, matches, idx)); });
            });
            if (!found) list.appendChild(UP.el("div", "empty", "No se encontró nada con «" + trim(inp.value) + "»."));
        }

        inp.oninput = function () {
            clearTimeout(timer);
            timer = setTimeout(run, 250);
        };
        // ▼ baja a los resultados; ▲ desde el primer resultado vuelve al cuadro de texto.
        inp._nav = { down: function () { return list.querySelector(".srow") || false; } };
        run();

        return {
            name: "search",
            el: el,
            firstFocus: function () { return inp; },
            destroy: function () { clearTimeout(timer); }
        };
    };

    // ======================================================== continuar viendo
    UP.screens.continueWatching = function () {
        var el = UP.el("div", "screen cw");
        el.appendChild(UP.header("Continuar viendo"));
        var list = UP.el("div", "cw-list scroller");
        el.appendChild(list);
        var firstRow = null;

        function kindOf(e) { return e.type === "movie" ? "movie" : "series"; }

        function resume(e) {
            if (e.type === "movie") {
                var m = UP.lib.byId.movie[e.id];
                var ext = e.ext || (m && m.ext) || "mp4";
                var copy = {};
                for (var k in e) if (e.hasOwnProperty(k)) copy[k] = e[k];
                UP.player.open([{
                    type: "movie", id: e.id, title: e.title, subtitle: "", startMs: e.pos || 0,
                    url: UP.api.movieUrl(UP.session.creds, e.id, ext),
                    resume: { type: "movie", id: e.id, ext: ext, title: e.title, subtitle: "", image: e.image || "", cat: e.cat }
                }], 0, { onExit: build });
            } else {
                var s = UP.lib.byId.series[e.sid];
                if (!s) { UP.toast("Esa serie ya no está en tu lista."); return; }
                UP.nav.push(UP.screens.seriesDetail(s, { auto: { episodeId: e.id, posMs: e.pos || 0 } }));
            }
        }

        function build() {
            UP.clear(list);
            firstRow = null;
            var entries = UP.filter(UP.progress.entries(), function (e) { return !UP.lib.blocked(kindOf(e), e.cat); });
            if (!entries.length) {
                list.appendChild(UP.el("div", "empty", "Nada pendiente por ahora.\nLo que dejes a medias aparece aquí para seguirlo donde te quedaste."));
                return;
            }
            UP.each(entries, function (e) {
                var row = UP.f(UP.el("div", "cwrow"), function () { resume(e); });
                var img = UP.el("div", "cw-img");
                if (e.image) img.appendChild(UP.img(e.image));
                row.appendChild(img);
                var txt = UP.el("div", "cw-txt");
                txt.appendChild(UP.el("div", "cw-title", e.title || ""));
                if (e.subtitle) txt.appendChild(UP.el("div", "cw-sub", e.subtitle));
                txt.appendChild(UP.el("div", "cw-pos", "Vas por " + UP.fmtDuration(e.pos || 0) + (e.dur > 0 ? " de " + UP.fmtDuration(e.dur) : "")));
                if (e.dur > 0) txt.appendChild(UP.bar((e.pos || 0) / e.dur));
                row.appendChild(txt);
                var del = UP.btn("Quitar", function () {
                    var rowsBefore = list.querySelectorAll(".cwrow"), at = 0;
                    for (var n = 0; n < rowsBefore.length; n++) if (rowsBefore[n] === row) at = n;
                    UP.progress.remove(e.type, e.id);
                    build();
                    // El foco se queda en el mismo lugar de la lista (o en el último renglón).
                    var rowsAfter = list.querySelectorAll(".cwrow");
                    if (rowsAfter.length) UP.nav.focus(rowsAfter[Math.min(at, rowsAfter.length - 1)]);
                }, "pill cw-remove");
                del._nav = { left: function () { return row; }, right: function () { return false; } };
                row._e = e;
                row._nav = { right: function () { return del; } };
                row.appendChild(del);
                list.appendChild(row);
                if (!firstRow) firstRow = row;
            });
        }
        build();

        return {
            name: "continue",
            el: el,
            firstFocus: function () { return firstRow; },
            onShow: function (back) {
                if (!back) return;
                build();
                if (firstRow) UP.nav.focus(firstRow);
            }
        };
    };

    // ================================================================= mi cuenta
    UP.screens.account = function () {
        var el = UP.el("div", "screen account");
        el.appendChild(UP.header("Mi cuenta"));
        var card = UP.el("div", "acc-card");
        el.appendChild(card);
        var rowsBox = UP.el("div", "acc-rows");
        card.appendChild(rowsBox);
        var msg = UP.el("div", "acc-msg", "Actualizando datos…");
        card.appendChild(msg);
        var btns = UP.el("div", "acc-btns");
        var refreshBtn = UP.btn("Actualizar datos", function () { load(); });
        var outBtn = UP.btn("Cerrar sesión", function () {
            UP.confirm("Cerrar sesión", "Se borrará tu usuario y contraseña de esta TV.\n¿Seguro?", "Cerrar sesión", "Cancelar", function (yes) {
                if (yes) UP.app.signOut();
            });
        }, "primary");
        btns.appendChild(refreshBtn);
        btns.appendChild(outBtn);
        card.appendChild(btns);

        function kv(k, v, warn) {
            var r = UP.el("div", "kv");
            r.appendChild(UP.el("span", "kv-k", k));
            r.appendChild(UP.el("span", "kv-v" + (warn ? " warn" : ""), v));
            rowsBox.appendChild(r);
        }

        function render(acc) {
            UP.clear(rowsBox);
            var c = UP.session.creds || {};
            kv("Usuario", (acc && acc.username) || c.user || "");
            if (acc) {
                kv("Estado", acc.status);
                if (acc.expiresAt) {
                    var days = Math.floor((acc.expiresAt * 1000 - new Date().getTime()) / 86400000);
                    kv("Vence", UP.fmtDate(new Date(acc.expiresAt * 1000)));
                    kv("Días restantes", String(Math.max(0, days)), days < 7);
                } else {
                    kv("Vence", "Sin fecha de vencimiento");
                }
                var conns = acc.activeConnections !== null && acc.activeConnections !== undefined ? String(acc.activeConnections) : "?";
                var maxc = acc.maxConnections !== null && acc.maxConnections !== undefined ? String(acc.maxConnections) : "?";
                var full = acc.maxConnections && acc.activeConnections >= acc.maxConnections;
                kv("Conexiones", conns + " de " + maxc, full);
            }
        }

        function load() {
            msg.textContent = "Actualizando datos…";
            UP.api.login(UP.session.creds, function (err, acc) {
                if (!el.parentNode) return;
                if (err) {
                    render(UP.lib.account);
                    msg.textContent = "No se pudieron actualizar los datos: " + err.message;
                    return;
                }
                UP.lib.account = acc;
                render(acc);
                var notes = [];
                if (acc.message) notes.push("Mensaje de tu proveedor: " + acc.message);
                if (acc.maxConnections && acc.activeConnections >= acc.maxConnections) {
                    notes.push("Tu cuenta ya está en uso en todas las pantallas que permite. Si un canal no abre, cierra la app en otro aparato.");
                }
                msg.textContent = notes.join("\n");
            });
        }
        render(UP.lib.account);
        load();

        return { name: "account", el: el, firstFocus: function () { return refreshBtn; } };
    };

    // ================================================================== ajustes
    UP.screens.settings = function () {
        var el = UP.el("div", "screen settings");
        el.appendChild(UP.header("Ajustes"));
        var box = UP.el("div", "set-list scroller");
        el.appendChild(box);
        var firstRow = null;

        function formatName(f) { return f === "m3u8" ? "HLS (m3u8)" : "MPEG-TS"; }
        function sizeName() {
            var id = UP.settings.get("sizeMode", "fit");
            return id === "fill" ? "Llenar la pantalla" : (id === "stretch" ? "Estirar" : "Ajustar");
        }

        function row(label, value, fn) {
            var r = UP.listRow(label, value, fn);
            box.appendChild(r);
            if (!firstRow) firstRow = r;
            return r;
        }

        function build(focusLabel) {
            UP.clear(box);
            firstRow = null;
            var focusEl = null;

            box.appendChild(UP.el("div", "set-head", "VIDEO"));
            row("Formato de TV en vivo", formatName(UP.liveFormat()), function () {
                UP.pick("Formato de TV en vivo", [
                    { label: "HLS (m3u8)", value: "m3u8", selected: UP.liveFormat() === "m3u8" },
                    { label: "MPEG-TS", value: "ts", selected: UP.liveFormat() === "ts" }
                ], function (v) { UP.setLiveFormat(v); UP.toast("Formato: " + formatName(v)); build("Formato de TV en vivo"); });
            });
            box.appendChild(UP.el("div", "set-note", "Si un canal se corta o no abre, prueba cambiando el formato. También puedes cambiarlo viendo un canal con el botón AMARILLO."));
            row("Tamaño de la imagen", sizeName(), function () {
                var cur = UP.settings.get("sizeMode", "fit");
                UP.pick("Tamaño de la imagen", [
                    { label: "Ajustar (sin recortar)", value: "fit", selected: cur === "fit" },
                    { label: "Llenar la pantalla (recorta)", value: "fill", selected: cur === "fill" },
                    { label: "Estirar", value: "stretch", selected: cur === "stretch" }
                ], function (v) { UP.settings.set("sizeMode", v); build("Tamaño de la imagen"); });
            });

            box.appendChild(UP.el("div", "set-head", "LISTAS"));
            row("Actualizar todas las listas ahora", "", function () { UP.app.reload(); });
            box.appendChild(UP.el("div", "set-note", "Vuelve a descargar canales, películas y series. Se actualizan solas cada 12 horas."));

            box.appendChild(UP.el("div", "set-head", "CONTROL PARENTAL"));
            var pstate = !UP.parental.hasPin() ? "Sin PIN" : (UP.parental.unlocked ? "Desbloqueado" : "Activado");
            row("Control parental", pstate, function () { parentalMenu(); });
            box.appendChild(UP.el("div", "set-note", "Con un PIN puesto se esconden las categorías para adultos (XXX, +18, adultos…) en listas, buscador y guía."));

            box.appendChild(UP.el("div", "set-head", "DATOS DE ESTA TV"));
            row("Borrar el historial de canales", "", function () {
                UP.confirm("Historial de canales", "Se borra la lista de los últimos canales que viste.", "Borrar", "Cancelar", function (yes) {
                    if (yes) { UP.recent.clear(); UP.toast("Historial borrado"); }
                });
            });
            row("Borrar la lista de Continuar viendo", "", function () {
                UP.confirm("Continuar viendo", "Se borra el minuto en que te quedaste en cada película y episodio.", "Borrar", "Cancelar", function (yes) {
                    if (yes) { UP.progress.clear(); UP.toast("Lista borrada"); }
                });
            });
            row("Cerrar sesión", "", function () {
                UP.confirm("Cerrar sesión", "Se borrará tu usuario y contraseña de esta TV.\n¿Seguro?", "Cerrar sesión", "Cancelar", function (yes) {
                    if (yes) UP.app.signOut();
                });
            });
            box.appendChild(UP.el("div", "set-note", "Ultra Premier TV para Samsung · versión " + UP.CONFIG.VERSION));

            var rowsNow = box.querySelectorAll(".lrow");
            for (var i = 0; i < rowsNow.length; i++) {
                if (focusLabel && rowsNow[i].firstChild.textContent === focusLabel) focusEl = rowsNow[i];
            }
            if (focusLabel && focusEl) UP.nav.focus(focusEl);
        }

        // ---- control parental
        function checkWait() {
            var w = UP.parental.waitSeconds();
            if (w > 0) { UP.toast("Demasiados intentos. Espera " + w + " segundos."); return true; }
            return false;
        }

        function askCurrent(then) {
            if (checkWait()) return;
            UP.askPin("PIN", "Escribe tu PIN actual.", function (p) {
                if (p === null) return;
                if (UP.parental.verify(p)) { then(); return; }
                UP.toast(UP.parental.waitSeconds() > 0 ? "Demasiados intentos. Espera " + UP.parental.waitSeconds() + " segundos." : "PIN incorrecto");
            });
        }

        function chooseNewPin(done) {
            UP.askPin("Elige un PIN", "4 números. Lo pedirá para ver contenido de adultos.", function (p1) {
                if (p1 === null) return;
                UP.askPin("Repite el PIN", "Escríbelo otra vez.", function (p2) {
                    if (p2 === null) return;
                    if (p1 !== p2) { UP.toast("Los PIN no coinciden. Inténtalo de nuevo."); return; }
                    UP.parental.setPin(p1);
                    UP.toast("Control parental activado");
                    if (done) done();
                });
            });
        }

        function parentalMenu() {
            var opts;
            if (!UP.parental.hasPin()) {
                opts = [{ label: "Poner un PIN y activar el control parental", value: "set" }];
            } else if (UP.parental.unlocked) {
                opts = [
                    { label: "Bloquear ahora", value: "lock" },
                    { label: "Cambiar el PIN", value: "change" },
                    { label: "Quitar el PIN (desactivar)", value: "remove" }
                ];
            } else {
                opts = [
                    { label: "Desbloquear con el PIN", value: "unlock" },
                    { label: "Cambiar el PIN", value: "change" },
                    { label: "Quitar el PIN (desactivar)", value: "remove" }
                ];
            }
            UP.pick("Control parental", opts, function (v) {
                if (v === "set") chooseNewPin(function () { build("Control parental"); });
                else if (v === "lock") { UP.parental.lock(); UP.toast("Contenido para adultos bloqueado"); build("Control parental"); }
                else if (v === "unlock") askCurrent(function () { UP.parental.unlocked = true; UP.toast("Desbloqueado hasta cerrar la app"); build("Control parental"); });
                else if (v === "change") askCurrent(function () { chooseNewPin(function () { build("Control parental"); }); });
                else if (v === "remove") askCurrent(function () { UP.parental.removePin(); UP.toast("Control parental desactivado"); build("Control parental"); });
            });
        }

        build();

        return {
            name: "settings",
            el: el,
            firstFocus: function () { return firstRow; },
            onShow: function (back) { if (back) build(); }
        };
    };
})();
