package eluajs.project;

import eluajs.model.ProjectMeta;
import eluajs.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects and registers an existing Android Studio / Gradle project without copying it. */
public final class ProjectImporter {
    private static final Pattern PACKAGE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+$");
    private static final Pattern NAMESPACE = Pattern.compile("namespace\\s*(?:=\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern APPLICATION_ID = Pattern.compile("applicationId\\s*(?:=\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern MANIFEST_PACKAGE = Pattern.compile("package\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_NAME_BUILD = Pattern.compile("versionName\\s*(?:=\\s*)?['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_NAME_MANIFEST = Pattern.compile("android:versionName\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern VERSION_CODE_BUILD = Pattern.compile("versionCode\\s*(?:=\\s*)?(\\d+)");
    private static final Pattern VERSION_CODE_MANIFEST = Pattern.compile("android:versionCode\\s*=\\s*['\"]?(\\d+)");
    private static final Pattern STRING_APP_NAME = Pattern.compile(
            "<string[^>]*name\\s*=\\s*['\"]app_name['\"][^>]*>(.*?)</string>", Pattern.DOTALL);
    private static final Pattern MANIFEST_LABEL = Pattern.compile("android:label\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern ROOT_PROJECT = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");

    private ProjectImporter() {}

    public record Detected(
            Path root,
            Path moduleDir,
            String appName,
            String versionName,
            int versionCode,
            String packageName,
            String dsl,
            boolean kotlin,
            boolean wrapper
    ) {
        public String moduleLabel() {
            Path rel = root.relativize(moduleDir);
            return rel.toString().isBlank() ? ":" : ":" + rel.toString().replace('\\', '/');
        }

        public String summary() {
            List<String> parts = new ArrayList<>();
            parts.add(moduleLabel());
            parts.add(dsl);
            if (kotlin) parts.add("Kotlin source");
            parts.add(wrapper ? "Gradle wrapper" : "no wrapper");
            return String.join("  •  ", parts);
        }

        public ProjectMeta meta() {
            return new ProjectMeta(appName, versionName, versionCode, packageName, null, root);
        }
    }

    public static Detected detect(Path folder) throws IOException {
        if (folder == null) throw new IOException("Proje klasörü seçilmedi.");
        Path root = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Proje klasörü bulunamadı: " + root);

        Path module = findAppModule(root);
        Path build = buildFile(module);
        String buildText = Files.readString(build, StandardCharsets.UTF_8);
        Path manifest = module.resolve("src/main/AndroidManifest.xml");
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("AndroidManifest.xml bulunamadı. Bu klasör geçerli bir Android uygulama modülü değil.");
        }
        String manifestText = Files.readString(manifest, StandardCharsets.UTF_8);

        String pkg = firstValid(NAMESPACE, buildText, PACKAGE);
        if (pkg == null) pkg = firstValid(APPLICATION_ID, buildText, PACKAGE);
        if (pkg == null) pkg = firstValid(MANIFEST_PACKAGE, manifestText, PACKAGE);
        if (pkg == null) pkg = "";

        String appName = appName(module, manifestText, root);
        String versionName = firstSafe(VERSION_NAME_BUILD, buildText);
        if (versionName == null) versionName = firstSafe(VERSION_NAME_MANIFEST, manifestText);
        if (versionName == null) versionName = "1.0";

        int versionCode = 1;
        Integer code = firstInt(VERSION_CODE_BUILD, buildText);
        if (code == null) code = firstInt(VERSION_CODE_MANIFEST, manifestText);
        if (code != null && code > 0) versionCode = code;

        boolean kotlin = buildText.contains("org.jetbrains.kotlin.android")
                || Files.isDirectory(module.resolve("src/main/kotlin"));
        boolean wrapper = Files.isRegularFile(root.resolve("gradlew"))
                || Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.jar"));
        String dsl = build.getFileName().toString().endsWith(".kts") ? "Kotlin DSL" : "Groovy DSL";

        return new Detected(root, module, appName, versionName, versionCode, pkg, dsl, kotlin, wrapper);
    }

    public static void install(Detected detected, String appName, String versionName, int versionCode, String packageName)
            throws IOException {
        Path root = detected.root().toAbsolutePath().normalize();
        ProjectMeta meta = new ProjectMeta(appName.trim(), versionName.trim(), versionCode, packageName.trim(), null, root);
        ProjectTemplate.validateMeta(meta);
        Path dir = root.resolve(".eluajs");
        Files.createDirectories(dir);
        Properties props = new Properties();
        props.setProperty("appName", meta.appName());
        props.setProperty("versionName", meta.versionName());
        props.setProperty("versionCode", String.valueOf(meta.versionCode()));
        props.setProperty("packageName", meta.packageName());
        props.setProperty("templateVersion", "5");
        props.setProperty("imported", "true");
        props.setProperty("createdAt", String.valueOf(System.currentTimeMillis()));
        Path relative = root.relativize(detected.moduleDir().toAbsolutePath().normalize());
        String module = relative.toString().replace('\\', '/');
        if (module.equals(".")) module = "";
        props.setProperty("module", module);
        java.io.StringWriter writer = new java.io.StringWriter();
        props.store(writer, "eLuajs imported project");
        FileUtil.writeStringAtomic(dir.resolve("project.properties"), writer.toString());
        ProjectStore.registerImported(root);
    }

    private static Path findAppModule(Path root) throws IOException {
        Path direct = root.resolve("app");
        if (isAndroidModule(direct)) return direct;
        if (isAndroidModule(root)) return root;
        try (var stream = Files.list(root)) {
            List<Path> dirs = stream.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path dir : dirs) if (isAndroidModule(dir)) return dir;
        }
        throw new IOException("Android uygulama modülü bulunamadı (app/build.gradle veya com.android.application).");
    }

    private static boolean isAndroidModule(Path dir) {
        return ProjectLayout.isAndroidModule(dir);
    }

    private static Path buildFile(Path module) throws IOException {
        Path file = buildFileOrNull(module);
        if (file == null) throw new IOException("build.gradle veya build.gradle.kts bulunamadı: " + module);
        return file;
    }

    private static Path buildFileOrNull(Path module) {
        Path groovy = module.resolve("build.gradle");
        if (Files.isRegularFile(groovy)) return groovy;
        Path kts = module.resolve("build.gradle.kts");
        return Files.isRegularFile(kts) ? kts : null;
    }

    private static String appName(Path module, String manifestText, Path root) {
        Path strings = module.resolve("src/main/res/values/strings.xml");
        if (Files.isRegularFile(strings)) {
            try {
                String text = Files.readString(strings, StandardCharsets.UTF_8);
                Matcher m = STRING_APP_NAME.matcher(text);
                if (m.find()) {
                    String name = unescape(m.group(1).trim());
                    if (!name.isBlank()) return name;
                }
            } catch (IOException ignored) { }
        }
        Matcher label = MANIFEST_LABEL.matcher(manifestText);
        if (label.find()) {
            String name = label.group(1).trim();
            if (!name.startsWith("@")) return unescape(name);
        }
        Path settings = root.resolve("settings.gradle");
        if (!Files.isRegularFile(settings)) settings = root.resolve("settings.gradle.kts");
        if (Files.isRegularFile(settings)) {
            try {
                Matcher m = ROOT_PROJECT.matcher(Files.readString(settings, StandardCharsets.UTF_8));
                if (m.find() && !m.group(1).isBlank()) return m.group(1);
            } catch (IOException ignored) { }
        }
        return root.getFileName() == null ? "Android Project" : root.getFileName().toString();
    }

    private static String firstValid(Pattern pattern, String text, Pattern validator) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String value = m.group(1).trim();
            if (validator.matcher(value).matches()) return value;
        }
        return null;
    }

    private static String firstSafe(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String value = m.group(1).trim();
            if (!value.isBlank() && !value.contains("$") && !value.contains("{")) return value;
        }
        return null;
    }

    private static Integer firstInt(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    private static String unescape(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&#39;", "'").replace("\\'", "'");
    }
}
