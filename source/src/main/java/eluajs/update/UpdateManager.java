package eluajs.update;

import eluajs.ai.Json;
import eluajs.util.AppInfo;
import eluajs.util.Platform;
import eluajs.util.I18n;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** GitHub Releases based updater. Downloads an eLuajs.jar asset and replaces the running jar. */
public final class UpdateManager {
    public static final String OFFICIAL_REPO = "whyefkan/eLuajs";

    public record Release(String tag, String version, String notes, String pageUrl,
                          String assetUrl, String assetName, boolean newer) { }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final AtomicBoolean STARTUP_CHECKED = new AtomicBoolean();

    private UpdateManager() {}

    public static Release check() throws Exception {
        String cleaned = OFFICIAL_REPO;
        if (!cleaned.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException(I18n.t("Official repository is invalid"));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + cleaned + "/releases/latest"))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "eLuajs/" + AppInfo.version())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 404) throw new IOException(I18n.t("No release has been published yet."));
        if (status == 403) throw new IOException(I18n.t("GitHub API rate limit reached. Try again later."));
        if (status / 100 != 2) throw new IOException(I18n.t("GitHub returned HTTP %d", status));
        Object root = Json.parse(response.body());
        String tag = Json.stringAt(root, "tag_name");
        if (tag == null || tag.isBlank()) throw new IOException(I18n.t("Latest release has no tag"));
        String version = normalizeVersion(tag);
        String notes = Json.stringAt(root, "body");
        if (notes == null) notes = "";
        if (notes.length() > 6000) notes = notes.substring(0, 6000) + "\n…";
        String page = Json.stringAt(root, "html_url");
        String assetUrl = null;
        String assetName = null;
        Object assets = Json.at(root, "assets");
        if (assets instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Object name = map.get("name");
                Object url = map.get("browser_download_url");
                if (name == null || url == null) continue;
                String n = String.valueOf(name);
                if (n.equalsIgnoreCase("eLuajs.jar")) {
                    assetName = n;
                    assetUrl = String.valueOf(url);
                    break;
                }
                if (assetUrl == null && n.toLowerCase().endsWith(".jar")) {
                    assetName = n;
                    assetUrl = String.valueOf(url);
                }
            }
        }
        boolean newer = compareVersions(version, AppInfo.version()) > 0;
        return new Release(tag, version, notes, page, assetUrl, assetName, newer);
    }

    public static void checkOnStartup(Window owner, Consumer<Release> onUpdate) {
        if (!STARTUP_CHECKED.compareAndSet(false, true)) return;
        UpdateConfig cfg = UpdateConfig.load();
        if (!cfg.autoCheck()) return;
        SwingWorker<Release, Void> worker = new SwingWorker<>() {
            @Override protected Release doInBackground() throws Exception { return check(); }
            @Override protected void done() {
                try {
                    Release release = get();
                    if (release != null && release.newer() && onUpdate != null) onUpdate.accept(release);
                } catch (Exception ignored) { }
            }
        };
        worker.execute();
    }

    public static void install(Release release) throws Exception {
        if (release == null || release.assetUrl() == null || release.assetUrl().isBlank()) {
            throw new IOException(I18n.t("No eLuajs.jar asset in the release"));
        }
        Path jar = currentJar();
        Path temp = Files.createTempFile("eluajs-update-", ".part");
        Files.deleteIfExists(temp);
        HttpRequest request = HttpRequest.newBuilder(URI.create(release.assetUrl()))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "eLuajs/" + AppInfo.version())
                .GET()
                .build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(temp));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(temp);
            throw new IOException(I18n.t("Download failed: HTTP %d", response.statusCode()));
        }
        if (!validJar(temp)) {
            Files.deleteIfExists(temp);
            throw new IOException(I18n.t("Downloaded file is not a valid eLuajs jar"));
        }
        if (Platform.isWindows()) {
            installWindows(jar, temp);
            return;
        }
        Path backup = jar.resolveSibling("eLuajs.jar.bak");
        Files.copy(jar, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void installWindows(Path jar, Path downloaded) throws Exception {
        Path appDir = jar.getParent();
        Path staged = appDir.resolve("eLuajs-update.jar");
        Files.copy(downloaded, staged, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(downloaded);
        Path batch = appDir.resolve("update.bat");
        String script = "@echo off\r\n"
                + "timeout /t 3 /nobreak >nul\r\n"
                + "copy /y \"%~dp0eLuajs.jar\" \"%~dp0eLuajs.jar.bak\" >nul\r\n"
                + ":retry\r\n"
                + "move /y \"%~dp0eLuajs-update.jar\" \"%~dp0eLuajs.jar\" >nul 2>&1\r\n"
                + "if errorlevel 1 (\r\n"
                + "  timeout /t 1 /nobreak >nul\r\n"
                + "  goto retry\r\n"
                + ")\r\n"
                + "if exist \"%~dp0..\\eLuajs.bat\" (\r\n"
                + "  start \"\" \"%~dp0..\\eLuajs.bat\"\r\n"
                + ") else (\r\n"
                + "  start \"\" \"%~dp0..\\runtime\\bin\\javaw.exe\" -Xms64m -Xmx1024m -jar \"%~dp0eLuajs.jar\"\r\n"
                + ")\r\n"
                + "del \"%~f0\"\r\n";
        Files.writeString(batch, script, java.nio.charset.StandardCharsets.UTF_8);
        new ProcessBuilder("cmd.exe", "/c", "start", "", batch.toString()).start();
        System.exit(0);
    }

    public static void restart() throws Exception {
        Path jar = currentJar();
        Path appRoot = jar.getParent().getParent();
        ProcessBuilder builder;
        if (Platform.isWindows()) {
            Path bat = appRoot.resolve("eLuajs.bat");
            if (Files.isRegularFile(bat)) {
                builder = new ProcessBuilder("cmd.exe", "/c", "start", "", bat.toString());
            } else {
                Path javaw = appRoot.resolve("runtime/bin/javaw.exe");
                if (!Files.isRegularFile(javaw)) javaw = appRoot.resolve("runtime/bin/java.exe");
                if (!Files.isRegularFile(javaw)) throw new IOException(I18n.t("Launcher and bundled runtime not found"));
                builder = new ProcessBuilder(javaw.toString(), "-jar", jar.toString());
            }
        } else {
            Path launcher = appRoot.resolve("eLuajs");
            if (Files.isExecutable(launcher)) {
                builder = new ProcessBuilder(launcher.toString());
            } else {
                Path java = appRoot.resolve("runtime/bin/java");
                if (!Files.isExecutable(java)) throw new IOException(I18n.t("Launcher and bundled runtime not found"));
                builder = new ProcessBuilder(java.toString(), "-jar", jar.toString());
            }
        }
        builder.directory(appRoot.toFile());
        builder.start();
        System.exit(0);
    }

    public static String currentVersion() { return AppInfo.version(); }

    private static Path currentJar() throws Exception {
        Path path = Path.of(UpdateManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isRegularFile(path)) throw new IOException(I18n.t("This update mode needs a packaged eLuajs.jar"));
        return path;
    }

    private static boolean validJar(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.getEntry("eluajs/Main.class") != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String normalizeVersion(String tag) {
        String v = tag == null ? "" : tag.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    private static int compareVersions(String a, String b) {
        int[] x = versionParts(a);
        int[] y = versionParts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xv = i < x.length ? x[i] : 0;
            int yv = i < y.length ? y[i] : 0;
            if (xv != yv) return Integer.compare(xv, yv);
        }
        return 0;
    }

    private static int[] versionParts(String v) {
        String[] pieces = normalizeVersion(v).split("[^0-9]+");
        int[] out = new int[Math.max(1, pieces.length)];
        for (int i = 0; i < pieces.length; i++) {
            try { out[i] = pieces[i].isEmpty() ? 0 : Integer.parseInt(pieces[i]); }
            catch (NumberFormatException ignored) { out[i] = 0; }
        }
        return out;
    }
}
