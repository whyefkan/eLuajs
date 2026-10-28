package eluajs.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Edits ordinary Gradle dependency declarations without knowing individual libraries. */
public final class ProjectLibraryManager {
    private ProjectLibraryManager() {}

    private static final List<String> CONFIGURATIONS = List.of(
            "implementation", "api", "compileOnly", "runtimeOnly", "annotationProcessor",
            "testImplementation", "androidTestImplementation");

    private static final Pattern DEPENDENCY = Pattern.compile(
            "(?m)^\\s*(implementation|api|compileOnly|runtimeOnly|annotationProcessor|testImplementation|androidTestImplementation)\\s+(platform\\()?['\"]([^'\"]+)['\"]\\)?\\s*$");

    public record Dependency(String configuration, String notation, boolean platform) {
        @Override public String toString() {
            return configuration + "   •   " + (platform ? "BOM/platform   •   " : "") + notation;
        }
    }

    public static List<String> configurations() { return CONFIGURATIONS; }

    public static List<Dependency> list(Path projectRoot) throws IOException {
        String text = Files.readString(buildGradle(projectRoot), StandardCharsets.UTF_8);
        List<Dependency> result = new ArrayList<>();
        Matcher m = DEPENDENCY.matcher(text);
        while (m.find()) {
            result.add(new Dependency(m.group(1), m.group(3), m.group(2) != null));
        }
        return result;
    }

    public static Dependency add(Path projectRoot, String configuration, String notation, boolean platform) throws IOException {
        String cfg = configuration == null ? "implementation" : configuration.trim();
        if (!CONFIGURATIONS.contains(cfg)) throw new IOException("Geçersiz Gradle dependency türü: " + cfg);
        String dep = normalizeNotation(notation);

        Path gradle = buildGradle(projectRoot);
        String text = Files.readString(gradle, StandardCharsets.UTF_8);
        for (Dependency existing : list(projectRoot)) {
            if (existing.configuration().equals(cfg) && existing.notation().equals(dep) && existing.platform() == platform) {
                return existing;
            }
        }

        int blockStart = findDependenciesBlock(text);
        if (blockStart < 0) {
            if (!text.endsWith("\n")) text += "\n";
            text += "\ndependencies {\n}\n";
            blockStart = findDependenciesBlock(text);
        }
        int close = matchingBrace(text, text.indexOf('{', blockStart));
        if (close < 0) throw new IOException("app/build.gradle içindeki dependencies bloğu okunamadı.");

        String line = platform
                ? "    " + cfg + " platform('" + escapeGroovy(dep) + "')\n"
                : "    " + cfg + " '" + escapeGroovy(dep) + "'\n";
        String updated = text.substring(0, close) + line + text.substring(close);
        Files.writeString(gradle, updated, StandardCharsets.UTF_8);
        return new Dependency(cfg, dep, platform);
    }

    public static void remove(Path projectRoot, Dependency dependency) throws IOException {
        if (dependency == null) return;
        Path gradle = buildGradle(projectRoot);
        String text = Files.readString(gradle, StandardCharsets.UTF_8);
        String qCfg = Pattern.quote(dependency.configuration());
        String qDep = Pattern.quote(dependency.notation());
        String pattern = dependency.platform()
                ? "(?m)^\\s*" + qCfg + "\\s+platform\\(\\s*['\"]" + qDep + "['\"]\\s*\\)\\s*\\R?"
                : "(?m)^\\s*" + qCfg + "\\s+['\"]" + qDep + "['\"]\\s*\\R?";
        String updated = text.replaceFirst(pattern, "");
        if (!updated.equals(text)) Files.writeString(gradle, updated, StandardCharsets.UTF_8);
    }

    private static String normalizeNotation(String notation) throws IOException {
        if (notation == null) throw new IOException("Kütüphane koordinatı boş olamaz.");
        String s = notation.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.isBlank()) throw new IOException("Kütüphane koordinatı boş olamaz.");
        if (s.contains("\n") || s.contains("\r") || s.contains("'") || s.contains("\"")) {
            throw new IOException("Koordinat yalnızca Maven/Gradle biçiminde olmalı. Örnek: com.example:library:1.0.0");
        }
        long colons = s.chars().filter(c -> c == ':').count();
        if (colons < 1) throw new IOException("Koordinat 'grup:artifact' veya 'grup:artifact:sürüm' biçiminde olmalı.");
        return s;
    }

    private static Path buildGradle(Path root) throws IOException {
        Path file = root.resolve("app/build.gradle");
        if (!Files.isRegularFile(file)) throw new IOException("app/build.gradle bulunamadı.");
        return file;
    }

    private static int findDependenciesBlock(String text) {
        Matcher m = Pattern.compile("(?m)^\\s*dependencies\\s*\\{").matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static int matchingBrace(String text, int open) {
        if (open < 0) return -1;
        int depth = 0;
        boolean inSingle = false, inDouble = false, escaped = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if ((inSingle || inDouble) && c == '\\') { escaped = true; continue; }
            if (!inDouble && c == '\'') { inSingle = !inSingle; continue; }
            if (!inSingle && c == '"') { inDouble = !inDouble; continue; }
            if (inSingle || inDouble) continue;
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static String escapeGroovy(String s) { return s.replace("\\", "\\\\").replace("'", "\\'"); }
}
