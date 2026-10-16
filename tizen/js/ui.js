/* Piezas de pantalla que se repiten: botones, encabezado, avisos, ventanas de diálogo, selector y teclado de PIN. */
(function () {
    /** Marca un elemento como "enfocable" con el control remoto; al pulsar OK se llama onClick. */
    UP.f = function (el, onClick) {
        el.className += " f";
        if (onClick) el.onclick = onClick;
        return el;
    };

    UP.btn = function (text, onClick, cls) {
        return UP.f(UP.el("div", "btn" + (cls ? " " + cls : ""), text), onClick);
    };

    /** Imagen que se oculta sola si no carga. */
    UP.img = function (src, cls) {
        var i = document.createElement("img");
        if (cls) i.className = cls;
        i.onerror = function () { i.style.visibility = "hidden"; };
        i.onload = function () { i.style.visibility = "visible"; };
        if (src) i.src = src; else i.style.visibility = "hidden";
        return i;
    };

    // ------------------------------------------------------------------ reloj
    function tick() {
        var d = new Date();
        var t = UP.fmtClock(d), s = UP.fmtDate(d);
        var a = document.querySelectorAll(".clock-time"), b = document.querySelectorAll(".clock-date"), i;
        for (i = 0; i < a.length; i++) a[i].textContent = t;
        for (i = 0; i < b.length; i++) b[i].textContent = s;
    }
    setInterval(tick, 15000);
    UP.tickClock = tick;

    /** Encabezado común: logo, título y reloj. */
    UP.header = function (title) {
        var h = UP.el("div", "hdr");
        var logo = UP.el("img", "hdr-logo");
        logo.src = "images/logo.png";
        h.appendChild(logo);
        h.appendChild(UP.el("div", "hdr-title", title));
        var clock = UP.el("div", "clock");
        clock.appendChild(UP.el("div", "clock-time", ""));
        clock.appendChild(UP.el("div", "clock-date", ""));
        h.appendChild(clock);
        setTimeout(tick, 0);
        return h;
    };

    // ------------------------------------------------------------------ aviso
    var toastTimer = null;
    UP.toast = function (text, ms) {
        var t = UP.$("toast");
        t.textContent = text;
        t.className = "show";
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { t.className = ""; }, ms || 3500);
    };

    // ---------------------------------------------------------------- diálogo
    /** buttons: [{label, onClick, primary}]. Devuelve el modal. */
    UP.dialog = function (title, text, buttons, opts) {
        opts = opts || {};
        var back = UP.el("div", "modal-back");
        var box = UP.el("div", "dlg");
        box.appendChild(UP.el("div", "dlg-title", title));
        if (text) box.appendChild(UP.el("div", "dlg-text", text));
        var row = UP.el("div", "dlg-btns");
        var primary = null, spec;
        UP.each(buttons, function (b) {
            var btn = UP.btn(b.label, function () {
                UP.modal.close(spec);
                if (b.onClick) b.onClick();
            }, b.primary ? "primary" : "");
            row.appendChild(btn);
            if (b.primary || !primary) primary = btn;
        });
        box.appendChild(row);
        back.appendChild(box);
        spec = UP.modal.open({
            el: back,
            focus: primary,
            onBack: function () {
                if (opts.onCancel) opts.onCancel();
                return false;           // cierra el diálogo
            },
            onKey: function (e, code) {
                if (code === UP.keys.LEFT || code === UP.keys.RIGHT) { UP.nav.move(code === UP.keys.LEFT ? "left" : "right"); return true; }
                if (code === UP.keys.UP || code === UP.keys.DOWN) return true;
                return false;
            }
        });
        return spec;
    };

    UP.confirm = function (title, text, yes, no, cb) {
        UP.dialog(title, text, [
            { label: no || "No", onClick: function () { if (cb) cb(false); } },
            { label: yes || "Sí", primary: true, onClick: function () { if (cb) cb(true); } }
        ], { onCancel: function () { if (cb) cb(false); } });
    };

    UP.alert = function (title, text, cb) {
        UP.dialog(title, text, [{ label: "Entendido", primary: true, onClick: cb }]);
    };

    // --------------------------------------------------------------- selector
    /** options: [{label, value, selected}]. cb(value). */
    UP.pick = function (title, options, cb) {
        var back = UP.el("div", "modal-back");
        var box = UP.el("div", "dlg dlg-pick");
        box.appendChild(UP.el("div", "dlg-title", title));
        var list = UP.el("div", "pick-list scroller");
        var focusEl = null, spec;
        UP.each(options, function (o) {
            var row = UP.f(UP.el("div", "pick-row" + (o.selected ? " sel" : ""), o.label), function () {
                UP.modal.close(spec);
                cb(o.value);
            });
            list.appendChild(row);
            if (o.selected || !focusEl) focusEl = row;
        });
        box.appendChild(list);
        back.appendChild(box);
        spec = UP.modal.open({ el: back, focus: focusEl });
        return spec;
    };

    // ------------------------------------------------------------ teclado de PIN
    /** Pide un PIN de 4 dígitos. cb(pin) o cb(null) si se cancela. Acepta los números del control y los botones en pantalla. */
    UP.askPin = function (title, hint, cb) {
        var back = UP.el("div", "modal-back");
        var box = UP.el("div", "dlg dlg-pin");
        box.appendChild(UP.el("div", "dlg-title", title));
        var msg = UP.el("div", "dlg-text", hint || "");
        box.appendChild(msg);
        var dots = UP.el("div", "pin-dots");
        var dotEls = [];
        for (var i = 0; i < 4; i++) { var d = UP.el("span", "pin-dot"); dots.appendChild(d); dotEls.push(d); }
        box.appendChild(dots);
        var pad = UP.el("div", "pin-pad");
        var value = "", spec, five = null, finished = false;

        function render() {
            for (var k = 0; k < 4; k++) dotEls[k].className = "pin-dot" + (k < value.length ? " on" : "");
        }
        function finish(result) {
            if (finished) return;
            finished = true;
            UP.modal.close(spec);
            cb(result);
        }
        function add(n) {
            if (value.length >= 4) return;
            value += String(n);
            render();
            if (value.length === 4) setTimeout(function () { finish(value); }, 120);
        }
        function del() { value = value.slice(0, -1); render(); }

        var labels = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "Cancelar"];
        UP.each(labels, function (l) {
            var b = UP.btn(l, function () {
                if (l === "⌫") del(); else if (l === "Cancelar") finish(null); else add(l);
            }, "key");
            if (l === "5") five = b;
            pad.appendChild(b);
        });
        box.appendChild(pad);
        back.appendChild(box);
        spec = UP.modal.open({
            el: back,
            focus: five,
            onBack: function () { finish(null); return true; },
            onKey: function (e, code) {
                if (code >= 48 && code <= 57) { add(code - 48); return true; }
                if (code >= 96 && code <= 105) { add(code - 96); return true; }
                if (code === UP.keys.BACKSPACE) { del(); return true; }
                return false;
            }
        });
        return spec;
    };

    // ---------------------------------------------------- barra de progreso
    UP.bar = function (fraction, cls) {
        var wrap = UP.el("div", "bar" + (cls ? " " + cls : ""));
        var fill = UP.el("div", "bar-fill");
        fill.style.width = Math.max(0, Math.min(100, Math.round(fraction * 100))) + "%";
        wrap.appendChild(fill);
        return wrap;
    };

    /** Fila de una lista de pantalla (categorías, ajustes...). */
    UP.listRow = function (label, count, onClick, cls) {
        var row = UP.f(UP.el("div", "lrow" + (cls ? " " + cls : "")), onClick);
        row.appendChild(UP.el("span", "lrow-label", label));
        if (count !== null && count !== undefined) row.appendChild(UP.el("span", "lrow-count", String(count)));
        return row;
    };
})();
