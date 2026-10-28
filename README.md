<p align="center">
  <img src="eLuajs-icon.png" width="96" alt="eLuajs icon">
</p>

<h1 align="center">eLuajs</h1>

<p align="center">
  <b>A lightweight Android Java IDE for Linux and Windows — built for old computers.</b><br>
  Create real Android Gradle projects, write Java + XML, validate, and export a debug APK or a signed Play Store AAB.
</p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-1.4.3-37d6a4">
  <img alt="platform" src="https://img.shields.io/badge/platform-Linux-2ea043">
  <img alt="platform" src="https://img.shields.io/badge/platform-Windows-0078d4">
  <img alt="java" src="https://img.shields.io/badge/Java-17%2B-007396">
  <img alt="license" src="https://img.shields.io/badge/license-Apache--2.0-blue">
</p>

---

## Features

- **Lightweight by design** — no Electron, no bundled browser; a compact Java/Swing IDE that stays fast on old machines on Linux and Windows.
- **Real Android projects** — Java + AndroidX, Gradle, `AndroidManifest.xml`, resources and standard project structure.
- **VS Code-style shell** — activity bar, explorer sidebar, editor tabs, output panel, custom window controls and a dark theme. The Build/Output and signing windows use the same custom chrome.
- **Editor built for coding** — syntax highlighting, line numbers, `Ctrl+Space` completion, `Ctrl+Shift+F` project search, automatic imports.
- **One-click output** — validate the project and build a debug APK or a signed Play Store **AAB** with an upload keystore.
- **AI coding assistant** — docked chat panel, on-demand project reading, Markdown/code rendering, token usage and approximate cost.
- **Providers** — OpenAI, Claude (Anthropic), Google Gemini, Groq, DeepSeek, OpenRouter, River AI and any OpenAI-compatible endpoint.
- **English by default** — Turkish is available in Settings.
- **AI agent editing** — the assistant reads, creates, modifies and deletes project files directly; the open editor reloads automatically and edits can be undone.
- **Persistent AI chats** — conversations are saved per project, restored when you reopen the panel, and previous chats are available from the history button.
- **Clean chat composer** — press **Enter** to send and **Shift+Enter** for a new line. The AI reports what changed instead of dumping code into the chat.
- **Auto-update** — checks the official GitHub repository on startup and can replace `app/eLuajs.jar` and restart.

## Screenshots

| Hub | Editor |
| --- | --- |
| ![Hub](docs/hub-v13.png) | ![Editor](docs/editor-v13.png) |

<p align="center">
  <img src="docs/ai-v143.png" alt="eLuajs AI assistant">
</p>

<p align="center">
  <img src="docs/build-v143.png" alt="eLuajs build output window">
</p>

## Download

Grab the latest release from the [Releases](https://github.com/whyefkan/eLuajs/releases) page:

- **`eLuajs-windows-x86_64.exe`** — Windows installer. Run it, then launch eLuajs from the desktop or Start Menu. No Java installation required.
- **`eLuajs-linux-x86_64.run`** — Linux installer. Run `chmod +x eLuajs-linux-x86_64.run && ./eLuajs-linux-x86_64.run`; it installs to `~/.local/share/eLuajs` and creates desktop/application-menu shortcuts.
- **`eLuajs-linux-x86_64.zip`** — portable Linux package. Extract and run `./eLuajs`.
- **`eLuajs.jar`** — application jar if you already have Java 17+.

The installers and portable packages include their own Java runtime, so no system Java is required.

## Build from source

Requirements: **JDK 17+** (or the bundled `runtime/bin/javac`).

```bash
cd source
./build.sh
# output: source/out/eLuajs.jar
```

On Windows, run the build from Git Bash. The GitHub Actions workflow also produces the ready-to-run Windows zip.

To create the portable package locally:

```bash
source/packaging/build_runtime.sh runtime
gcc -O2 -s -o eLuajs source/packaging/launcher.c
mkdir -p dist/eLuajs/app
cp eLuajs dist/eLuajs/
cp source/out/eLuajs.jar dist/eLuajs/app/
cp -r runtime dist/eLuajs/runtime
```

## AI assistant

Open the AI panel with the activity bar icon or `Ctrl+Alt+A`.

- API keys are stored locally in `~/.eluajs/ai.properties` with `600` permissions.
- The AI receives the project file tree and requests only the files it needs with a `READ: path` protocol — economical on tokens.
- Token usage and approximate cost are shown per response and for the session.
- Generated code can be inserted at the cursor or used to replace the current selection.

## Updates

- Updates are always fetched from the official repository: **`whyefkan/eLuajs`**.
- Auto-check runs on startup and can be disabled in **Settings → Updates**.
- Releases must include an `eLuajs.jar` asset. The included GitHub Actions workflow builds the jar and portable zip when a `v*` tag is pushed.
- The updater backs up the old jar as `eLuajs.jar.bak` before installing.
- On Windows the updater stages the new jar and lets a tiny batch script replace it after the app exits, avoiding file locks.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

Made by [whyefkan](https://github.com/whyefkan) / Efkwn Mobile.
