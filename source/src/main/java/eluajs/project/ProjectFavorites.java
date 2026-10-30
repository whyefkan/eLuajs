package eluajs.project;

import eluajs.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Lightweight pinned-project list stored in the eLuajs config folder. */
public final class ProjectFavorites {
    private ProjectFavorites() {}
    private static Path file() { return AppPaths.config().resolve("favorites.txt"); }
    public static Set<Path> list() {
        Set<Path> out = new LinkedHashSet<>();
        if (!Files.isRegularFile(file())) return out;
        try {
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                if (!line.isBlank()) out.add(Path.of(line.trim()).toAbsolutePath().normalize());
            }
        } catch (Exception ignored) { }
        return out;
    }
    public static boolean isFavorite(Path root) {
        return root != null && list().contains(root.toAbsolutePath().normalize());
    }
    public static boolean toggle(Path root) {
        if (root == null) return false;
        Path normalized = root.toAbsolutePath().normalize();
        Set<Path> roots = list();
        boolean added;
        if (roots.contains(normalized)) { roots.remove(normalized); added = false; }
        else { roots.add(normalized); added = true; }
        try {
            Files.createDirectories(file().getParent());
            StringBuilder text = new StringBuilder();
            for (Path p : roots) text.append(p).append('\n');
            Files.writeString(file(), text.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
        return added;
    }
}
