#!/usr/bin/env python3
"""Prueba QA en el emulador: entra a la app con un catálogo enorme y mide qué tan rápida es al cambiar de sección.

Uso: qa.py CARPETA_DE_SALIDA   (lee la configuración de la app en tools/qa/app.json)

Se guía por las marcas que deja la propia app en el registro (etiqueta QA, solo en la compilación de prueba):
  ABRIR / NAV  -> se pidió abrir una pantalla y cuándo se dibujó
  LISTO        -> la sección ya tiene su contenido en pantalla
  LAG          -> el hilo principal se quedó trabado N ms (y en qué código)
  CATALOGO     -> cuánto tardó en cargar cada lista
Además revisa cierres (FATAL EXCEPTION), "no responde" (ANR), memoria y deja capturas de cada pantalla.
Para moverse usa el control remoto (teclas) y, para abrir secciones, la orden de prueba "qa_open".
"""
import json
import os
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else "qa-out"
os.makedirs(OUT, exist_ok=True)
APP = json.load(open(os.path.join(HERE, "app.json"), encoding="utf-8"))
PKG = APP["pkg"]
ACT = APP["activity"]
W, H = 1920, 1080

REPORT = []
SHOTS = [0]
RESULTS = {"navs": [], "moves": [], "crashes": 0, "anrs": 0, "fails": 0}

UP, DOWN, LEFT, RIGHT, OK, BACK, ENTER = 19, 20, 21, 22, 23, 4, 66


# ----------------------------------------------------------------------------- utilidades

def log(kind, text):
    REPORT.append(f"- **{kind}** {text}")
    print(f"[{kind}] {text}", flush=True)
    if kind == "FALLO":
        RESULTS["fails"] += 1
    with open(os.path.join(OUT, "report.md"), "w", encoding="utf-8") as f:
        f.write(f"# Prueba QA de {APP['name']}\n\n" + "\n".join(REPORT) + "\n")


def adb(*args, timeout=60, binary=False):
    try:
        r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return b"" if binary else ""
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def now_dev():
    """Milisegundos desde el arranque del emulador (el mismo reloj de las marcas de la app)."""
    try:
        return int(float(sh("cat /proc/uptime").split()[0]) * 1000)
    except (ValueError, IndexError):
        return 0


def dump(quiet=False):
    """Textos y posiciones de lo que hay en pantalla (puede fallar si la app nunca está quieta)."""
    for _ in range(2):
        sh("rm -f /sdcard/ui.xml")
        res = sh("uiautomator dump /sdcard/ui.xml 2>&1", timeout=40)
        xml = sh("cat /sdcard/ui.xml 2>/dev/null")
        if "<hierarchy" in xml:
            try:
                root = ET.fromstring(xml[xml.index("<"):])
            except ET.ParseError:
                continue
            out = []
            for n in root.iter("node"):
                m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", n.get("bounds", ""))
                if m:
                    out.append({"text": n.get("text", ""), "desc": n.get("content-desc", ""), "cls": n.get("class", ""),
                                "box": tuple(map(int, m.groups()))})
            return out
        if not quiet:
            print("[dump falló] " + res.strip()[:160], flush=True)
    return []


def texts(nodes):
    seen, out = set(), []
    for n in nodes:
        for t in (n["text"], n["desc"]):
            t = t.strip()
            if t and t not in seen:
                seen.add(t)
                out.append(t)
    return out


def find(nodes, labels, exact=False):
    labels = [labels] if isinstance(labels, str) else labels
    for label in labels:
        low = label.lower()
        for n in nodes:
            for t in (n["text"], n["desc"]):
                tl = t.strip().lower()
                if (tl == low) if exact else (low in tl):
                    return n
    return None


def tap_node(n, wait=0.8):
    x1, y1, x2, y2 = n["box"]
    sh(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    time.sleep(wait)


def keys(*codes, wait=0.5):
    sh("input keyevent " + " ".join(str(c) for c in codes))
    time.sleep(wait)


def shot(name):
    SHOTS[0] += 1
    png = adb("exec-out", "screencap", "-p", binary=True, timeout=40)
    if png:
        with open(os.path.join(OUT, f"{SHOTS[0]:02d}_{name}.png"), "wb") as f:
            f.write(png)


def qa_lines(since=0):
    out = adb("logcat", "-d", "-v", "brief", "-s", "QA:I", timeout=60)
    res = []
    for line in out.splitlines():
        m = re.search(r"t=(\d+) (.*)$", line)
        if m and int(m.group(1)) >= since:
            res.append((int(m.group(1)), m.group(2).strip()))
    return res


def wait_qa(pattern, since, timeout=90):
    end = time.time() + timeout
    rx = re.compile(pattern)
    while time.time() < end:
        for t, msg in qa_lines(since):
            if rx.search(msg):
                return t, msg
        time.sleep(0.4)
    return None, None


def lags_between(t0, t1):
    res = []
    for t, msg in qa_lines(t0):
        if t > t1:
            break
        m = re.match(r"LAG (\d+) ms :: (.*)", msg)
        if m:
            res.append((t, int(m.group(1)), m.group(2)))
    return res


def top_activity():
    return sh("dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'").strip()


def app_alive():
    return sh(f"pidof {PKG}").strip() != ""


def start_app():
    sh(f"am start -n {PKG}/{ACT}")


def qa_open(screen):
    t0 = now_dev()
    sh(f"am start -n {PKG}/{ACT} --activity-single-top --es qa_open {screen}")
    return t0


CRASH_SEEN = [0]


def check_crash(step_name):
    out = adb("logcat", "-d", "-b", "crash", "-v", "time", timeout=60)
    n = out.count("FATAL EXCEPTION")
    if n > CRASH_SEEN[0]:
        lines = out.splitlines()
        idx = [i for i, l in enumerate(lines) if "FATAL EXCEPTION" in l]
        start = idx[CRASH_SEEN[0]]
        log("FALLO", f"La app se CERRÓ durante «{step_name}»: " +
            " / ".join(re.sub(r"^\S+ \S+ ", "", l).strip()[:220] for l in lines[start:start + 12]))
        with open(os.path.join(OUT, f"cierre_{n}.txt"), "w") as f:
            f.write("\n".join(lines[start:start + 80]))
        CRASH_SEEN[0] = n
        RESULTS["crashes"] += 1
    main = adb("logcat", "-d", "-b", "main,system,events", "-v", "time", timeout=60)
    anrs = len(re.findall(r"ANR in " + re.escape(PKG), main))
    if anrs > RESULTS["anrs"]:
        log("FALLO", f"Android avisó que la app NO RESPONDE (ANR) durante «{step_name}»")
        RESULTS["anrs"] = anrs


def step(name):
    def deco(fn):
        def run(*a, **k):
            print(f"\n=== {name}", flush=True)
            try:
                return fn(*a, **k)
            except Exception as e:
                log("FALLO", f"{name}: error en la prueba {e!r}")
                traceback.print_exc()
            finally:
                try:
                    check_crash(name)
                    if not app_alive() or PKG not in top_activity():
                        log("AVISO", f"Después de «{name}» la app no estaba en pantalla ({top_activity()[-80:]}); se vuelve a abrir")
                        start_app()
                        time.sleep(6)
                except Exception:
                    pass
        return run
    return deco


def report_lags(where, lags, limit=3):
    for t, d, stack in sorted(lags, key=lambda l: -l[1])[:limit]:
        log("TRABON", f"{d} ms en {where} :: {stack[:420]}")


def open_section(key, shot_name=None):
    """Abre la sección y espera a que tenga contenido. Devuelve True si se llenó."""
    screen = APP["screens"][key]
    t0 = qa_open(screen)
    t_open, _ = wait_qa(r"^ABRIR " + screen, t0, timeout=20)
    t_ready, msg = wait_qa(r"^LISTO ", t_open or t0, timeout=150)
    internal = None
    m = re.search(r"a (\d+) ms de abrir", msg or "")
    if m:
        internal = int(m.group(1))
    lags = lags_between(t_open or t0, (t_ready or now_dev()) + 1200)
    worst = max((l[1] for l in lags), default=0)
    RESULTS["navs"].append({"seccion": key, "ms": internal, "peor_trabon_ms": worst})
    if t_ready is None:
        log("FALLO", f"Abrir {key}: nunca se llenó la pantalla (2.5 min)")
    else:
        slow = (internal or 0) > APP.get("max_nav_ms", 1200) or worst > 600
        log("LENTO" if slow else "OK", f"Abrir {key}: lista en pantalla a {internal} ms; peor trabón {worst} ms ({msg})")
    report_lags(f"abrir {key}", lags)
    time.sleep(1.2)
    if shot_name:
        shot(shot_name)
    return t_ready is not None


def move_inside(key, take_shots=False):
    """Con el control: cambia de categoría varias veces y luego baja rápido por la lista o cuadrícula."""
    t0 = now_dev()
    # El foco empieza en la lista de categorías: bajar y elegir varias.
    for _ in range(6):
        keys(DOWN, DOWN, OK, wait=0.35)
    if take_shots:
        shot(f"{key}_otra_categoria")
    t1 = now_dev()
    lag_cat = lags_between(t0, t1 + 400)
    # Al contenido y bajar rápido.
    keys(RIGHT, wait=0.4)
    keys(*([DOWN] * 30), wait=0.2)
    keys(*([RIGHT] * 3), wait=0.2)
    keys(*([DOWN] * 30), wait=0.8)
    if take_shots:
        shot(f"{key}_bajando")
    t2 = now_dev()
    lag_scroll = lags_between(t1, t2 + 400)
    for label, lags in (("cambiar categorías", lag_cat), ("bajar por la lista", lag_scroll)):
        worst = max((l[1] for l in lags), default=0)
        total = sum(l[1] for l in lags)
        RESULTS["moves"].append({"seccion": key, "accion": label, "trabones": len(lags), "peor_ms": worst, "total_ms": total})
        log("OK" if worst <= 400 and total <= 1500 else "LENTO",
            f"{key} · {label}: {len(lags)} trabones, peor {worst} ms, total {total} ms")
        report_lags(f"{key} · {label}", lags, limit=2)


# ---------------------------------------------------------------------------------- pasos

@step("preparar")
def prepare():
    sh(f"wm size {W}x{H}")
    sh("wm density 320")
    sh("settings put secure show_ime_with_hard_keyboard 0")
    sh(f"pm clear {PKG}")
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    adb("logcat", "-b", "all", "-c")
    sh("logcat -G 16M")
    t0 = now_dev()
    start_app()
    t, _ = wait_qa(r"^NAV Login", t0, timeout=40)
    time.sleep(2.5)
    shot("login")
    log("INFO", f"Pantalla de inicio de sesión dibujada a {t - t0 if t else '?'} ms de abrir la app")


@step("iniciar sesión")
def login():
    nodes = []
    for _ in range(12):
        nodes = dump()
        if len([n for n in nodes if n["cls"].endswith("EditText")]) >= 2:
            break
        time.sleep(1.5)
    edits = [n for n in nodes if n["cls"].endswith("EditText")]
    if len(edits) < 2:
        log("FALLO", f"No encontré los campos de usuario y contraseña (textos: {texts(nodes)[:12]})")
        return False
    tap_node(edits[0], 0.6)
    sh("input text demo")
    tap_node(edits[1], 0.6)
    sh("input text demo")
    time.sleep(0.5)
    shot("login_lleno")
    t0 = now_dev()
    keys(ENTER, wait=0.6)
    nodes = dump(quiet=True)
    button = find(nodes, APP.get("login_button", ["Entrar"]), exact=True)
    if button and len([n for n in nodes if n["cls"].endswith("EditText")]) >= 2:
        tap_node(button, 0.3)
    t, _ = wait_qa(r"^NAV Home", t0, timeout=120)
    if not t:
        shot("login_fallo")
        log("FALLO", "Después de poner usuario y contraseña no apareció el inicio; en pantalla: " +
            " | ".join(texts(dump())[:15]))
        return False
    log("OK", f"Entró a la app: el inicio se dibujó a {t - t0} ms de pulsar Entrar")
    lags = lags_between(t0, t + 3000)
    report_lags("entrar", lags)
    time.sleep(2)
    shot("inicio_recien_entrado")
    return True


@step("entrar a Películas mientras cargan las listas")
def early_movies():
    open_section("movies", "peliculas_primera_vez")
    keys(BACK, wait=1.0)


@step("esperar a que carguen las tres listas")
def wait_catalogs():
    t0 = time.time()
    got = {}
    while time.time() - t0 < 240 and len(got) < 3:
        for t, msg in qa_lines(0):
            m = re.match(r"CATALOGO (LIVE|MOVIES|SERIES) (\d+) elementos, (\d+) categorías en (\d+) ms", msg)
            if m and m.group(1) not in got:
                got[m.group(1)] = msg
                log("INFO", f"Lista {m.group(1)}: {m.group(2)} elementos y {m.group(3)} categorías, lista en {m.group(4)} ms")
        time.sleep(2)
    if len(got) < 3:
        log("AVISO", f"Solo cargaron {sorted(got)} en 4 minutos")
    for line in adb("logcat", "-d", "-s", "UPTV:D", timeout=30).splitlines():
        if "lista " in line:
            log("INFO", "Medición interna: " + line[line.find("lista "):][:220])
    time.sleep(3)
    shot("inicio_con_listas")


@step("cambiar de sección varias veces")
def sections():
    for r in range(3):
        for key in ("live", "movies", "series"):
            if open_section(key, key if r == 0 else None):
                if r < 2:
                    move_inside(key, take_shots=(r == 0))
            keys(BACK, wait=1.0)


@step("ficha de película y reproducir")
def movie_detail():
    if not open_section("movies"):
        return
    keys(RIGHT, wait=0.6)
    t0 = now_dev()
    keys(OK, wait=0.2)
    t, _ = wait_qa(r"^NAV MovieDetails", t0, timeout=30)
    time.sleep(3)
    shot("ficha_pelicula")
    lags = lags_between(t0, now_dev())
    log("OK" if t else "FALLO", f"Ficha de película {'abrió en ' + str(t - t0) + ' ms' if t else 'no abrió'}")
    report_lags("ficha de película", lags, 2)
    keys(OK, wait=9)
    shot("pelicula_reproduciendo")
    top = top_activity()
    log("OK" if "Player" in top else "AVISO", "Al pulsar OK en la ficha: " + top[-70:])
    keys(BACK, wait=1.5)
    keys(BACK, wait=1.0)
    keys(BACK, wait=1.0)


@step("ficha de serie y episodios")
def series_detail():
    if not open_section("series"):
        return
    keys(RIGHT, wait=0.6)
    t0 = now_dev()
    keys(OK, wait=0.2)
    t, _ = wait_qa(r"^NAV SeriesDetails", t0, timeout=30)
    time.sleep(3.5)
    shot("ficha_serie")
    keys(DOWN, DOWN, wait=1.2)
    shot("ficha_serie_abajo")
    lags = lags_between(t0, now_dev())
    log("OK" if t else "FALLO", f"Ficha de serie {'abrió en ' + str(t - t0) + ' ms' if t else 'no abrió'}")
    report_lags("ficha de serie", lags, 2)
    keys(BACK, wait=1)
    keys(BACK, wait=1)


@step("canal en vivo")
def live_channel():
    if not open_section("live"):
        return
    keys(RIGHT, wait=0.5)
    keys(DOWN, DOWN, wait=0.4)
    keys(OK, wait=7)
    shot("vivo_vista_previa")
    keys(OK, wait=7)
    shot("vivo_pantalla_completa")
    log("INFO", "Al pulsar OK dos veces en un canal: " + top_activity()[-70:])
    keys(BACK, wait=1.5)
    keys(BACK, wait=1.0)
    keys(BACK, wait=1.0)


@step("buscar")
def search():
    t0 = qa_open("Search")
    time.sleep(2.5)
    nodes = dump(quiet=True)
    edits = [n for n in nodes if n["cls"].endswith("EditText")]
    if edits:
        tap_node(edits[0], 0.5)
    t1 = now_dev()
    sh("input text fuego")
    time.sleep(4)
    shot("busqueda")
    lags = lags_between(t1, now_dev())
    worst = max((l[1] for l in lags), default=0)
    log("OK" if worst < 500 else "LENTO", f"Buscar «fuego»: peor trabón {worst} ms; en pantalla: {' | '.join(texts(dump(quiet=True))[:8])[:240]}")
    report_lags("buscar", lags, 2)
    keys(BACK, wait=0.8)
    keys(BACK, wait=0.8)


@step("ajustes")
def settings():
    qa_open("Settings")
    time.sleep(2.5)
    shot("ajustes")
    keys(BACK, wait=1)


@step("volver a abrir la app con la sesión guardada")
def reopen():
    sh(f"am force-stop {PKG}")
    time.sleep(2)
    t0 = now_dev()
    start_app()
    t, _ = wait_qa(r"^NAV Home", t0, timeout=60)
    log("OK" if t else "FALLO", f"Con la sesión guardada, el inicio se dibujó a {t - t0 if t else '?'} ms de abrir la app")
    report_lags("abrir la app", lags_between(t0, (t or now_dev()) + 3000), 2)
    time.sleep(1.0)
    shot("inicio_al_reabrir")
    open_section("movies", "peliculas_al_reabrir")
    keys(BACK, wait=1)
    open_section("live")
    keys(BACK, wait=1)


@step("memoria y cuadros")
def memory():
    mem = sh(f"dumpsys meminfo {PKG}")
    with open(os.path.join(OUT, "meminfo.txt"), "w") as f:
        f.write(mem)
    java = re.search(r"Java Heap:\s+(\d+)", mem)
    total = re.search(r"TOTAL(?: PSS)?:\s+(\d+)", mem)
    log("INFO", f"Memoria: Java {int(java.group(1)) // 1024 if java else '?'} MB, total {int(total.group(1)) // 1024 if total else '?'} MB")
    gfx = sh(f"dumpsys gfxinfo {PKG}")
    with open(os.path.join(OUT, "gfxinfo.txt"), "w") as f:
        f.write(gfx)
    jank = re.search(r"Janky frames: (\d+) \(([\d.]+)%\)", gfx)
    if jank:
        log("INFO", f"Cuadros lentos según Android: {jank.group(1)} ({jank.group(2)}%) — en el emulador sin GPU real esto es normal")


def final():
    lines = qa_lines(0)
    with open(os.path.join(OUT, "qa-marcas.txt"), "w") as f:
        f.write("\n".join(f"{t} {m}" for t, m in lines))
    allog = adb("logcat", "-d", "-v", "time", timeout=120)
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(allog)
    lags = [(t, int(m.group(1)), m.group(2)) for t, msg in lines for m in [re.match(r"LAG (\d+) ms :: (.*)", msg)] if m]
    own = [l for l in lags if "HardwareRenderer" not in l[2].split("||")[0]]
    log("INFO", f"Trabones del hilo principal en toda la prueba: {len(lags)} (de ellos {len(own)} por código de la app), "
                f"total {sum(l[1] for l in lags)} ms")
    report_lags("la prueba", own, 8)
    log("RESUMEN", f"{RESULTS['fails']} fallos, {RESULTS['crashes']} cierres, {RESULTS['anrs']} ANR")
    with open(os.path.join(OUT, "resultados.json"), "w") as f:
        json.dump(RESULTS, f, ensure_ascii=False, indent=1)
    print("\n----- marcas de la app (últimas 160) -----")
    for t, m in lines[-160:]:
        print(f"{t} {m[:300]}")


def main():
    prepare()
    if login():
        early_movies()
        wait_catalogs()
        sections()
        movie_detail()
        series_detail()
        live_channel()
        search()
        settings()
        reopen()
        memory()
    final()


if __name__ == "__main__":
    main()
