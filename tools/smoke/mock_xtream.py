#!/usr/bin/env python3
"""Servidor de mentira con la API de Xtream Codes (y un OpenSubtitles falso) para la prueba automática.

Sirve canales, películas y series de ejemplo, una guía (EPG) con canales sin información a propósito,
video de prueba (mp4 con Range y HLS) e imágenes. No necesita internet ni cuentas.
Uso: mock_xtream.py --media DIR [--port 8880]
"""
import argparse
import base64
import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST = "http://10.0.2.2:8880"
USER = "demo"
PASS = "demo"
API_KEY = "TESTKEY"
MEDIA = "."
LOG = []

LIVE_CATS = [("1", "MX | Noticias"), ("2", "MX | Deportes"), ("3", "XXX Adultos")]
LIVE = [
    # id, nombre, categoría, ¿tiene guía propia?
    ("101", "MX | Canal Uno HD", "1", True),
    ("102", "MX | Canal Uno FHD", "1", False),   # sin guía: debe tomar la de "Canal Uno HD"
    ("103", "MX | Canal Uno 4K", "1", False),
    ("201", "MX | Deportes 1 HD", "2", True),
    ("202", "MX | Deportes 2", "2", False),       # sin guía y sin hermano: bloque "En vivo"
    ("301", "XXX Canal Adulto", "3", False),
]
VOD_CATS = [("1", "Acción"), ("2", "Comedia"), ("3", "+18 Adultos")]
MOVIES = [
    ("501", "ES - La Película de Prueba (2021) FHD", "1", "7.5"),
    ("502", "Otra Película Divertida (2019)", "2", "6.1"),
    ("503", "Acción Total (2020) [4K]", "1", "8.0"),
    ("504", "Comedia Familiar (2018)", "2", "5.5"),
    ("505", "Película Para Adultos (2022)", "3", "4.0"),
]
SERIES_CATS = [("1", "Drama"), ("2", "Aventura")]
SERIES = [("701", "La Serie de Prueba", "1", "8.2"), ("702", "Aventuras Sin Fin", "2", "7.0")]


BIG = False


def build_big():
    """Catálogos del tamaño de una lista real de IPTV (miles de canales, decenas de miles de películas)."""
    global LIVE_CATS, LIVE, VOD_CATS, MOVIES, SERIES_CATS, SERIES
    countries = ["MX", "US", "ES", "AR", "CO", "CL", "PE", "BR", "FR", "IT"]
    LIVE_CATS = [(str(i + 1), f"{countries[i % 10]} | Categoría {i + 1}") for i in range(60)]
    LIVE_CATS.append(("61", "XXX Adultos"))
    LIVE = []
    for n in range(6000):
        cat = str(n % 60 + 1)
        base = f"{countries[(n % 60) % 10]} | Canal {n // 3} "
        quality = ("HD", "FHD", "4K")[n % 3]
        LIVE.append((str(10000 + n), base + quality, cat, n % 4 != 0))
    LIVE.append(("19999", "XXX Canal Adulto", "61", False))
    VOD_CATS = [(str(i + 1), f"Género {i + 1}") for i in range(40)] + [("41", "+18 Adultos")]
    MOVIES = []
    for n in range(20000):
        year = 1980 + n % 45
        MOVIES.append((str(100000 + n), f"ES - Película número {n} ({year}) FHD", str(n % 40 + 1), f"{(n % 90) / 10:.1f}"))
    SERIES_CATS = [(str(i + 1), f"Serie género {i + 1}") for i in range(30)]
    SERIES = [(str(300000 + n), f"Serie número {n}", str(n % 30 + 1), f"{(n % 90) / 10:.1f}") for n in range(3000)]


def b64(text):
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


def epg_listings(stream_id, table):
    now = int(time.time())
    base = now - (now % 1800) - 3600  # hace una hora, alineado a la media hora
    out = []
    count = 48 if table else 4
    names = ["Noticiero", "Película de la noche", "Deportes en vivo", "Documental", "Serie estelar", "Magacín"]
    for i in range(count):
        start = base + i * 1800
        title = f"{names[(i + int(stream_id)) % len(names)]} {i + 1}"
        out.append({
            "id": str(i), "epg_id": "1", "title": b64(title), "lang": "es",
            "start": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(start)),
            "end": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(start + 1800)),
            "description": b64("Descripción de " + title),
            "channel_id": "x", "start_timestamp": str(start), "stop_timestamp": str(start + 1800),
            "has_archive": 0,
        })
    return out


def has_epg(stream_id):
    return any(c[0] == stream_id and c[3] for c in LIVE)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        LOG.append(self.requestline)
        sys.stderr.write("%s %s\n" % (self.command, self.path))

    # ---------------------------------------------------------------- utilidades
    def send_json(self, obj, code=200):
        data = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_bytes(self, data, ctype, code=200):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def send_file(self, path, ctype):
        if not os.path.isfile(path):
            self.send_bytes(b"not found", "text/plain", 404)
            return
        size = os.path.getsize(path)
        start, end = 0, size - 1
        rng = self.headers.get("Range")
        code = 200
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng)
            if m:
                if m.group(1):
                    start = int(m.group(1))
                if m.group(2):
                    end = min(int(m.group(2)), size - 1)
                if not m.group(1) and m.group(2):
                    start = max(0, size - int(m.group(2)))
                    end = size - 1
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

    def body_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(n) if n else b"{}"
        try:
            return json.loads(raw or b"{}")
        except Exception:
            return {}

    # --------------------------------------------------------------------- rutas
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
            return self.send_file(os.path.join(MEDIA, os.path.basename(path)), "image/jpeg")
        if path.startswith("/os/api/v1/subtitles"):
            return self.os_search(q)
        if path.startswith("/os/file/"):
            return self.os_file(path)
        if path == "/__log":
            return self.send_bytes("\n".join(LOG).encode("utf-8"), "text/plain")
        self.send_bytes(b"not found", "text/plain", 404)

    def do_POST(self):
        path = urlparse(self.path).path
        LOG.append("BODY " + path)
        if path == "/os/api/v1/login":
            return self.send_json({"token": "TOKEN123", "status": 200})
        if path == "/os/api/v1/download":
            if self.headers.get("Api-Key") != API_KEY:
                return self.send_json({"message": "bad key"}, 403)
            body = self.body_json()
            LOG.append("DOWNLOAD " + json.dumps(body))
            return self.send_json({
                "link": f"{HOST}/os/file/{body.get('file_id', 0)}.srt",
                "file_name": "x.srt", "requests": 1, "remaining": 4,
            })
        self.send_bytes(b"not found", "text/plain", 404)

    # ----------------------------------------------------------------- Xtream API
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
                    "allowed_output_formats": ["m3u8", "ts"],
                },
                "server_info": {"url": "10.0.2.2", "port": "8880", "timezone": "UTC"},
            })
        if action == "get_live_categories":
            return self.send_json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in LIVE_CATS])
        if action == "get_live_streams":
            return self.send_json([
                {"num": n + 1, "name": name, "stream_type": "live", "stream_id": int(sid), "stream_icon": f"{HOST}/img/poster.jpg",
                 "epg_channel_id": "x" if epg else None, "added": "1600000000", "category_id": cat,
                 "tv_archive": 0, "tv_archive_duration": 0}
                for n, (sid, name, cat, epg) in enumerate(LIVE)
            ])
        if action == "get_short_epg":
            sid = q.get("stream_id", "")
            return self.send_json({"epg_listings": epg_listings(sid, False) if has_epg(sid) else []})
        if action == "get_simple_data_table":
            sid = q.get("stream_id", "")
            return self.send_json({"epg_listings": epg_listings(sid, True) if has_epg(sid) else []})
        if action == "get_vod_categories":
            return self.send_json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in VOD_CATS])
        if action == "get_vod_streams":
            return self.send_json([
                {"num": n + 1, "name": name, "stream_type": "movie", "stream_id": int(sid), "stream_icon": f"{HOST}/img/poster.jpg",
                 "rating": rating, "added": str(1600000000 + n), "category_id": cat, "container_extension": "mp4"}
                for n, (sid, name, cat, rating) in enumerate(MOVIES)
            ])
        if action == "get_vod_info":
            return self.send_json({
                "info": {
                    "tmdb_id": "603", "name": "La Película de Prueba", "o_name": "The Test Movie",
                    "movie_image": f"{HOST}/img/poster.jpg", "releasedate": "2021-05-20",
                    "plot": "Una película de prueba con una sinopsis larga para comprobar cómo se ve el texto en la pantalla de detalle, con varias líneas y acentos: canción, niño, pingüino.",
                    "cast": "Ana Gómez, Luis Pérez, Marta Ruiz", "director": "Carlos Prueba", "genre": "Acción, Aventura",
                    "duration": "01:30:00", "rating": "7.5",
                    "backdrop_path": [f"{HOST}/img/backdrop.jpg"], "youtube_trailer": "dQw4w9WgXcQ",
                },
                "movie_data": {"stream_id": 501, "name": "x", "container_extension": "mp4"},
            })
        if action == "get_series_categories":
            return self.send_json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in SERIES_CATS])
        if action == "get_series":
            return self.send_json([
                {"num": n + 1, "name": name, "series_id": int(sid), "cover": f"{HOST}/img/poster.jpg", "plot": "Una serie de prueba.",
                 "rating": rating, "category_id": cat, "last_modified": "1600000000"}
                for n, (sid, name, cat, rating) in enumerate(SERIES)
            ])
        if action == "get_series_info":
            eps = {}
            for season in (1, 2):
                eps[str(season)] = [
                    {"id": str(9000 + season * 10 + e), "episode_num": e, "title": f"Episodio {e} de la temporada {season}",
                     "container_extension": "mp4", "season": season,
                     "info": {"plot": f"Resumen del episodio {e}.", "movie_image": f"{HOST}/img/backdrop.jpg", "duration_secs": 90}}
                    for e in (1, 2, 3)
                ]
            return self.send_json({
                "info": {"name": "La Serie de Prueba", "cover": f"{HOST}/img/poster.jpg", "plot": "Una serie de prueba con varias temporadas.",
                         "cast": "Reparto de prueba", "director": "Director Prueba", "genre": "Drama", "releaseDate": "2020-01-10",
                         "backdrop_path": [f"{HOST}/img/backdrop.jpg"], "youtube_trailer": "dQw4w9WgXcQ", "tmdb": "1396"},
                "episodes": eps,
            })
        return self.send_json([])

    # ----------------------------------------------------------------------- vivo
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

    # ------------------------------------------------------ OpenSubtitles de mentira
    def os_search(self, q):
        LOG.append("OSSEARCH " + json.dumps(q, sort_keys=False) + " keys=" + ",".join(q.keys()))
        if self.headers.get("Api-Key") != API_KEY:
            return self.send_json({"message": "bad key"}, 403)
        lang = q.get("languages", "es")
        return self.send_json({
            "total_count": 1,
            "data": [{"id": "1", "type": "subtitle", "attributes": {
                "subtitle_id": "1", "language": lang, "download_count": 1000, "ratings": 8.0, "from_trusted": False,
                "ai_translated": False, "machine_translated": False, "release": "Test.Release",
                "files": [{"file_id": 4200 if lang == "es" else 4201, "file_name": "x.srt"}],
            }}],
        })

    def os_file(self, path):
        fid = os.path.basename(path).split(".")[0]
        if fid == "4200":
            text = "1\n00:00:01,000 --> 00:00:30,000\nSUBTITULO EXTERNO EN ESPAÑOL: canción, niño, pingüino\n\n2\n00:00:31,000 --> 00:01:30,000\nSegunda línea del subtítulo externo\n"
            return self.send_bytes(text.encode("windows-1252"), "application/x-subrip")
        text = "1\n00:00:01,000 --> 00:00:30,000\nEXTERNAL SUBTITLE IN ENGLISH\n\n2\n00:00:31,000 --> 00:01:30,000\nSecond external line\n"
        return self.send_bytes(text.encode("utf-8"), "application/x-subrip")


def main():
    global MEDIA
    ap = argparse.ArgumentParser()
    ap.add_argument("--media", default=".")
    ap.add_argument("--port", type=int, default=8880)
    ap.add_argument("--big", action="store_true", help="catálogos grandes (6 mil canales, 20 mil películas, 3 mil series)")
    args = ap.parse_args()
    MEDIA = args.media
    if args.big:
        build_big()
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print("mock listo en puerto", args.port, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
