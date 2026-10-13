#!/usr/bin/env python3
"""Prueba de humo: maneja la app en un Android virtual (adb + uiautomator) y deja capturas e informe.

Uso: smoke.py CARPETA_DE_SALIDA
Cada paso se registra como OK / FALLO / AVISO en report.md, y se guarda una captura y los textos visibles.
Un paso que falla no detiene los demás.
"""
import os
import re
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
os.makedirs(OUT, exist_ok=True)
PKG = "com.ultrapremier.tv"
REPORT = []
SHOT_N = [0]
W, H = 1280, 720


def log(kind, text):
    line = f"- **{kind}** {text}"
    REPORT.append(line)
    print(f"[{kind}] {text}", flush=True)
    with open(os.path.join(OUT, "report.md"), "w") as f:
        f.write("# Informe de la prueba de humo\n\n" + "\n".join(REPORT) + "\n")


def adb(*args, timeout=60, binary=False):
    try:
        r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return b"" if binary else ""
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


# ------------------------------------------------------------------ pantalla
DUMP_ERRORS = [0]


def dump():
    """Devuelve la lista de nodos visibles (texto, descripción, rectángulo)."""
    for attempt in range(3):
        sh("rm -f /sdcard/ui.xml")
        res = sh("uiautomator dump /sdcard/ui.xml 2>&1", timeout=40)
        xml = sh("cat /sdcard/ui.xml 2>/dev/null")
        if "hierarchy" not in xml:
            DUMP_ERRORS[0] += 1
            if DUMP_ERRORS[0] <= 6:
                print(f"[dump falló, intento {attempt + 1}] {res.strip()[:200]}", flush=True)
        if xml.strip().startswith("<?xml") or "<hierarchy" in xml:
            try:
                root = ET.fromstring(xml[xml.index("<"):])
            except ET.ParseError:
                time.sleep(1)
                continue
            out = []
            for n in root.iter("node"):
                m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", n.get("bounds", ""))
                if not m:
                    continue
                x1, y1, x2, y2 = map(int, m.groups())
                out.append({
                    "text": n.get("text", ""), "desc": n.get("content-desc", ""), "rid": n.get("resource-id", ""),
                    "cls": n.get("class", ""), "clickable": n.get("clickable") == "true",
                    "focused": n.get("focused") == "true", "box": (x1, y1, x2, y2),
                })
            return out
        time.sleep(1)
    return None


def texts(nodes):
    seen, out = set(), []
    for n in nodes or []:
        for t in (n["text"], n["desc"]):
            t = t.strip()
            if t and t not in seen:
                seen.add(t)
                out.append(t)
    return out


def shot(name, with_texts=True):
    SHOT_N[0] += 1
    base = f"{SHOT_N[0]:02d}_{name}"
    png = adb("exec-out", "screencap", "-p", binary=True, timeout=40)
    if png:
        with open(os.path.join(OUT, base + ".png"), "wb") as f:
            f.write(png)
    nodes = dump() if with_texts else None
    if nodes is not None:
        with open(os.path.join(OUT, base + ".txt"), "w") as f:
            f.write("\n".join(texts(nodes)) + "\n")
    return nodes


def find(nodes, sub, exact=False):
    sub_l = sub.lower()
    for n in nodes or []:
        for t in (n["text"], n["desc"]):
            tl = t.strip().lower()
            if (tl == sub_l) if exact else (sub_l in tl):
                return n
    return None


def center(n):
    x1, y1, x2, y2 = n["box"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_xy(x, y):
    sh(f"input tap {x} {y}")


def tap(sub, exact=False, wait=1.2, nodes=None):
    nodes = nodes if nodes is not None else dump()
    n = find(nodes, sub, exact)
    if not n:
        return False
    x, y = center(n)
    tap_xy(x, y)
    time.sleep(wait)
    return True


def wait_for(sub, timeout=25, exact=False):
    end = time.time() + timeout
    nodes = None
    n = 0
    while time.time() < end:
        nodes = dump()
        if find(nodes, sub, exact):
            return nodes
        if dismiss_update(nodes):
            continue
        n += 1
        if n in (2, 6):
            tag = re.sub(r"\W+", "_", sub)[:20]
            shot(f"esperando_{tag}_{n}", with_texts=False)
        time.sleep(1.5)
    sh("dumpsys window | grep -m2 'mCurrentFocus\\|mFocusedApp'")
    log("INFO", "Ventana en primer plano al fallar: " + current_focus())
    return None


def key(code, wait=0.8):
    sh(f"input keyevent {code}")
    time.sleep(wait)


def back(wait=1.0):
    key(4, wait)


def step(name):
    def deco(fn):
        def run(*a, **k):
            print(f"\n=== {name}", flush=True)
            try:
                return fn(*a, **k)
            except Exception as e:  # un paso roto no detiene la prueba
                log("FALLO", f"{name}: excepción {e!r}")
                traceback.print_exc()
                try:
                    shot("excepcion_" + re.sub(r"\W+", "_", name)[:30])
                except Exception:
                    pass
        return run
    return deco


def expect(cond, ok, bad):
    log("OK" if cond else "FALLO", ok if cond else bad)
    return cond


def current_focus():
    out = sh("dumpsys activity activities | grep -m1 mResumedActivity")
    return out.strip()


def dismiss_update(nodes):
    """El aviso de versión nueva tapa la pantalla (la prueba compila con un número de versión bajo)."""
    if nodes and find(nodes, "Hay una versión nueva") and find(nodes, "Más tarde"):
        tap("Más tarde", nodes=nodes, wait=1.0)
        return True
    return False


def go_home():
    for _ in range(6):
        nodes = dump()
        if dismiss_update(nodes):
            nodes = dump()
        if find(nodes, "TV EN DIRECTO") and find(nodes, "CONTINUAR VIENDO"):
            return nodes
        back(1.2)
    return dump()


# -------------------------------------------------------------------- pasos
@step("preparar")
def prepare():
    sh("wm size 1280x720")
    sh("wm density 160")
    sh("settings put global window_animation_scale 0")
    sh("settings put global transition_animation_scale 0")
    sh("settings put global animator_duration_scale 0")
    sh("settings put secure show_ime_with_hard_keyboard 0")
    sh("pm grant com.ultrapremier.tv android.permission.POST_NOTIFICATIONS")
    sh("pm clear com.ultrapremier.tv")
    sh("logcat -c")
    sh("am start -n com.ultrapremier.tv/com.example.mitvplayer.MainActivity")
    time.sleep(6)
    log("INFO", "tamaño de pantalla: " + sh("wm size").strip().replace("\n", " ") + " densidad " + sh("wm density").strip().replace("\n", " "))


@step("login")
def login():
    nodes = wait_for("Entrar", 30)
    shot("login")
    if not expect(nodes is not None, "Pantalla de inicio de sesión visible", "No apareció la pantalla de inicio de sesión"):
        return False
    edits = [n for n in nodes if n["cls"].endswith("EditText")]
    if len(edits) < 2:
        log("FALLO", f"Se esperaban 2 campos de texto y hay {len(edits)}")
        return False
    x, y = center(edits[0])
    tap_xy(x, y)
    time.sleep(0.6)
    sh("input text demo")
    x, y = center(edits[1])
    tap_xy(x, y)
    time.sleep(0.6)
    sh("input text demo")
    time.sleep(0.5)
    shot("login_lleno")
    key(66, 1.0)  # ENTER = Hecho
    nodes = wait_for("TV EN DIRECTO", 60)
    return expect(nodes is not None, "Inicio de sesión correcto y pantalla principal visible", "No se llegó a la pantalla principal")


@step("principal")
def home():
    time.sleep(6)  # deja cargar catálogos
    nodes = shot("principal")
    t = " | ".join(texts(nodes))
    expect("canales" in t, "La pantalla principal muestra cuántos canales hay", "No aparece el conteo de canales")
    expect("películas" in t, "Muestra cuántas películas hay", "No aparece el conteo de películas")
    expect("series" in t.lower(), "Muestra cuántas series hay", "No aparece el conteo de series")
    log("INFO", "Textos de la principal: " + t[:400])


@step("canales en vivo")
def live():
    nodes = go_home()
    if not tap("TV EN DIRECTO", nodes=nodes, wait=3):
        log("FALLO", "No se pudo abrir TV en directo")
        return
    time.sleep(3)
    nodes = shot("vivo_lista")
    t = texts(nodes)
    log("INFO", "Textos de TV en vivo: " + " | ".join(t)[:500])
    expect(find(nodes, "Noticias") is not None, "Aparece la categoría Noticias", "No aparece la categoría Noticias")
    adult = find(nodes, "XXX") or find(nodes, "Adulto")
    log("OK" if adult is None else "AVISO",
        "Las categorías de adultos no se ven (control parental)" if adult is None else "Se ve una categoría de adultos: " + (adult["text"] or adult["desc"]))
    tap("Noticias", wait=2.5)
    nodes = shot("vivo_noticias")
    for ch in ("Canal Uno FHD", "Canal Uno 4K", "Canal Uno HD"):
        n = find(nodes, ch)
        log("OK" if n else "AVISO", f"Canal «{ch}» en la lista" if n else f"No se ve el canal «{ch}»")
    # entrar a un canal sin guía propia: debe mostrar la guía del canal hermano
    if tap("Canal Uno FHD", wait=3):
        nodes = shot("vivo_canal_fhd")
        txt = " | ".join(texts(nodes))
        has_prog = re.search(r"(Noticiero|Película de la noche|Deportes en vivo|Documental|Serie estelar|Magacín) \d+", txt)
        expect(bool(has_prog), "El canal sin guía propia muestra la guía de su canal hermano", "El canal sin guía propia NO muestra programación")
    # canal sin hermano
    back()
    tap("Deportes", wait=2.5)
    nodes = shot("vivo_deportes")
    if tap("Deportes 2", wait=3):
        nodes = shot("vivo_deportes2")
        txt = " | ".join(texts(nodes))
        expect("En vivo" in txt, "El canal sin ninguna guía muestra «En vivo»", "El canal sin ninguna guía no muestra «En vivo»")
    # pantalla completa
    nodes = dump()
    if tap("Pantalla completa", nodes=nodes, wait=7):
        shot("vivo_reproduciendo", with_texts=False)
        nodes = dump()
        log("INFO", "Actividad en primer plano: " + current_focus())
        key(22, 1.0)  # flecha derecha → botón de tamaño
        shot("vivo_barra_opciones")
        key(66, 1.5)  # abrir tamaño
        nodes = shot("vivo_dialogo_tamano")
        txt = " | ".join(texts(nodes))
        expect("Normal" in txt and "Llenar" in txt and "Acercar" in txt,
               "El canal ofrece exactamente las 3 opciones de tamaño", "No se ve el cuadro de tamaño con 3 opciones: " + txt[:200])
        if tap("Llenar", nodes=nodes, wait=1.5):
            shot("vivo_tamano_llenar", with_texts=False)
        back()
        back()


@step("guía EPG")
def epg():
    nodes = go_home()
    if not tap("EPG", nodes=nodes, exact=True, wait=4):
        log("FALLO", "No se pudo abrir la guía EPG")
        return
    time.sleep(5)
    nodes = shot("epg_cuadricula")
    txt = " | ".join(texts(nodes))
    log("INFO", "Textos de la guía: " + txt[:600])
    expect("En vivo" in txt or find(nodes, "Noticiero") is not None, "La guía muestra programas o «En vivo»", "La guía está vacía")
    n_envivo = txt.count("En vivo")
    log("INFO", f"Bloques «En vivo» visibles: {n_envivo}")
    key(20, 0.5)
    key(20, 0.5)
    shot("epg_abajo")
    back()


@step("películas")
def movies():
    nodes = go_home()
    if not tap("CINE", nodes=nodes, wait=4):
        log("FALLO", "No se pudo abrir Cine")
        return
    time.sleep(3)
    nodes = shot("cine_lista")
    log("INFO", "Textos de Cine: " + " | ".join(texts(nodes))[:500])
    search = find(nodes, "Buscar")
    if search:
        x1, y1, x2, y2 = search["box"]
        log("OK" if x1 > W * 0.8 and y2 < H * 0.2 else "AVISO", f"Lupa en la lista de películas en {search['box']}")
    if not (tap("La Película de Prueba", nodes=nodes, wait=4) or tap("Película", nodes=nodes, wait=4)):
        log("FALLO", "No se encontró la película de prueba en la lista")
        return
    nodes = shot("cine_detalle")
    log("INFO", "Textos del detalle: " + " | ".join(texts(nodes))[:600])
    search = find(nodes, "Buscar")
    if search:
        x1, y1, x2, y2 = search["box"]
        ok = x1 > W * 0.85 and y2 < H * 0.2
        log("OK" if ok else "FALLO", f"Lupa de búsqueda del detalle de película: {search['box']} ({'esquina superior derecha' if ok else 'mal ubicada'})")
        tap_xy(*center(search))
        time.sleep(2.5)
        shot("cine_busqueda")
        back()
    else:
        log("FALLO", "El detalle de la película no tiene la lupa de búsqueda")
    nodes = dump()
    if tap("Reproducir", nodes=nodes, wait=8) or tap("Desde el inicio", nodes=nodes, wait=8):
        shot("cine_reproduciendo", with_texts=False)
        log("INFO", "Actividad en primer plano: " + current_focus())
        player_checks("pelicula")
    else:
        log("FALLO", "No se encontró el botón Reproducir")


def player_checks(tag):
    """Dentro del reproductor de una película o episodio: opciones, subtítulos y tamaño."""
    key(82, 1.5)  # MENU
    nodes = shot(f"{tag}_menu_opciones")
    txt = " | ".join(texts(nodes))
    if not expect("Subtítulos" in txt and "Tamaño" in txt, "El menú de opciones del reproductor ofrece Subtítulos y Tamaño",
                  "No salió el menú de opciones: " + txt[:200]):
        return
    tap("Subtítulos", exact=True, nodes=nodes, wait=1.5)
    nodes = shot(f"{tag}_dialogo_subtitulos")
    txt = " | ".join(texts(nodes))
    log("INFO", "Opciones de subtítulos: " + txt[:400])
    expect("Desactivados" in txt, "Hay opción para desactivar subtítulos", "Falta «Desactivados»")
    expect("Inglés" in txt, "Se ofrece conseguir subtítulos en inglés", "No se ofrece inglés")
    # activar el subtítulo incluido en el video
    chosen = False
    for cand in ("spa", "Español", "Spanish", "es"):
        n = find(nodes, cand)
        if n and "internet" not in (n["text"] + n["desc"]).lower():
            tap_xy(*center(n))
            chosen = True
            break
    time.sleep(3)
    shot(f"{tag}_subtitulo_incluido", with_texts=False)
    log("INFO", f"Subtítulo incluido elegido: {chosen}")
    # buscar inglés en internet
    key(82, 1.2)
    nodes = dump()
    tap("Subtítulos", exact=True, nodes=nodes, wait=1.5)
    nodes = dump()
    if tap("Buscar en internet: Inglés", nodes=nodes, wait=2):
        shot(f"{tag}_sin_clave")
        nodes = dump()
        txt = " | ".join(texts(nodes))
        log("INFO", "Respuesta al buscar sin clave: " + txt[:300])
        if find(nodes, "clave"):
            tap("Entendido", nodes=nodes) or tap("OK", nodes=nodes) or back()
    # tamaño
    key(82, 1.2)
    nodes = dump()
    tap("Tamaño de la imagen", exact=True, nodes=nodes, wait=1.5)
    nodes = shot(f"{tag}_dialogo_tamano")
    txt = " | ".join(texts(nodes))
    expect("Normal" in txt and "Llenar" in txt and "Acercar" in txt, "La película ofrece las 3 opciones de tamaño", "Faltan opciones de tamaño: " + txt[:200])
    tap("Acercar", nodes=nodes, wait=1.5)
    shot(f"{tag}_tamano_acercar", with_texts=False)
    back(1.5)


@step("clave de subtítulos en ajustes")
def settings_key():
    nodes = go_home()
    if not tap("Ajustes", nodes=nodes, wait=3):
        log("FALLO", "No se pudo abrir Ajustes")
        return
    nodes = shot("ajustes")
    log("INFO", "Textos de Ajustes: " + " | ".join(texts(nodes))[:500])
    if not tap("Subtítulos en internet", nodes=nodes, wait=2):
        log("FALLO", "No hay botón de subtítulos en internet en Ajustes")
        return
    nodes = shot("ajustes_clave")
    edits = [n for n in nodes if n["cls"].endswith("EditText")]
    if not edits:
        log("FALLO", "El cuadro de la clave no tiene campos de texto")
        return
    tap_xy(*center(edits[0]))
    time.sleep(0.5)
    sh("input text TESTKEY")
    back(0.8)  # cierra el teclado si estaba
    nodes = dump()
    if not find(nodes, "Guardar"):
        nodes = dump()
    tap("Guardar", nodes=nodes, wait=2.5)
    nodes = shot("ajustes_clave_guardada")
    log("INFO", "Después de guardar: " + " | ".join(texts(nodes))[:300])
    # idioma por defecto → inglés para probar la búsqueda automática
    back()


@step("subtítulos desde internet")
def online_subs():
    nodes = go_home()
    if not tap("CINE", nodes=nodes, wait=4):
        return
    time.sleep(2)
    nodes = dump()
    if not (tap("La Película de Prueba", nodes=nodes, wait=4) or tap("Película", nodes=nodes, wait=4)):
        return
    nodes = dump()
    if not (tap("Reproducir", nodes=nodes, wait=8) or tap("Desde el inicio", nodes=nodes, wait=8) or tap("Continuar", nodes=nodes, wait=8)):
        log("FALLO", "No se pudo reproducir la película la segunda vez")
        return
    key(82, 1.5)
    nodes = dump()
    tap("Subtítulos", exact=True, nodes=nodes, wait=1.5)
    nodes = dump()
    if tap("Buscar en internet: Inglés", nodes=nodes, wait=6):
        shot("pelicula_ingles_buscando", with_texts=False)
        time.sleep(4)
        key(82, 1.2)
        nodes = dump()
        tap("Subtítulos", exact=True, nodes=nodes, wait=1.5)
        nodes = shot("pelicula_dialogo_tras_ingles")
        txt = " | ".join(texts(nodes))
        expect("Inglés" in txt and "internet" in txt.lower() and "Buscar en internet: Inglés" not in txt,
               "Tras buscar, el subtítulo en inglés quedó disponible en la lista", "El inglés no quedó en la lista: " + txt[:300])
        # elegirlo
        n = find(nodes, "Inglés (internet)")
        if n:
            tap_xy(*center(n))
            time.sleep(3)
            shot("pelicula_subtitulo_ingles", with_texts=False)
        else:
            back()
    else:
        log("FALLO", "No salió la opción «Buscar en internet: Inglés»")


@step("series")
def series():
    nodes = go_home()
    if not tap("SERIES", nodes=nodes, exact=True, wait=4):
        log("FALLO", "No se pudo abrir Series")
        return
    time.sleep(3)
    nodes = shot("series_lista")
    if not tap("La Serie de Prueba", nodes=nodes, wait=4):
        log("FALLO", "No se encontró la serie de prueba")
        return
    nodes = shot("series_detalle")
    log("INFO", "Textos del detalle de serie: " + " | ".join(texts(nodes))[:600])
    search = find(nodes, "Buscar")
    if search:
        x1, y1, x2, y2 = search["box"]
        ok = x1 > W * 0.85 and y2 < H * 0.2
        log("OK" if ok else "FALLO", f"Lupa de búsqueda del detalle de serie: {search['box']}")
    else:
        log("FALLO", "El detalle de la serie no tiene lupa")
    if tap("Episodio 1", nodes=nodes, wait=8):
        shot("series_reproduciendo", with_texts=False)
        log("INFO", "Actividad en primer plano: " + current_focus())
        player_checks("episodio")
    else:
        log("FALLO", "No se encontró el episodio 1")


@step("registro final")
def final_logs():
    for _ in range(4):
        back(0.8)
    nodes = shot("final")
    log_text = adb("logcat", "-d", "-v", "time", timeout=120)
    with open(os.path.join(OUT, "logcat.txt"), "w") as f:
        f.write(log_text)
    bad = [l for l in log_text.splitlines()
           if ("FATAL EXCEPTION" in l) or ("AndroidRuntime" in l and "E/" in l) or ("ANR in" in l)]
    own = [l for l in log_text.splitlines() if PKG in l and (" E/" in l or " W/System.err" in l)]
    with open(os.path.join(OUT, "errores.txt"), "w") as f:
        f.write("\n".join(bad + ["---- errores de la app ----"] + own[:200]))
    expect(not bad, "No hubo cierres inesperados (FATAL EXCEPTION) en el registro", f"Hubo {len(bad)} líneas de cierre/ANR; ver errores.txt")


def main():
    prepare()
    if login():
        home()
        live()
        epg()
        movies()
        settings_key()
        online_subs()
        series()
    final_logs()
    # resumen
    fails = [l for l in REPORT if l.startswith("- **FALLO**")]
    log("RESUMEN", f"{len(fails)} fallos")


if __name__ == "__main__":
    main()
