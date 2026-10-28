package eluajs.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

/** Resolves the Android application module without assuming it is always named "app". */
public final class ProjectLayout {
    private ProjectLayout() {}

    public static String module(Path root) {
        Path props = root.resolve(".eluajs/project.properties");
        if (Files.isRegularFile(props)) {
            try (InputStream in = Files.newInputStream(props)) {
                Properties p = new Properties();
                p.load(in);
                if (p.containsKey("module")) return safeRelative(root, p.getProperty("module", "").trim());
            } catch (Exception ignored) { }
        }
        Path app = root.resolve("app");
        if (buildFile(app) != null) return "app";
        if (isAndroidModule(root)) return "";
        try (var stream = Files.list(root)) {
            for (Path dir : stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                if (isAndroidModule(dir)) return root.relativize(dir).toString().replace('\\', '/');
            }
        } catch (IOException ignored) { }
        return "app";
    }

    public static Path moduleDir(Path root) {
        String module = module(root);
        return module.isEmpty() ? root : root.resolve(module);
    }

    public static String task(Path root, String task) {
        String module = module(root).replace('\\', '/');
        String gradlePath = module.replace('/', ':');
        return (gradlePath.isEmpty() ? ":" : ":" + gradlePath + ":") + task;
    }

    public static Path debugApk(Path root) {
        return newest(root.resolve(module(root)).resolve("build/outputs/apk/debug"), ".apk");
    }

    public static Path releaseAab(Path root) {
        return newest(root.resolve(module(root)).resolve("build/outputs/bundle/release"), ".aab");
    }

    public static Path mainSource(Path root) {
        Path sourceRoot = moduleDir(root).resolve("src/main/java");
        for (String name : new String[]{"MainActivity.java", "MainActivity.kt"}) {
            Path found = findNamed(sourceRoot, name);
            if (found != null) return found;
        }
        Path kotlinRoot = moduleDir(root).resolve("src/main/kotlin");
        for (String name : new String[]{"MainActivity.kt", "MainActivity.java"}) {
            Path found = findNamed(kotlinRoot, name);
            if (found != null) return found;
        }
        return firstSource(sourceRoot, kotlinRoot);
    }

    private static String safeRelative(Path root, String value) {
        if (value.isEmpty()) return "";
        try {
            Path normalized = Path.of(value).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) return "app";
            Path target = root.resolve(normalized).normalize();
            return target.startsWith(root.normalize()) ? normalized.toString().replace('\\', '/') : "app";
        } catch (RuntimeException ex) {
            return "app";
        }
    }

    public static boolean isAndroidModule(Path dir) {
        if (!Files.isDirectory(dir) || !Files.isRegularFile(dir.resolve("src/main/AndroidManifest.xml"))) return false;
        Path build = buildFile(dir);
        if (build == null) return false;
        try { return hasAndroidApplication(Files.readString(build, StandardCharsets.UTF_8)); }
        catch (IOException ex) { return false; }
    }

    static boolean hasAndroidApplication(String text) {
        String needle = "com.android.application";
        int index = text.indexOf(needle);
        while (index >= 0) {
            int start = Math.max(0, index - 100);
            int end = Math.min(text.length(), index + needle.length() + 180);
            if (!text.substring(start, end).contains("apply false")) return true;
            index = text.indexOf(needle, index + 1);
        }
        return false;
    }

    private static Path buildFile(Path dir) {
        for (String name : new String[]{"build.gradle", "build.gradle.kts"}) {
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }

    private static Path newest(Path dir, String extension) {
        if (!Files.isDirectory(dir)) return null;
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(extension))
                    .max(Comparator.comparingLong(ProjectLayout::modified))
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ex) { return 0L; }
    }

    private static Path findNamed(Path root, String name) {
        if (!Files.isDirectory(root)) return null;
        try (var stream = Files.walk(root, 8)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(name))
                    .findFirst().orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    private static Path firstSource(Path javaRoot, Path kotlinRoot) {
        for (Path root : new Path[]{javaRoot, kotlinRoot}) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root, 8)) {
                var found = stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java")
                                || p.getFileName().toString().endsWith(".kt"))
                        .sorted().findFirst();
                if (found.isPresent()) return found.get();
            } catch (IOException ignored) { }
        }
        return null;
    }
}
