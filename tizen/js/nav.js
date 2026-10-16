/* Control remoto: teclas, foco entre elementos (por posición en pantalla), pila de pantallas y ventanas emergentes. */
(function () {
    UP.keys = {
        LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACKSPACE: 8, ESC: 27,
        BACK: 10009, PLAY: 415, PAUSE: 19, PLAYPAUSE: 10252, STOP: 413, FF: 417, RW: 412,
        CHUP: 427, CHDOWN: 428, INFO: 457, RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406,
        IME_DONE: 65376, IME_CANCEL: 65385
    };

    var DIRS = { 37: "left", 38: "up", 39: "right", 40: "down" };
    var nav = UP.nav = {
        stack: [],
        current: null,
        modal: null,
        root: null,

        init: function () {
            nav.root = UP.$("app");
            document.addEventListener("keydown", onKeyDown, false);
            nav.registerKeys();
        },

        /** Pide a la TV las teclas que normalmente maneja ella misma (multimedia, canales, colores y números). */
        registerKeys: function () {
            var names = ["MediaPlay", "MediaPause", "MediaPlayPause", "MediaStop", "MediaFastForward", "MediaRewind",
                "ChannelUp", "ChannelDown", "Info", "ColorF0Red", "ColorF1Green", "ColorF2Yellow", "ColorF3Blue",
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];
            for (var i = 0; i < names.length; i++) {
                try { window.tizen.tvinputdevice.registerKey(names[i]); } catch (e) { /* no es una TV Samsung */ }
            }
        },

        top: function () { return nav.stack.length ? nav.stack[nav.stack.length - 1] : null; },

        // ------------------------------------------------------------ pantallas
        push: function (scr) {
            var prev = nav.top();
            if (prev) {
                prev.lastFocus = nav.current;
                prev.el.classList.remove("on");
                if (prev.onHide) prev.onHide();
            }
            nav.root.appendChild(scr.el);
            scr.el.classList.add("on");
            nav.stack.push(scr);
            nav.current = null;
            if (scr.onShow) scr.onShow(false);
            if (!nav.current) {
                var first = scr.firstFocus ? scr.firstFocus() : null;
                if (first) nav.focus(first); else nav.focusFirst();
            }
        },

        pop: function () {
            if (nav.stack.length < 1) return;
            var s = nav.stack.pop();
            s.el.classList.remove("on");
            if (s.onHide) s.onHide();
            if (s.destroy) s.destroy();
            if (s.el.parentNode) s.el.parentNode.removeChild(s.el);
            nav.current = null;
            var p = nav.top();
            if (p) {
                p.el.classList.add("on");
                if (p.onShow) p.onShow(true);
                var last = p.lastFocus;
                if (last && p.el.contains(last) && visible(last)) nav.focus(last);
                else if (!nav.current) nav.focusFirst();
            }
        },

        /** Quita todas las pantallas y deja solo esta. */
        reset: function (scr) {
            while (nav.stack.length) {
                var s = nav.stack.pop();
                if (s.onHide) s.onHide();
                if (s.destroy) s.destroy();
                if (s.el.parentNode) s.el.parentNode.removeChild(s.el);
            }
            nav.current = null;
            nav.push(scr);
        },

        /** Reemplaza la pantalla de arriba por otra. */
        replace: function (scr) {
            var s = nav.stack.pop();
            if (s) {
                if (s.onHide) s.onHide();
                if (s.destroy) s.destroy();
                if (s.el.parentNode) s.el.parentNode.removeChild(s.el);
            }
            nav.current = null;
            nav.push(scr);
        },

        back: function () {
            if (nav.modal) {
                if (nav.modal.onBack && nav.modal.onBack() === true) return;
                UP.modal.close();
                return;
            }
            var t = nav.top();
            if (!t) return;
            if (t.onBack && t.onBack() === true) return;
            if (nav.stack.length > 1) nav.pop(); else if (UP.app && UP.app.askExit) UP.app.askExit();
        },

        // ---------------------------------------------------------------- foco
        activeRoot: function () {
            if (nav.modal) return nav.modal.el;
            var t = nav.top();
            return t ? t.el : null;
        },

        candidates: function () {
            var r = nav.activeRoot();
            if (!r) return [];
            var all = r.querySelectorAll(".f"), out = [];
            for (var i = 0; i < all.length; i++) if (visible(all[i])) out.push(all[i]);
            return out;
        },

        focus: function (el, noScroll) {
            if (!el) return;
            var cur = nav.current;
            if (cur && cur !== el) {
                cur.classList.remove("focused");
                if (cur._onblur) cur._onblur(cur);
            }
            nav.current = el;
            el.classList.add("focused");
            if (el.tagName === "INPUT") {
                try { el.focus(); } catch (e) { /* nada */ }
            } else if (document.activeElement && document.activeElement.tagName === "INPUT") {
                try { document.activeElement.blur(); } catch (e2) { /* nada */ }
            }
            if (!noScroll) nav.ensureVisible(el);
            if (el._onfocus) el._onfocus(el);
        },

        focusFirst: function () {
            var list = nav.candidates();
            if (list.length) nav.focus(list[0]);
        },

        /** Si el elemento quedó fuera de la vista de su lista, la desplaza. */
        ensureVisible: function (el) {
            var sc = el.parentNode;
            while (sc && sc !== document.body && sc.nodeType === 1) {
                if (sc.classList.contains("scroller")) {
                    var s = sc.getBoundingClientRect(), r = el.getBoundingClientRect(), m = 36;
                    if (sc.scrollHeight > sc.clientHeight + 1) {
                        if (r.top < s.top + m) sc.scrollTop -= (s.top + m - r.top);
                        else if (r.bottom > s.bottom - m) sc.scrollTop += (r.bottom - (s.bottom - m));
                    }
                    if (sc.classList.contains("hscroll") && sc.scrollWidth > sc.clientWidth + 1) {
                        if (r.left < s.left + m) sc.scrollLeft -= (s.left + m - r.left);
                        else if (r.right > s.right - m) sc.scrollLeft += (r.right - (s.right - m));
                    }
                }
                sc = sc.parentNode;
            }
        },

        /** Mueve el foco hacia una dirección: el elemento más cercano en esa dirección y mejor alineado. */
        move: function (dir) {
            var cur = nav.current;
            if (!cur || !document.body.contains(cur) || !visible(cur)) {
                nav.focusFirst();
                return;
            }
            if (cur._nav && cur._nav[dir]) {
                var target = cur._nav[dir](cur);
                if (target === false) return;     // se atendió a mano (sin mover el foco)
                if (target) { nav.focus(target); return; }
            }
            var list = nav.candidates(), best = null, bestScore = 1e12;
            var c = cur.getBoundingClientRect();
            var ccx = c.left + c.width / 2, ccy = c.top + c.height / 2;
            for (var i = 0; i < list.length; i++) {
                var el = list[i];
                if (el === cur) continue;
                var r = el.getBoundingClientRect();
                var dx = (r.left + r.width / 2) - ccx, dy = (r.top + r.height / 2) - ccy;
                var prim, perp, ok;
                if (dir === "right") { ok = dx > 2 && r.left >= c.left + 2; prim = dx; perp = Math.abs(dy); }
                else if (dir === "left") { ok = dx < -2 && r.right <= c.right - 2; prim = -dx; perp = Math.abs(dy); }
                else if (dir === "down") { ok = dy > 2 && r.top >= c.top + 2; prim = dy; perp = Math.abs(dx); }
                else { ok = dy < -2 && r.bottom <= c.bottom - 2; prim = -dy; perp = Math.abs(dx); }
                if (!ok) continue;
                var score = prim + perp * 2.5;
                if (score < bestScore) { bestScore = score; best = el; }
            }
            if (best) nav.focus(best);
        }
    };

    function visible(el) {
        return el.offsetWidth > 0 && el.offsetHeight > 0;
    }

    function onKeyDown(e) {
        var code = e.keyCode;
        var handler = nav.modal || nav.top();
        if (handler && handler.onKey && handler.onKey(e, code) === true) {
            e.preventDefault();
            return;
        }
        var t = e.target;
        var isInput = !!(t && t.tagName === "INPUT");

        if (DIRS[code]) {
            if (isInput && (code === UP.keys.LEFT || code === UP.keys.RIGHT)) {
                // Dentro de un cuadro de texto, ◀ ▶ mueven el cursor; solo se sale al llegar al borde.
                var atEdge = true;
                try {
                    atEdge = code === UP.keys.LEFT ? t.selectionStart === 0 : t.selectionEnd === t.value.length;
                } catch (err) { atEdge = true; }
                if (!atEdge) return;
            }
            e.preventDefault();
            nav.move(DIRS[code]);
            return;
        }
        if (code === UP.keys.ENTER) {
            if (isInput) return;                       // la TV abre su teclado
            var cur = nav.current;
            if (cur && document.body.contains(cur) && cur.click) { e.preventDefault(); cur.click(); }
            return;
        }
        if (code === UP.keys.IME_DONE) {               // "Listo" del teclado de la TV
            e.preventDefault();
            nav.move("down");
            return;
        }
        if (code === UP.keys.BACK || code === UP.keys.ESC || (code === UP.keys.BACKSPACE && !isInput)) {
            e.preventDefault();
            nav.back();
        }
    }

    // ---------------------------------------------------- ventanas emergentes
    UP.modal = {
        stack: [],

        /** spec: { el, onKey(e, code), onBack(), onClose(), focus (elemento inicial) }. */
        open: function (spec) {
            spec.prevFocus = nav.current;
            spec.prevModal = nav.modal;
            UP.$("modal-root").appendChild(spec.el);
            nav.modal = spec;
            UP.modal.stack.push(spec);
            var first = spec.focus || null;
            if (first) nav.focus(first); else nav.focusFirst();
            return spec;
        },

        close: function (spec) {
            spec = spec || nav.modal;
            if (!spec) return;
            var i = UP.modal.stack.indexOf(spec);
            if (i >= 0) UP.modal.stack.splice(i, 1);
            if (spec.el.parentNode) spec.el.parentNode.removeChild(spec.el);
            nav.modal = UP.modal.stack.length ? UP.modal.stack[UP.modal.stack.length - 1] : null;
            if (spec.onClose) spec.onClose();
            var p = spec.prevFocus;
            if (p && document.body.contains(p) && visible(p)) {
                nav.focus(p, true);
            } else {
                nav.current = null;
                if (!nav.modal) nav.focusFirst();
            }
        }
    };
})();
