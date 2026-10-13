#!/usr/bin/env bash
# Corre dentro del emulador: instala el APK de depuración y recorre la app.
set -u
APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="${SMOKE_OUT:-smoke-out}"
mkdir -p "$OUT"
adb wait-for-device
adb shell input keyevent 82 || true
adb install -r -g "$APK" > "$OUT/install.txt" 2>&1 || { cat "$OUT/install.txt"; exit 1; }
cat "$OUT/install.txt"
python3 tools/smoke/smoke.py "$OUT" 2>&1 | tee "$OUT/smoke-console.txt"
exit 0
