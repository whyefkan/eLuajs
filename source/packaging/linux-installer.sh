#!/bin/sh
set -e
DEST="$HOME/.local/share/eLuajs"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
LINE="$(awk '/^__ARCHIVE_BELOW__$/{print NR + 1; exit}' "$0")"
if [ -z "$LINE" ]; then
  echo "Invalid eLuajs installer." >&2
  exit 1
fi
tail -n +"$LINE" "$0" | tar -xz -C "$TMP"
rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
mv "$TMP/eLuajs" "$DEST"
chmod +x "$DEST/eLuajs"
mkdir -p "$HOME/.local/share/applications"
DESKTOP="$HOME/.local/share/applications/eLuajs.desktop"
cat > "$DESKTOP" <<ENTRY
[Desktop Entry]
Type=Application
Name=eLuajs
Comment=Lightweight Android Java IDE
Exec=$DEST/eLuajs
Path=$DEST
Icon=$DEST/eLuajs-icon.png
Terminal=false
Categories=Development;IDE;
StartupNotify=true
ENTRY
chmod +x "$DESKTOP"
if [ -d "$HOME/Desktop" ]; then
  cp "$DESKTOP" "$HOME/Desktop/eLuajs.desktop"
  chmod +x "$HOME/Desktop/eLuajs.desktop"
  if command -v gio >/dev/null 2>&1; then
    gio set "$HOME/Desktop/eLuajs.desktop" metadata::trusted true 2>/dev/null || true
  fi
fi
printf 'eLuajs installed to %s\n' "$DEST"
printf 'Launch it from the desktop shortcut or the application menu.\n'
"$DEST/eLuajs" >/dev/null 2>&1 &
exit 0
__ARCHIVE_BELOW__
