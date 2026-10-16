/* Datos guardados en la TV: sesión, favoritos, "Continuar viendo", recientes, ajustes y control parental. */
(function () {
    var PREFIX = "up_";
    var mem = {};   // por si la TV no deja usar localStorage

    UP.store = {
        get: function (key, def) {
            var raw = null;
            try { raw = window.localStorage.getItem(PREFIX + key); } catch (e) { raw = null; }
            if (raw === null || raw === undefined) return mem.hasOwnProperty(key) ? mem[key] : def;
            try { return JSON.parse(raw); } catch (e2) { return def; }
        },
        set: function (key, value) {
            mem[key] = value;
            try { window.localStorage.setItem(PREFIX + key, JSON.stringify(value)); } catch (e) { /* queda en memoria */ }
        },
        del: function (key) {
            delete mem[key];
            try { window.localStorage.removeItem(PREFIX + key); } catch (e) { /* nada */ }
        }
    };

    // ----------------------------------------------------------------- sesión
    UP.session = {
        creds: null,
        load: function () {
            var s = UP.store.get("session", null);
            if (s && s.user && s.pass && s.server) {
                this.creds = s;
                return s;
            }
            return null;
        },
        save: function (creds) {
            this.creds = creds;
            UP.store.set("session", creds);
        },
        clear: function () {
            this.creds = null;
            UP.store.del("session");
        }
    };

    // ------------------------------------------------------------ ajustes
    UP.settings = {
        get: function (key, def) {
            var all = UP.store.get("settings", {});
            return all.hasOwnProperty(key) ? all[key] : def;
        },
        set: function (key, value) {
            var all = UP.store.get("settings", {});
            all[key] = value;
            UP.store.set("settings", all);
        }
    };

    // ---------------------------------------------------------- favoritos
    // Se guardan solo los números de identificación, por tipo: live, movie, series (los más nuevos primero).
    UP.fav = {
        list: function (type) {
            var all = UP.store.get("fav", {});
            return all[type] || [];
        },
        has: function (type, id) {
            return UP.fav.list(type).indexOf(String(id)) >= 0;
        },
        /** Pone o quita; devuelve true si quedó como favorito. */
        toggle: function (type, id) {
            id = String(id);
            var all = UP.store.get("fav", {});
            var list = all[type] || [];
            var at = list.indexOf(id);
            var now;
            if (at >= 0) { list.splice(at, 1); now = false; } else { list.unshift(id); now = true; }
            all[type] = list;
            UP.store.set("fav", all);
            return now;
        }
    };

    // ----------------------------------------------------------- recientes
    UP.recent = {
        MAX: 30,
        list: function () { return UP.store.get("recent_live", []); },
        add: function (id) {
            id = String(id);
            var list = UP.filter(UP.recent.list(), function (x) { return x !== id; });
            list.unshift(id);
            UP.store.set("recent_live", list.slice(0, UP.recent.MAX));
        },
        clear: function () { UP.store.del("recent_live"); }
    };

    // --------------------------------------------------- continuar viendo
    // Una película o episodio que se dejó a medias. No se guarda la dirección del video (lleva usuario y contraseña).
    // Campos: type ("movie"|"episode"), id, ext, title, subtitle, image, pos, dur (milisegundos), ts, cat, sid (id de la serie).
    UP.progress = {
        MAX: 40,
        entries: function () {
            var list = UP.store.get("progress", []);
            return list instanceof Array ? list : [];
        },
        find: function (type, id) {
            id = String(id);
            return UP.find(UP.progress.entries(), function (e) { return e.type === type && String(e.id) === id; });
        },
        save: function (entry) {
            var rest = UP.filter(UP.progress.entries(), function (e) { return !(e.type === entry.type && String(e.id) === String(entry.id)); });
            entry.ts = new Date().getTime();
            rest.unshift(entry);
            UP.store.set("progress", rest.slice(0, UP.progress.MAX));
        },
        remove: function (type, id) {
            var list = UP.filter(UP.progress.entries(), function (e) { return !(e.type === type && String(e.id) === String(id)); });
            UP.store.set("progress", list);
        },
        clear: function () { UP.store.del("progress"); }
    };

    // ---------------------------------------------------- control parental
    // Con un PIN puesto, las categorías para adultos se esconden en todas partes. Se ven de nuevo al escribir el PIN
    // y vuelven a esconderse al pulsar "Bloquear ahora", al cerrar sesión o al cerrar la app.
    // El PIN no se guarda tal cual: solo su huella (SHA-256) con una sal al azar.
    UP.parental = {
        unlocked: false,
        failures: 0,
        blockedUntil: 0,
        MAX_FAILURES: 5,
        LOCK_MS: 60000,

        hasPin: function () { return !!UP.store.get("pin_hash", null); },
        isLocked: function () { return UP.parental.hasPin() && !UP.parental.unlocked; },
        waitSeconds: function () {
            var left = UP.parental.blockedUntil - new Date().getTime();
            return left <= 0 ? 0 : Math.ceil(left / 1000);
        },
        setPin: function (pin) {
            var salt = UP.randomHex(16);
            UP.store.set("pin_salt", salt);
            UP.store.set("pin_hash", UP.sha256(salt + ":" + pin));
            UP.parental.failures = 0;
            UP.parental.unlocked = false;
        },
        verify: function (pin) {
            var salt = UP.store.get("pin_salt", null), saved = UP.store.get("pin_hash", null);
            if (!salt || !saved) return false;
            if (UP.parental.waitSeconds() > 0) return false;
            var ok = UP.sha256(salt + ":" + pin) === saved;
            if (ok) {
                UP.parental.failures = 0;
            } else {
                UP.parental.failures++;
                if (UP.parental.failures >= UP.parental.MAX_FAILURES) {
                    UP.parental.blockedUntil = new Date().getTime() + UP.parental.LOCK_MS;
                    UP.parental.failures = 0;
                }
            }
            return ok;
        },
        unlock: function (pin) {
            var ok = UP.parental.verify(pin);
            if (ok) UP.parental.unlocked = true;
            return ok;
        },
        lock: function () { UP.parental.unlocked = false; },
        removePin: function () {
            UP.store.del("pin_hash");
            UP.store.del("pin_salt");
            UP.parental.unlocked = false;
        },
        /** ¿Hay que esconder una categoría con este nombre ahora mismo? */
        blocks: function (categoryName) {
            return UP.parental.isLocked() && UP.isAdultName(categoryName);
        }
    };
})();
