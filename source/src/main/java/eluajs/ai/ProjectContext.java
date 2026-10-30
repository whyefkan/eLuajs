package eluajs.ai;

import eluajs.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Builds a compact but complete textual snapshot of an eLuajs project for AI context. */
public final class ProjectContext {
    private static final int MAX_TREE_ENTRIES = 3000;
    private static final int MAX_TOTAL_CHARS = 160_000;
    private static final int MAX_FILE_CHARS = 24_000;
    private static final long MAX_FILE_BYTES = 512_000;

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
             "java", "xml", "gradle", "properties", "json", "txt", "md", "markdown",
            "pro", "kt", "kts", "cfg", "conf", "yml", "yaml", "html", "htm", "css", "js",
            "ts", "sh", "bat", "cmd", "ps1", "toml", "ini", "gitignore", "gitattributes",
            "editorconfig", "csv", "sql", "c", "h", "cpp", "hpp", "py", "rb", "go", "rs"
    );
    private static final Set<String> TEXT_NAMES = Set.of(
            "gradlew", "gradlew.bat", ".gitignore", ".gitattributes", ".editorconfig",
            "gradle.properties", "settings.gradle", "build.gradle", "proguard-rules.pro"
    );
    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".gradle", ".idea", "build", "release", "node_modules", ".eluajs", "out", "dist"
    );

    public record Snapshot(String text, int files, int chars, boolean truncated) { }

    private ProjectContext() {}

    public static Snapshot read(Path root, Path currentFile) {
        if (root == null || !Files.isDirectory(root)) return new Snapshot("", 0, 0, false);
        List<Path> files = new ArrayList<>();
        StringBuilder tree = new StringBuilder();
        tree.append("FILE TREE:\n");
        int[] treeEntries = {0};
        try (Stream<Path> walk = Files.walk(root, 12)) {
            walk.filter(p -> !p.equals(root) && !Files.isSymbolicLink(p))
                    .filter(p -> !isSkipped(root.relativize(p)))
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_TREE_ENTRIES)
                    .forEach(p -> {
                        if (treeEntries[0]++ >= MAX_TREE_ENTRIES) return;
                        Path rel = root.relativize(p);
                        tree.append(Files.isDirectory(p) ? rel + "/" : rel.toString()).append('\n');
                        if (Files.isRegularFile(p) && isText(p)) files.add(p);
                    });
        } catch (IOException ignored) { }
        if (treeEntries[0] >= MAX_TREE_ENTRIES) tree.append("... (tree truncated)\n");
        tree.append('\n');

        // Put the open file first so the model always sees what the user is editing.
        files.sort(Comparator.comparing((Path p) -> !p.equals(currentFile))
                .thenComparing(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT)));

        StringBuilder body = new StringBuilder();
        int total = 0;
        int included = 0;
        boolean truncated = false;
        body.append("PROJECT ROOT: ").append(root).append("\n\n");
        body.append(tree);
        body.append("FILE CONTENTS:\n");
        for (Path file : files) {
            if (total >= MAX_TOTAL_CHARS) { truncated = true; break; }
            String rel = root.relativize(file).toString();
            try {
                long size = Files.size(file);
                if (size > MAX_FILE_BYTES) continue;
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.indexOf('\u0000') >= 0) continue;
                String header = "\n===== " + rel + " =====\n";
                body.append(header);
                int room = Math.min(MAX_FILE_CHARS, MAX_TOTAL_CHARS - total - header.length());
                if (room <= 0) { truncated = true; break; }
                if (content.length() > room) {
                    body.append(content, 0, room).append("\n... (file truncated)\n");
                    truncated = true;
                } else {
                    body.append(content);
                    if (!content.endsWith("\n")) body.append('\n');
                }
                total += Math.min(content.length(), room) + header.length();
                included++;
            } catch (Exception ignored) { }
        }
        if (truncated) {
            body.append("\nNOTE: The snapshot was truncated to stay within the request limit. ")
                    .append("If you need a missing file, ask the user to open it.\n");
        }
        String text = body.toString();
        return new Snapshot(text, included, text.length(), truncated);
    }


    public static String tree(Path root) {
        if (root == null || !Files.isDirectory(root)) return "";
        StringBuilder tree = new StringBuilder();
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(root, 12)) {
            walk.filter(p -> !p.equals(root) && !Files.isSymbolicLink(p))
                    .filter(p -> !isSkipped(root.relativize(p)))
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_TREE_ENTRIES)
                    .forEach(p -> {
                        if (count[0]++ >= MAX_TREE_ENTRIES) return;
                        Path rel = root.relativize(p);
                        tree.append(Files.isDirectory(p) ? rel + "/" : rel.toString()).append('\n');
                    });
        } catch (IOException ignored) { }
        if (count[0] >= MAX_TREE_ENTRIES) tree.append("... (tree truncated)\n");
        return tree.toString();
    }

    /** Resolves a model-supplied relative path, rejecting escapes and generated/output folders. */
    public static Path safeResolve(Path root, String relative) {
        if (root == null || relative == null) return null;
        String rel = relative.trim().replace('\\', '/');
        if (rel.isEmpty() || rel.startsWith("/") || rel.startsWith("\\") || rel.matches("^[A-Za-z]:.*")) return null;
        Path base = root.toAbsolutePath().normalize();
        Path target;
        try { target = base.resolve(rel).normalize(); }
        catch (Exception ex) { return null; }
        if (!target.startsWith(base) || target.equals(base)) return null;
        for (Path part : base.relativize(target)) {
            if ("..".equals(part.toString()) || SKIP_DIRS.contains(part.toString())) return null;
        }
        if (!FileUtil.isSafeProjectPath(base, target) || !isText(target)) return null;
        return target;
    }

    /** Reads a line range from one requested project file. */
    public static String readTextFileRange(Path root, String relative, int startLine, int endLine, int maxChars) {
        if (root == null || relative == null || relative.isBlank()) return null;
        try {
            Path file = safeResolve(root, relative);
            if (file == null || !Files.isRegularFile(file)) return null;
            if (Files.size(file) > MAX_FILE_BYTES) return null;
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.indexOf('\u0000') >= 0) return null;
            int start = Math.max(1, startLine);
            int end = endLine <= 0 ? Integer.MAX_VALUE : Math.max(start, endLine);
            String[] lines = text.split("\\R", -1);
            if (start > lines.length) return "";
            end = Math.min(end, lines.length);
            StringBuilder out = new StringBuilder();
            for (int i = start; i <= end; i++) {
                out.append(i).append(": ").append(lines[i - 1]).append('\n');
                if (out.length() >= maxChars) { out.append("... (truncated)\n"); break; }
            }
            return out.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Reads one requested project file for the on-demand AI protocol. */
    public static String readTextFile(Path root, String relative, int maxChars) {
        if (root == null || relative == null || relative.isBlank()) return null;
        try {
            Path file = safeResolve(root, relative);
            if (file == null || !Files.isRegularFile(file)) return null;
            if (Files.size(file) > MAX_FILE_BYTES) return null;
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.indexOf('\u0000') >= 0) return null;
            if (text.length() > maxChars) text = text.substring(0, maxChars) + "\n... (truncated)\n";
            return text;
        } catch (Exception ex) {
            return null;
        }
    }
    private static boolean isSkipped(Path rel) {
        for (Path part : rel) {
            if (SKIP_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    public static boolean isTextFile(Path p) { return isText(p); }

    private static boolean isText(Path p) {
        String name = p.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (TEXT_NAMES.contains(lower)) return true;
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return false;
        return TEXT_EXTENSIONS.contains(lower.substring(dot + 1));
    }
}
