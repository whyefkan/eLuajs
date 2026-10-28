# Contributing to eLuajs

Thanks for helping improve eLuajs. The project is open source under the **Apache License 2.0**, and contributions of all sizes are welcome.

## Ways to contribute

- Report bugs or regressions in **Issues**.
- Propose ideas and UI improvements in **Discussions**.
- Improve documentation, translations, icons or screenshots.
- Fix code, add tests, or send a focused pull request.
- Build your own fork and send a tested version/PR when it is ready.

## Development setup

Requirements:

- JDK 17 or newer.
- Linux or Windows for packaging.
- Android SDK is only needed for APK/AAB integration tests.

Build locally:

```bash
cd source
./build.sh
java -jar out/eLuajs.jar
```

Run the built-in smoke/self test:

```bash
java -jar out/eLuajs.jar --self-test
```

Before opening a pull request, also test:

1. Creating a new project from each template.
2. Opening, editing, saving and closing multiple tabs.
3. Validate and build a debug APK.
4. Importing an existing Android project, including a non-`app` module if possible.
5. The changed area on Linux and Windows if you can.
6. Any translation you changed, in the relevant language.

## Pull request checklist

- Keep changes focused; avoid unrelated formatting or refactors.
- Follow the existing Java/Swing style and no external dependency unless discussed first.
- Never commit API keys, signing passwords, keystores, `~/.eluajs` data or generated `app/`/`release/` artifacts.
- Include a clear description of the problem and the tested solution.
- Add or update tests/documentation when behavior changes.
- If AI tools helped write the change, that is fine; review and test the result yourself and mention it if useful.
- By submitting a PR, you agree that your contribution is licensed under the project's Apache 2.0 license.

## Versioning and releases

- Large feature sets bump the minor version (`1.5` → `1.6`).
- Small fixes and security patches bump the patch version (`1.5.1`, `1.5.2`, ...).
- There is no beta channel; published releases are stable.
- Maintainers review changes, run the test suite and publish accepted work in the next release.

## Questions

Open a Discussion or an Issue with as much detail as possible: OS, Java version, project type, steps to reproduce, and the relevant log/crash output.
