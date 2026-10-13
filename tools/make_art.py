"""Genera el arte de fondo de Ultra Premier TV (original, creado por código).

Uso (necesita numpy, opencv-python y pillow):
    python3 tools/make_art.py app/src/main/res/drawable-nodpi            # las cuatro imágenes
    python3 tools/make_art.py app/src/main/res/drawable-nodpi live        # solo una: home, live, movies, series
"""
import sys, math
import numpy as np, cv2
from PIL import Image

OUT = sys.argv[1]
W, H = 2560, 1440   # se dibuja al doble y se reduce a 1280x720
rng = np.random.default_rng(7)

def canvas(c0, c1, angle=90):
    """Degradado lineal. angle=90 -> de arriba (c0) a abajo (c1)."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    a = math.radians(angle)
    t = (xx / W * math.cos(a) + yy / H * math.sin(a))
    t = (t - t.min()) / (t.max() - t.min())
    c0 = np.array(c0, np.float32); c1 = np.array(c1, np.float32)
    return c0[None, None, :] * (1 - t[..., None]) + c1[None, None, :] * t[..., None]

def add(img, layer, k=1.0):
    img += layer * k

def glow(img, cx, cy, r, color, k=1.0):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d2 = ((xx - cx) ** 2 + (yy - cy) ** 2) / (r * r)
    g = np.exp(-d2 * 2.2)
    img += g[..., None] * np.array(color, np.float32)[None, None, :] * k

def layer_from_mask(mask, color, blur=0):
    m = mask.astype(np.float32)
    if blur: m = cv2.GaussianBlur(m, (0, 0), blur)
    return m[..., None] * np.array(color, np.float32)[None, None, :]

def beam(img, apex, angle_deg, spread_deg, length, color, k=0.5, blur=14):
    ax, ay = apex
    a1 = math.radians(angle_deg - spread_deg / 2)
    a2 = math.radians(angle_deg + spread_deg / 2)
    p = np.array([[ax, ay],
                  [ax + length * math.cos(a1), ay + length * math.sin(a1)],
                  [ax + length * math.cos(a2), ay + length * math.sin(a2)]], np.int32)
    m = np.zeros((H, W), np.uint8)
    cv2.fillPoly(m, [p], 255)
    m = cv2.GaussianBlur(m.astype(np.float32) / 255, (0, 0), blur)
    # se desvanece con la distancia
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    dist = np.sqrt((xx - ax) ** 2 + (yy - ay) ** 2) / length
    fade = np.clip(1 - dist, 0, 1) ** 1.3
    img += (m * fade)[..., None] * np.array(color, np.float32)[None, None, :] * k

def bokeh(img, n, colors, rmin, rmax, k=0.35, region=(0, 0, 1, 1)):
    layer = np.zeros((H, W, 3), np.float32)
    for _ in range(n):
        x = rng.uniform(region[0], region[2]) * W
        y = rng.uniform(region[1], region[3]) * H
        r = rng.uniform(rmin, rmax)
        col = np.array(colors[rng.integers(len(colors))], np.float32)
        alpha = rng.uniform(0.25, 1.0)
        tmp = np.zeros((H, W), np.uint8)
        cv2.circle(tmp, (int(x), int(y)), int(r), 255, -1, cv2.LINE_AA)
        ring = np.zeros((H, W), np.uint8)
        cv2.circle(ring, (int(x), int(y)), int(r), 255, max(2, int(r * 0.08)), cv2.LINE_AA)
        m = (tmp.astype(np.float32) * 0.55 + ring.astype(np.float32) * 0.9) / 255
        # recorte local para que sea rápido
        x0, x1 = max(int(x - r - 6), 0), min(int(x + r + 6), W)
        y0, y1 = max(int(y - r - 6), 0), min(int(y + r + 6), H)
        sub = cv2.GaussianBlur(m[y0:y1, x0:x1], (0, 0), max(1.0, r * 0.06))
        layer[y0:y1, x0:x1] += sub[..., None] * col[None, None, :] * alpha
    img += layer * k

def vignette(img, strength=0.55):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    v = 1 - strength * np.clip(d - 0.35, 0, 1.2) ** 1.6
    img *= v[..., None]

def grain(img, s=3.0):
    n = rng.normal(0, s, (H, W, 1)).astype(np.float32)
    img += n

def save(img, name, q=84):
    out = np.clip(img, 0, 255).astype(np.uint8)
    im = Image.fromarray(out).resize((1280, 720), Image.LANCZOS)
    im.save(f"{OUT}/{name}", quality=q, optimize=True, progressive=True)

def rounded_poly(cx, cy, w, h, r, rot=0, skew=0.0):
    pts = []
    for (ccx, ccy, a0) in [(w / 2 - r, -h / 2 + r, -90), (w / 2 - r, h / 2 - r, 0), (-w / 2 + r, h / 2 - r, 90), (-w / 2 + r, -h / 2 + r, 180)]:
        for t in np.linspace(a0, a0 + 90, 10):
            pts.append((ccx + r * math.cos(math.radians(t)), ccy + r * math.sin(math.radians(t))))
    pts = np.array(pts, np.float32)
    pts[:, 0] += pts[:, 1] * skew
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    R = np.array([[c, -s], [s, c]], np.float32)
    pts = pts @ R.T
    pts[:, 0] += cx; pts[:, 1] += cy
    return pts.astype(np.int32)

BLUE = (0, 87, 253); CYAN = (60, 200, 255); WHITE = (255, 255, 255)
GOLD = (255, 190, 80); ORANGE = (255, 120, 40); PINK = (255, 80, 160); VIOLET = (140, 80, 255)

# ------------------------------------------------------------------ fondo general
def make_home():
    img = canvas((6, 8, 30), (2, 20, 80), 70)
    glow(img, W * 0.78, H * 0.18, 900, (0, 70, 200), 1.0)
    glow(img, W * 0.12, H * 0.95, 800, (20, 40, 160), 0.8)
    beam(img, (W * 0.95, -80), 118, 16, 2300, (70, 140, 255), 0.35)
    beam(img, (W * 0.62, -80), 105, 10, 2000, (60, 120, 255), 0.22)
    beam(img, (W * 0.05, H + 60), -55, 14, 2200, (50, 110, 255), 0.22)
    bokeh(img, 34, [(50, 120, 255), (90, 170, 255), (20, 80, 220), (160, 200, 255)], 18, 90, 0.32)
    vignette(img, 0.6); grain(img, 2.5)
    return img

# ------------------------------------------------------------------ TV en directo
def make_live():
    img = canvas((4, 10, 40), (0, 40, 120), 80)
    # focos de estadio
    for i, x in enumerate(np.linspace(0.12, 0.88, 7)):
        glow(img, W * x, H * 0.05, 140, (190, 220, 255), 0.9)
        beam(img, (W * x, H * 0.04), 90 + (x - 0.5) * 38, 20, 1500, (120, 180, 255), 0.30, 18)
    # señal: arcos concéntricos
    arcs = np.zeros((H, W), np.uint8)
    for r in range(180, 1500, 150):
        cv2.ellipse(arcs, (int(W * 0.5), int(H * 0.98)), (r, int(r * 0.7)), 0, 195, 345, 255, 6, cv2.LINE_AA)
    add(img, layer_from_mask(arcs / 255, CYAN, 3), 0.45)
    # pantalla central brillante
    scr = np.zeros((H, W), np.uint8)
    cv2.fillPoly(scr, [rounded_poly(W * 0.5, H * 0.53, 900, 520, 46)], 255)
    add(img, layer_from_mask(scr / 255, (10, 60, 170), 2), 0.9)
    edge = np.zeros((H, W), np.uint8)
    cv2.polylines(edge, [rounded_poly(W * 0.5, H * 0.53, 900, 520, 46)], True, 255, 7, cv2.LINE_AA)
    add(img, layer_from_mask(edge / 255, (140, 200, 255), 2), 0.9)
    add(img, layer_from_mask(edge / 255, CYAN, 22), 0.9)
    # triángulo play
    tri = np.zeros((H, W), np.uint8)
    cx, cy = W * 0.5, H * 0.53
    cv2.fillPoly(tri, [np.array([[cx - 80, cy - 105], [cx - 80, cy + 105], [cx + 110, cy]], np.int32)], 255)
    add(img, layer_from_mask(tri / 255, (255, 255, 255), 1.5), 0.95)
    add(img, layer_from_mask(tri / 255, CYAN, 30), 0.8)
    # barra "en vivo" (punto rojo)
    dot = np.zeros((H, W), np.uint8)
    cv2.circle(dot, (int(W * 0.5 - 380), int(H * 0.53 - 200)), 20, 255, -1, cv2.LINE_AA)
    add(img, layer_from_mask(dot / 255, (255, 40, 40), 1), 2.0)
    add(img, layer_from_mask(dot / 255, (255, 40, 40), 26), 1.6)
    bokeh(img, 26, [(120, 190, 255), (255, 255, 255), (60, 140, 255)], 14, 70, 0.25)
    vignette(img, 0.55); grain(img, 2.5)
    return img

# ------------------------------------------------------------------ Cine
def make_movies():
    img = canvas((14, 6, 28), (30, 18, 70), 75)
    glow(img, W * 0.5, H * 0.4, 1100, (140, 50, 20), 0.8)
    glow(img, W * 0.85, H * 0.9, 700, (60, 30, 150), 0.7)
    # reflectores cruzados
    beam(img, (W * 0.08, -60), 62, 12, 2400, (255, 200, 120), 0.38, 16)
    beam(img, (W * 0.92, -60), 118, 12, 2400, (255, 170, 90), 0.38, 16)
    beam(img, (W * 0.5, -80), 90, 9, 2200, (255, 230, 170), 0.30, 16)
    # tira de película diagonal con fotogramas
    strip = np.zeros((H, W), np.uint8)
    cx, cy = W * 0.5, H * 0.62
    sw, sh, rot = 2900, 420, -9
    poly = rounded_poly(cx, cy, sw, sh, 10, rot)
    cv2.fillPoly(strip, [poly], 255)
    add(img, layer_from_mask(strip / 255, (6, 4, 14), 1.2), 0.0)
    img *= (1 - 0.82 * cv2.GaussianBlur(strip.astype(np.float32) / 255, (0, 0), 1.2))[..., None]
    # fotogramas con degradados cálidos/fríos
    c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    fw, fh, gap = 340, 270, 40
    n = int(sw / (fw + gap)) + 1
    for i in range(n):
        lx = -sw / 2 + 60 + i * (fw + gap) + fw / 2
        pts = rounded_poly(0, 0, fw, fh, 14)
        pts = pts.astype(np.float32)
        R = np.array([[c, -s], [s, c]], np.float32)
        pts = pts @ R.T + np.array([cx + lx * c, cy + lx * s], np.float32)
        m = np.zeros((H, W), np.uint8)
        cv2.fillPoly(m, [pts.astype(np.int32)], 255)
        t = i / max(n - 1, 1)
        col = np.array([[255, 150, 70], [255, 90, 120], [120, 90, 255], [60, 170, 255], [255, 200, 90]][i % 5], np.float32)
        mm = cv2.GaussianBlur(m.astype(np.float32) / 255, (0, 0), 1.0)
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        grad = np.clip(0.35 + 0.65 * (1 - (yy - (cy - 200)) / 400), 0.2, 1)
        img += (mm * grad)[..., None] * col[None, None, :] * 0.55
    # perforaciones
    perf = np.zeros((H, W), np.uint8)
    pw, ph, pg = 36, 24, 34
    for side in (-1, 1):
        for i in range(int(sw / (pw + pg))):
            lx = -sw / 2 + 20 + i * (pw + pg)
            ly = side * (sh / 2 - 34)
            pts = rounded_poly(0, 0, pw, ph, 6).astype(np.float32)
            R = np.array([[c, -s], [s, c]], np.float32)
            pts = pts @ R.T + np.array([cx + lx * c - ly * s, cy + lx * s + ly * c], np.float32)
            cv2.fillPoly(perf, [pts.astype(np.int32)], 255)
    img += layer_from_mask(perf / 255, (230, 215, 190), 0.8) * 0.75
    bokeh(img, 40, [(255, 190, 90), (255, 140, 60), (255, 230, 170), (150, 110, 255)], 16, 80, 0.3)
    vignette(img, 0.6); grain(img, 2.5)
    return img

# ------------------------------------------------------------------ Series
def make_series():
    img = canvas((10, 6, 40), (46, 12, 92), 60)
    glow(img, W * 0.7, H * 0.35, 1000, (150, 40, 160), 0.8)
    glow(img, W * 0.15, H * 0.8, 800, (40, 70, 220), 0.8)
    beam(img, (W * 0.98, -60), 120, 12, 2300, (200, 120, 255), 0.25)
    beam(img, (W * 0.02, H + 60), -60, 12, 2300, (90, 130, 255), 0.25)
    # pantallas apiladas en abanico
    cards = [
        (W * 0.30, H * 0.58, 720, 420, -14, (60, 100, 255), (150, 60, 255)),
        (W * 0.43, H * 0.54, 760, 440, -7, (130, 60, 255), (255, 70, 170)),
        (W * 0.57, H * 0.50, 800, 460, 0, (255, 80, 170), (255, 150, 80)),
        (W * 0.72, H * 0.54, 760, 440, 7, (60, 200, 255), (110, 80, 255)),
    ]
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for (cx, cy, w, h, rot, c0, c1) in cards:
        pts = rounded_poly(cx, cy, w, h, 34, rot, skew=-0.06)
        m = np.zeros((H, W), np.uint8)
        cv2.fillPoly(m, [pts], 255)
        shadow = cv2.GaussianBlur(np.roll(m, 40, axis=0).astype(np.float32) / 255, (0, 0), 38)
        img *= (1 - 0.55 * shadow)[..., None]
        mm = cv2.GaussianBlur(m.astype(np.float32) / 255, (0, 0), 1.0)
        t = np.clip((xx - (cx - w / 2)) / w * 0.6 + (yy - (cy - h / 2)) / h * 0.4, 0, 1)
        col = np.array(c0, np.float32)[None, None, :] * (1 - t[..., None]) + np.array(c1, np.float32)[None, None, :] * t[..., None]
        img[:] = img * (1 - mm[..., None]) + col * mm[..., None]
        edge = np.zeros((H, W), np.uint8)
        cv2.polylines(edge, [pts], True, 255, 5, cv2.LINE_AA)
        img += layer_from_mask(edge / 255, (255, 255, 255), 1.2) * 0.55
        # símbolo play tenue
        px, py = cx, cy
        tri = np.zeros((H, W), np.uint8)
        cv2.fillPoly(tri, [np.array([[px - 40, py - 55], [px - 40, py + 55], [px + 58, py]], np.int32)], 255)
        img += layer_from_mask(tri / 255, (255, 255, 255), 1.2) * 0.35
    bokeh(img, 34, [(200, 120, 255), (120, 160, 255), (255, 130, 200), (255, 255, 255)], 14, 80, 0.28)
    vignette(img, 0.6); grain(img, 2.5)
    return img

if __name__ == "__main__":
    which = sys.argv[2:] or ["home", "live", "movies", "series"]
    fns = {"home": make_home, "live": make_live, "movies": make_movies, "series": make_series}
    for k in which:
        save(fns[k](), {"home": "bg_home.jpg", "live": "art_live.jpg", "movies": "art_movies.jpg", "series": "art_series.jpg"}[k])
        print("ok", k)
