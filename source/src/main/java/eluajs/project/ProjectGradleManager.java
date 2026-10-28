package eluajs.project;

import eluajs.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Updates Android versions, SDK levels and Gradle performance properties. */
public final class ProjectGradleManager {
    public record Versions(String versionName, int versionCode, String applicationId, String namespace,
                           int compileSdk, int minSdk, int targetSdk) { }

    private static final Pattern VERSION_NAME = Pattern.compile("versionName\\s*(?:=\\s*)?[\"']([^\"']*)[\"']");
    private static final Pattern VERSION_CODE = Pattern.compile("versionCode\\s*(?:=\\s*)?(\\d+)");
    private static final Pattern APPLICATION_ID = Pattern.compile("applicationId\\s*(?:=\\s*)?[\"']([^\"']*)[\"']");
    private static final Pattern NAMESPACE = Pattern.compile("namespace\\s*(?:=\\s*)?[\"']([^\"']*)[\"']");
    private static final Pattern COMPILE_SDK = Pattern.compile("compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
    private static final Pattern MIN_SDK = Pattern.compile("minSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
    private static final Pattern TARGET_SDK = Pattern.compile("targetSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");

    private ProjectGradleManager() {}

    public static Versions read(Path root) throws IOException {
        Path module = ProjectLayout.moduleDir(root);
        Path build = buildFile(module);
        String text = build == null ? "" : Files.readString(build, StandardCharsets.UTF_8);
        Properties meta = readMetadata(root);
        String versionName = first(VERSION_NAME, text, meta.getProperty("versionName", "1.0"));
        int versionCode = number(first(VERSION_CODE, text, meta.getProperty("versionCode", "1")), 1);
        String applicationId = first(APPLICATION_ID, text, meta.getProperty("packageName", ""));
        String namespace = first(NAMESPACE, text, applicationId);
        int compileSdk = number(first(COMPILE_SDK, text, "36"), 36);
        int minSdk = number(first(MIN_SDK, text, "23"), 23);
        int targetSdk = number(first(TARGET_SDK, text, "36"), 36);
        return new Versions(versionName, Math.max(1, versionCode), applicationId, namespace,
                clampSdk(compileSdk), clampSdk(minSdk), clampSdk(targetSdk));
    }

    public static void write(Path root, Versions versions) throws IOException {
        Path module = ProjectLayout.moduleDir(root);
        Path build = buildFile(module);
        if (build == null) throw new IOException("build.gradle bulunamadı.");
        String text = Files.readString(build, StandardCharsets.UTF_8);
        text = replaceValue(text, "versionCode", String.valueOf(Math.max(1, versions.versionCode())));
        text = replaceQuoted(text, "versionName", versions.versionName());
        text = replaceValue(text, "compileSdk(?:Version)?", String.valueOf(clampSdk(versions.compileSdk())));
        text = replaceValue(text, "minSdk(?:Version)?", String.valueOf(clampSdk(versions.minSdk())));
        text = replaceValue(text, "targetSdk(?:Version)?", String.valueOf(clampSdk(versions.targetSdk())));
        FileUtil.writeStringAtomic(build, text);
        updateMetadataVersion(root, versions.versionName(), versions.versionCode());
    }

    public static int incrementVersionCode(Path root) throws IOException {
        Versions current = read(root);
        int next = Math.max(1, current.versionCode()) + 1;
        write(root, new Versions(current.versionName(), next, current.applicationId(), current.namespace(),
                current.compileSdk(), current.minSdk(), current.targetSdk()));
        return next;
    }

    public record Performance(String jvmArgs, boolean parallel, boolean caching, boolean daemon,
                              boolean configurationCache) { }

    public static Performance readPerformance(Path root) {
        Properties p = readProperties(root.resolve("gradle.properties"));
        return new Performance(
                p.getProperty("org.gradle.jvmargs", "-Xmx2048m -Dfile.encoding=UTF-8"),
                bool(p.getProperty("org.gradle.parallel"), true),
                bool(p.getProperty("org.gradle.caching"), true),
                bool(p.getProperty("org.gradle.daemon"), true),
                bool(p.getProperty("org.gradle.configuration-cache"), false));
    }

    public static void writePerformance(Path root, Performance performance) throws IOException {
        Path file = root.resolve("gradle.properties");
        List<String> lines = Files.isRegularFile(file)
                ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                : new ArrayList<>();
        set(lines, "org.gradle.jvmargs", performance.jvmArgs() == null || performance.jvmArgs().isBlank()
                ? "-Xmx2048m -Dfile.encoding=UTF-8" : performance.jvmArgs().trim());
        set(lines, "org.gradle.parallel", String.valueOf(performance.parallel()));
        set(lines, "org.gradle.caching", String.valueOf(performance.caching()));
        set(lines, "org.gradle.daemon", String.valueOf(performance.daemon()));
        set(lines, "org.gradle.configuration-cache", String.valueOf(performance.configurationCache()));
        set(lines, "android.useAndroidX", "true");
        FileUtil.writeStringAtomic(file, String.join("\n", lines) + "\n");
    }

    private static void set(List<String> lines, String key, String value) {
        String prefix = key + "=";
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith(prefix)) {
                lines.set(i, prefix + value);
                return;
            }
        }
        lines.add(prefix + value);
    }

    private static boolean bool(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return Boolean.parseBoolean(value.trim());
    }

    private static String replaceQuoted(String text, String key, String value) {
        String safe = value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        return text.replaceAll("(" + key + "\\s*(?:=\\s*)?[\"'])[^\"']*([\"'])",
                "$1" + Matcher.quoteReplacement(safe) + "$2");
    }

    private static String replaceValue(String text, String key, String value) {
        return text.replaceAll("(" + key + "\\s*(?:=\\s*)?)\\d+", "$1" + value);
    }

    private static String first(Pattern pattern, String text, String fallback) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) return matcher.group(1).trim();
        return fallback == null ? "" : fallback;
    }

    private static int number(String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private static int clampSdk(int value) { return Math.max(1, Math.min(99, value)); }

    private static Path buildFile(Path module) {
        for (String name : new String[]{"build.gradle", "build.gradle.kts"}) {
            Path file = module.resolve(name);
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }

    private static Properties readMetadata(Path root) {
        Properties p = new Properties();
        Path file = root.resolve(".eluajs/project.properties");
        if (!Files.isRegularFile(file)) return p;
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        catch (IOException ignored) { }
        return p;
    }

    private static Properties readProperties(Path file) {
        Properties p = new Properties();
        if (!Files.isRegularFile(file)) return p;
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        catch (IOException ignored) { }
        return p;
    }

    private static void updateMetadataVersion(Path root, String versionName, int versionCode) throws IOException {
        Path file = root.resolve(".eluajs/project.properties");
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        p.setProperty("versionName", versionName);
        p.setProperty("versionCode", String.valueOf(versionCode));
        java.io.StringWriter writer = new java.io.StringWriter();
        p.store(writer, "eLuajs project metadata");
        FileUtil.writeStringAtomic(file, writer.toString());
    }
}
