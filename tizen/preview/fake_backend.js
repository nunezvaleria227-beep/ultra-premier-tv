/* Reproductor de mentira para la demostración y las pruebas: no baja video; avanza el reloj y avisa igual que AVPlay.
   NO va dentro del paquete de la TV (solo se usa en navegador). */
(function () {
    UP.fakeNames = UP.fakeNames || {};
    UP.fakeLog = [];
    UP.fakeSpeed = 1;             // 1 = tiempo real; un número grande hace que el video "avance" más rápido (pruebas)
    UP.fakeFail = false;          // true: todo intento de reproducir falla (para probar los reintentos)

    var style = document.createElement("style");
    style.textContent =
        "#fake-video{position:absolute;left:0;top:0;width:1920px;height:1080px;z-index:0;display:none;overflow:hidden;" +
        "background:linear-gradient(120deg,#0b1030,#1d2f7a,#0d5a6e,#2a1b6b);background-size:400% 400%;animation:fvmove 14s ease infinite}" +
        "@keyframes fvmove{0%{background-position:0% 50%}50%{background-position:100% 50%}100%{background-position:0% 50%}}" +
        "#fake-video .fv-badge{position:absolute;right:48px;top:40px;padding:8px 18px;border-radius:20px;background:rgba(0,0,0,.45);font-size:22px;color:#cdd6f0;z-index:1}" +
        "#fake-video .fv-title{position:absolute;left:0;right:0;top:400px;text-align:center;font-size:76px;font-weight:700;color:rgba(255,255,255,.88)}" +
        "#fake-video .fv-sub{position:absolute;left:0;right:0;top:500px;text-align:center;font-size:34px;color:rgba(255,255,255,.65)}";
    document.head.appendChild(style);

    function ensureEl() {
        var e = document.getElementById("fake-video");
        if (!e) {
            e = document.createElement("div");
            e.id = "fake-video";
            e.innerHTML = '<div class="fv-badge">DEMOSTRACIÓN · sin video real</div><div class="fv-title"></div><div class="fv-sub"></div>';
            document.body.insertBefore(e, document.body.firstChild);
        }
        return e;
    }

    UP.fakeBackend = function () {
        var h = null, t = 0, dur = 0, playing = false, live = false, el = null, timer = null, startTimer = null;
        var audioSel = 0, textSel = null, lastSubSec = -1;
        var LANG = ["Español", "Inglés"];

        function clear() {
            clearInterval(timer); timer = null;
            clearTimeout(startTimer); startTimer = null;
        }

        return {
            name: "fake",
            open: function (url, startMs, handlers) {
                clear();
                h = handlers;
                var m = /\/(live|movie|series)\/[^\/]+\/[^\/]+\/([^.\/?]+)\.([a-z0-9]+)/i.exec(url) || [];
                var kind = m[1] || "movie";
                live = kind === "live";
                dur = live ? 0 : (kind === "series" ? 42 * 60000 : 95 * 60000);
                t = startMs || 0;
                playing = false;
                UP.fakeLog.push({ op: "open", url: url, startMs: startMs || 0 });
                el = ensureEl();
                el.style.display = "block";
                el.querySelector(".fv-title").textContent = UP.fakeNames[kind + ":" + m[2]] || (kind + " " + m[2]);
                el.querySelector(".fv-sub").textContent = live ? "EN VIVO  ·  formato " + String(m[3] || "").toUpperCase() : "";
                if (UP.fakeFail) { startTimer = setTimeout(function () { h.onError("fake"); }, 80); return; }
                h.onBuffering(true);
                startTimer = setTimeout(function () {
                    h.onBuffering(false);
                    playing = true;
                    h.onPlaying();
                    timer = setInterval(function () {
                        if (!playing) return;
                        t += 250 * (UP.fakeSpeed || 1);
                        h.onTime(t);
                        if (textSel !== null && Math.floor(t / 1000) % 6 === 0 && Math.floor(t / 1000) !== lastSubSec) {
                            lastSubSec = Math.floor(t / 1000);
                            h.onSubtitle("Subtítulo de ejemplo (" + LANG[textSel] + ")", 3000);
                        }
                        if (!live && t >= dur) { playing = false; clear(); h.onEnded(); }
                    }, 250);
                }, 450);
            },
            play: function () { playing = true; },
            pause: function () { playing = false; },
            isPlaying: function () { return playing; },
            seekTo: function (ms) { t = Math.max(0, Math.min(dur || ms, ms)); },
            jump: function (ms) { t = Math.max(0, dur ? Math.min(dur, t + ms) : t + ms); },
            duration: function () { return dur; },
            time: function () { return t; },
            stop: function () {
                clear();
                playing = false;
                UP.fakeLog.push({ op: "stop" });
                var e = document.getElementById("fake-video");
                if (e) e.style.display = "none";
            },
            setSize: function (mode) { UP.fakeLog.push({ op: "size", id: mode.id }); },
            tracks: function () {
                if (live) return { audio: [], text: [] };
                return {
                    audio: [{ id: 0, label: "Español", sel: audioSel === 0 }, { id: 1, label: "Inglés", sel: audioSel === 1 }],
                    text: [{ id: 0, label: "Español", sel: textSel === 0 }, { id: 1, label: "Inglés", sel: textSel === 1 }]
                };
            },
            selectTrack: function (kind, id) {
                UP.fakeLog.push({ op: "track", kind: kind, id: id });
                if (kind === "audio") audioSel = id; else textSel = id;
            }
        };
    };
})();
