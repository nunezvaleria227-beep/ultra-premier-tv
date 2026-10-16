/* Listas de canales, películas y series (en memoria) y guía de programación. */
(function () {
    var KINDS = ["live", "movie", "series"];

    UP.lib = {
        loaded: false,
        loadedAt: 0,
        account: null,
        data: { live: [], movie: [], series: [] },
        cats: { live: [], movie: [], series: [] },
        byId: { live: {}, movie: {}, series: {} },
        catName: { live: {}, movie: {}, series: {} },
        adultCat: { live: {}, movie: {}, series: {} },
        siblings: {},          // nombre base -> canales con ese nombre base

        reset: function () {
            UP.lib.loaded = false;
            UP.lib.loadedAt = 0;
            UP.lib.account = null;
            UP.each(KINDS, function (k) {
                UP.lib.data[k] = [];
                UP.lib.cats[k] = [];
                UP.lib.byId[k] = {};
                UP.lib.catName[k] = {};
                UP.lib.adultCat[k] = {};
            });
            UP.lib.siblings = {};
            UP.epg.cache = {};
        },

        /** Baja todo. onStep(hechos, total, texto). cb(error). */
        load: function (creds, onStep, cb) {
            var jobs = [
                ["Canales", "live", "categories", "get_live_categories"],
                ["Canales", "live", "items"],
                ["Películas", "movie", "categories", "get_vod_categories"],
                ["Películas", "movie", "items"],
                ["Series", "series", "categories", "get_series_categories"],
                ["Series", "series", "items"]
            ];
            var done = 0, errors = [], pending = jobs.length;
            var results = { live: {}, movie: {}, series: {} };

            function finished() {
                // Si no llegó nada, el problema es de conexión o de la cuenta.
                var got = results.live.items || results.movie.items || results.series.items;
                var gotAny = (results.live.items && results.live.items.length) || (results.movie.items && results.movie.items.length) ||
                    (results.series.items && results.series.items.length);
                if (!got) {
                    var authErr = UP.find(errors, function (e) { return e.auth; });
                    return cb(authErr || errors[0] || { message: "No se pudieron bajar las listas." });
                }
                UP.lib.reset();
                UP.each(KINDS, function (k) {
                    UP.lib.cats[k] = results[k].categories || [];
                    UP.lib.data[k] = results[k].items || [];
                    UP.each(UP.lib.cats[k], function (c) {
                        UP.lib.catName[k][c.id] = c.name;
                        UP.lib.adultCat[k][c.id] = UP.isAdultName(c.name);
                    });
                    UP.each(UP.lib.data[k], function (it) { UP.lib.byId[k][it.id] = it; });
                });
                UP.each(UP.lib.data.live, function (ch) {
                    if (!ch.base) return;
                    (UP.lib.siblings[ch.base] = UP.lib.siblings[ch.base] || []).push(ch);
                });
                UP.lib.loaded = true;
                UP.lib.loadedAt = new Date().getTime();
                cb(null, { empty: !gotAny });
            }

            UP.each(jobs, function (job) {
                var label = job[0], kind = job[1], what = job[2];
                function end(err, value) {
                    if (err) errors.push(err); else results[kind][what] = value;
                    done++;
                    if (onStep) onStep(done, jobs.length, label);
                    if (--pending === 0) finished();
                }
                if (what === "categories") UP.api.categories(creds, job[3], end);
                else if (kind === "live") UP.api.liveStreams(creds, end);
                else if (kind === "movie") UP.api.movies(creds, end);
                else UP.api.series(creds, end);
            });
        },

        /** ¿Hay que esconder ahora lo que pertenece a esta categoría? */
        blocked: function (kind, catId) {
            return UP.parental.isLocked() && !!UP.lib.adultCat[kind][catId];
        },

        visibleCats: function (kind) {
            return UP.filter(UP.lib.cats[kind], function (c) { return !UP.lib.blocked(kind, c.id); });
        },

        visibleItems: function (kind) {
            if (!UP.parental.isLocked()) return UP.lib.data[kind];
            return UP.filter(UP.lib.data[kind], function (it) { return !UP.lib.blocked(kind, it.cat); });
        },

        count: function (kind) { return UP.lib.visibleItems(kind).length; },

        /** Elementos de una "carpeta": all, fav, history (solo canales), added (películas y series) o una categoría. */
        items: function (kind, folder) {
            var out = [], i, it;
            if (folder === "all") return UP.lib.visibleItems(kind);
            if (folder === "fav" || folder === "history") {
                var ids = folder === "fav" ? UP.fav.list(kind) : UP.recent.list();
                for (i = 0; i < ids.length; i++) {
                    it = UP.lib.byId[kind][ids[i]];
                    if (it && !UP.lib.blocked(kind, it.cat)) out.push(it);
                }
                return out;
            }
            if (folder === "added") {
                var all = UP.lib.visibleItems(kind).slice(0);
                all.sort(function (a, b) { return b.added - a.added; });
                return all.slice(0, 100);
            }
            var data = UP.lib.data[kind];
            for (i = 0; i < data.length; i++) if (data[i].cat === folder) out.push(data[i]);
            return UP.lib.blocked(kind, folder) ? [] : out;
        },

        /** Canales con el mismo nombre base (otras calidades), sin contar el propio. */
        siblingsOf: function (ch) {
            if (!ch.base) return [];
            return UP.filter(UP.lib.siblings[ch.base] || [], function (o) { return o.id !== ch.id; });
        }
    };

    // ------------------------------------------------------------ guía (EPG)
    UP.epg = {
        cache: {},
        waiting: {},
        queue: [],
        running: 0,
        MAX: 3,
        TTL: 120000,

        /** cb(lista de programas); lista vacía si no hay guía. Un canal sin guía propia usa la de su canal hermano. */
        get: function (ch, cb) {
            var now = new Date().getTime();
            var hit = UP.epg.cache[ch.id];
            if (hit && now - hit.ts < UP.epg.TTL) {
                setTimeout(function () { cb(hit.list); }, 0);
                return;
            }
            if (UP.epg.waiting[ch.id]) { UP.epg.waiting[ch.id].push(cb); return; }
            UP.epg.waiting[ch.id] = [cb];
            UP.epg.queue.push(ch);
            if (UP.epg.queue.length > 80) {
                var drop = UP.epg.queue.shift();
                UP.epg.resolve(drop, []);
            }
            UP.epg.pump();
        },

        resolve: function (ch, list, cache) {
            if (cache !== false) UP.epg.cache[ch.id] = { ts: new Date().getTime(), list: list };
            var cbs = UP.epg.waiting[ch.id] || [];
            delete UP.epg.waiting[ch.id];
            UP.each(cbs, function (f) { try { f(list); } catch (e) { UP.log("epg cb", e); } });
        },

        pump: function () {
            while (UP.epg.running < UP.epg.MAX && UP.epg.queue.length) {
                var ch = UP.epg.queue.pop();   // lo último que se pidió es lo que la persona está mirando
                UP.epg.running++;
                UP.epg.fetch(ch);
            }
        },

        fetch: function (ch) {
            var creds = UP.session.creds;
            var tries = [ch].concat(UP.lib.siblingsOf(ch).slice(0, 3));
            var n = 0;
            function next() {
                if (n >= tries.length || !creds) return end([]);
                var t = tries[n++];
                UP.api.shortEpg(creds, t.id, 2, function (err, list) {
                    if (err) return n === 1 ? end([], false) : end([]);
                    var now = Math.floor(new Date().getTime() / 1000);
                    list = UP.filter(list, function (e) { return e.end > now; });
                    if (list.length) end(list); else next();
                });
            }
            function end(list, cache) {
                UP.epg.running--;
                UP.epg.resolve(ch, list, cache);
                UP.epg.pump();
            }
            next();
        },

        /** De una lista de programas saca el que está al aire y el siguiente. */
        nowNext: function (list) {
            var now = Math.floor(new Date().getTime() / 1000), cur = null, nxt = null;
            for (var i = 0; i < list.length; i++) {
                if (list[i].start <= now && list[i].end > now) { cur = list[i]; nxt = list[i + 1] || null; break; }
                if (list[i].start > now) { nxt = list[i]; break; }
            }
            return { now: cur, next: nxt };
        }
    };
})();
