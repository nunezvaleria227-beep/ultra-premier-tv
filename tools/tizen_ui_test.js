/* Prueba de la app de Samsung en un navegador sin pantalla (Chromium), con los datos y el reproductor de ejemplo.
   Uso: node tizen_ui_test.js URL_DE_LA_APP CARPETA_DE_CAPTURAS
   Se mueve con el teclado igual que con el control remoto: flechas, Enter y Escape (Atrás). */
const { chromium } = require("playwright");
const fs = require("fs");

const BASE = process.argv[2] || "http://127.0.0.1:8765/index.html";
const OUT = process.argv[3] || "shots";
fs.mkdirSync(OUT, { recursive: true });

const results = [];
function check(name, ok, extra) {
    results.push({ name, ok: !!ok, extra: extra === undefined ? "" : String(extra) });
    console.log((ok ? "PASS " : "FAIL ") + name + (ok || extra === undefined ? "" : "  -> " + extra));
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
    const browser = await chromium.launch({ args: ["--autoplay-policy=no-user-gesture-required"] });
    const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
    const page = await ctx.newPage();
    const errors = [];
    page.on("pageerror", (e) => errors.push("pageerror: " + e.message));
    page.on("console", (m) => { if (m.type() === "error") errors.push("console: " + m.text()); });

    const key = async (k, n = 1) => { for (let i = 0; i < n; i++) { await page.keyboard.press(k); await sleep(60); } };
    // Teclas del control remoto que el teclado no tiene (amarillo, avance rápido, canal +/-...).
    const fire = (code) => page.evaluate((c) => {
        const ev = new KeyboardEvent("keydown", { bubbles: true, cancelable: true });
        Object.defineProperty(ev, "keyCode", { get: () => c });
        (document.activeElement && document.activeElement !== document.body ? document.activeElement : document).dispatchEvent(ev);
    }, code);
    const shot = (name) => page.screenshot({ path: `${OUT}/${name}.png` });
    const text = (sel) => page.evaluate((s) => { const e = document.querySelector(s); return e ? e.textContent : null; }, sel);
    const texts = (sel) => page.evaluate((s) => Array.prototype.map.call(document.querySelectorAll(s), (e) => e.textContent), sel);
    const focused = () => page.evaluate(() => (UP.nav.current ? (UP.nav.current.textContent || UP.nav.current.placeholder || "") : ""));
    const topScreen = () => page.evaluate(() => (UP.nav.top() ? UP.nav.top().name : null));
    const waitScreen = async (name, ms = 8000) => {
        const t0 = Date.now();
        while (Date.now() - t0 < ms) { if ((await topScreen()) === name) return true; await sleep(100); }
        return false;
    };
    const waitFor = async (fn, arg, ms = 8000) => {
        const t0 = Date.now();
        while (Date.now() - t0 < ms) { if (await page.evaluate(fn, arg)) return true; await sleep(100); }
        return false;
    };
    const goHome = async () => { for (let i = 0; i < 8 && (await topScreen()) !== "home"; i++) { await key("Escape"); await sleep(150); } };
    const tileSubs = () => texts(".home .tile-sub");
    // El inicio recuerda el último mosaico usado; para no depender de eso se pone el foco en el primero y se avanza con ▶.
    const tileFocus = async (i) => {
        await page.evaluate(() => UP.nav.focus(document.querySelector(".home .tile")));
        await key("ArrowRight", i);
    };

    try {
        // ------------------------------------------------------------ entrar
        await page.goto(BASE);
        check("aparece la pantalla de entrar", await waitScreen("login"));
        await sleep(300);
        await shot("01_login");
        const vals = await page.evaluate(() => Array.prototype.map.call(document.querySelectorAll(".login input"), (i) => i.value));
        check("usuario y contraseña de ejemplo ya escritos", vals.join("|") === "demo|demo", vals.join("|"));

        // contraseña mala: debe avisar
        await page.evaluate(() => { document.querySelectorAll(".login input")[1].value = "mala"; });
        await key("Enter", 2);                       // usuario -> contraseña -> entrar
        check("contraseña incorrecta avisa", await waitFor(() => /incorrectos/.test(document.querySelector(".login-error").textContent)),
            await text(".login-error"));
        await shot("01b_login_error");
        await page.evaluate(() => { document.querySelectorAll(".login input")[1].value = "demo"; });
        await page.evaluate(() => UP.nav.focus(document.querySelectorAll(".login input")[1]));
        await key("Enter");                          // entrar
        check("carga y llega al inicio", await waitScreen("home", 15000));
        await sleep(400);
        await shot("02_home");
        const subs = await tileSubs();
        check("inicio muestra conteos", subs[0] === "23 canales" && subs[1] === "22 películas" && subs[2] === "10 series", subs.join(" | "));
        check("saludo con usuario y vencimiento", /Hola, demo/.test(await text(".home-greeting")) && /EXPIRACIÓN/.test(await text(".home-greeting")), await text(".home-greeting"));

        // --------------------------------------------------------- TV en directo
        await key("Enter");
        check("abre TV en directo", await waitScreen("live"));
        await waitFor(() => document.querySelectorAll(".crow").length > 0);
        await sleep(500);
        await shot("03_live");
        const cats = await texts(".live .lrow-label");
        check("categorías de TV (todo, favoritos, historial + 6)", cats.length === 9 && cats[0] === "TODO" && /Noticias/.test(cats[3]), cats.join(", "));
        await key("ArrowRight");                     // a la lista de canales
        check("foco pasa a la lista de canales", (await page.evaluate(() => UP.nav.current && UP.nav.current.className)).indexOf("crow") >= 0);
        await key("ArrowDown", 2);
        await sleep(700);
        const infoName = await text(".info-name");
        check("panel de información muestra el canal", !!infoName && infoName.length > 2, infoName);
        const nowTxt = await text(".info-now");
        check("la guía muestra «Ahora»", /Ahora|En vivo/.test(nowTxt || ""), nowTxt);
        await shot("04_live_focus");
        // el canal sin guía propia toma la de su canal hermano (FHD -> HD) o muestra «En vivo»
        await key("ArrowUp");                        // canal 2 (Canal Uno FHD)
        await sleep(900);
        check("canal sin guía propia usa la de su canal hermano", /Ahora/.test((await text(".info-now")) || ""), await text(".info-now"));
        // favorito
        await key("ArrowRight");                     // estrella
        await key("Enter");
        check("marca favorito", await page.evaluate(() => UP.fav.list("live").length === 1));
        await key("ArrowLeft");
        // reproducir
        await key("Enter");
        check("abre el reproductor", await waitScreen("player"));
        await sleep(1500);
        check("el video de ejemplo está encendido", await page.evaluate(() => document.getElementById("fake-video").style.display === "block"));
        await shot("05_player_live");
        const t1 = await text(".p-title");
        await key("ArrowUp");                        // canal siguiente
        await sleep(800);
        const t2 = await text(".p-title");
        check("▲ cambia de canal", t1 !== t2, t1 + " -> " + t2);
        await fire(405);                             // amarillo: formato
        await sleep(800);
        const fmt = await page.evaluate(() => UP.settings.get("liveFormat", null));
        const lastOpen = await page.evaluate(() => UP.fakeLog.filter((x) => x.op === "open").pop().url);
        check("AMARILLO cambia el formato y reabre el canal", fmt === "ts" && /\.ts$/.test(lastOpen), fmt + " " + lastOpen);
        await fire(405);
        await sleep(500);
        await key("Escape");
        check("Atrás vuelve a la lista", await waitScreen("live"));
        const histCount = await text(".live .lrow:nth-child(3) .lrow-count");
        check("el canal queda en el historial", Number(histCount) >= 2, histCount);
        // fallo de reproducción: reintenta y se rinde con un mensaje claro
        await page.evaluate(() => { UP.fakeFail = true; });
        await key("Enter");
        await waitScreen("player");
        check("si falla, avisa que reconecta", await waitFor(() => /Reconectando/.test(document.querySelector(".p-msg").textContent), null, 6000), await text(".p-msg"));
        check("tras los reintentos explica qué hacer", await waitFor(() => /No se pudo reproducir este canal/.test(document.querySelector(".p-msg").textContent), null, 12000), await text(".p-msg"));
        await shot("05b_player_error");
        await page.evaluate(() => { UP.fakeFail = false; });
        await key("Escape");
        await goHome();

        // ------------------------------------------------------------------- Cine
        await tileFocus(1);
        await key("Enter");
        check("abre Cine", await waitScreen("movie"));
        await waitFor(() => document.querySelectorAll(".card").length > 0);
        await sleep(600);
        await shot("06_movies");
        check("carteles de películas", (await page.evaluate(() => document.querySelectorAll(".card").length)) === 22);
        const catsM = await texts(".browse .lrow-label");
        check("categorías de cine incluyen Acción y Comedia", /Acción/.test(catsM.join()) && /Comedia/.test(catsM.join()), catsM.join(", "));
        await key("ArrowRight");
        await key("Enter");
        check("abre la ficha de la película", await waitScreen("movieDetail"));
        await waitFor(() => !/Cargando/.test(document.querySelector(".detail-plot").textContent));
        await sleep(300);
        await shot("07_movie_detail");
        check("ficha con sinopsis", /Una historia de ejemplo/.test(await text(".detail-plot")), await text(".detail-plot"));
        check("botón Reproducir", /Reproducir/.test(await focused()), await focused());
        await key("Enter");
        check("reproduce la película", await waitScreen("player"));
        await sleep(6200);
        await fire(417);                             // avance rápido +30 s
        await sleep(400);
        await shot("08_player_movie");
        check("barra de avance se mueve", (await text(".p-cur")) !== "00:00", await text(".p-cur"));
        await key("Escape");
        check("vuelve a la ficha", await waitScreen("movieDetail"));
        await sleep(300);
        check("ficha ofrece «Continuar desde»", /Continuar desde/.test(await focused()), await focused());
        await shot("09_movie_resume");
        await goHome();

        // ----------------------------------------------------------------- Series
        await tileFocus(2);
        await key("Enter");
        check("abre Series", await waitScreen("series"));
        await waitFor(() => document.querySelectorAll(".card").length > 0);
        check("carteles de series", (await page.evaluate(() => document.querySelectorAll(".card").length)) === 10);
        await key("ArrowRight");
        await key("Enter");
        check("abre la ficha de la serie", await waitScreen("seriesDetail"));
        await waitFor(() => document.querySelectorAll(".eprow").length > 0);
        await sleep(400);
        await shot("10_series_detail");
        check("temporadas y episodios", (await page.evaluate(() => document.querySelectorAll(".chip").length)) >= 2 &&
            (await page.evaluate(() => document.querySelectorAll(".eprow").length)) >= 4);
        check("botón «Ver desde el primer episodio»", /primer episodio/.test(await focused()), await focused());
        await page.evaluate(() => { UP.fakeSpeed = 700; });
        await key("Enter");
        check("reproduce el primer episodio", await waitScreen("player"));
        check("al terminar sigue el siguiente episodio", await waitFor(() => /E2/.test(document.querySelector(".p-sub").textContent), null, 15000), await text(".p-sub"));
        await page.evaluate(() => { UP.fakeSpeed = 1; });
        await sleep(1200);
        await shot("11_player_next_episode");
        await key("Escape");
        await waitScreen("seriesDetail");
        await goHome();

        // ---------------------------------------------------- continuar viendo
        await tileFocus(3);
        await key("Enter");
        check("abre Continuar viendo", await waitScreen("continue"));
        await sleep(400);
        await shot("12_continue");
        const cw = await texts(".cw-title");
        check("la película a medias aparece", cw.length >= 1, cw.join(" | "));
        const pos = await text(".cw-pos");
        check("dice en qué minuto vas", /Vas por \d\d:\d\d de 1:35:00/.test(pos || ""), pos);
        await key("Enter");
        check("reanuda la película", await waitScreen("player"));
        const startMs = await page.evaluate(() => UP.fakeLog.filter((x) => x.op === "open").pop().startMs);
        check("empieza en el minuto guardado", startMs >= 30000, startMs);
        await key("Escape");
        await waitScreen("continue");
        await goHome();

        // ----------------------------------------------------------------- buscar
        await tileFocus(0);
        await key("ArrowUp");
        for (let i = 0; i < 4 && !/Búsqueda maestra/.test(await focused()); i++) await key("ArrowLeft");
        check("foco llega a Búsqueda maestra con las flechas", /Búsqueda maestra/.test(await focused()), await focused());
        await key("Enter");
        check("abre la búsqueda", await waitScreen("search"));
        await page.keyboard.type("canal", { delay: 30 });
        await sleep(600);
        const heads = await texts(".sr-head");
        check("resultados agrupados", heads.length >= 1 && /CANALES/.test(heads[0]), heads.join(", "));
        await shot("13_search");
        await page.evaluate(() => { document.querySelector(".sr-top input").value = ""; });
        await page.keyboard.type("CAMINO");           // sin importar mayúsculas
        await sleep(600);
        check("busca sin importar mayúsculas ni acentos", (await texts(".srow-title")).some((t) => /Camino a Casa/.test(t)), (await texts(".srow-title")).join(" | "));
        await key("Escape");
        await goHome();

        // ------------------------------------------------------------- mi cuenta
        await tileFocus(0);
        await key("ArrowUp");
        for (let i = 0; i < 4 && !/Mi cuenta/.test(await focused()); i++) await key("ArrowRight");
        await key("Enter");
        check("abre Mi cuenta", await waitScreen("account"));
        await sleep(500);
        await shot("14_account");
        const kv = (await texts(".kv")).join(" | ");
        check("Mi cuenta: usuario, estado, vence, conexiones", /demo/.test(kv) && /Active/.test(kv) && /Vence/.test(kv) && /1 de 2/.test(kv), kv);
        await key("Escape");

        // --------------------------------------------------------------- ajustes
        await key("ArrowRight");
        check("foco en Ajustes", /Ajustes/.test(await focused()), await focused());
        await key("Enter");
        check("abre Ajustes", await waitScreen("settings"));
        await sleep(300);
        await shot("15_settings");
        // control parental: poner PIN 1234
        await page.evaluate(() => { const r = Array.prototype.filter.call(document.querySelectorAll(".settings .lrow"), (x) => /Control parental/.test(x.textContent))[0]; UP.nav.focus(r); });
        await key("Enter");
        await sleep(200);
        await key("Enter");                           // "Poner un PIN"
        await sleep(300);
        await shot("15b_pin");
        await page.keyboard.type("1234");
        await sleep(500);
        await page.keyboard.type("1234");
        await sleep(800);
        check("el PIN queda guardado y activo", await page.evaluate(() => UP.parental.hasPin() && UP.parental.isLocked()));
        check("el PIN no se guarda tal cual", await page.evaluate(() => JSON.stringify(localStorage).indexOf("1234") < 0));
        await goHome();
        const subs2 = await tileSubs();
        check("con PIN se esconden adultos (22 canales, 21 películas)", subs2[0] === "22 canales" && subs2[1] === "21 películas", subs2.join(" | "));
        await shot("16_home_locked");
        // desbloquear con PIN equivocado y luego correcto
        await key("ArrowUp");
        for (let i = 0; i < 4 && !/Ajustes/.test(await focused()); i++) await key("ArrowRight");
        await key("Enter");
        await waitScreen("settings");
        await page.evaluate(() => { const r = Array.prototype.filter.call(document.querySelectorAll(".settings .lrow"), (x) => /Control parental/.test(x.textContent))[0]; UP.nav.focus(r); });
        await key("Enter"); await sleep(200); await key("Enter");   // "Desbloquear con el PIN"
        await sleep(300);
        await page.keyboard.type("0000");
        await sleep(700);
        check("PIN equivocado no desbloquea", await page.evaluate(() => UP.parental.isLocked()));
        await page.evaluate(() => { const r = Array.prototype.filter.call(document.querySelectorAll(".settings .lrow"), (x) => /Control parental/.test(x.textContent))[0]; UP.nav.focus(r); });
        await key("Enter"); await sleep(200); await key("Enter");
        await sleep(300);
        await page.keyboard.type("1234");
        await sleep(700);
        check("PIN correcto desbloquea", await page.evaluate(() => !UP.parental.isLocked()));
        // quitar el PIN
        await page.evaluate(() => { const r = Array.prototype.filter.call(document.querySelectorAll(".settings .lrow"), (x) => /Control parental/.test(x.textContent))[0]; UP.nav.focus(r); });
        await key("Enter"); await sleep(200);
        await key("ArrowDown", 2); await key("Enter");  // "Quitar el PIN"
        await sleep(300);
        await page.keyboard.type("1234");
        await sleep(700);
        check("quitar el PIN lo borra", await page.evaluate(() => !UP.parental.hasPin()));

        // ----------------------------------------------- sesión guardada y salir
        await goHome();
        await page.reload();
        check("al reabrir entra solo (sesión guardada)", await waitScreen("home", 15000));
        await key("ArrowUp");
        for (let i = 0; i < 4 && !/Ajustes/.test(await focused()); i++) await key("ArrowRight");
        await key("Enter");
        await waitScreen("settings");
        await page.evaluate(() => { const r = Array.prototype.filter.call(document.querySelectorAll(".settings .lrow"), (x) => /Cerrar sesión/.test(x.textContent))[0]; UP.nav.focus(r); });
        await key("Enter");
        await sleep(300);
        await key("ArrowRight");                      // «Cerrar sesión» (botón principal)
        await key("Enter");
        check("cerrar sesión vuelve a la pantalla de entrar", await waitScreen("login"));
        check("cerrar sesión borra usuario y contraseña", await page.evaluate(() => !localStorage.getItem("up_session")));
    } catch (e) {
        check("la prueba no se interrumpió", false, e && e.stack ? e.stack.split("\n").slice(0, 3).join(" / ") : e);
        try { await shot("zz_error"); } catch (e2) { /* nada */ }
    }

    check("sin errores en la consola", errors.length === 0, errors.slice(0, 5).join(" || "));
    await browser.close();
    const failed = results.filter((r) => !r.ok);
    console.log("\n" + (results.length - failed.length) + " bien, " + failed.length + " mal");
    process.exit(failed.length ? 1 : 0);
})();
