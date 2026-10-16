/* Utilidades generales (ES5). */
(function () {
    var MONTHS = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

    UP.log = function () {
        try { console.log.apply(console, arguments); } catch (e) { /* nada */ }
    };

    UP.$ = function (id) { return document.getElementById(id); };

    /** Crea un elemento: UP.el("div", "clase", "texto"). */
    UP.el = function (tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text !== undefined && text !== null) e.textContent = text;
        return e;
    };

    UP.clear = function (node) {
        while (node.firstChild) node.removeChild(node.firstChild);
    };

    UP.each = function (list, fn) {
        for (var i = 0; i < list.length; i++) fn(list[i], i);
    };

    UP.filter = function (list, fn) {
        var out = [];
        for (var i = 0; i < list.length; i++) if (fn(list[i], i)) out.push(list[i]);
        return out;
    };

    UP.find = function (list, fn) {
        for (var i = 0; i < list.length; i++) if (fn(list[i], i)) return list[i];
        return null;
    };

    UP.pad2 = function (n) { return n < 10 ? "0" + n : String(n); };

    /** Texto sin acentos y en minúsculas: sirve para buscar sin importar mayúsculas ni acentos. */
    UP.searchKey = function (text) {
        var s = String(text === undefined || text === null ? "" : text).toLowerCase();
        try { s = s.normalize("NFD").replace(/[̀-ͯ]/g, ""); } catch (e) { /* sin normalize */ }
        return s;
    };

    // ---------------------------------------------------------------- títulos
    var BRACKETS = /\[[^\]]*\]|\{[^}]*\}/g;
    var PAREN = /\([^)]*\)/g;
    var PAREN_YEAR = /\(\s*((?:19|20)\d{2})\s*\)/;
    var PREFIX = /^\s*(?:es|en|mx|lat|latam|us|uk|fr|de|it|pt|br|ar|co|cl|pe|ve|vip|cast|esp)\s*[|:\-–]+\s*/i;
    var TAGS = /\b(?:4k|uhd|fhd|hd|2160p?|1080p?|720p?|480p?|hdrip|web-?dl|webrip|bluray|brrip|dvdrip|x26[45]|h\.?26[45]|hevc|latino|castellano|dual|subtitulado)\b/gi;
    var TRAILING_YEAR = /\s+((?:19|20)\d{2})\s*$/;
    var SPACES = /\s+/g;
    var EDGES = /^[\s\-–|:.]+|[\s\-–|:.]+$/g;

    /** Nombre de película o serie como lo pondría una persona: sin etiquetas de calidad, idioma ni año. */
    UP.cleanTitle = function (raw) {
        var text = String(raw || "");
        text = text.replace(BRACKETS, " ").replace(PAREN, " ").replace(PREFIX, "").replace(TAGS, " ").replace(TRAILING_YEAR, "");
        text = text.replace(SPACES, " ").replace(EDGES, "");
        return text ? text : String(raw || "").replace(/^\s+|\s+$/g, "");
    };

    UP.yearOf = function (raw) {
        raw = String(raw || "");
        var m = PAREN_YEAR.exec(raw);
        if (m) return parseInt(m[1], 10);
        m = TRAILING_YEAR.exec(raw.replace(BRACKETS, " ").replace(/^\s+|\s+$/g, ""));
        return m ? parseInt(m[1], 10) : null;
    };

    // --------------------------------------------------------------- adultos
    var ADULT = new RegExp(
        "(\\bxxx\\b|\\badult(?!\\s*swim)\\w*|\\bporn\\w*|\\berotic\\w*|\\bhentai\\b|\\bplayboy\\b|" +
        "\\bsexo\\b|\\bsexy\\b|\\bsex\\b|\\bxvideos\\b|\\bonlyfans\\b|\\bpara mayores\\b|\\bmayores de 18\\b|" +
        "(^|\\s|\\[|\\()\\+\\s?18|(^|[^0-9])18\\s?\\+)"
    );

    UP.isAdultName = function (name) { return ADULT.test(UP.searchKey(name)); };

    // ----------------------------------------------------- guía de canal hermano
    var EPG_BRACKETS = /\[[^\]]*\]|\([^)]*\)|\{[^}]*\}/g;
    var EPG_QUALITY = /\b(uhd|fhd|hd|sd|4k|8k|hevc|h\.?26[45]|raw|backup|\d{3,4}p|\d{2,3}fps)\b/g;
    var NON_ALNUM = /[^a-z0-9]+/g;

    /** Nombre "base" de un canal: "MX | ESPN HD", "MX | ESPN FHD" y "MX | ESPN 4K" dan lo mismo. */
    UP.epgBaseKey = function (name) {
        var cleaned = UP.searchKey(name).replace(EPG_BRACKETS, " ").replace(EPG_QUALITY, " ").replace(NON_ALNUM, " ").replace(/^\s+|\s+$/g, "");
        return cleaned.length >= 3 ? cleaned : null;
    };

    // ------------------------------------------------------------- tiempo y fechas
    UP.fmtClock = function (d) {
        var h = d.getHours(), m = d.getMinutes();
        var ap = h >= 12 ? "PM" : "AM";
        h = h % 12; if (h === 0) h = 12;
        return UP.pad2(h) + ":" + UP.pad2(m) + " " + ap;
    };

    UP.fmtDate = function (d) {
        return MONTHS[d.getMonth()] + " " + d.getDate() + ", " + d.getFullYear();
    };

    UP.fmtHm = function (sec) {
        var d = new Date(sec * 1000);
        return UP.pad2(d.getHours()) + ":" + UP.pad2(d.getMinutes());
    };

    /** 3725000 -> "1:02:05" ; 47000 -> "00:47". */
    UP.fmtDuration = function (ms) {
        var s = Math.max(0, Math.floor(ms / 1000));
        var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), r = s % 60;
        return (h > 0 ? h + ":" + UP.pad2(m) : UP.pad2(m)) + ":" + UP.pad2(r);
    };

    // ------------------------------------------------------------------ texto
    UP.b64decode = function (value) {
        if (!value) return "";
        try {
            var bin = atob(String(value));
            try { return decodeURIComponent(escape(bin)).replace(/^\s+|\s+$/g, ""); } catch (e) { return bin.replace(/^\s+|\s+$/g, ""); }
        } catch (e2) {
            return String(value);
        }
    };

    /** Lee un campo como texto aunque el servidor lo mande como número; null si está vacío. */
    UP.str = function (v) {
        if (v === undefined || v === null) return null;
        var s = String(v).replace(/^\s+|\s+$/g, "");
        return (s === "" || s === "null") ? null : s;
    };

    UP.int = function (v, def) {
        var n = parseInt(v, 10);
        return isNaN(n) ? (def === undefined ? 0 : def) : n;
    };

    UP.randomHex = function (bytes) {
        var out = "";
        for (var i = 0; i < bytes; i++) out += ("0" + Math.floor(Math.random() * 256).toString(16)).slice(-2);
        return out;
    };

    UP.trunc = function (text, n) {
        text = String(text || "");
        return text.length > n ? text.slice(0, n - 1) + "…" : text;
    };

    /** SHA-256 de un texto con caracteres 0-255 (el PIN y su sal). Devuelve hexadecimal. */
    UP.sha256 = function sha256(ascii) {
        function rightRotate(value, amount) { return (value >>> amount) | (value << (32 - amount)); }
        var mathPow = Math.pow;
        var maxWord = mathPow(2, 32);
        var i, j;
        var result = "";
        var words = [];
        var asciiBitLength = ascii.length * 8;
        var hash = sha256.h = sha256.h || [];
        var k = sha256.k = sha256.k || [];
        var primeCounter = k.length;
        var isComposite = {};
        for (var candidate = 2; primeCounter < 64; candidate++) {
            if (!isComposite[candidate]) {
                for (i = 0; i < 313; i += candidate) isComposite[i] = candidate;
                hash[primeCounter] = (mathPow(candidate, 0.5) * maxWord) | 0;
                k[primeCounter++] = (mathPow(candidate, 1 / 3) * maxWord) | 0;
            }
        }
        ascii += "\x80";
        while (ascii.length % 64 - 56) ascii += "\x00";
        for (i = 0; i < ascii.length; i++) {
            j = ascii.charCodeAt(i);
            if (j >> 8) return "";
            words[i >> 2] |= j << ((3 - i) % 4) * 8;
        }
        words[words.length] = ((asciiBitLength / maxWord) | 0);
        words[words.length] = asciiBitLength;
        for (j = 0; j < words.length;) {
            var w = words.slice(j, j += 16);
            var oldHash = hash;
            hash = hash.slice(0, 8);
            for (i = 0; i < 64; i++) {
                var w15 = w[i - 15], w2 = w[i - 2];
                var a = hash[0], e = hash[4];
                var temp1 = hash[7] +
                    (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)) +
                    ((e & hash[5]) ^ ((~e) & hash[6])) +
                    k[i] +
                    (w[i] = (i < 16) ? w[i] : (
                        w[i - 16] +
                        (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) +
                        w[i - 7] +
                        (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))
                    ) | 0);
                var temp2 = (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)) +
                    ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]));
                hash = [(temp1 + temp2) | 0].concat(hash);
                hash[4] = (hash[4] + temp1) | 0;
            }
            for (i = 0; i < 8; i++) hash[i] = (hash[i] + oldHash[i]) | 0;
        }
        for (i = 0; i < 8; i++) {
            for (j = 3; j + 1; j--) {
                var b = (hash[i] >> (j * 8)) & 255;
                result += ((b < 16) ? 0 : "") + b.toString(16);
            }
        }
        return result;
    };
})();
