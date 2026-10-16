/* Reproductor a pantalla completa. En la TV Samsung usa AVPlay (el reproductor propio de Samsung);
   si no existe (por ejemplo en una computadora) usa la etiqueta de video del navegador. */
(function () {
    var SIZE_MODES = [
        { id: "fit", label: "Ajustar (sin recortar)", av: "PLAYER_DISPLAY_MODE_LETTER_BOX", css: "contain" },
        { id: "fill", label: "Llenar la pantalla (recorta)", av: "PLAYER_DISPLAY_MODE_CROPPED_FULL", css: "cover" },
        { id: "stretch", label: "Estirar", av: "PLAYER_DISPLAY_MODE_FULL_SCREEN", css: "fill" }
    ];
    var LANGS = {
        spa: "Español", esp: "Español", es: "Español", eng: "Inglés", en: "Inglés", por: "Portugués", pt: "Portugués",
        fra: "Francés", fre: "Francés", fr: "Francés", ita: "Italiano", it: "Italiano", deu: "Alemán", ger: "Alemán",
        de: "Alemán", jpn: "Japonés", ja: "Japonés", kor: "Coreano", rus: "Ruso", chi: "Chino", zho: "Chino", und: "Sin idioma"
    };
    var MAX_RETRIES = 3;
    var SAVE_EVERY_MS = 10000;
    var BANNER_MS = 5000;
    var STALL_MS = 25000;

    /** Formato del video en vivo: "m3u8" (HLS) o "ts". */
    UP.liveFormat = function () {
        var f = UP.settings.get("liveFormat", null);
        if (f === "m3u8" || f === "ts") return f;
        var allowed = (UP.lib.account && UP.lib.account.allowedFormats) || [];
        var hasHls = allowed.length === 0;
        UP.each(allowed, function (a) { if (String(a).toLowerCase() === "m3u8") hasHls = true; });
        return hasHls ? "m3u8" : "ts";
    };

    UP.setLiveFormat = function (f) { UP.settings.set("liveFormat", f); };

    function sizeMode() {
        var id = UP.settings.get("sizeMode", "fit");
        return UP.find(SIZE_MODES, function (m) { return m.id === id; }) || SIZE_MODES[0];
    }

    function langName(code) {
        if (!code) return null;
        var c = String(code).toLowerCase();
        return LANGS[c] || (c.length <= 3 ? c.toUpperCase() : code);
    }

    // =========================================================== AVPlay (TV Samsung)
    function makeAvBackend() {
        var av = window.webapis.avplay;
        var h = null;
        return {
            name: "avplay",
            open: function (url, startMs, handlers) {
                h = handlers;
                try {
                    av.open(url);
                    av.setDisplayRect(0, 0, 1920, 1080);
                    try { av.setDisplayMethod(sizeMode().av); } catch (e0) { /* según el modelo */ }
                    av.setListener({
                        onbufferingstart: function () { h.onBuffering(true); },
                        onbufferingprogress: function () { },
                        onbufferingcomplete: function () { h.onBuffering(false); },
                        oncurrentplaytime: function (ms) { h.onTime(ms); },
                        onevent: function () { },
                        onerror: function (type) { h.onError(String(type)); },
                        onsubtitlechange: function (duration, text) { h.onSubtitle(text, duration); },
                        ondrmevent: function () { },
                        onstreamcompleted: function () { h.onEnded(); }
                    });
                    av.prepareAsync(function () {
                        function go() {
                            try { av.play(); } catch (e1) { h.onError(String(e1 && e1.message || e1)); return; }
                            h.onPlaying();
                        }
                        if (startMs > 0) {
                            try { av.seekTo(startMs, go, go); } catch (e2) { go(); }
                        } else {
                            go();
                        }
                    }, function (err) { h.onError(String(err && err.message || err)); });
                } catch (e) {
                    h.onError(String(e && e.message || e));
                }
            },
            play: function () { try { av.play(); } catch (e) { /* nada */ } },
            pause: function () { try { av.pause(); } catch (e) { /* nada */ } },
            isPlaying: function () { try { return av.getState() === "PLAYING"; } catch (e) { return false; } },
            seekTo: function (ms) { try { av.seekTo(Math.max(0, ms)); } catch (e) { /* nada */ } },
            jump: function (ms) {
                try {
                    if (ms >= 0) av.jumpForward(ms); else av.jumpBackward(-ms);
                } catch (e) { /* nada */ }
            },
            duration: function () { try { return av.getDuration(); } catch (e) { return 0; } },
            time: function () { try { return av.getCurrentTime(); } catch (e) { return 0; } },
            stop: function () {
                try { av.stop(); } catch (e) { /* nada */ }
                try { av.close(); } catch (e2) { /* nada */ }
            },
            setSize: function (mode) { try { av.setDisplayMethod(mode.av); } catch (e) { /* nada */ } },
            tracks: function () {
                var out = { audio: [], text: [] };
                try {
                    var all = av.getTotalTrackInfo() || [], cur = av.getCurrentStreamInfo() || [];
                    UP.each(all, function (t) {
                        var kind = t.type === "AUDIO" ? "audio" : (t.type === "TEXT" ? "text" : null);
                        if (!kind) return;
                        var info = {};
                        try { info = JSON.parse(t.extra_info); } catch (e) { info = {}; }
                        var n = out[kind].length + 1;
                        var sel = !!UP.find(cur, function (c) { return c.type === t.type && c.index === t.index; });
                        out[kind].push({ id: t.index, label: langName(info.language) || ((kind === "audio" ? "Audio " : "Subtítulo ") + n), sel: sel });
                    });
                } catch (e) { /* sin pistas */ }
                return out;
            },
            selectTrack: function (kind, id) {
                try {
                    if (kind === "text" && id === null) { av.setSilentSubtitle(true); return; }
                    av.setSelectTrack(kind === "audio" ? "AUDIO" : "TEXT", id);
                    if (kind === "text") av.setSilentSubtitle(false);
                } catch (e) { /* nada */ }
            }
        };
    }

    // ======================================================= <video> (computadora)
    function makeHtmlBackend() {
        var v = UP.$("html5-player");
        var h = null;
        return {
            name: "html5",
            open: function (url, startMs, handlers) {
                h = handlers;
                v.style.display = "block";
                v.style.objectFit = sizeMode().css;
                v.onloadedmetadata = function () { if (startMs > 0) { try { v.currentTime = startMs / 1000; } catch (e) { /* nada */ } } };
                v.onplaying = function () { h.onBuffering(false); h.onPlaying(); };
                v.onwaiting = function () { h.onBuffering(true); };
                v.ontimeupdate = function () { h.onTime(Math.floor(v.currentTime * 1000)); };
                v.onended = function () { h.onEnded(); };
                v.onerror = function () { h.onError("video"); };
                v.src = url;
                try { var p = v.play(); if (p && p.catch) p.catch(function () { /* lo maneja onerror */ }); } catch (e) { h.onError("video"); }
            },
            play: function () { try { v.play(); } catch (e) { /* nada */ } },
            pause: function () { try { v.pause(); } catch (e) { /* nada */ } },
            isPlaying: function () { return !v.paused; },
            seekTo: function (ms) { try { v.currentTime = Math.max(0, ms / 1000); } catch (e) { /* nada */ } },
            jump: function (ms) { try { v.currentTime = Math.max(0, v.currentTime + ms / 1000); } catch (e) { /* nada */ } },
            duration: function () { return isFinite(v.duration) ? Math.floor(v.duration * 1000) : 0; },
            time: function () { return Math.floor(v.currentTime * 1000); },
            stop: function () {
                v.onloadedmetadata = v.onplaying = v.onwaiting = v.ontimeupdate = v.onended = v.onerror = null;
                try { v.pause(); } catch (e) { /* nada */ }
                v.removeAttribute("src");
                try { v.load(); } catch (e2) { /* nada */ }
                v.style.display = "none";
            },
            setSize: function (mode) { v.style.objectFit = mode.css; },
            tracks: function () { return { audio: [], text: [] }; },
            selectTrack: function () { }
        };
    }

    function makeBackend() {
        // Las pruebas y la demostración pueden poner un reproductor de mentira (UP.fakeBackend).
        if (UP.fakeBackend) return UP.fakeBackend();
        if (window.webapis && window.webapis.avplay) return makeAvBackend();
        return makeHtmlBackend();
    }

    // =================================================================== pantalla
    /**
     * items: [{ type: "live"|"movie"|"episode", id, title, subtitle, url (texto o función(formato)), ch (canal, en vivo),
     *           startMs, resume (datos para "Continuar viendo") }]
     */
    UP.player = {
        open: function (items, index, opts) {
            if (!items || !items.length) return;
            opts = opts || {};
            var be = makeBackend();
            var idx = Math.max(0, Math.min(items.length - 1, index || 0));
            var retry = 0, lastTime = 0, lastDur = 0, lastTick = 0, loadedAt = 0, buffering = false, started = false;
            var timers = { banner: null, save: null, watch: null, retry: null, sub: null };
            var closed = false;

            var el = UP.el("div", "screen player");
            var top = UP.el("div", "p-top");
            var title = UP.el("div", "p-title"), sub = UP.el("div", "p-sub");
            top.appendChild(title); top.appendChild(sub);
            var msg = UP.el("div", "p-msg");
            var subtitle = UP.el("div", "p-subtitle");
            var bottom = UP.el("div", "p-bottom");
            var nowLine = UP.el("div", "p-now"), nextLine = UP.el("div", "p-next");
            var seek = UP.el("div", "p-seek");
            var curT = UP.el("span", "p-cur", "00:00"), durT = UP.el("span", "p-dur", "00:00");
            var bar = UP.bar(0, "p-bar");
            seek.appendChild(curT); seek.appendChild(bar); seek.appendChild(durT);
            var hint = UP.el("div", "p-hint");
            bottom.appendChild(nowLine); bottom.appendChild(nextLine); bottom.appendChild(seek); bottom.appendChild(hint);
            el.appendChild(top); el.appendChild(msg); el.appendChild(subtitle); el.appendChild(bottom);

            function item() { return items[idx]; }
            function isLive() { return item().type === "live"; }

            function setMsg(text) {
                msg.textContent = text || "";
                msg.style.display = text ? "block" : "none";
            }

            function showBanner(keep) {
                el.className = "screen player on show";
                clearTimeout(timers.banner);
                if (!keep) timers.banner = setTimeout(function () { el.className = "screen player on"; }, BANNER_MS);
            }

            function drawProgress() {
                if (isLive()) return;
                var dur = lastDur || be.duration();
                curT.textContent = UP.fmtDuration(lastTime);
                durT.textContent = dur > 0 ? UP.fmtDuration(dur) : "--:--";
                var fill = bar.firstChild;
                fill.style.width = (dur > 0 ? Math.max(0, Math.min(100, Math.round(lastTime * 100 / dur))) : 0) + "%";
            }

            function drawEpg(list) {
                var nn = UP.epg.nowNext(list || []);
                nowLine.textContent = nn.now ? "Ahora  " + UP.fmtHm(nn.now.start) + "  " + nn.now.title : "En vivo";
                nextLine.textContent = nn.next ? "Después  " + UP.fmtHm(nn.next.start) + "  " + nn.next.title : "";
            }

            function drawItem() {
                var it = item();
                title.textContent = it.title || "";
                sub.textContent = it.subtitle || "";
                seek.style.display = isLive() ? "none" : "block";
                nowLine.style.display = nextLine.style.display = isLive() ? "block" : "none";
                nowLine.textContent = isLive() ? "En vivo" : "";
                nextLine.textContent = "";
                hint.textContent = isLive()
                    ? "▲▼ cambiar de canal   OK información   AMARILLO formato (" + UP.liveFormat().toUpperCase() + ")   Atrás salir"
                    : "◀▶ adelantar / atrasar   OK pausa   ▲ opciones   Atrás salir";
                subtitle.textContent = "";
                if (isLive() && it.ch) {
                    var ch = it.ch;
                    UP.epg.get(ch, function (list) { if (!closed && item().ch === ch) drawEpg(list); });
                }
                drawProgress();
            }

            function saveProgress() {
                var it = item();
                if (!it.resume || it.type === "live" || lastTime < 5000) return;
                var e = {}, k;
                for (k in it.resume) if (it.resume.hasOwnProperty(k)) e[k] = it.resume[k];
                e.pos = lastTime;
                e.dur = lastDur || be.duration();
                if (e.dur > 0 && lastTime > e.dur * 0.95) UP.progress.remove(e.type, e.id);
                else UP.progress.save(e);
            }

            function clearTimers() {
                clearTimeout(timers.retry);
                clearInterval(timers.save);
                clearInterval(timers.watch);
                clearTimeout(timers.sub);
            }

            function giveUp() {
                be.stop();
                clearInterval(timers.watch);
                setMsg(isLive()
                    ? "No se pudo reproducir este canal.\nPulsa AMARILLO para probar otro formato (HLS o TS), o elige otro canal con ▲▼."
                    : "No se pudo reproducir este video.\nPulsa Atrás para volver.");
                showBanner(true);
            }

            function scheduleRetry(reason) {
                if (closed) return;
                UP.log("reintento", reason);
                clearInterval(timers.watch);
                retry++;
                if (retry > MAX_RETRIES) { giveUp(); return; }
                setMsg("Reconectando… (" + retry + "/" + MAX_RETRIES + ")");
                be.stop();
                var resumeAt = isLive() ? 0 : lastTime;
                clearTimeout(timers.retry);
                timers.retry = setTimeout(function () { if (!closed) startPlayback(resumeAt); }, 1500);
            }

            var handlers = {
                onPlaying: function () {
                    started = true;
                    lastTick = new Date().getTime();
                    setMsg("");
                },
                onBuffering: function (b) {
                    buffering = b;
                    lastTick = new Date().getTime();
                    if (b) setMsg("Cargando…"); else if (started) setMsg("");
                },
                onTime: function (ms) {
                    lastTick = new Date().getTime();
                    if (!started) { started = true; setMsg(""); }
                    if (retry && lastTick - loadedAt > 10000) retry = 0;
                    if (!isLive()) {
                        lastTime = ms;
                        var d = be.duration();
                        if (d > 0) lastDur = d;
                        drawProgress();
                    }
                },
                onEnded: function () {
                    if (closed) return;
                    if (isLive()) { scheduleRetry("fin de transmisión"); return; }
                    UP.progress.remove(item().type, item().id);
                    if (idx < items.length - 1) {
                        UP.toast("Siguiente: " + items[idx + 1].title);
                        idx++;
                        retry = 0;
                        loadItem();
                    } else {
                        exit();
                    }
                },
                onError: function (why) { scheduleRetry(why); },
                onSubtitle: function (text, duration) {
                    var clean = String(text || "").replace(/<br\s*\/?>/gi, "\n").replace(/<[^>]*>/g, "");
                    subtitle.textContent = clean;
                    clearTimeout(timers.sub);
                    timers.sub = setTimeout(function () { subtitle.textContent = ""; }, Math.max(1500, duration || 4000));
                }
            };

            function startPlayback(startMs) {
                started = false;
                buffering = false;
                loadedAt = lastTick = new Date().getTime();
                var it = item();
                var url = typeof it.url === "function" ? it.url(UP.liveFormat()) : it.url;
                if (!retry) setMsg("Cargando…");
                be.open(url, startMs || 0, handlers);
                clearInterval(timers.watch);
                timers.watch = setInterval(function () {
                    if (closed) return;
                    var idle = new Date().getTime() - (started ? lastTick : loadedAt);
                    if (idle > (buffering ? STALL_MS * 2 : STALL_MS)) scheduleRetry("sin avance");
                }, 2000);
            }

            function loadItem() {
                var it = item();
                lastTime = it.startMs || 0;
                lastDur = 0;
                retry = 0;
                drawItem();
                showBanner();
                if (it.type === "live" && it.ch) UP.recent.add(it.ch.id);
                startPlayback(it.startMs || 0);
            }

            function zap(delta) {
                if (!isLive() || items.length < 2) { showBanner(); return; }
                saveProgress();
                be.stop();
                idx = (idx + delta + items.length) % items.length;
                loadItem();
            }

            function toggleFormat() {
                if (!isLive()) return;
                var next = UP.liveFormat() === "m3u8" ? "ts" : "m3u8";
                UP.setLiveFormat(next);
                UP.toast("Formato de TV en vivo: " + (next === "m3u8" ? "HLS (m3u8)" : "MPEG-TS"));
                be.stop();
                retry = 0;
                drawItem();
                showBanner();
                startPlayback(0);
            }

            function togglePause() {
                if (isLive()) { showBanner(); return; }
                if (be.isPlaying()) { be.pause(); setMsg("Pausa"); showBanner(true); }
                else { be.play(); setMsg(""); showBanner(); }
            }

            function seekBy(ms) {
                if (isLive()) { showBanner(); return; }
                be.jump(ms);
                lastTime = Math.max(0, lastTime + ms);
                drawProgress();
                showBanner();
            }

            function exit() {
                if (closed) return;
                saveProgress();
                closed = true;
                clearTimers();
                clearTimeout(timers.banner);
                be.stop();
                UP.nav.pop();
                if (opts.onExit) opts.onExit(items[idx]);
            }

            // ------------------------------------------------------- opciones
            function openOptions() {
                var tr = be.tracks();
                var audioSel = UP.find(tr.audio, function (t) { return t.sel; });
                var textSel = UP.find(tr.text, function (t) { return t.sel; });
                var subsOn = !!(textSel && el.getAttribute("data-subs") !== "off");
                var rows = [
                    { label: "Audio: " + (audioSel ? audioSel.label : "Automático"), value: "audio" },
                    { label: "Subtítulos: " + (subsOn ? textSel.label : "Desactivados"), value: "text" },
                    { label: "Tamaño de la imagen: " + sizeMode().label, value: "size" }
                ];
                UP.pick("Opciones", rows, function (v) {
                    if (v === "audio") {
                        if (!tr.audio.length) { UP.toast("Este video no ofrece otros audios."); return; }
                        UP.pick("Audio", UP.filter(tr.audio, function () { return true; }).map(function (t) {
                            return { label: t.label, value: t.id, selected: t.sel };
                        }), function (id) { be.selectTrack("audio", id); });
                    } else if (v === "text") {
                        var opts2 = [{ label: "Desactivados", value: null, selected: !subsOn }];
                        UP.each(tr.text, function (t) { opts2.push({ label: t.label, value: t.id, selected: subsOn && t.sel }); });
                        if (opts2.length === 1) { UP.toast("Este video no trae subtítulos."); return; }
                        UP.pick("Subtítulos", opts2, function (id) {
                            el.setAttribute("data-subs", id === null ? "off" : "on");
                            if (id === null) subtitle.textContent = "";
                            be.selectTrack("text", id);
                        });
                    } else {
                        UP.pick("Tamaño de la imagen", SIZE_MODES.map(function (m) {
                            return { label: m.label, value: m.id, selected: m.id === sizeMode().id };
                        }), function (id) {
                            UP.settings.set("sizeMode", id);
                            be.setSize(sizeMode());
                        });
                    }
                });
            }

            // ------------------------------------------------------- pantalla
            var scr = {
                name: "player",
                el: el,
                firstFocus: function () { return null; },
                onShow: function () {
                    document.body.className = "playing";
                    document.documentElement.className = "playing";
                },
                onHide: function () {
                    document.body.className = "";
                    document.documentElement.className = "";
                },
                destroy: function () {
                    if (!closed) {
                        saveProgress();
                        closed = true;
                        clearTimers();
                        clearTimeout(timers.banner);
                        be.stop();
                    }
                },
                onBack: function () { exit(); return true; },
                onKey: function (e, code) {
                    var K = UP.keys;
                    switch (code) {
                        case K.ENTER:
                            if (isLive()) showBanner(); else togglePause();
                            return true;
                        case K.UP:
                            if (isLive()) zap(+1); else openOptions();
                            return true;
                        case K.DOWN:
                            if (isLive()) zap(-1); else showBanner();
                            return true;
                        case K.CHUP: zap(+1); return true;
                        case K.CHDOWN: zap(-1); return true;
                        case K.LEFT: seekBy(-10000); return true;
                        case K.RIGHT: seekBy(10000); return true;
                        case K.RW: seekBy(-30000); return true;
                        case K.FF: seekBy(30000); return true;
                        case K.PLAY: if (!be.isPlaying()) { be.play(); setMsg(""); } showBanner(); return true;
                        case K.PAUSE: if (!isLive()) { be.pause(); setMsg("Pausa"); showBanner(true); } return true;
                        case K.PLAYPAUSE: togglePause(); return true;
                        case K.STOP: exit(); return true;
                        case K.INFO: showBanner(); return true;
                        case K.YELLOW: toggleFormat(); return true;
                        case K.RED: openOptions(); return true;
                    }
                    return false;
                }
            };

            // Guarda el avance cada cierto tiempo.
            timers.save = setInterval(function () { if (!closed) saveProgress(); }, SAVE_EVERY_MS);

            UP.nav.push(scr);
            loadItem();
            return scr;
        }
    };
})();
