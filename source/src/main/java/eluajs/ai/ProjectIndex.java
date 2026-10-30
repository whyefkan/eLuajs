package eluajs.ai;

import eluajs.project.ProjectLayout;
import eluajs.project.ProjectSearch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Compact, token-friendly project map and link graph for the AI context. */
public final class ProjectIndex {
    private static final int MAX_FILES = 220;
    private static final int MAX_JAVA_READ = 80;
    private static final int MAX_CHARS = 14_000;
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;");
    private static final Pattern CLASS = Pattern.compile("(?m)^\\s*(?:public\\s+|final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern LAYOUT_REF = Pattern.compile("R\\.layout\\.([A-Za-z0-9_]+)");
    private static final Pattern STRING_REF = Pattern.compile("R\\.string\\.([A-Za-z0-9_]+)");
    private static final Pattern ID_REF = Pattern.compile("R\\.id\\.([A-Za-z0-9_]+)");
    private static final Pattern ACTIVITY = Pattern.compile("<activity\\b[^>]*android:name\\s*=\\s*['\"](?:\\.|[A-Za-z0-9_$.]+\\.)?([A-Za-z0-9_$.]+)['\"]", Pattern.DOTALL);
    private static final Set<String> SKIP = Set.of(".git", ".gradle", ".idea", "build", "release", "node_modules", ".eluajs", "out", "dist");

    private ProjectIndex() {}

    public static String compact(Path root) {
        if (root == null || !Files.isDirectory(root)) return "";
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, 12)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> !skipped(root.relativize(p)))
                    .filter((Path p) -> ProjectContext.isTextFile(p))
                    .sorted(Comparator.comparing(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_FILES)
                    .forEach(files::add);
        } catch (Exception ignored) { }

        StringBuilder b = new StringBuilder();
        b.append("PROJECT INDEX (compact; file contents are available through READ)\n");
        b.append("root: ").append(root.getFileName() == null ? "." : root.getFileName()).append('\n');
        List<String> java = new ArrayList<>();
        List<String> layouts = new ArrayList<>();
        List<String> values = new ArrayList<>();
        List<String> other = new ArrayList<>();
        Map<String, String> javaPackage = new LinkedHashMap<>();
        Map<String, List<String>> links = new LinkedHashMap<>();
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            String lower = rel.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".java") || lower.endsWith(".kt")) java.add(rel);
            else if (lower.contains("/res/layout/")) layouts.add(rel);
            else if (lower.contains("/res/values/")) values.add(rel);
            else if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".webp") && !lower.endsWith(".ttf")) other.add(rel);
        }
        b.append("Java/Kotlin files (").append(java.size()).append("):\n");
        int read = 0;
        for (String rel : java) {
            if (read++ < MAX_JAVA_READ) {
                try {
                    String text = Files.readString(root.resolve(rel), StandardCharsets.UTF_8);
                    String pkg = first(PACKAGE, text);
                    String cls = first(CLASS, text);
                    if (pkg != null) javaPackage.put(rel, pkg);
                    b.append("- ").append(rel);
                    if (cls != null) b.append(" [").append(cls).append(']');
                    b.append('\n');
                    List<String> refs = new ArrayList<>();
                    refs.addAll(refs(LAYOUT_REF, text, "layout"));
                    refs.addAll(refs(STRING_REF, text, "string"));
                    refs.addAll(refs(ID_REF, text, "id"));
                    if (!refs.isEmpty()) links.put(rel, refs);
                } catch (Exception ignored) {
                    b.append("- ").append(rel).append('\n');
                }
            } else {
                b.append("- ").append(rel).append('\n');
            }
            if (b.length() > MAX_CHARS / 2) break;
        }
        if (!layouts.isEmpty()) {
            b.append("Layouts:\n");
            for (String rel : layouts) b.append("- ").append(rel).append('\n');
        }
        if (!values.isEmpty()) {
            b.append("Values/resources:\n");
            for (String rel : values) b.append("- ").append(rel).append('\n');
        }
        if (!other.isEmpty() && b.length() < MAX_CHARS) {
            b.append("Other text files:\n");
            int n = 0;
            for (String rel : other) {
                if (n++ >= 40 || b.length() > MAX_CHARS) break;
                b.append("- ").append(rel).append('\n');
            }
        }
        String graph = graph(root, links);
        if (!graph.isBlank() && b.length() + graph.length() < MAX_CHARS) b.append('\n').append(graph);
        if (files.size() >= MAX_FILES) b.append("\n(index capped at ").append(MAX_FILES).append(" files)\n");
        return limit(b.toString(), MAX_CHARS);
    }

    public static String graph(Path root, Map<String, List<String>> links) {
        StringBuilder b = new StringBuilder();
        Path module = ProjectLayout.moduleDir(root);
        Path manifest = module.resolve("src/main/AndroidManifest.xml");
        if (Files.isRegularFile(manifest)) {
            try {
                String xml = Files.readString(manifest, StandardCharsets.UTF_8);
                LinkedHashSet<String> activities = new LinkedHashSet<>();
                Matcher m = ACTIVITY.matcher(xml);
                while (m.find()) activities.add(m.group(1));
                if (!activities.isEmpty()) {
                    b.append("Manifest activities: ");
                    b.append(String.join(", ", activities)).append('\n');
                }
            } catch (Exception ignored) { }
        }
        if (!links.isEmpty()) {
            b.append("Linked references:\n");
            int n = 0;
            for (var entry : links.entrySet()) {
                if (n++ >= 50 || b.length() > 5000) break;
                b.append("- ").append(entry.getKey()).append(" -> ")
                        .append(String.join(", ", entry.getValue())).append('\n');
            }
        }
        return b.toString();
    }

    public static List<ProjectSearch.Match> search(Path root, String query, int limit) {
        try { return ProjectSearch.search(root, query, false, Math.max(1, Math.min(80, limit))); }
        catch (Exception ex) { return List.of(); }
    }

    private static List<String> refs(Pattern p, String text, String kind) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(text);
        while (m.find() && out.size() < 12) out.add(kind + "/" + m.group(1));
        return new ArrayList<>(out);
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static boolean skipped(Path rel) {
        for (Path part : rel) if (SKIP.contains(part.toString())) return true;
        return false;
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n... (index truncated)\n";
    }
}
