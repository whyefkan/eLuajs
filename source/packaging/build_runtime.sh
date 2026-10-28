#!/usr/bin/env bash
set -euo pipefail

# Rebuild the portable eLuajs Java runtime from a full JDK 21+.
# jdk.crypto.ec is intentionally explicit: removing it breaks TLS/ECDHE for
# Gradle, Google Maven and Maven Central on modern HTTPS endpoints.

OUT="${1:-runtime}"
MODULES="java.se,java.net.http,jdk.compiler,jdk.jlink,jdk.jdeps,jdk.zipfs,jdk.crypto.ec"
EXE=""
case "$(uname -s 2>/dev/null || echo)" in
  MINGW*|MSYS*|CYGWIN*) EXE=".exe" ;;
esac

if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME/jmods" ]]; then
  JDK="$JAVA_HOME"
else
  JAVAC_BIN="$(command -v javac || true)"
  if [[ -z "$JAVAC_BIN" ]]; then
    echo "javac bulunamadı. Tam JDK 21+ ile çalıştırın." >&2
    exit 1
  fi
  JDK="$(cd "$(dirname "$(readlink -f "$JAVAC_BIN")")/.." && pwd)"
fi

if [[ ! -f "$JDK/bin/jlink$EXE" || ! -d "$JDK/jmods" ]]; then
  echo "Tam JDK bulunamadı: $JDK (bin/jlink + jmods gerekli)." >&2
  exit 1
fi

rm -rf "$OUT"
"$JDK/bin/jlink$EXE" \
  --module-path "$JDK/jmods" \
  --add-modules "$MODULES" \
  --bind-services \
  --strip-debug \
  --no-header-files \
  --no-man-pages \
  --compress=zip-6 \
  --output "$OUT"

echo "Runtime hazır: $OUT"
"$OUT/bin/java$EXE" --list-modules | grep -q '^jdk.crypto.ec@' || {
  echo "HATA: jdk.crypto.ec runtime içine girmedi." >&2
  exit 1
}
