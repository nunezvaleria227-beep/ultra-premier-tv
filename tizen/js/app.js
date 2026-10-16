/* Arranque de la app: entrar, bajar las listas y mostrar el inicio. */
(function () {
    var app = UP.app = {
        init: function () {
            UP.nav.init();
            var s = UP.session.load();
            if (s) resumeSession(s); else app.showLogin();
            // Las listas se vuelven a bajar solas cada 12 horas, pero solo cuando se está en el inicio.
            setInterval(function () {
                if (!UP.lib.loaded || !UP.session.creds || UP.nav.modal) return;
                if (new Date().getTime() - UP.lib.loadedAt < UP.CONFIG.REFRESH_MS) return;
                var t = UP.nav.top();
                if (t && t.name === "home") app.reload();
            }, 5 * 60 * 1000);
        },

        showLogin: function (opts) {
            UP.nav.reset(UP.screens.login(opts));
        },

        /** Desde la pantalla de entrar. cb(error) solo se llama si falla. */
        signIn: function (creds, cb) {
            UP.api.login(creds, function (err, acc) {
                if (err) { cb(err); return; }
                UP.session.save(creds);
                loadEverything(creds, acc, false);
            });
        },

        signOut: function () {
            UP.session.clear();
            UP.lib.reset();
            UP.parental.lock();
            app.showLogin();
        },

        /** Vuelve a bajar todas las listas y regresa al inicio. */
        reload: function () {
            var creds = UP.session.creds;
            if (!creds) return;
            loadEverything(creds, UP.lib.account, true);
        },

        askExit: function () {
            if (UP.nav.modal) return;
            UP.confirm("Salir", "¿Quieres salir de Ultra Premier TV?", "Salir", "Quedarme", function (yes) {
                if (yes) app.exit();
            });
        },

        exit: function () {
            try { window.tizen.application.getCurrentApplication().exit(); return; } catch (e) { /* no es una TV Samsung */ }
            try { window.close(); } catch (e2) { /* nada */ }
        }
    };

    /** Hay una sesión guardada: se comprueba la cuenta y se entra directo. */
    function resumeSession(creds) {
        var scr = UP.screens.loading("Entrando…");
        UP.nav.reset(scr);
        UP.session.creds = creds;
        UP.api.login(creds, function (err, acc) {
            if (err) {
                if (err.auth) {
                    // El proveedor ya no acepta esta cuenta: se pide entrar de nuevo.
                    UP.session.clear();
                    app.showLogin({ user: creds.user, error: err.message });
                } else {
                    UP.dialog("No se pudo entrar", err.message, [
                        { label: "Cambiar de cuenta", onClick: function () { app.signOut(); } },
                        { label: "Reintentar", primary: true, onClick: function () { resumeSession(creds); } }
                    ], { onCancel: function () { app.showLogin({ user: creds.user, error: err.message }); } });
                }
                return;
            }
            loadEverything(creds, acc, false);
        });
    }

    function loadEverything(creds, acc, isReload) {
        var scr = UP.screens.loading(isReload ? "Actualizando listas…" : "Cargando tu lista…");
        UP.nav.reset(scr);
        UP.lib.load(creds, function (done, total, label) {
            scr.set("Cargando " + label.toLowerCase() + "…", done / total);
        }, function (err, info) {
            if (err) {
                if (isReload && UP.lib.loaded) {
                    // Si falla una actualización, se sigue con lo que ya estaba.
                    UP.nav.reset(UP.screens.home());
                    UP.toast("No se pudieron actualizar las listas: " + err.message, 5000);
                    return;
                }
                if (err.auth) {
                    UP.session.clear();
                    app.showLogin({ user: creds.user, error: err.message });
                    return;
                }
                UP.dialog("No se pudieron cargar las listas", err.message, [
                    { label: "Cambiar de cuenta", onClick: function () { app.signOut(); } },
                    { label: "Reintentar", primary: true, onClick: function () { loadEverything(creds, acc, isReload); } }
                ], { onCancel: function () { app.showLogin({ user: creds.user, error: err.message }); } });
                return;
            }
            UP.lib.account = acc;
            UP.nav.reset(UP.screens.home());
            if (info && info.empty) UP.toast("Tu cuenta no tiene canales, películas ni series por ahora.", 6000);
            else if (isReload) UP.toast("Listas actualizadas", 2500);
        });
    }

    window.onerror = function (msg, src, line) { UP.log("error:", msg, src, line); };
    window.addEventListener("load", function () { app.init(); }, false);
})();
