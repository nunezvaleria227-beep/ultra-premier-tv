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

OUT = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else "out"
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


CRASHES = [0]


def crash_check(name):
    """Si la app se cerró durante el paso, lo anota con el inicio del error."""
    out = adb("logcat", "-d", "-b", "crash", "-v", "time", timeout=60)
    n = out.count("FATAL EXCEPTION")
    if n > CRASHES[0]:
        lines = out.splitlines()
        idx = [i for i, l in enumerate(lines) if "FATAL EXCEPTION" in l]
        start = idx[CRASHES[0]]
        trace = [re.sub(r"^\S+ \S+ ", "", l)[:230] for l in lines[start:start + 14]]
        log("FALLO", f"La app se CERRÓ durante «{name}»: " + " / ".join(trace[:8]))
        with open(os.path.join(OUT, f"cierre_{len(idx)}.txt"), "w") as f:
            f.write("\n".join(lines[start:start + 60]))
        CRASHES[0] = n
        return True
    return False


def step(name):
    def deco(fn):
        def run(*a, **k):
            print(f"\n=== {name}", flush=True)
            result = None
            try:
                result = fn(*a, **k)
            except Exception as e:  # un paso roto no detiene la prueba
                log("FALLO", f"{name}: excepción {e!r}")
                traceback.print_exc()
                try:
                    shot("excepcion_" + re.sub(r"\W+", "_", name)[:30])
                except Exception:
                    pass
            try:
                crash_check(name)
            except Exception:
                pass
            return result
        return run
    return deco


def expect(cond, ok, bad):
    log("OK" if cond else "FALLO", ok if cond else bad)
    return cond


def current_focus():
    out = sh("dumpsys activity activities | grep -m1 mResumedActivity")
    return out.strip()


LOGIN_T0 = [0.0]


def dismiss_update(nodes):
    """El aviso de versión nueva tapa la pantalla (la prueba compila con un número de versión bajo)."""
    if nodes and find(nodes, "Hay una versión nueva") and find(nodes, "Más tarde"):
        tap("Más tarde", nodes=nodes, wait=1.0)
        return True
    return False


def ensure_app():
    """Si la app se cerró (o se salió al escritorio), se vuelve a abrir; la sesión guardada entra sola."""
    if PKG in current_focus():
        return
    log("INFO", "La app no estaba en primer plano; se vuelve a abrir")
    sh("am start -n com.ultrapremier.tv/com.example.mitvplayer.MainActivity")
    time.sleep(8)


def go_home():
    ensure_app()
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
    LOGIN_T0[0] = time.time()
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
    nodes = dump()
    tap("DEPORTES", nodes=nodes, wait=2.5)
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
        # En modo táctil Android se "come" la primera flecha para salir de ese modo (con control remoto no pasa):
        # primero un OK (muestra la información), luego la flecha.
        key(66, 1.0)
        key(22, 1.5)  # flecha derecha → marca «Tamaño»
        shot("vivo_barra_opciones", with_texts=False)  # sin leer textos: la barra se esconde a los 5 s
        key(66, 1.5)  # OK abre el cuadro de tamaño
        nodes = shot("vivo_dialogo_tamano")
        txt = " | ".join(texts(nodes))
        expect("Normal" in txt and "Llenar" in txt and "Acercar" in txt,
               "El canal ofrece exactamente las 3 opciones de tamaño", "No se ve el cuadro de tamaño con 3 opciones: " + txt[:200])
        if tap("Llenar", nodes=nodes, wait=1.5):
            shot("vivo_tamano_llenar", with_texts=False)
        back(1.5)
        shot("vivo_tras_tamano", with_texts=False)


@step("guía EPG")
def epg():
    nodes = go_home()
    if not tap("EPG", nodes=nodes, exact=True, wait=4):
        log("FALLO", "No se pudo abrir la guía EPG")
        return
    time.sleep(2)
    nodes = dump()
    if not tap("TODO", nodes=nodes, exact=True, wait=6):
        tap("Noticias", nodes=nodes, wait=6)
    nodes = shot("epg_cuadricula")
    txt = " | ".join(texts(nodes))
    log("INFO", "Textos de la guía: " + txt[:900])
    prog = re.search(r"(Noticiero|Película de la noche|Deportes en vivo|Documental|Serie estelar|Magacín) \d+", txt)
    expect(bool(prog), "La guía muestra programas", "La guía no muestra programas")
    n_envivo = txt.count("En vivo")
    log("INFO", f"Bloques «En vivo» visibles: {n_envivo}")
    nodes = dump()
    if tap("Ver en cuadrícula", nodes=nodes, wait=6):
        nodes = shot("epg_grilla")
        txt = " | ".join(texts(nodes))
        log("INFO", "Guía en cuadrícula: " + txt[:600])
        expect("En vivo" in txt, "La cuadrícula muestra «En vivo» en los canales sin guía", "La cuadrícula no muestra «En vivo»")
        # abrir un programa futuro y programar su grabación
        done = False
        for name in ("Deportes en vivo 4", "Noticiero 4", "Película de la noche 3", "Magacín 3"):
            n = find(nodes, name)
            if n:
                tap_xy(*center(n))
                time.sleep(1.5)
                nodes = shot("epg_grilla_dialogo")
                txt = " | ".join(texts(nodes))
                log("INFO", f"Opciones al pulsar «{name}»: " + txt[:300])
                if tap("Programar grabación", nodes=nodes, wait=2):
                    nodes = shot("epg_grilla_programada")
                    done = True
                else:
                    tap("Cerrar", nodes=nodes, wait=1)
                break
        log("OK" if done else "AVISO", "Se programó una grabación desde la guía" if done else "No se pudo programar una grabación desde la guía (solo había programas en curso)")
        back()
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
    if find(nodes, "Inglés (internet)"):
        log("INFO", "El inglés ya estaba descargado (se buscó solo al empezar, por el idioma recordado)")
        back(1.0)
    elif tap("Buscar en internet: Inglés", nodes=nodes, wait=2):
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
    time.sleep(0.8)
    nodes = dump()
    shot("ajustes_clave_escrita", with_texts=False)
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
    shot("pelicula_subtitulo_automatico", with_texts=False)
    log("INFO", "Captura pelicula_subtitulo_automatico: debe verse «SUBTITULO INCLUIDO EN EL VIDEO» sin tocar nada (español recordado)")
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


def pin_enter(digits):
    for d in digits:
        nodes = dump()
        n = find(nodes, d, exact=True)
        if not n:
            log("FALLO", f"No se ve la tecla {d} del teclado de PIN")
            return False
        tap_xy(*center(n))
        time.sleep(0.6)
    time.sleep(1.0)
    return True


@step("control parental")
def parental():
    nodes = go_home()
    if not tap("Ajustes", nodes=nodes, wait=3):
        log("FALLO", "No se pudo abrir Ajustes (parental)")
        return
    nodes = dump()
    if not tap("Control parental: poner un PIN", nodes=nodes, wait=1.5):
        log("FALLO", "No hay botón de control parental")
        return
    nodes = shot("pin_elegir")
    if not pin_enter("1234"):
        return
    nodes = shot("pin_siguiente")
    if find(nodes, "✕"):  # sigue el cuadro: pide repetir el PIN
        pin_enter("1234")
        nodes = shot("pin_repetido")
    nodes = shot("parental_activado")
    txt = " | ".join(texts(nodes))
    expect("Control parental activado" in txt or "escondido" in txt, "El control parental quedó activado", "No se ve el control parental activado: " + txt[:300])
    back(1.5)
    nodes = go_home()
    tap("TV EN DIRECTO", nodes=nodes, wait=3)
    time.sleep(2)
    nodes = shot("parental_vivo")
    txt = " | ".join(texts(nodes))
    expect("XXX" not in txt, "Con el control parental, la categoría XXX no aparece en TV", "La categoría XXX sigue visible con el control parental")
    expect("Canal Adulto" not in txt, "El canal para adultos no aparece", "El canal para adultos sigue en la lista")
    back(1.5)
    nodes = go_home()
    tap("CINE", nodes=nodes, wait=3)
    time.sleep(2)
    nodes = shot("parental_cine")
    txt = " | ".join(texts(nodes))
    expect("+18" not in txt and "Para Adultos" not in txt, "En Cine no aparecen las películas ni la categoría +18", "En Cine sigue habiendo contenido para adultos: " + txt[:300])
    back(1.5)
    # ver el contenido con el PIN
    nodes = go_home()
    tap("Ajustes", nodes=nodes, wait=3)
    nodes = dump()
    if tap("Ver contenido para adultos", nodes=nodes, wait=1.5):
        nodes = shot("pin_pedir")
        pin_enter("1234")
        nodes = shot("parental_desbloqueado")
        txt = " | ".join(texts(nodes))
        expect("visible" in txt, "Con el PIN correcto el contenido para adultos se muestra", "El PIN correcto no desbloqueó: " + txt[:300])
    else:
        log("FALLO", "No se encontró «Ver contenido para adultos (PIN)»")
    back(1.5)


@step("otras pantallas")
def other_screens():
    for label, exact in (("PANTALLA MÚLTIPLE", False), ("CATCH UP", False), ("CONTINUAR VIENDO", False),
                         ("Búsqueda maestra", False), ("Avisos", False), ("Mi cuenta", False)):
        nodes = go_home()
        if not tap(label, nodes=nodes, exact=exact, wait=3.5):
            log("FALLO", f"No se pudo abrir «{label}»")
            continue
        nodes = shot("pantalla_" + re.sub(r"\W+", "_", label.lower()))
        txt = " | ".join(texts(nodes))
        log("INFO", f"«{label}»: " + txt[:250])
        back(1.5)
    # radio y grabaciones: íconos sin texto, arriba a la derecha de la pantalla principal
    for name, x, y in (("radio", 1009, 64), ("grabaciones", 1141, 64)):
        go_home()
        tap_xy(x, y)
        time.sleep(3.5)
        nodes = shot("pantalla_" + name)
        log("INFO", f"«{name}»: " + " | ".join(texts(nodes))[:300])
        back(1.5)


@step("grabar y convertir a MP4")
def recording():
    # 1) La grabación programada desde la guía debió arrancar sola a su hora.
    go_home()
    tap_xy(1141, 64)  # Grabaciones
    time.sleep(3)
    nodes = shot("grabaciones_inicio")
    txt = " | ".join(texts(nodes))
    log("INFO", "Grabaciones al empezar este paso: " + txt[:400])
    if find(nodes, "Grabando"):
        log("OK", "Una grabación en curso: la programada desde la guía arrancó sola a su hora (o ya había una)")
    else:
        # 2) Si no hay ninguna, se graba a mano un canal en vivo.
        back(1.5)
        nodes = go_home()
        tap("TV EN DIRECTO", nodes=nodes, wait=3)
        time.sleep(2)
        nodes = dump()
        tap("NOTICIAS", nodes=nodes, wait=2)
        nodes = dump()
        tap("Canal Uno FHD", nodes=nodes, wait=3)
        nodes = dump()
        if not tap("Grabar", nodes=nodes, wait=9):
            log("FALLO", "No se encontró el botón Grabar: " + " | ".join(texts(nodes))[:300])
            return
        go_home()
        tap_xy(1141, 64)
        time.sleep(3)
        nodes = shot("grabaciones_en_curso")
    if not tap("Detener y guardar", nodes=nodes, wait=6):
        log("FALLO", "No hay botón «Detener y guardar» en Grabaciones")
        return
    nodes = shot("grabaciones_guardada")
    txt = " | ".join(texts(nodes))
    log("INFO", "Después de detener: " + txt[:400])
    expect("A MP4" in txt, "La grabación guardada ofrece «A MP4»", "No aparece «A MP4» tras guardar")
    if tap("A MP4", nodes=nodes, wait=2):
        nodes = shot("mp4_opciones")
        if not tap("conservar la original", nodes=nodes, wait=2):
            log("FALLO", "No se ofrecieron las opciones de conversión")
            return
        t0 = time.time()
        done = False
        i = 0
        while time.time() - t0 < 300:
            nodes = dump()
            t = " | ".join(texts(nodes))
            if i in (1, 6, 14):
                shot(f"mp4_progreso_{i}", with_texts=False)
                log("INFO", "Progreso: " + t[:300])
            i += 1
            if "Convirtiendo" not in t and "Guardando el MP4" not in t and time.time() - t0 > 8:
                done = True
                break
            time.sleep(3)
        log("INFO", f"La conversión tardó unos {int(time.time() - t0)} s (terminó: {done})")
        nodes = shot("mp4_final")
        txt = " | ".join(texts(nodes))
        log("INFO", "Pantalla tras convertir: " + txt[:500])
        files = sh("ls -la /sdcard/Movies/'Ultra Premier TV'/ /sdcard/Android/data/com.ultrapremier.tv/files/Movies/Grabaciones 2>&1")
        log("INFO", "Archivos guardados: " + files.replace("\n", " / ")[:600])
        has_mp4 = ".mp4" in files.lower()
        expect(has_mp4, "Se creó un archivo MP4", "No se encontró ningún archivo MP4 tras convertir")
        if has_mp4:
            mp4dir = os.path.join(OUT, "mp4")
            os.makedirs(mp4dir, exist_ok=True)
            names = [l.split()[-1] for l in files.splitlines() if l.strip().lower().endswith(".mp4")]
            for name in names[:1]:
                adb("pull", f"/sdcard/Movies/Ultra Premier TV/{name}", os.path.join(mp4dir, "convertido.mp4"), timeout=120)
            local = os.path.join(mp4dir, "convertido.mp4")
            if os.path.isfile(local):
                probe = subprocess.run(
                    ["ffprobe", "-v", "error", "-show_entries", "stream=codec_name,codec_type,width,height:format=duration,size",
                     "-of", "compact", local], capture_output=True, text=True)
                log("INFO", "ffprobe del MP4: " + (probe.stdout + probe.stderr).replace("\n", " ; ")[:400])
                expect("h264" in probe.stdout and "aac" in probe.stdout, "El MP4 tiene video H.264 y audio AAC y se puede leer", "El MP4 no se pudo leer bien")
                if os.path.getsize(local) > 30 * 1024 * 1024:
                    os.remove(local)
    back(1.5)


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


def main_keys():
    """Prueba corta del control remoto en pantalla completa de TV en vivo."""
    prepare()
    if not login():
        return
    time.sleep(6)
    nodes = go_home()
    tap("TV EN DIRECTO", nodes=nodes, wait=3)
    time.sleep(2)
    nodes = dump()
    tap("DEPORTES", nodes=nodes, wait=2.5)
    nodes = dump()
    tap("Deportes 1", nodes=nodes, wait=3)
    nodes = dump()
    tap("Pantalla completa", nodes=nodes, wait=9)
    log("INFO", "Actividad: " + current_focus())
    for name, code in (("derecha", 22), ("OK", 66), ("derecha", 22), ("OK", 66)):
        sh("logcat -c -s UPTV")
        key(code, 1.2)
        shot(f"teclas_{name}_{code}", with_texts=False)
        out = adb("logcat", "-d", "-s", "UPTV:D", timeout=30)
        log("INFO", f"Tecla {name}: " + out.strip().replace("\n", " ; ")[-300:])
        time.sleep(1)
    key(20, 1.2)  # abajo = canal anterior
    shot("teclas_abajo", with_texts=False)
    out = adb("logcat", "-d", "-s", "UPTV:D", timeout=30)
    log("INFO", "Tras ABAJO: " + out.strip().replace("\n", " ; ")[-300:])
    key(22, 1.2)
    shot("teclas_derecha_final", with_texts=False)
    key(66, 1.5)
    nodes = shot("teclas_dialogo_final")
    log("INFO", "Textos tras OK: " + " | ".join(texts(nodes))[:300])
    crash_check("teclas")


def start_total_ms():
    out = sh("am start -W -n com.ultrapremier.tv/com.example.mitvplayer.MainActivity", timeout=90)
    m = re.search(r"TotalTime:\s*(\d+)", out)
    return int(m.group(1)) if m else None


def median(values):
    v = sorted(x for x in values if x is not None)
    return v[len(v) // 2] if v else None


def wait_counts(t0, timeout=150):
    """Segundos desde t0 hasta que la pantalla principal muestra las tres cantidades (canales, películas, series)."""
    while time.time() - t0 < timeout:
        nodes = dump()
        if dismiss_update(nodes):
            continue
        txt = " | ".join(texts(nodes))
        if (re.search(r"\d[\d,\.]* canales", txt) and re.search(r"\d[\d,\.]* películas", txt)
                and re.search(r"\d[\d,\.]* series", txt)):
            return round(time.time() - t0, 1), txt
    return None, ""


def main_perf():
    """Mide el arranque en frío (ms) y varias esperas con listas pequeñas o grandes (--big en el servidor)."""
    prepare()
    cold = []
    for _ in range(4):
        sh("am force-stop com.ultrapremier.tv")
        time.sleep(3)
        cold.append(start_total_ms())
        time.sleep(4)
    log("PERF", f"Arranque en frío SIN sesión (ms): {cold}")
    if not login():
        return
    t, txt = wait_counts(LOGIN_T0[0])
    log("PERF", f"Primera carga: desde pulsar Entrar hasta ver las tres cantidades: {t} s  ({txt[:200]})")
    shot("perf_principal_primera_carga", with_texts=False)
    time.sleep(8)
    warm = []
    for _ in range(4):
        sh("am force-stop com.ultrapremier.tv")
        time.sleep(3)
        warm.append(start_total_ms())
        time.sleep(7)
    log("PERF", f"Arranque en frío CON sesión (ms): {warm}")
    seen = []
    for _ in range(3):
        sh("am force-stop com.ultrapremier.tv")
        time.sleep(3)
        t0 = time.time()
        sh("am start -n com.ultrapremier.tv/com.example.mitvplayer.MainActivity")
        t, _txt = wait_counts(t0)
        seen.append(t)
        time.sleep(3)
    log("PERF", f"Con sesión y listas guardadas: desde abrir hasta ver las tres cantidades (s): {seen}")
    for label, expect_text in (("TV EN DIRECTO", "Canal"), ("CINE", "Pel"), ("SERIES", "Serie")):
        nodes = go_home()
        t0 = time.time()
        tap(label, nodes=nodes, exact=(label == "SERIES"), wait=0)
        got = None
        while time.time() - t0 < 60:
            nodes = dump()
            if find(nodes, expect_text) and not find(nodes, "Cargando") and not find(nodes, "Preparando"):
                got = round(time.time() - t0, 1)
                break
        log("PERF", f"Abrir «{label}» hasta ver contenido (s): {got}")
        shot("perf_" + label.lower().replace(" ", "_"), with_texts=False)
        back(1.5)
    # búsqueda maestra
    nodes = go_home()
    if tap("Búsqueda maestra", nodes=nodes, wait=2.5):
        nodes = dump()
        edits = [n for n in nodes if n["cls"].endswith("EditText")]
        if edits:
            tap_xy(*center(edits[0]))
            time.sleep(0.5)
            t0 = time.time()
            sh("input text mero%s19")
            got = None
            while time.time() - t0 < 40:
                nodes = dump()
                if find(nodes, "número 19"):
                    got = round(time.time() - t0, 1)
                    break
            log("PERF", f"Búsqueda maestra «mero 19»: resultados en (s): {got}")
            shot("perf_busqueda", with_texts=False)
    uptv = adb("logcat", "-d", "-s", "UPTV:D", timeout=60)
    with open(os.path.join(OUT, "uptv-log.txt"), "w") as f:
        f.write(uptv)
    log("INFO", "Mediciones internas (UPTV): " + " ; ".join(l[l.find("UPTV"):] for l in uptv.splitlines() if "UPTV" in l and ("lista" in l or "listas" in l))[:1500])
    with open(os.path.join(OUT, "perf.txt"), "w") as f:
        f.write("\n".join(l for l in REPORT if "PERF" in l))


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
        other_screens()
        recording()
        parental()
    final_logs()
    # resumen
    fails = [l for l in REPORT if l.startswith("- **FALLO**")]
    log("RESUMEN", f"{len(fails)} fallos")


if __name__ == "__main__":
    if "--keys" in sys.argv:
        main_keys()
    elif "--perf" in sys.argv:
        main_perf()
    else:
        main()
