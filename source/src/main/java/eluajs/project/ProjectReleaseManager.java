package eluajs.project;

import eluajs.android.ReleaseSigningConfig;
import eluajs.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/**
 * Play Store release signing bilgilerini proje bazında yönetir.
 * Şifreler yalnız kullanıcı isterse bu dosyada tutulur; dosya .gitignore'a eklenir.
 */
public final class ProjectReleaseManager {
    public static final String SETTINGS_RELATIVE = ".eluajs/release-signing.properties";
    public static final String INIT_RELATIVE = ".eluajs/release-signing.init.gradle";

    private ProjectReleaseManager() {}

    public record Saved(Path storeFile, String storePassword, String keyAlias, String keyPassword) {
        public ReleaseSigningConfig toConfig() {
            return new ReleaseSigningConfig(storeFile, storePassword, keyAlias, keyPassword);
        }
    }

    public static Path settingsFile(Path projectRoot) { return projectRoot.resolve(SETTINGS_RELATIVE); }
    public static Path initScript(Path projectRoot) { return projectRoot.resolve(INIT_RELATIVE); }

    public static Saved load(Path projectRoot) {
        Path file = settingsFile(projectRoot);
        if (!Files.isRegularFile(file)) return new Saved(null, "", "upload", "");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            String raw = p.getProperty("storeFile", "").trim();
            Path store = raw.isEmpty() ? null : Path.of(raw).toAbsolutePath().normalize();
            return new Saved(store,
                    p.getProperty("storePassword", ""),
                    p.getProperty("keyAlias", "upload").trim(),
                    p.getProperty("keyPassword", ""));
        } catch (Exception ex) {
            return new Saved(null, "", "upload", "");
        }
    }

    public static Path ensureSettingsFile(Path projectRoot) throws IOException {
        ensureGitIgnore(projectRoot);
        Path file = settingsFile(projectRoot);
        if (!Files.exists(file)) {
            String text = """
                    # eLuajs Play Store / AAB signing ayarları
                    # Bu dosya .gitignore'a eklenir. Yine de şifreleri düz metin saklamak istemiyorsanız
                    # storePassword ve keyPassword alanlarını boş bırakıp eLuajs arayüzünden girin.
                    storeFile=
                    storePassword=
                    keyAlias=upload
                    keyPassword=
                    """;
            FileUtil.writeStringAtomic(file, text);
            secure(file);
        }
        return file;
    }

    public static void save(Path projectRoot, ReleaseSigningConfig config, boolean savePasswords) throws IOException {
        ensureGitIgnore(projectRoot);
        Properties p = new Properties();
        p.setProperty("storeFile", config.storeFile() == null ? "" : config.storeFile().toString());
        p.setProperty("keyAlias", config.keyAlias());
        p.setProperty("storePassword", savePasswords ? config.storePassword() : "");
        p.setProperty("keyPassword", savePasswords ? config.keyPassword() : "");
        Path file = settingsFile(projectRoot);
        Files.createDirectories(file.getParent());
        Path tmp = FileUtil.secureTempFile(file, ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
            p.store(out, "eLuajs release signing - NEVER COMMIT SECRETS");
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        secure(file);
    }

    public static Path ensureInitScript(Path projectRoot) throws IOException {
        Path script = initScript(projectRoot);
        String text = """
                // eLuajs release AAB signing injection.
                // Secrets are read from process environment; they are not embedded into build.gradle.
                gradle.beforeProject { p ->
                    p.plugins.withId('com.android.application') {
                        def androidExt = p.extensions.findByName('android')
                        if (androidExt == null) return
                        def storePath = System.getenv('ELUAJS_STORE_FILE')
                        def storePass = System.getenv('ELUAJS_STORE_PASSWORD')
                        def keyAliasValue = System.getenv('ELUAJS_KEY_ALIAS')
                        def keyPass = System.getenv('ELUAJS_KEY_PASSWORD')
                        if (!storePath || !storePass || !keyAliasValue || !keyPass) {
                            throw new org.gradle.api.GradleException('eLuajs release signing bilgileri eksik.')
                        }
                        def cfg = androidExt.signingConfigs.findByName('eluajsRelease')
                        if (cfg == null) cfg = androidExt.signingConfigs.create('eluajsRelease')
                        cfg.storeFile = p.file(storePath)
                        cfg.storePassword = storePass
                        cfg.keyAlias = keyAliasValue
                        cfg.keyPassword = keyPass
                        def releaseType = androidExt.buildTypes.findByName('release')
                        if (releaseType == null) throw new org.gradle.api.GradleException('release buildType bulunamadı.')
                        releaseType.signingConfig = cfg
                    }
                }
                """;
        FileUtil.writeStringAtomic(script, text);
        return script;
    }

    public static void ensureGitIgnore(Path projectRoot) throws IOException {
        Path git = projectRoot.resolve(".gitignore");
        String text = Files.isRegularFile(git) ? Files.readString(git, StandardCharsets.UTF_8) : "";
        String entry = SETTINGS_RELATIVE;
        boolean hasSecret = text.lines().anyMatch(l -> l.trim().equals(entry));
        boolean hasRelease = text.lines().anyMatch(l -> l.trim().equals("release/"));
        if (!hasSecret || !hasRelease) {
            if (!text.isEmpty() && !text.endsWith("\n")) text += "\n";
            text += "\n# eLuajs release outputs / signing secrets\n";
            if (!hasRelease) text += "release/\n";
            if (!hasSecret) text += entry + "\n";
            FileUtil.writeStringAtomic(git, text);
        }
    }

    private static void secure(Path file) {
        try { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")); }
        catch (Exception ignored) { }
    }
}
