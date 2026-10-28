package eluajs.project;

import eluajs.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

/** Safe file/folder operations limited to the project root. */
public final class ProjectFileManager {
    private ProjectFileManager() {}

    public static Path createFile(Path root, Path parent, String name) throws IOException {
        Path target = safeTarget(root, parent, name);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) throw new IOException("Dosya zaten var: " + target.getFileName());
        Files.writeString(target, defaultContent(root, target), StandardCharsets.UTF_8);
        return target;
    }

    public static Path createDirectory(Path root, Path parent, String name) throws IOException {
        Path target = safeTarget(root, parent, name);
        if (Files.exists(target)) throw new IOException("Klasör zaten var: " + target.getFileName());
        Files.createDirectories(target);
        return target;
    }

    public static Path createLayout(Path root, String name) throws IOException {
        String clean = safeName(name);
        if (!clean.endsWith(".xml")) clean += ".xml";
        Path layout = ProjectLayout.moduleDir(root).resolve("src/main/res/layout");
        Files.createDirectories(layout);
        Path target = layout.resolve(clean);
        if (Files.exists(target)) throw new IOException("Layout zaten var: " + clean);
        Files.writeString(target, """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:padding="24dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Hello eLuajs" />

                </LinearLayout>
                """, StandardCharsets.UTF_8);
        return target;
    }

    public static Path rename(Path root, Path path, String newName) throws IOException {
        Path source = normalizeInside(root, path);
        protect(root, source);
        String clean = safeName(newName);
        Path target = source.resolveSibling(clean);
        if (Files.exists(target)) throw new IOException("Hedef zaten var: " + clean);
        Files.move(source, target);
        return target;
    }

    public static Path duplicate(Path root, Path path) throws IOException {
        Path source = normalizeInside(root, path);
        protect(root, source);
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        Path parent = source.getParent();
        Path target = parent.resolve(base + "-copy" + ext);
        int n = 2;
        while (Files.exists(target)) target = parent.resolve(base + "-copy-" + n++ + ext);
        if (Files.isDirectory(source)) copyTree(source, target);
        else Files.copy(source, target);
        return target;
    }

    public static void delete(Path root, Path path) throws IOException {
        Path target = normalizeInside(root, path);
        protect(root, target);
        FileUtil.deleteTree(target);
    }

    private static Path safeTarget(Path root, Path parent, String name) throws IOException {
        Path baseRoot = root.toAbsolutePath().normalize();
        Path base = parent == null ? baseRoot : parent.toAbsolutePath().normalize();
        if (!base.startsWith(baseRoot)) throw new IOException("Proje dışına çıkılamaz.");
        Path target = base.resolve(safeName(name)).normalize();
        if (!target.startsWith(baseRoot)) throw new IOException("Proje dışına çıkılamaz.");
        return target;
    }

    private static Path normalizeInside(Path root, Path path) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        if (!target.startsWith(base) || target.equals(base)) throw new IOException("Proje dışındaki yollar değiştirilemez.");
        return target;
    }

    private static void protect(Path root, Path path) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        if (path.equals(base)) throw new IOException("Proje kök klasörü değiştirilemez.");
        for (Path part : base.relativize(path)) {
            String name = part.toString();
            if (name.equals(".eluajs") || name.equals(".git") || name.equals(".gradle") || name.equals("build")) {
                throw new IOException("Korunan klasör değiştirilemez: " + name);
            }
        }
    }

    private static String safeName(String name) throws IOException {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IOException("Ad boş olamaz.");
        if (clean.contains("/") || clean.contains("\\") || clean.equals(".") || clean.equals("..")) {
            throw new IOException("Geçersiz dosya adı.");
        }
        return clean;
    }

    private static String defaultContent(Path root, Path target) {
        String name = target.getFileName().toString();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        Path module = ProjectLayout.moduleDir(root);
        if (lower.endsWith(".java") || lower.endsWith(".kt")) {
            String className = name.substring(0, name.lastIndexOf('.'));
            if (!className.matches("[A-Za-z_$][A-Za-z0-9_$]*")) className = "NewFile";
            String pkg = packageFor(module, target);
            String packageLine = pkg.isBlank() ? "" : "package " + pkg + ";\n\n";
            if (lower.endsWith(".kt")) return packageLine + "class " + className + " {\n}\n";
            return packageLine + "public class " + className + " {\n}\n";
        }
        if (lower.endsWith(".xml")) {
            return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                    + "<title>" + name + "</title>\n</head>\n<body>\n</body>\n</html>\n";
        }
        if (lower.endsWith(".css")) return "/* " + name + " */\n";
        if (lower.endsWith(".js")) return "// " + name + "\n";
        if (lower.endsWith(".json")) return "{}\n";
        if (lower.endsWith(".md")) return "# " + name.substring(0, name.lastIndexOf('.')) + "\n";
        return "";
    }

    private static String packageFor(Path module, Path target) {
        Path javaRoot = module.resolve("src/main/java");
        if (!target.startsWith(javaRoot)) return "";
        Path parent = target.getParent();
        if (parent == null) return "";
        return javaRoot.relativize(parent).toString().replace('\\', '.').replace('/', '.');
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(dest);
                else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
