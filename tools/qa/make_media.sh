#!/usr/bin/env bash
# Video, pósters y logos de prueba para el servidor de mentira (se crean con ffmpeg).
set -euo pipefail
OUT="${1:-media}"
mkdir -p "$OUT"
cd "$OUT"

cat > embedded.srt <<'SRT'
1
00:00:01,000 --> 00:00:40,000
SUBTITULO INCLUIDO EN EL VIDEO (español)

2
00:00:41,000 --> 00:01:30,000
Segunda línea incluida
SRT

ffmpeg -y -loglevel error \
  -f lavfi -i "testsrc2=duration=90:size=1280x720:rate=24" \
  -f lavfi -i "sine=frequency=440:duration=90" \
  -i embedded.srt \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -c:s mov_text \
  -metadata:s:s:0 language=spa -movflags +faststart -shortest test.mp4

ffmpeg -y -loglevel error \
  -f lavfi -i "testsrc2=duration=120:size=1280x720:rate=25" \
  -f lavfi -i "sine=frequency=330:duration=120" \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac \
  -f hls -hls_time 4 -hls_list_size 0 -hls_segment_filename "seg%d.ts" live.m3u8

# Pósters de varios tamaños (uno enorme, como los que mandan algunos paneles) y logos con transparencia.
ffmpeg -y -loglevel error -f lavfi -i "testsrc2=size=300x450" -frames:v 1 poster_s.jpg
ffmpeg -y -loglevel error -f lavfi -i "smptehdbars=size=300x450" -frames:v 1 poster_s2.jpg
ffmpeg -y -loglevel error -f lavfi -i "mandelbrot=size=600x900" -frames:v 1 poster_m.jpg
ffmpeg -y -loglevel error -f lavfi -i "testsrc2=size=2000x3000" -frames:v 1 -q:v 2 poster_l.jpg
ffmpeg -y -loglevel error -f lavfi -i "mandelbrot=size=1280x720" -frames:v 1 backdrop.jpg
ffmpeg -y -loglevel error -f lavfi -i "testsrc=size=400x400" -frames:v 1 logo.png
ffmpeg -y -loglevel error -f lavfi -i "smptebars=size=800x300" -frames:v 1 logo_wide.png
ls -la
