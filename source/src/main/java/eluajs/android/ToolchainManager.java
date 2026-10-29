package eluajs.android;

import eluajs.util.AppPaths;
import eluajs.util.FileUtil;
import eluajs.util.Platform;
import eluajs.project.ProjectLayout;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ToolchainManager {
    public static final String GRADLE_VERSION = "8.13";
    public static final String BUILD_TOOLS = "35.0.0";
    public static final int ANDROID_PLATFORM = 36;
    public static final String GRADLE_URL = "https://services.gradle.org/distributions/gradle-" + GRADLE_VERSION + "-bin.zip";
    private static final String GRADLE_SHA256 = "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78";
    private static final String SDK_TOOLS_SHA1_LINUX = "040d3996a65543d22ec4bf73e4c37aa37a8d4af4";
    private static final String SDK_TOOLS_SHA1_WINDOWS = "b9862337a13e2809a5159dc3a08d058091bd59f6";

    public static String sdkToolsFile() {
        return "commandlinetools-" + (Platform.isWindows() ? "win" : "linux") + "-15859902_latest.zip";
    }

    public static String sdkToolsUrl() {
        return "https://dl.google.com/android/repository/" + sdkToolsFile();
    }

    private ToolchainManager() {}

    public static Path sdkRoot() {
        String root = System.getenv("ANDROID_SDK_ROOT");
        if (root == null || root.isBlank()) root = System.getenv("ANDROID_HOME");
        return (root == null || root.isBlank()) ? AppPaths.managedSdk() : Path.of(root);
    }

    public static Path sdkManager() {
        return sdkRoot().resolve("cmdline-tools/latest/bin/" + Platform.bat("sdkmanager"));
    }
    public static Path gradleHome() { return AppPaths.tools().resolve("gradle-" + GRADLE_VERSION); }
    public static Path gradleExecutable() { return gradleHome().resolve("bin/" + Platform.bat("gradle")); }

    public static Path javaHome() { return Path.of(System.getProperty("java.home")).toAbsolutePath().normalize(); }
    public static Path javaExecutable() { return javaHome().resolve("bin/" + Platform.exe("java")); }
    public static Path javacExecutable() { return javaHome().resolve("bin/" + Platform.exe("javac")); }
    public static Path jlinkExecutable() { return javaHome().resolve("bin/" + Platform.exe("jlink")); }
    public static Path keytoolExecutable() { return javaHome().resolve("bin/" + Platform.exe("keytool")); }

    /** Android Gradle Plugin için yalnızca java yeterli değildir; javac ve jlink de zorunludur. */
    public static boolean javaReady() {
        return Platform.runnable(javaExecutable())
                && Platform.runnable(javacExecutable())
                && Platform.runnable(jlinkExecutable())
                && Platform.runnable(keytoolExecutable())
                && tlsCryptoReady();
    }

    /** The portable runtime must keep EC crypto for modern Gradle/Maven HTTPS endpoints. */
    public static boolean tlsCryptoReady() {
        return ModuleLayer.boot().findModule("jdk.crypto.ec").isPresent();
    }

    public static boolean gradleReady() { return Platform.runnable(gradleExecutable()); }
    public static boolean sdkManagerReady() { return Platform.runnable(sdkManager()); }
    public static boolean platformReady() { return Files.isDirectory(sdkRoot().resolve("platforms/android-" + ANDROID_PLATFORM)); }
    public static boolean buildToolsReady() { return Files.isDirectory(sdkRoot().resolve("build-tools/" + BUILD_TOOLS)); }
    public static boolean sdkReady() { return sdkManagerReady() && platformReady() && buildToolsReady(); }

    public static void ensureGradle(Consumer<String> log) throws Exception {
        if (gradleReady()) { log.accept("Gradle " + GRADLE_VERSION + " hazır."); return; }
        AppPaths.ensureBaseDirs();
        Path zip = AppPaths.downloads().resolve("gradle-" + GRADLE_VERSION + "-bin.zip");
        if (Files.exists(zip) && !FileUtil.hashMatches(zip, GRADLE_SHA256, "SHA-256")) Files.delete(zip);
        if (!Files.exists(zip)) FileUtil.downloadVerified(GRADLE_URL, zip, GRADLE_SHA256, "SHA-256", log);
        Path tmp = AppPaths.tools().resolve("gradle-unpack");
        FileUtil.deleteTree(tmp);
        log.accept("Gradle açılıyor...");
        FileUtil.unzip(zip, tmp);
        Path extracted = tmp.resolve("gradle-" + GRADLE_VERSION);
        if (!Files.isDirectory(extracted)) throw new IOException("Gradle ZIP yapısı beklenenden farklı.");
        FileUtil.deleteTree(gradleHome());
        Files.move(extracted, gradleHome(), StandardCopyOption.REPLACE_EXISTING);
        FileUtil.deleteTree(tmp);
        gradleExecutable().toFile().setExecutable(true);
        log.accept("Gradle hazır.");
    }

    public static void prepareAndroidSdk(boolean licenseAccepted, Consumer<String> log) throws Exception {
        if (!licenseAccepted) throw new IllegalStateException("Android SDK lisans koşullarını kabul etmeden SDK kurulamaz.");
        AppPaths.ensureBaseDirs();
        Path sdk = sdkRoot();
        Files.createDirectories(sdk);
        if (!sdkManagerReady()) {
            Path zip = AppPaths.downloads().resolve(sdkToolsFile());
            String expected = Platform.isWindows() ? SDK_TOOLS_SHA1_WINDOWS : SDK_TOOLS_SHA1_LINUX;
            if (Files.exists(zip) && !FileUtil.hashMatches(zip, expected, "SHA-1")) Files.delete(zip);
            if (!Files.exists(zip)) FileUtil.downloadVerified(sdkToolsUrl(), zip, expected, "SHA-1", log);
            Path tmp = AppPaths.tools().resolve("android-cmdline-unpack");
            FileUtil.deleteTree(tmp);
            log.accept("Android komut satırı araçları açılıyor...");
            FileUtil.unzip(zip, tmp);
            Path source = tmp.resolve("cmdline-tools");
            if (!Files.isDirectory(source)) throw new IOException("Android command-line tools ZIP yapısı beklenenden farklı.");
            Path latest = sdk.resolve("cmdline-tools/latest");
            FileUtil.deleteTree(latest);
            Files.createDirectories(latest.getParent());
            Files.move(source, latest, StandardCopyOption.REPLACE_EXISTING);
            FileUtil.deleteTree(tmp);
            sdkManager().toFile().setExecutable(true);
            Path avd = latest.resolve("bin/" + Platform.bat("avdmanager"));
            if (Files.exists(avd)) avd.toFile().setExecutable(true);
            log.accept("Android SDK yöneticisi hazır.");
        }

        Map<String,String> env = environment();
        String yes = "y\n".repeat(80);
        log.accept("Android SDK lisansları uygulanıyor...");
        int licenseExit = ProcessRunner.run(toolCommand(sdkManager(), "--sdk_root=" + sdk, "--licenses"), null, env, yes, log);
        if (licenseExit != 0) throw new IOException("SDK lisans işlemi başarısız (kod " + licenseExit + ").");

        log.accept("Android platformu ve Build Tools kuruluyor...");
        int exit = ProcessRunner.run(toolCommand(sdkManager(), "--sdk_root=" + sdk,
                "platform-tools", "platforms;android-" + ANDROID_PLATFORM, "build-tools;" + BUILD_TOOLS),
                null, env, yes, log);
        if (exit != 0) throw new IOException("Android SDK paket kurulumu başarısız (kod " + exit + ").");
        log.accept("Android SDK hazır: " + sdk);
    }

    public static Path buildDebugApk(Path projectRoot, Consumer<String> log) throws Exception {
        return buildDebugApk(projectRoot, false, false, log);
    }

    public static Path buildDebugApk(Path projectRoot, boolean clean, boolean offline, Consumer<String> log) throws Exception {
        if (!javaReady()) {
            throw new IllegalStateException("JDK eksik: java, javac ve jlink birlikte bulunmalıdır. JDK: " + javaHome());
        }
        if (!sdkReady()) throw new IllegalStateException("Android SDK eksik. Önce araçları hazırlayın.");
        ensureGradle(log);

        log.accept("JDK hazır: " + javaHome());
        log.accept("jlink hazır: " + jlinkExecutable());
        Path local = projectRoot.resolve("local.properties");
        String sdkEscaped = sdkRoot().toAbsolutePath().toString().replace("\\", "\\\\").replace(":", "\\:");
        FileUtil.writeStringAtomic(local, "sdk.dir=" + sdkEscaped + "\n");
        Map<String,String> env = environment();
        log.accept("APK derlemesi başlatıldı...");
        List<String> command = new ArrayList<>(List.of(gradleExecutable().toString(), "--no-daemon", "--console=plain"));
        if (offline) command.add("--offline");
        if (clean) command.add(ProjectLayout.task(projectRoot, "clean"));
        command.add(ProjectLayout.task(projectRoot, "assembleDebug"));
        int exit = ProcessRunner.run(command, projectRoot, env, null, log);
        if (exit != 0) throw new IOException("Gradle derlemesi başarısız (kod " + exit + "). Ayrıntılar yukarıdaki günlükte.");
        Path apk = ProjectLayout.debugApk(projectRoot);
        if (apk == null || !Files.isRegularFile(apk)) {
            throw new IOException("Derleme tamamlandı ancak APK bulunamadı: "
                    + (apk == null ? ProjectLayout.moduleDir(projectRoot).resolve("build/outputs/apk/debug") : apk));
        }
        log.accept("APK hazır: " + apk);
        return apk;
    }


    public static Path adbExecutable() {
        return sdkRoot().resolve("platform-tools").resolve(Platform.exe("adb"));
    }

    public static List<String> adbDevices(Consumer<String> log) throws Exception {
        Path adb = adbExecutable();
        if (!Files.isRegularFile(adb)) {
            throw new IOException("ADB bulunamadı. Android SDK platform-tools paketini hazırlayın.");
        }
        List<String> devices = new ArrayList<>();
        int exit = ProcessRunner.run(List.of(adb.toString(), "devices"), null, environment(), null, line -> {
            if (log != null) log.accept(line);
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.endsWith("\tdevice") || trimmed.endsWith(" device")) {
                devices.add(trimmed.split("\\s+")[0]);
            }
        });
        if (exit != 0) throw new IOException("ADB cihaz listesi alınamadı (kod " + exit + ").");
        if (devices.isEmpty()) {
            throw new IOException("Bağlı Android cihaz/emülatör bulunamadı. USB hata ayıklamayı açın.");
        }
        return devices;
    }

    public static void installApk(Path apk, Consumer<String> log) throws Exception {
        if (apk == null || !Files.isRegularFile(apk)) throw new IOException("Yüklenecek APK bulunamadı.");
        List<String> devices = adbDevices(log);
        String serial = devices.get(0);
        if (devices.size() > 1 && log != null) {
            log.accept("Birden fazla cihaz bulundu; ilk cihaz kullanılıyor: " + serial);
        }
        installApk(serial, apk, log);
    }

    public static void installApk(String serial, Path apk, Consumer<String> log) throws Exception {
        if (serial == null || serial.isBlank()) throw new IOException("Cihaz seçilmedi.");
        if (apk == null || !Files.isRegularFile(apk)) throw new IOException("Yüklenecek APK bulunamadı.");
        if (log != null) log.accept("APK yükleniyor: " + apk.getFileName() + " → " + serial);
        int exit = ProcessRunner.run(List.of(adbExecutable().toString(), "-s", serial, "install", "-r", apk.toString()),
                null, environment(), null, log);
        if (exit != 0) throw new IOException("ADB install başarısız (kod " + exit + ").");
        if (log != null) log.accept("APK cihaza yüklendi: " + serial);
    }

    public static void generateUploadKeystore(Path storeFile, String alias, String storePassword, String keyPassword, String commonName, Consumer<String> log) throws Exception {
        if (!javaReady()) throw new IllegalStateException("JDK/keytool eksik: " + javaHome());
        if (storeFile == null) throw new IllegalArgumentException("Keystore yolu boş olamaz.");
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Key alias boş olamaz.");
        if (storePassword == null || storePassword.length() < 6) throw new IllegalArgumentException("Keystore şifresi en az 6 karakter olmalıdır.");
        if (keyPassword == null || keyPassword.length() < 6) throw new IllegalArgumentException("Anahtar şifresi en az 6 karakter olmalıdır.");
        storeFile = storeFile.toAbsolutePath().normalize();
        if (Files.exists(storeFile)) throw new IOException("Bu keystore zaten var: " + storeFile);
        Files.createDirectories(storeFile.getParent());
        String cn = (commonName == null || commonName.isBlank()) ? "Android Upload Key" : commonName.trim();
        cn = cn.replace(",", " ").replace("=", " ").trim();

        Map<String,String> env = environment();
        env.put("ELUAJS_NEW_STORE_PASSWORD", storePassword);
        env.put("ELUAJS_NEW_KEY_PASSWORD", keyPassword);
        List<String> cmd = List.of(
                keytoolExecutable().toString(), "-genkeypair", "-v",
                "-keystore", storeFile.toString(),
                "-storetype", "JKS",
                "-alias", alias.trim(),
                "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=" + cn,
                "-storepass:env", "ELUAJS_NEW_STORE_PASSWORD",
                "-keypass:env", "ELUAJS_NEW_KEY_PASSWORD");
        if (log != null) log.accept("Upload keystore oluşturuluyor: " + storeFile);
        int exit = ProcessRunner.run(cmd, null, env, null, log);
        if (exit != 0 || !Files.isRegularFile(storeFile)) throw new IOException("Keystore oluşturulamadı (kod " + exit + ").");
        if (log != null) log.accept("Upload keystore hazır. Bu dosyanın güvenli bir yedeğini alın.");
    }

    public static void verifyKeystore(ReleaseSigningConfig signing, Consumer<String> log) throws Exception {
        signing.validate();
        Map<String,String> env = environment();
        env.putAll(signing.asEnvironment());
        List<String> cmd = List.of(
                keytoolExecutable().toString(), "-list",
                "-keystore", signing.storeFile().toString(),
                "-alias", signing.keyAlias(),
                "-storepass:env", "ELUAJS_STORE_PASSWORD");
        if (log != null) log.accept("Keystore ve alias doğrulanıyor...");
        int exit = ProcessRunner.run(cmd, null, env, null, log);
        if (exit != 0) throw new IOException("Keystore/alias doğrulanamadı. Şifre veya alias hatalı olabilir.");
        if (log != null) log.accept("Keystore doğrulandı: " + signing.keyAlias());
    }

    public static Path buildReleaseBundle(Path projectRoot, ReleaseSigningConfig signing, Consumer<String> log) throws Exception {
        return buildReleaseBundle(projectRoot, signing, false, false, log);
    }

    public static Path buildReleaseBundle(Path projectRoot, ReleaseSigningConfig signing, boolean clean, boolean offline,
                                          Consumer<String> log) throws Exception {
        if (!javaReady()) throw new IllegalStateException("JDK eksik: java, javac, jlink ve keytool birlikte bulunmalıdır. JDK: " + javaHome());
        if (!sdkReady()) throw new IllegalStateException("Android SDK eksik. Önce araçları hazırlayın.");
        signing.validate();
        ensureGradle(log);
        verifyKeystore(signing, log);

        Path local = projectRoot.resolve("local.properties");
        String sdkEscaped = sdkRoot().toAbsolutePath().toString().replace("\\", "\\\\").replace(":", "\\:");
        FileUtil.writeStringAtomic(local, "sdk.dir=" + sdkEscaped + "\n");
        Path initScript = eluajs.project.ProjectReleaseManager.ensureInitScript(projectRoot);

        Map<String,String> env = environment();
        env.putAll(signing.asEnvironment());
        if (log != null) {
            log.accept("Release AAB derlemesi başlatıldı...");
            log.accept("İmzalama: " + signing.storeFile() + " / alias=" + signing.keyAlias());
        }
        List<String> command = new ArrayList<>(List.of(gradleExecutable().toString(), "--no-daemon", "--console=plain",
                "--init-script", initScript.toString()));
        if (offline) command.add("--offline");
        if (clean) command.add(ProjectLayout.task(projectRoot, "clean"));
        command.add(ProjectLayout.task(projectRoot, "bundleRelease"));
        int exit = ProcessRunner.run(command, projectRoot, env, null, log);
        if (exit != 0) throw new IOException("Release AAB Gradle derlemesi başarısız (kod " + exit + "). Ayrıntılar yukarıdaki günlükte.");

        Path aab = ProjectLayout.releaseAab(projectRoot);
        if (aab == null || !Files.isRegularFile(aab)) {
            throw new IOException("Derleme tamamlandı ancak AAB bulunamadı: "
                    + (aab == null ? ProjectLayout.moduleDir(projectRoot).resolve("build/outputs/bundle/release") : aab));
        }
        if (!bundleLooksSigned(aab)) throw new IOException("AAB üretildi ancak imza girdileri doğrulanamadı. Play Store'a yüklemeden önce imzalamayı kontrol edin.");

        Path releaseDir = projectRoot.resolve("release");
        Files.createDirectories(releaseDir);
        String base = projectRoot.getFileName().toString().replaceAll("[^A-Za-z0-9._-]+", "-");
        Path friendly = releaseDir.resolve(base + "-release.aab");
        Files.copy(aab, friendly, StandardCopyOption.REPLACE_EXISTING);
        if (log != null) {
            log.accept("İmzalı release AAB hazır: " + friendly);
            log.accept("Google Play'e yüklerken güncellemelerde versionCode değerini artırmayı unutmayın.");
        }
        return friendly;
    }

    public static boolean bundleLooksSigned(Path aab) {
        if (aab == null || !Files.isRegularFile(aab)) return false;
        try (ZipFile zip = new ZipFile(aab.toFile())) {
            boolean sf = false, sig = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String n = entries.nextElement().getName().toUpperCase(Locale.ROOT);
                if (n.startsWith("META-INF/") && n.endsWith(".SF")) sf = true;
                if (n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))) sig = true;
            }
            return sf && sig;
        } catch (IOException ex) { return false; }
    }

    private static List<String> toolCommand(Path executable, String... args) {
        List<String> command = new ArrayList<>();
        String exe = executable.toString();
        if (Platform.isWindows() && exe.toLowerCase(Locale.ROOT).endsWith(".bat")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(exe);
        command.addAll(Arrays.asList(args));
        return command;
    }

    public static Map<String,String> environment() {
        Map<String,String> env = new HashMap<>();
        env.put("ANDROID_SDK_ROOT", sdkRoot().toString());
        env.put("ANDROID_HOME", sdkRoot().toString());
        env.put("JAVA_HOME", javaHome().toString());
        String path = System.getenv().getOrDefault("PATH", "");
        env.put("PATH", javaHome().resolve("bin") + File.pathSeparator
                + sdkRoot().resolve("platform-tools") + File.pathSeparator + path);
        return env;
    }
}
