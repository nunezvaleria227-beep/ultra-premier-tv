#!/usr/bin/env python3
"""Servidor Xtream Codes de mentira para la prueba automática (QA) de las apps de Fire TV.

Con --size huge entrega catálogos del tamaño de un panel grande de IPTV y con datos "sucios" a propósito:
elementos repetidos en dos categorías, campos vacíos o nulos, números como texto, calificaciones raras,
nombres larguísimos o con emojis, pósters rotos o enormes, series con formatos distintos de episodios.
Así se ve cómo se porta la app con el servidor real de un cliente, sin necesitar cuentas ni internet.

Uso: mock_xtream.py --media DIR [--port 8880] [--size small|big|huge] [--rate BYTES_POR_SEGUNDO]
"""
import argparse
import base64
import hashlib
import json
import os
import random
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = "http://10.0.2.2:8880"
USER = "demo"
PASS = "demo"
MEDIA = "."
RATE = 0  # bytes por segundo para las listas grandes (0 = sin límite)
LOG = []
LOG_LOCK = threading.Lock()

DATA = {}       # acción -> bytes JSON completos
ITEMS = {}      # acción -> lista de dicts (para filtrar por categoría)
PER_CAT = {}    # (acción, categoría) -> bytes


# --------------------------------------------------------------------------- datos

COUNTRIES = ["MX", "US", "ES", "AR", "CO", "CL", "PE", "LAT", "BR", "FR", "IT", "UK", "DE", "PT", "EC", "VE"]
LIVE_TOPICS = ["NOTICIAS", "DEPORTES", "CINE", "INFANTILES", "MÚSICA", "DOCUMENTALES", "ENTRETENIMIENTO",
               "LOCALES", "RELIGIOSOS", "24/7", "PPV", "EVENTOS", "CULTURA", "SERIES 24H", "ANIME"]
VOD_TOPICS = ["ACCIÓN", "COMEDIA", "DRAMA", "TERROR", "SUSPENSO", "ANIMADAS", "CIENCIA FICCIÓN", "ROMANCE",
              "AVENTURA", "DOCUMENTAL", "FAMILIAR", "MUSICALES", "BÉLICAS", "WESTERN", "CLÁSICAS", "ESTRENOS",
              "4K", "NAVIDEÑAS", "MARVEL", "DC", "SAGAS", "OSCAR"]
SERIES_TOPICS = ["DRAMA", "COMEDIA", "ANIME", "NOVELAS", "TURCAS", "COREANAS", "DOCUSERIES", "INFANTILES",
                 "POLICIACAS", "CIENCIA FICCIÓN", "REALITY", "NETFLIX", "HBO", "DISNEY+", "AMAZON"]
WORDS = ["Sombra", "Fuego", "Río", "Noche", "Corazón", "Ciudad", "Último", "Viaje", "Secreto", "Leyenda",
         "Tormenta", "Destino", "Guerra", "Amor", "Silencio", "Reino", "Hermanos", "Código", "Misión", "Sueño",
         "Pingüino", "Niño", "Canción", "Acción", "Dragón", "Espejo", "Lobo", "Estrella", "Isla", "Fantasma"]


def mixed_id(rnd, value):
    """Los paneles mandan los números a veces como texto y a veces como número."""
    r = rnd.random()
    if r < 0.5:
        return str(value)
    return value


def messy_rating(rnd):
    r = rnd.random()
    if r < 0.45:
        return f"{rnd.uniform(3, 9.5):.1f}"
    if r < 0.6:
        return round(rnd.uniform(3, 9.5), 1)
    if r < 0.7:
        return ""
    if r < 0.75:
        return "N/A"
    if r < 0.8:
        return f"{rnd.uniform(3, 9.5):.1f}".replace(".", ",")
    if r < 0.85:
        return None
    return str(rnd.randint(2, 9))


def messy_added(rnd, base):
    r = rnd.random()
    if r < 0.6:
        return str(base)
    if r < 0.85:
        return base
    if r < 0.92:
        return ""
    return None


def title(rnd, n):
    k = rnd.randint(1, 4)
    words = " ".join(rnd.choice(WORDS) for _ in range(k))
    year = rnd.randint(1970, 2026)
    tag = rnd.choice(["", " [LAT]", " (LAT)", " [SUB]", " 4K", " FHD", " HD", " [CAM]", " - DUAL", " (2026)", ""])
    name = f"{words} {n} ({year}){tag}"
    r = rnd.random()
    if r < 0.01:
        name = "🔥 " + name + " 🎬"
    elif r < 0.02:
        name = name + " — " + " ".join(rnd.choice(WORDS) for _ in range(25))  # nombre larguísimo
    elif r < 0.03:
        name = "  " + name.lower() + "  "
    elif r < 0.04:
        name = name.upper()
    return name


def icon_url(rnd, kind, sid):
    r = rnd.random()
    if r < 0.05:
        return ""
    if r < 0.08:
        return None
    if r < 0.10:
        return "http://imagen-rota.invalid/" + str(sid) + ".jpg"
    if r < 0.12:
        return f"{HOST}/img/{kind}/{sid}.jpg?w=2000"  # póster enorme
    return f"{HOST}/img/{kind}/{sid}.jpg"


def build(size):
    rnd = random.Random(20261113)
    if size == "small":
        n_live, n_live_cats, n_vod, n_vod_cats, n_ser, n_ser_cats = 60, 6, 80, 6, 30, 4
    elif size == "big":
        n_live, n_live_cats, n_vod, n_vod_cats, n_ser, n_ser_cats = 6000, 120, 25000, 300, 5000, 120
    else:
        n_live, n_live_cats, n_vod, n_vod_cats, n_ser, n_ser_cats = 22000, 450, 130000, 1400, 28000, 600

    def cats(n, topics, adult_name):
        out = []
        for i in range(n):
            c = COUNTRIES[i % len(COUNTRIES)]
            t = topics[(i // len(COUNTRIES)) % len(topics)]
            suffix = "" if i < len(COUNTRIES) * len(topics) else f" {i}"
            out.append({"category_id": str(i + 1), "category_name": f"{c} | {t}{suffix}", "parent_id": 0})
        out.append({"category_id": str(n + 1), "category_name": adult_name, "parent_id": 0})
        out.append({"category_id": str(n + 2), "category_name": "Categoría vacía", "parent_id": 0})
        return out

    live_cats = cats(n_live_cats, LIVE_TOPICS, "XXX | ADULTOS")
    vod_cats = cats(n_vod_cats, VOD_TOPICS, "+18 | ADULTOS")
    ser_cats = cats(n_ser_cats, SERIES_TOPICS, "SERIES +18")

    def cat_for(i, n):
        return str(i % n + 1)

    live = []
    for n in range(n_live):
        sid = 10000 + n
        cid = cat_for(n, n_live_cats)
        if n % 997 == 0:
            cid = str(n_live_cats + 1)  # adultos
        c = COUNTRIES[int(cid) % len(COUNTRIES)]
        q = rnd.choice(["HD", "FHD", "4K", "SD", "HEVC", ""])
        name = f"{c} | {rnd.choice(WORDS)} {rnd.choice(LIVE_TOPICS).title()} {n // 4} {q}".strip()
        if n % 500 == 0:
            name = "⚽ " + name + " ⚽"
        entry = {
            "num": mixed_id(rnd, n + 1), "name": name, "stream_type": "live", "stream_id": mixed_id(rnd, sid),
            "stream_icon": icon_url(rnd, "l", sid), "epg_channel_id": f"ch{sid}.mx" if n % 3 else None,
            "added": messy_added(rnd, 1600000000 + n), "is_adult": "0", "category_id": cid,
            "category_ids": [int(cid)], "custom_sid": "", "tv_archive": 1 if n % 40 == 0 else 0,
            "direct_source": "", "tv_archive_duration": 3 if n % 40 == 0 else 0,
        }
        if n % 1013 == 0:
            entry["category_id"] = None
        live.append(entry)
        if n % 50 == 0:  # repetido en otra categoría (pasa en paneles reales)
            dup = dict(entry)
            dup["category_id"] = cat_for(n + 7, n_live_cats)
            live.append(dup)
    live.append({"name": "Elemento sin id", "stream_type": "live", "category_id": "1"})

    vod = []
    for n in range(n_vod):
        sid = 100000 + n
        cid = cat_for(n * 7919, n_vod_cats)
        if n % 1499 == 0:
            cid = str(n_vod_cats + 1)
        entry = {
            "num": n + 1, "name": title(rnd, n), "stream_type": "movie", "stream_id": mixed_id(rnd, sid),
            "stream_icon": icon_url(rnd, "m", sid), "rating": messy_rating(rnd),
            "rating_5based": round(rnd.uniform(1, 5), 1), "added": messy_added(rnd, 1500000000 + n * 600),
            "is_adult": "0", "category_id": cid, "category_ids": [int(cid)],
            "container_extension": rnd.choice(["mp4", "mkv", "mp4", "avi", "mp4"]), "custom_sid": None,
            "direct_source": "",
        }
        if n % 2003 == 0:
            entry["category_id"] = ""
        vod.append(entry)
        if n % 33 == 0:
            dup = dict(entry)
            dup["category_id"] = cat_for(n * 7919 + 3, n_vod_cats)
            vod.append(dup)
    vod.append({"name": "Película sin id", "stream_type": "movie", "category_id": "1"})

    series = []
    for n in range(n_ser):
        sid = 300000 + n
        cid = cat_for(n * 7907, n_ser_cats)
        if n % 1999 == 0:
            cid = str(n_ser_cats + 1)
        entry = {
            "num": n + 1, "name": title(rnd, n).replace("(", "- ").replace(")", ""), "series_id": mixed_id(rnd, sid),
            "cover": icon_url(rnd, "s", sid), "plot": "Una serie con una sinopsis de varias líneas. " * rnd.randint(1, 4),
            "cast": "Ana Gómez, Luis Pérez, Marta Ruiz, Juan Díaz", "director": "Carlos Prueba",
            "genre": rnd.choice(["Drama", "Comedia", "Acción, Aventura", ""]), "releaseDate": f"{rnd.randint(1990, 2026)}-01-10",
            "last_modified": messy_added(rnd, 1600000000 + n * 300), "rating": messy_rating(rnd),
            "rating_5based": 3.5, "backdrop_path": [f"{HOST}/img/b/{sid}.jpg"] if n % 2 else [],
            "youtube_trailer": "", "episode_run_time": "45", "category_id": cid, "category_ids": [int(cid)],
        }
        series.append(entry)
        if n % 40 == 0:
            dup = dict(entry)
            dup["category_id"] = cat_for(n * 7907 + 1, n_ser_cats)
            series.append(dup)

    ITEMS["get_live_streams"] = live
    ITEMS["get_vod_streams"] = vod
    ITEMS["get_series"] = series
    DATA["get_live_categories"] = json.dumps(live_cats).encode()
    DATA["get_vod_categories"] = json.dumps(vod_cats).encode()
    DATA["get_series_categories"] = json.dumps(ser_cats).encode()
    for k in ("get_live_streams", "get_vod_streams", "get_series"):
        DATA[k] = json.dumps(ITEMS[k], ensure_ascii=False).encode("utf-8")
    print(f"catálogo {size}: {len(live)} canales ({len(DATA['get_live_streams']) // 1024} KB), "
          f"{len(vod)} películas ({len(DATA['get_vod_streams']) // 1024} KB), "
          f"{len(series)} series ({len(DATA['get_series']) // 1024} KB)", flush=True)


def b64(text):
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


def epg_listings(stream_id, count):
    now = int(time.time())
    base = now - (now % 1800) - 3600
    names = ["Noticiero", "Película de la noche", "Deportes en vivo", "Documental", "Serie estelar", "Magacín"]
    out = []
    seed = int(hashlib.md5(stream_id.encode()).hexdigest()[:6], 16)
    for i in range(count):
        start = base + i * 1800
        t = f"{names[(i + seed) % len(names)]} {i + 1}"
        out.append({
            "id": str(i), "epg_id": "1", "title": b64(t), "lang": "es",
            "start": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(start)),
            "end": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(start + 1800)),
            "description": b64("Descripción de " + t), "channel_id": "x",
            "start_timestamp": str(start), "stop_timestamp": str(start + 1800), "has_archive": 0,
        })
    return out


def num(value, default=0):
    try:
        return int(str(value))
    except (TypeError, ValueError):
        return default


# ------------------------------------------------------------------------- servidor

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        line = f"{time.strftime('%H:%M:%S')} {self.command} {self.path[:160]}"
        with LOG_LOCK:
            LOG.append(line)
            if len(LOG) > 20000:
                del LOG[:5000]

    def send_bytes(self, data, ctype, code=200, throttle=False):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if self.command == "HEAD":
            return
        try:
            if throttle and RATE > 0:
                chunk = 64 * 1024
                started = time.time()
                for i in range(0, len(data), chunk):
                    self.wfile.write(data[i:i + chunk])
                    should = (i + chunk) / RATE
                    ahead = should - (time.time() - started)
                    if ahead > 0:
                        time.sleep(ahead)
            else:
                self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def send_json(self, obj, code=200):
        self.send_bytes(json.dumps(obj, ensure_ascii=False).encode("utf-8"), "application/json", code)

    def send_file(self, path, ctype):
        if not os.path.isfile(path):
            return self.send_bytes(b"not found", "text/plain", 404)
        size = os.path.getsize(path)
        start, end, code = 0, size - 1, 200
        rng = self.headers.get("Range")
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng)
            if m:
                if m.group(1):
                    start = int(m.group(1))
                if m.group(2):
                    end = min(int(m.group(2)), size - 1)
                if not m.group(1) and m.group(2):
                    start, end = max(0, size - int(m.group(2))), size - 1
                code = 206
        length = end - start + 1
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if code == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if self.command == "HEAD":
            return
        try:
            with open(path, "rb") as f:
                f.seek(start)
                left = length
                while left > 0:
                    chunk = f.read(min(65536, left))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    left -= len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        path = url.path
        if path == "/player_api.php":
            return self.api(q)
        if path.startswith("/live/"):
            return self.live(path)
        if path.startswith("/movie/") or path.startswith("/series/"):
            return self.send_file(os.path.join(MEDIA, "test.mp4"), "video/mp4")
        if path.startswith("/img/"):
            return self.image(path, q)
        if path == "/__log":
            with LOG_LOCK:
                text = "\n".join(LOG)
            return self.send_bytes(text.encode("utf-8"), "text/plain")
        self.send_bytes(b"not found", "text/plain", 404)

    def image(self, path, q):
        parts = path.strip("/").split("/")  # img, tipo, id.jpg
        kind = parts[1] if len(parts) > 2 else "m"
        ident = num(parts[-1].split(".")[0], 0)
        time.sleep(0.02 + (ident % 7) * 0.01)  # algo de latencia de red
        if ident % 97 == 0:
            return self.send_bytes(b"not found", "text/plain", 404)
        if kind == "l":
            name = "logo.png" if ident % 2 else "logo_wide.png"
            return self.send_file(os.path.join(MEDIA, name), "image/png")
        if kind == "b":
            return self.send_file(os.path.join(MEDIA, "backdrop.jpg"), "image/jpeg")
        if q.get("w") == "2000":
            return self.send_file(os.path.join(MEDIA, "poster_l.jpg"), "image/jpeg")
        name = ("poster_s.jpg", "poster_m.jpg", "poster_s2.jpg")[ident % 3]
        return self.send_file(os.path.join(MEDIA, name), "image/jpeg")

    def api(self, q):
        if q.get("username") != USER or q.get("password") != PASS:
            return self.send_json({"user_info": {"auth": 0}})
        action = q.get("action")
        if action is None:
            return self.send_json({
                "user_info": {
                    "username": USER, "password": PASS, "message": "Bienvenido a la prueba", "auth": 1,
                    "status": "Active", "exp_date": str(int(time.time()) + 90 * 86400), "is_trial": "0",
                    "active_cons": "0", "created_at": "1600000000", "max_connections": "2",
                    "allowed_output_formats": ["m3u8", "ts", "rtmp"],
                },
                "server_info": {"url": "10.0.2.2", "port": "8880", "https_port": "443", "server_protocol": "http",
                                "rtmp_port": "8000", "timezone": "America/Mexico_City",
                                "timestamp_now": int(time.time()), "time_now": time.strftime("%Y-%m-%d %H:%M:%S")},
            })
        if action in ("get_live_categories", "get_vod_categories", "get_series_categories"):
            return self.send_bytes(DATA[action], "application/json")
        if action in ("get_live_streams", "get_vod_streams", "get_series"):
            cat = q.get("category_id")
            if cat:
                key = (action, cat)
                if key not in PER_CAT:
                    PER_CAT[key] = json.dumps([e for e in ITEMS[action] if str(e.get("category_id")) == cat],
                                              ensure_ascii=False).encode("utf-8")
                return self.send_bytes(PER_CAT[key], "application/json", throttle=True)
            return self.send_bytes(DATA[action], "application/json", throttle=True)
        if action == "get_short_epg":
            sid = q.get("stream_id", "0")
            listings = [] if num(sid) % 3 == 0 else epg_listings(sid, num(q.get("limit"), 4) or 4)
            return self.send_json({"epg_listings": listings})
        if action == "get_simple_data_table":
            sid = q.get("stream_id", "0")
            return self.send_json({"epg_listings": [] if num(sid) % 3 == 0 else epg_listings(sid, 48)})
        if action == "get_vod_info":
            vid = num(q.get("vod_id"), 501)
            if vid % 7 == 0:
                return self.send_json({"info": [], "movie_data": {"stream_id": vid, "name": "Sin ficha", "container_extension": "mp4"}})
            return self.send_json({
                "info": {
                    "tmdb_id": "603", "name": f"Película {vid}", "o_name": "The Test Movie",
                    "movie_image": f"{HOST}/img/m/{vid}.jpg", "cover_big": f"{HOST}/img/m/{vid}.jpg",
                    "releasedate": "2021-05-20",
                    "plot": "Una película de prueba con una sinopsis larga para comprobar cómo se ve el texto en la pantalla "
                            "de detalle, con varias líneas y acentos: canción, niño, pingüino. " * (1 + vid % 3),
                    "cast": "Ana Gómez, Luis Pérez, Marta Ruiz", "director": "Carlos Prueba", "genre": "Acción, Aventura",
                    "duration": "01:30:00", "duration_secs": 5400, "rating": "7.5",
                    "backdrop_path": "" if vid % 5 == 0 else [f"{HOST}/img/b/{vid}.jpg"],
                    "youtube_trailer": "dQw4w9WgXcQ" if vid % 2 else "",
                },
                "movie_data": {"stream_id": vid, "name": "x", "container_extension": "mp4", "added": "1600000000"},
            })
        if action == "get_series_info":
            sid = num(q.get("series_id"), 701)
            info = {"name": f"Serie {sid}", "cover": f"{HOST}/img/s/{sid}.jpg",
                    "plot": "Una serie de prueba con varias temporadas y una sinopsis de varias líneas.",
                    "cast": "Reparto de prueba", "director": "Director Prueba", "genre": "Drama", "releaseDate": "2020-01-10",
                    "backdrop_path": [f"{HOST}/img/b/{sid}.jpg"], "youtube_trailer": "", "tmdb": "1396"}
            if sid % 9 == 0:
                return self.send_json({"seasons": [], "info": info, "episodes": {}})
            seasons = {}
            for season in (1, 2, 3):
                seasons[str(season)] = [
                    {"id": str(sid * 100 + season * 10 + e), "episode_num": e if e % 4 else str(e),
                     "title": f"Episodio {e} de la temporada {season}", "container_extension": "mkv" if e % 2 else "mp4",
                     "season": season, "added": "1600000000",
                     "info": {"plot": f"Resumen del episodio {e}.", "movie_image": f"{HOST}/img/b/{sid}{e}.jpg",
                              "duration_secs": 2700, "duration": "00:45:00"}}
                    for e in range(1, 11)
                ]
            episodes = list(seasons.values()) if sid % 4 == 0 else seasons  # algunos paneles mandan lista de listas
            return self.send_json({
                "seasons": [{"season_number": s, "name": f"Temporada {s}", "episode_count": 10} for s in (1, 2, 3)],
                "info": info, "episodes": episodes,
            })
        return self.send_json([])

    def live(self, path):
        name = os.path.basename(path)
        if name.endswith(".m3u8"):
            return self.send_file(os.path.join(MEDIA, "live.m3u8"), "application/vnd.apple.mpegurl")
        if name.endswith(".ts"):
            seg = os.path.join(MEDIA, name)
            if not os.path.isfile(seg):
                seg = os.path.join(MEDIA, "seg0.ts")
            return self.send_file(seg, "video/mp2t")
        self.send_bytes(b"not found", "text/plain", 404)


def main():
    global MEDIA, RATE, HOST
    ap = argparse.ArgumentParser()
    ap.add_argument("--media", default=".")
    ap.add_argument("--port", type=int, default=8880)
    ap.add_argument("--size", default="huge", choices=["small", "big", "huge"])
    ap.add_argument("--rate", type=int, default=0, help="bytes por segundo de las listas (0 = sin límite)")
    ap.add_argument("--host", default=HOST)
    args = ap.parse_args()
    MEDIA, RATE, HOST = args.media, args.rate, args.host
    build(args.size)
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    server.daemon_threads = True
    print("mock listo en puerto", args.port, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
