"""Convierte el logotipo original (PNG) en un vector de Android nítido a cualquier tamaño.

Uso (necesita numpy, opencv-python, scipy y pillow):
    python3 tools/vectorize_logo.py tools/logo_original.png app/src/main/res/drawable/logo_ultra.xml /tmp/vista_previa.png
Si cambias el logotipo, actualiza también LOGO_RATIO en ui/Components.kt con el "viewport" que imprime el script (ancho / alto).
"""
import sys
import numpy as np, cv2
from PIL import Image
from scipy import ndimage as ndi

SRC = sys.argv[1]
OUT_XML = sys.argv[2]
OUT_PREVIEW = sys.argv[3]
S = 3  # factor de ampliación para contornos finos

im = Image.open(SRC).convert('RGB')
W0, H0 = im.size
big = im.resize((W0 * S, H0 * S), Image.LANCZOS)
a = np.asarray(big).astype(np.float32)

WHITE = np.array([255, 255, 255], np.float32)
NAVY = np.array([2, 11, 42], np.float32)
BLUE = np.array([0, 87, 253], np.float32)
SHADOW = np.array([0, 36, 140], np.float32)   # sombra de las letras TV
cents = np.stack([WHITE, NAVY, BLUE, SHADOW])
d = ((a[:, :, None, :] - cents[None, None, :, :]) ** 2).sum(-1)
cls = d.argmin(-1).astype(np.uint8)  # 0 blanco 1 marino 2 azul 3 sombra

# fondo = azul conectado a la esquina
blue = cls == 2
lab, n = ndi.label(blue)
bg = lab == lab[0, 0]
logo = ~bg
# limpiar motas: quedarnos con el componente grande del logo
lab2, n2 = ndi.label(logo)
sizes = ndi.sum(logo, lab2, range(1, n2 + 1))
logo = lab2 == (1 + int(np.argmax(sizes)))
logo = ndi.binary_fill_holes(logo | (cls == 0)) & logo | ndi.binary_fill_holes(logo)

white = (cls == 0) & logo
interior_blue = (cls == 2) & logo
shadow = (cls == 3) & logo

def clean(mask, min_area):
    m = mask.astype(np.uint8)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    lab, n = ndi.label(m)
    if n == 0:
        return m.astype(bool)
    sizes = ndi.sum(m, lab, range(1, n + 1))
    keep = np.isin(lab, [i + 1 for i, s in enumerate(sizes) if s >= min_area])
    return keep

white = clean(white, 400)
interior_blue = clean(interior_blue, 600)
shadow = clean(shadow, 300)
# el hueco del navy que quedó entre capas lo cubre la silueta: la silueta es 'logo'

ys, xs = np.where(logo)
pad = 12 * S
x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad, a.shape[1] - 1)
y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad, a.shape[0] - 1)
VW, VH = int(x1 - x0 + 1), int(y1 - y0 + 1)

def to_path(mask, eps):
    m = mask[y0:y1 + 1, x0:x1 + 1].astype(np.uint8) * 255
    # suavizado ligero para quitar escalones del umbral
    m = cv2.GaussianBlur(m, (0, 0), 1.2)
    m = (m > 127).astype(np.uint8)
    cnts, hier = cv2.findContours(m, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    parts = []
    for c in cnts:
        if cv2.contourArea(c) < 40:
            continue
        ap = cv2.approxPolyDP(c, eps, True)[:, 0, :]
        if len(ap) < 3:
            continue
        s = "M%d,%d" % tuple(ap[0])
        px, py = ap[0]
        for x, y in ap[1:]:
            s += "l%d,%d" % (x - px, y - py)
            px, py = x, y
        s += "z"
        parts.append(s)
    return "".join(parts)

halo = cv2.dilate(logo.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * 15 + 1, 2 * 15 + 1))).astype(bool)
layers = [
    ("#0057FE", halo, 1.5),
    ("#020B2A", logo, 1.0),
    ("#0057FE", interior_blue, 1.0),
    ("#00248C", shadow, 1.0),
    ("#FFFFFF", white, 1.0),
]
paths = []
for color, mask, eps in layers:
    p = to_path(mask, eps)
    paths.append((color, p))
    print(color, len(p))

xml = ['<?xml version="1.0" encoding="utf-8"?>',
       '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
       '    android:width="%ddp"' % (VW // S),
       '    android:height="%ddp"' % (VH // S),
       '    android:viewportWidth="%d"' % VW,
       '    android:viewportHeight="%d">' % VH]
for color, p in paths:
    xml.append('    <path android:fillColor="%s" android:fillType="evenOdd" android:pathData="%s" />' % (color, p))
xml.append('</vector>')
open(OUT_XML, 'w').write("\n".join(xml) + "\n")
print("viewport", VW, VH, "aspect", VW / VH)

# vista previa: dibujar los mismos contornos con anti-alias sobre fondo oscuro y claro
def render(bgcol, scale=1.0):
    img = np.full((VH, VW, 3), bgcol, np.uint8)
    for color, mask, eps in layers:
        m = mask[y0:y1 + 1, x0:x1 + 1].astype(np.uint8) * 255
        m = cv2.GaussianBlur(m, (0, 0), 1.2)
        m = (m > 127).astype(np.uint8)
        cnts, hier = cv2.findContours(m, cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
        layer = np.zeros((VH, VW), np.uint8)
        # even-odd via rellenado de externos y vaciado de huecos
        for i, c in enumerate(cnts):
            if cv2.contourArea(c) < 40: continue
            ap = cv2.approxPolyDP(c, eps, True)
            is_hole = hier[0][i][3] != -1
            cv2.fillPoly(layer, [ap], 0 if is_hole else 255)
        rgb = tuple(int(color[i:i+2], 16) for i in (1, 3, 5))
        img[layer > 0] = rgb
    return img
prev = render((10, 12, 34))
Image.fromarray(prev).resize((VW // 2, VH // 2), Image.LANCZOS).save(OUT_PREVIEW)
np.save(OUT_PREVIEW + '.npy', np.array([VW, VH, x0, y0, S]))
