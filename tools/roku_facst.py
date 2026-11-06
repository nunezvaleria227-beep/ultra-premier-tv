#!/usr/bin/env python3
"""Arma el canal de Roku con la marca "Facst Play TV" a partir de la carpeta roku/.

Copia roku/, cambia el nombre en el manifest y en los textos visibles, dibuja el
logo / splash / íconos de Facst Play TV y deja un zip listo para instalar en un Roku
en modo desarrollador (y de ahí sacar el .pkg con el Packager).

Para usar el logo propio de la marca en vez del que dibuja este guion:
    python3 tools/roku_facst.py --logo mi_logo.png --build 2

NO se cambian a propósito el nombre del registro ("UltraPremierTV") ni el User-Agent
de las peticiones: son internos, la gente no los ve, y el servidor del proveedor ya
acepta ese User-Agent.
"""
import argparse
import os
import re
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "app/src/main/res/font"
NAME = "Facst Play TV"
BLUE_TOP, BLUE_BOTTOM = (47, 108, 240), (6, 34, 140)
NAVY = (4, 12, 48)
ACCENT = (0, 87, 253)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def shear(im, k):
    """Inclina la imagen hacia la derecha (efecto itálica de bloque)."""
    w, h = im.size
    extra = int(abs(k) * h)
    return im.transform((w + extra, h), Image.AFFINE, (1, k, -extra if k > 0 else 0, 0, 1, 0),
                        resample=Image.BICUBIC)


def make_logo(width=628):
    """Logo transparente: FACST grande, y debajo PLAY TV en un bloque azul."""
    S = 3  # se dibuja al triple y se reduce, para bordes suaves
    W, H = width * S, int(width * 0.568) * S
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # FACST
    d = ImageDraw.Draw(im)
    size1 = int(H * 0.50)
    while True:  # que el texto y su sombra quepan con margen
        f1 = font("inter_display_black_italic.otf", size1)
        tw = d.textlength("FACST", font=f1)
        if tw <= W * 0.86:
            break
        size1 -= 4
    x1 = (W - tw) / 2
    y1 = int(H * 0.04)
    sh = int(H * 0.035)
    # sombra / contorno oscuro
    d.text((x1 + sh, y1 + sh), "FACST", font=f1, fill=NAVY, stroke_width=int(H * 0.05), stroke_fill=NAVY)
    d.text((x1, y1), "FACST", font=f1, fill=(255, 255, 255), stroke_width=int(H * 0.012), stroke_fill=NAVY)
    # bloque azul con PLAY TV
    f2 = font("inter_display_extra_bold_italic.otf", int(H * 0.23))
    tw2 = d.textlength("PLAY TV", font=f2)
    bw, bh = int(tw2 + H * 0.20), int(H * 0.30)
    bx, by = int((W - bw) / 2), int(H * 0.60)
    skew = int(bh * 0.18)
    poly = [(bx + skew, by), (bx + bw + skew, by), (bx + bw, by + bh), (bx, by + bh)]
    d.polygon([(px + sh, py + sh) for px, py in poly], fill=NAVY)
    d.polygon(poly, fill=ACCENT, outline=NAVY)
    d.text((bx + (bw + skew - tw2) / 2, by + bh * 0.08), "PLAY TV", font=f2, fill=(255, 255, 255))
    # recorta lo vacío y reduce
    box = im.getbbox()
    im = im.crop(box)
    ratio = width / im.width
    return im.resize((width, max(1, int(im.height * ratio))), Image.LANCZOS)


def gradient(size):
    w, h = size
    base = Image.new("RGB", size)
    px = base.load()
    for y in range(h):
        for x in range(w):
            t = (0.65 * y / h) + (0.35 * x / w)
            px[x, y] = tuple(int(BLUE_TOP[i] + (BLUE_BOTTOM[i] - BLUE_TOP[i]) * t) for i in range(3))
    return base


def circles(bg):
    w, h = bg.size
    layer = Image.new("RGBA", bg.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    spots = [(0.10, 0.20, 0.045), (0.90, 0.14, 0.035), (0.06, 0.78, 0.035), (0.94, 0.72, 0.075), (0.46, 0.93, 0.03)]
    for fx, fy, fr in spots:
        r = int(fr * w)
        d.ellipse((fx * w - r, fy * h - r, fx * w + r, fy * h + r), outline=(255, 255, 255, 80), width=max(1, w // 600))
    bg = bg.convert("RGBA")
    bg.alpha_composite(layer)
    return bg.convert("RGB")


def make_card(size, logo, tagline=None, logo_frac=0.62):
    bg = circles(gradient(size))
    w, h = size
    lw = int(w * logo_frac)
    lg = logo.resize((lw, int(logo.height * lw / logo.width)), Image.LANCZOS)
    shadow = Image.new("RGBA", lg.size, (0, 0, 0, 0))
    shadow.putalpha(lg.getchannel("A").point(lambda a: int(a * 0.35)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(2, w // 120)))
    ly = int((h - lg.height) / 2 - (h * 0.06 if tagline else 0))
    lx = int((w - lg.width) / 2)
    bg = bg.convert("RGBA")
    bg.alpha_composite(shadow, (lx, ly + max(2, h // 60)))
    bg.alpha_composite(lg, (lx, ly))
    bg = bg.convert("RGB")
    if tagline:
        d = ImageDraw.Draw(bg)
        f = font("inter_display_semi_bold.otf", max(10, int(h * 0.04)))
        txt = "  ".join(tagline)  # espacio entre letras simple
        tw = d.textlength(txt, font=f)
        d.text(((w - tw) / 2, ly + lg.height + h * 0.07), txt, font=f, fill=(225, 235, 255))
    return bg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--logo", help="PNG con el logo de la marca (fondo transparente)")
    ap.add_argument("--build", type=int, default=1, help="build_version del manifest")
    ap.add_argument("--out", default=str(ROOT / "dist-facst"), help="carpeta de salida")
    a = ap.parse_args()

    out = Path(a.out)
    stage = out / "stage"
    if out.exists():
        shutil.rmtree(out)
    stage.mkdir(parents=True)
    for part in ("manifest", "source", "components", "images"):
        src = ROOT / "roku" / part
        (shutil.copytree if src.is_dir() else shutil.copy)(src, stage / part)

    # --- textos visibles
    man = (stage / "manifest").read_text()
    man = re.sub(r"^title=.*$", f"title={NAME}", man, flags=re.M)
    man = re.sub(r"^build_version=.*$", f"build_version={a.build}", man, flags=re.M)
    (stage / "manifest").write_text(man)
    for rel in ("components/screens/SettingsScreen.brs", "source/main.brs"):
        p = stage / rel
        s = p.read_text()
        s2 = s.replace("Ultra Premier TV", NAME)
        if s2 == s:
            sys.exit(f"No encontré el texto de marca en {rel}")
        p.write_text(s2)

    # --- imágenes de marca
    logo = Image.open(a.logo).convert("RGBA") if a.logo else make_logo()
    img = stage / "images"
    logo_small = logo.copy()
    logo_small.thumbnail((628, 357), Image.LANCZOS)
    logo_small.save(img / "logo.png")
    tag = ("TV EN VIVO", "·", "PELÍCULAS", "·", "SERIES")
    make_card((1280, 720), logo, tag).save(img / "splash_hd.jpg", quality=92)
    make_card((720, 480), logo, tag, 0.66).save(img / "splash_sd.jpg", quality=92)
    make_card((540, 405), logo, None, 0.72).save(img / "icon_fhd.png")
    make_card((290, 218), logo, None, 0.72).save(img / "icon_hd.png")
    make_card((246, 140), logo, None, 0.62).save(img / "icon_sd.png")

    # --- zip (solo lo que Roku necesita)
    zpath = out / f"FacstPlayTV-Roku-build{a.build}.zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for part in ("manifest", "source", "components", "images"):
            base = stage / part
            files = [base] if base.is_file() else sorted(f for f in base.rglob("*") if f.is_file())
            for f in files:
                z.write(f, f.relative_to(stage).as_posix())
    print(zpath)


if __name__ == "__main__":
    main()
