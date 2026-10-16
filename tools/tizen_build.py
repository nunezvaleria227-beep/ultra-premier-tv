#!/usr/bin/env python3
"""Arma lo que sale de la carpeta tizen/ (la app de Samsung).

  tizen_build.py check              revisa que todo esté completo y en ES5 (sin sintaxis nueva)
  tizen_build.py wgt SALIDA.wgt     paquete .wgt SIN firmar (lo firma Tizen Studio con el certificado de la persona)
  tizen_build.py project SALIDA.zip proyecto completo (con la guía) para importar en Tizen Studio
  tizen_build.py preview CARPETA [--shots CARPETA_CAPTURAS] [--progress AVANCE.json] [--stamp TEXTO]
                                    versión de demostración para navegador (datos de ejemplo, reproductor de mentira).
                                    CARPETA/app/ es la app suelta (la usa la prueba automática);
                                    CARPETA/index.html es la página de vista previa, con la app completa adentro.
"""
import base64
import io
import json
import os
import re
import shutil
import subprocess
import sys
import xml.dom.minidom
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "tizen")

# Lo que va dentro de la TV. Las carpetas preview/ y store/ y la guía se quedan fuera.
PACKAGE_FILES = ["config.xml", "index.html", "icon.png"]
PACKAGE_DIRS = ["css", "js", "images"]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def package_entries():
    out = [(f, os.path.join(APP, f)) for f in PACKAGE_FILES]
    for d in PACKAGE_DIRS:
        for base, _dirs, files in os.walk(os.path.join(APP, d)):
            for name in sorted(files):
                full = os.path.join(base, name)
                out.append((os.path.relpath(full, APP).replace(os.sep, "/"), full))
    return out


def cmd_check():
    problems = []
    # config.xml bien formado y con lo mínimo
    try:
        dom = xml.dom.minidom.parse(os.path.join(APP, "config.xml"))
        privs = [n.getAttribute("name") for n in dom.getElementsByTagName("tizen:privilege")]
        for need in ("http://tizen.org/privilege/internet", "http://tizen.org/privilege/tv.inputdevice"):
            if need not in privs:
                problems.append("config.xml: falta el permiso " + need)
        if not dom.getElementsByTagName("access"):
            problems.append("config.xml: falta <access origin=\"*\"> (sin eso la TV no deja llamar al servidor)")
    except Exception as e:  # noqa: BLE001
        problems.append("config.xml mal formado: %s" % e)

    # index.html: todos los scripts y estilos que menciona existen
    html = read(os.path.join(APP, "index.html"))
    for ref in re.findall(r'(?:src|href)="([^"]+)"', html):
        if ref.startswith("$WEBAPIS") or ref.startswith("http"):
            continue
        if not os.path.exists(os.path.join(APP, ref)):
            problems.append("index.html menciona %s pero no existe" % ref)
    # todos los .js de js/ están en index.html
    for name in sorted(os.listdir(os.path.join(APP, "js"))):
        if name.endswith(".js") and ("js/" + name) not in html:
            problems.append("js/%s no está en index.html" % name)

    # sintaxis (node) y ES5 (eslint, si está)
    node = shutil.which("node")
    js_files = [os.path.join(APP, "js", n) for n in sorted(os.listdir(os.path.join(APP, "js"))) if n.endswith(".js")]
    if node:
        for p in js_files:
            r = subprocess.run([node, "--check", p], capture_output=True, text=True)
            if r.returncode != 0:
                problems.append("sintaxis en %s: %s" % (os.path.basename(p), r.stderr.strip().splitlines()[-1:]))
    es5_regex = [
        (r"=>", "función flecha"), (r"\blet\s+[A-Za-z_$]", "let"), (r"\bconst\s+[A-Za-z_$]", "const"),
        (r"`", "plantilla con comillas invertidas"), (r"\bclass\s+[A-Za-z_$]+\s*[{ ]", "class"),
        (r"\.\.\.[A-Za-z_$(\[]", "spread"), (r"\basync\s+function|\bawait\s", "async/await"),
    ]
    for p in js_files:
        src = read(p)
        # se quitan comentarios y textos para no dar falsos avisos
        stripped = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
        stripped = re.sub(r"//[^\n]*", "", stripped)
        stripped = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', stripped)
        stripped = re.sub(r"'(?:\\.|[^'\\\n])*'", "''", stripped)
        stripped = re.sub(r"/(?:\\.|[^/\\\n])+/[gimy]*(?=[.,;)\s])", "/re/", stripped)
        for rx, what in es5_regex:
            m = re.search(rx, stripped)
            if m:
                line = stripped[:m.start()].count("\n") + 1
                problems.append("%s: %s (línea %d); la app es ES5 a propósito" % (os.path.basename(p), what, line))

    size = sum(os.path.getsize(full) for _rel, full in package_entries())
    if problems:
        print("PROBLEMAS:")
        for pr in problems:
            print(" -", pr)
        return 1
    print("OK: %d archivos, %.1f MB dentro del paquete" % (len(package_entries()), size / 1024.0 / 1024.0))
    return 0


def cmd_wgt(out):
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for rel, full in package_entries():
            z.write(full, rel)
    print("paquete:", out, "(%d bytes)" % os.path.getsize(out))
    return 0


def cmd_project(out):
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for rel, full in package_entries():
            z.write(full, "UltraPremierTV/" + rel)
        guide = os.path.join(APP, "LEEME.md")
        if os.path.exists(guide):
            z.write(guide, "UltraPremierTV/LEEME.md")
        for extra in ("store/icon_512.png",):
            if os.path.exists(os.path.join(APP, extra)):
                z.write(os.path.join(APP, extra), "UltraPremierTV/" + extra)
    print("proyecto:", out, "(%d bytes)" % os.path.getsize(out))
    return 0


PREVIEW_SCRIPTS = '<script src="js/preview/fake_backend.js"></script>\n<script src="js/preview/demo_api.js"></script>\n<script src="js/app.js"></script>'

# Las capturas que se muestran en la página de vista previa (archivo -> pie de foto).
SHOT_CAPTIONS = [
    ("01_login.png", "Entrar con usuario y clave"),
    ("02_home.png", "Inicio"),
    ("04_live_focus.png", "TV en vivo, con lo que pasa ahora y después"),
    ("06_movies.png", "Cine, con categorías y carátulas"),
    ("07_movie_detail.png", "Ficha de una película"),
    ("10_series_detail.png", "Ficha de una serie con sus episodios"),
    ("05_player_live.png", "Reproductor de un canal en vivo"),
    ("14_account.png", "Mi cuenta"),
]

BRIDGE = """<script>
(function () {
    // La página de vista previa manda las teclas del control remoto en pantalla como mensajes.
    window.addEventListener("message", function (e) {
        var d = e.data;
        if (!d || typeof d.upKey !== "number") return;
        var code = d.upKey, ev;
        try { ev = new KeyboardEvent("keydown", { bubbles: true, cancelable: true }); }
        catch (x) { ev = document.createEvent("Event"); ev.initEvent("keydown", true, true); }
        try {
            Object.defineProperty(ev, "keyCode", { get: function () { return code; } });
            Object.defineProperty(ev, "which", { get: function () { return code; } });
        } catch (y) { /* nada */ }
        var t = document.activeElement;
        if (!t || t === document.body) t = document;
        t.dispatchEvent(ev);
    });
    window.addEventListener("load", function () {
        setTimeout(function () { try { parent.postMessage({ upReady: true }, "*"); } catch (e) { /* nada */ } }, 300);
    });
})();
</script>"""


def data_uri(path, mime):
    with open(path, "rb") as f:
        return "data:%s;base64,%s" % (mime, base64.b64encode(f.read()).decode("ascii"))


def build_single_doc():
    """La app completa en un solo documento (estilos, scripts e imágenes adentro) para la página de vista previa."""
    html = read(os.path.join(APP, "index.html"))
    html = html.replace('<script src="$WEBAPIS/webapis/webapis.js"></script>\n', "")
    html = html.replace('<script src="js/app.js"></script>', PREVIEW_SCRIPTS)

    images = {}
    for name in sorted(os.listdir(os.path.join(APP, "images"))):
        mime = "image/png" if name.endswith(".png") else "image/jpeg"
        images[name] = data_uri(os.path.join(APP, "images", name), mime)

    css = read(os.path.join(APP, "css", "style.css"))
    css = css.replace("url(../images/bg.jpg)", 'url("%s")' % images["bg.jpg"])
    if "images/" in css:
        raise SystemExit("style.css todavía menciona images/ después de incrustar las imágenes")
    html = html.replace('<link rel="stylesheet" href="css/style.css">', "<style>\n%s\n</style>" % css)

    def inline_script(m):
        rel = m.group(1)
        path = os.path.join(APP, "preview", rel[len("js/preview/"):]) if rel.startswith("js/preview/") else os.path.join(APP, rel)
        src = read(path)
        src = src.replace('"images/" + t.img', "UP_IMG[t.img]")
        src = src.replace('"images/logo.png"', 'UP_IMG["logo.png"]')
        if "images/" in src.replace("// ", "//"):
            for n, line in enumerate(src.splitlines(), 1):
                if "images/" in line and not line.strip().startswith(("/*", "//", "*")):
                    raise SystemExit("%s:%d todavía menciona images/ (la vista previa no sabe incrustarla)" % (rel, n))
        return "<script>\n%s\n</script>" % src.replace("</script", "<\\/script")

    html = re.sub(r'<script src="(js/[^"]+)"></script>', inline_script, html)
    table = "<script>window.UP_IMG = %s;</script>\n" % json.dumps(images)
    html = html.replace("<script>\n/* ", table + "<script>\n/* ", 1)
    if "UP_IMG = " not in html:
        raise SystemExit("no se pudo poner la tabla de imágenes antes del primer script")
    html = html.replace("</body>", BRIDGE + "\n</body>")
    return html


def json_for_script(value):
    """JSON que se puede poner dentro de un <script type="application/json"> sin romper la página."""
    return json.dumps(value, ensure_ascii=False).replace("</", "<\\/").replace("<!--", "<\\u0021--")


def shots_payload(shots_dir):
    if not shots_dir:
        return []
    from PIL import Image
    out = []
    for name, caption in SHOT_CAPTIONS:
        path = os.path.join(shots_dir, name)
        if not os.path.exists(path):
            continue
        img = Image.open(path).convert("RGB")
        img = img.resize((960, int(960 * img.height / img.width)), Image.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, "JPEG", quality=72, optimize=True)
        out.append({"src": "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode("ascii"), "caption": caption})
    return out


def cmd_preview(outdir, shots_dir=None, progress_file=None, stamp=""):
    if os.path.exists(outdir):
        shutil.rmtree(outdir)
    app = os.path.join(outdir, "app")
    os.makedirs(app)
    for rel, full in package_entries():
        if rel in ("config.xml", "icon.png"):
            continue
        dest = os.path.join(app, rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copy(full, dest)
    pv = os.path.join(app, "js", "preview")
    os.makedirs(pv)
    for name in ("fake_backend.js", "demo_api.js"):
        shutil.copy(os.path.join(APP, "preview", name), os.path.join(pv, name))
    html = read(os.path.join(APP, "index.html"))
    html = html.replace('<script src="$WEBAPIS/webapis/webapis.js"></script>\n', "")
    html = html.replace('<script src="js/app.js"></script>', PREVIEW_SCRIPTS)
    with open(os.path.join(app, "index.html"), "w", encoding="utf-8") as f:
        f.write(html)

    wrapper = os.path.join(APP, "preview", "wrapper.html")
    if os.path.exists(wrapper):
        page = read(wrapper)
        progress = []
        if progress_file:
            with open(progress_file, encoding="utf-8") as f:
                progress = json.load(f)
        page = page.replace("__APP_DOC__", json_for_script(build_single_doc()))
        page = page.replace("__PROGRESS__", json_for_script(progress))
        page = page.replace("__SHOTS__", json_for_script(shots_payload(shots_dir)))
        page = page.replace("__STAMP__", stamp)
        with open(os.path.join(outdir, "index.html"), "w", encoding="utf-8") as f:
            f.write(page)
    print("vista previa en", outdir)
    return 0


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd = argv[1]
    if cmd == "check":
        return cmd_check()
    if cmd == "wgt" and len(argv) == 3:
        return cmd_wgt(argv[2])
    if cmd == "project" and len(argv) == 3:
        return cmd_project(argv[2])
    if cmd == "preview" and len(argv) >= 3:
        opts = {"--shots": None, "--progress": None, "--stamp": ""}
        rest = argv[3:]
        while rest:
            if rest[0] not in opts or len(rest) < 2:
                print(__doc__)
                return 2
            opts[rest[0]] = rest[1]
            rest = rest[2:]
        return cmd_preview(argv[2], opts["--shots"], opts["--progress"], opts["--stamp"])
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
