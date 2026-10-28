package eluajs.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds built APK/AAB artifacts across module outputs and the release folder. */
public final class BuildArtifacts {
    public record Artifact(Path file, String kind, long bytes, long modified) { }

    private BuildArtifacts() {}

    public static List<Artifact> list(Path root) {
        Map<Path, Artifact> found = new LinkedHashMap<>();
        add(found, ProjectLayout.debugApk(root));
        add(found, ProjectLayout.releaseAab(root));

        Path module = ProjectLayout.moduleDir(root);
        Path outputs = module.resolve("build/outputs");
        if (Files.isDirectory(outputs)) {
            try (var walk = Files.walk(outputs, 6)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (name.endsWith(".apk") || name.endsWith(".aab")) add(found, path);
                });
            } catch (IOException ignored) { }
        }
        Path release = root.resolve("release");
        if (Files.isDirectory(release)) {
            try (var stream = Files.list(release)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (name.endsWith(".apk") || name.endsWith(".aab")) add(found, path);
                });
            } catch (IOException ignored) { }
        }
        List<Artifact> artifacts = new ArrayList<>(found.values());
        artifacts.sort(Comparator.comparingLong(Artifact::modified).reversed());
        return List.copyOf(artifacts);
    }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static void add(Map<Path, Artifact> found, Path path) {
        if (path == null || !Files.isRegularFile(path)) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (found.containsKey(normalized)) return;
        String name = normalized.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        String kind = name.endsWith(".aab") ? "AAB" : "APK";
        try {
            found.put(normalized, new Artifact(normalized, kind, Files.size(normalized),
                    Files.getLastModifiedTime(normalized).toMillis()));
        } catch (IOException ignored) { }
    }
}
