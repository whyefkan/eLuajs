package eluajs.project;

import eluajs.model.ProjectMeta;
import eluajs.util.AppPaths;
import eluajs.util.FileUtil;

import java.io.IOException;
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
        List<ProjectMeta> result = new ArrayList<>();
        try {
            Files.createDirectories(projectsRoot);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(projectsRoot)) {
                for (Path dir : ds) {
                    if (!Files.isDirectory(dir)) continue;
                    readProject(dir).ifPresent(result::add);
                }
            }
        } catch (IOException ignored) { }
        result.sort(Comparator.comparingLong(this::modified).reversed());
        return List.copyOf(result);
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
        Path root = meta.rootDir() == null ? null : meta.rootDir().toAbsolutePath().normalize();
        Path allowed = projectsRoot.toAbsolutePath().normalize();
        if (root == null || root.equals(allowed) || !root.startsWith(allowed)) {
            throw new IOException("Güvenlik nedeniyle yalnızca eLuajs proje klasörü içindeki projeler silinebilir.");
        }
        if (!Files.isRegularFile(root.resolve(".eluajs/project.properties"))) {
            throw new IOException("Bu klasör geçerli bir eLuajs projesi olarak doğrulanamadı; otomatik silme iptal edildi.");
        }
        FileUtil.deleteTree(root);
    }
}
