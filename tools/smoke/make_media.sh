#!/usr/bin/env bash
# Crea el video, las imágenes y los subtítulos de prueba con ffmpeg.
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
  -f lavfi -i "testsrc2=duration=90:size=640x360:rate=24" \
  -f lavfi -i "sine=frequency=440:duration=90" \
  -i embedded.srt \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac -c:s mov_text \
  -metadata:s:s:0 language=spa -movflags +faststart -shortest test.mp4

ffmpeg -y -loglevel error \
  -f lavfi -i "testsrc2=duration=60:size=640x360:rate=24" \
  -f lavfi -i "sine=frequency=330:duration=60" \
  -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac \
  -f hls -hls_time 4 -hls_list_size 0 -hls_segment_filename "seg%d.ts" live.m3u8

ffmpeg -y -loglevel error -f lavfi -i "testsrc2=size=300x450" -frames:v 1 poster.jpg
ffmpeg -y -loglevel error -f lavfi -i "mandelbrot=size=1280x720" -frames:v 1 backdrop.jpg
ls -la
