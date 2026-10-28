package eluajs.project;

import eluajs.model.ProjectMeta;
import eluajs.util.AppPaths;
import eluajs.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

public final class ProjectStore {
    private final Path projectsRoot;

    public ProjectStore() { this(AppPaths.defaultProjects()); }
    public ProjectStore(Path projectsRoot) { this.projectsRoot = projectsRoot; }

    public Path projectsRoot() { return projectsRoot; }

    /** One damaged project must never hide all other projects from the hub. */
    public List<ProjectMeta> list() {
        Map<Path, ProjectMeta> found = new LinkedHashMap<>();
        try {
            Files.createDirectories(projectsRoot);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(projectsRoot)) {
                for (Path dir : ds) {
                    if (!Files.isDirectory(dir)) continue;
                    readProject(dir).ifPresent(meta -> found.put(normalize(meta.rootDir()), meta));
                }
            }
        } catch (IOException ignored) { }
        for (String line : importedRoots()) {
            try {
                Path root = Path.of(line).toAbsolutePath().normalize();
                if (!Files.isDirectory(root)) continue;
                readProject(root).ifPresent(meta -> found.putIfAbsent(normalize(root), meta));
            } catch (RuntimeException ignored) { }
        }
        List<ProjectMeta> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparingLong(this::modified).reversed());
        return List.copyOf(result);
    }

    public boolean isManaged(ProjectMeta meta) {
        if (meta == null || meta.rootDir() == null) return false;
        Path root = normalize(meta.rootDir());
        return root.startsWith(normalize(projectsRoot));
    }

    public static boolean isImported(Path root) {
        if (root == null) return false;
        Path normalized = normalize(root);
        for (String line : importedRoots()) {
            try { if (normalize(Path.of(line)).equals(normalized)) return true; }
            catch (RuntimeException ignored) { }
        }
        return false;
    }

    public static void registerImported(Path root) throws IOException {
        Path normalized = normalize(root);
        List<String> roots = importedRoots();
        String value = normalized.toString();
        for (String line : roots) {
            try { if (normalize(Path.of(line)).equals(normalized)) return; }
            catch (RuntimeException ignored) { }
        }
        roots.add(value);
        saveImportedRoots(roots);
    }

    public static void unregisterImported(Path root) throws IOException {
        Path normalized = normalize(root);
        List<String> roots = importedRoots();
        boolean removed = roots.removeIf(line -> {
            try { return normalize(Path.of(line)).equals(normalized); }
            catch (RuntimeException ignored) { return false; }
        });
        if (removed) saveImportedRoots(roots);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path importedRegistry() {
        return AppPaths.config().resolve("imported-projects.txt");
    }

    private static List<String> importedRoots() {
        Path file = importedRegistry();
        if (!Files.isRegularFile(file)) return new ArrayList<>();
        try {
            List<String> roots = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (!value.isBlank() && !value.startsWith("#")) roots.add(value);
            }
            return roots;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static void saveImportedRoots(List<String> roots) throws IOException {
        Path file = importedRegistry();
        StringBuilder text = new StringBuilder("# eLuajs imported projects (one absolute path per line)\n");
        for (String root : roots) text.append(root).append('\n');
        FileUtil.writeStringAtomic(file, text.toString());
    }

    private Optional<ProjectMeta> readProject(Path dir) {
        Path propsFile = dir.resolve(".eluajs/project.properties");
        if (!Files.isRegularFile(propsFile)) return Optional.empty();
        try {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(propsFile)) { p.load(in); }
            int versionCode;
            try { versionCode = Math.max(1, Integer.parseInt(p.getProperty("versionCode", "1").trim())); }
            catch (NumberFormatException ex) { versionCode = 1; }
            String pkg = p.getProperty("packageName", "com.example.app").trim();
            if (pkg.isBlank()) pkg = "com.example.app";
            return Optional.of(new ProjectMeta(
                    p.getProperty("appName", dir.getFileName().toString()),
                    p.getProperty("versionName", "1.0"),
                    versionCode,
                    pkg,
                    null,
                    dir
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private long modified(ProjectMeta meta) {
        try { return Files.getLastModifiedTime(meta.rootDir()).toMillis(); }
        catch (IOException e) { return 0L; }
    }

    public void delete(ProjectMeta meta) throws IOException {
        Path root = meta.rootDir() == null ? null : normalize(meta.rootDir());
        if (root == null) throw new IOException("Proje klasörü bulunamadı.");
        if (isManaged(meta)) {
            if (!Files.isRegularFile(root.resolve(".eluajs/project.properties"))) {
                throw new IOException("Bu klasör geçerli bir eLuajs projesi olarak doğrulanamadı; otomatik silme iptal edildi.");
            }
            FileUtil.deleteTree(root);
            return;
        }
        if (!isImported(root)) {
            throw new IOException("Bu proje eLuajs tarafından yönetilmiyor; silme iptal edildi.");
        }
        unregisterImported(root);
        Files.deleteIfExists(root.resolve(".eluajs/project.properties"));
    }
}
