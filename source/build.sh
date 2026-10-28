#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/out"
CLASSES="$OUT/classes"
VERSION="${VERSION:-1.4.3}"

JAVAC="${JAVAC:-}"
if [[ -z "$JAVAC" ]]; then
  if command -v javac >/dev/null 2>&1; then
    JAVAC="javac"
  elif [[ -x "$ROOT/../runtime/bin/javac" ]]; then
    JAVAC="$ROOT/../runtime/bin/javac"
  else
    echo "javac bulunamadı. JDK 17+ kurun veya paket içindeki runtime ile çalıştırın." >&2
    exit 1
  fi
fi

rm -rf "$OUT"
mkdir -p "$CLASSES"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 "$JAVAC" --release 17 -encoding UTF-8 -d "$CLASSES"

cat > "$OUT/MANIFEST.MF" <<MANIFEST
Manifest-Version: 1.0
Main-Class: eluajs.Main
Implementation-Title: eLuajs
Implementation-Version: $VERSION
MANIFEST

if command -v jar >/dev/null 2>&1; then
  jar cfm "$OUT/eLuajs.jar" "$OUT/MANIFEST.MF" -C "$CLASSES" .
elif command -v python3 >/dev/null 2>&1; then
  python3 - "$CLASSES" "$OUT/eLuajs.jar" "$OUT/MANIFEST.MF" <<'PY'
import os, sys, zipfile
classes, target, manifest_path = sys.argv[1], sys.argv[2], sys.argv[3]
with open(manifest_path, "r", encoding="utf-8") as f:
    manifest = f.read().replace("\r\n", "\n").rstrip("\n").replace("\n", "\r\n") + "\r\n\r\n"
with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("META-INF/MANIFEST.MF", manifest)
    for base, dirs, files in os.walk(classes):
        for name in sorted(files):
            path = os.path.join(base, name)
            z.write(path, os.path.relpath(path, classes))
PY
else
  echo "JAR oluşturmak için 'jar' veya 'python3' gerekli." >&2
  exit 1
fi

echo "Built $OUT/eLuajs.jar"
