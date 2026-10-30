package eluajs.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Lightweight checks for common Android XML accessibility problems. */
public final class AccessibilityChecker {
    private AccessibilityChecker() {}

    public record Issue(Path file, int line, String message) {
        @Override public String toString() { return file.getFileName() + ":" + line + "   " + message; }
    }

    public static List<Issue> scan(Path root) {
        List<Issue> issues = new ArrayList<>();
        Path layout = ProjectLayout.moduleDir(root).resolve("src/main/res/layout");
        if (!Files.isDirectory(layout)) return issues;
        try (var walk = Files.walk(layout, 3)) {
            for (Path file : walk.filter(p -> p.getFileName().toString().endsWith(".xml")).toList()) {
                String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\R", -1);
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    if (line.contains("<ImageView") && !line.contains("contentDescription")) {
                        issues.add(new Issue(file, i + 1, "ImageView için contentDescription eksik."));
                    }
                    if (line.contains("android:text=\"") && !line.contains("android:text=\"@string/")) {
                        issues.add(new Issue(file, i + 1, "Sabit metin yerine @string kullanın."));
                    }
                    if (line.contains("android:clickable=\"true\"")) {
                        String width = valueAround(lines, i, "android:layout_width");
                        String height = valueAround(lines, i, "android:layout_height");
                        if (small(width) || small(height)) {
                            issues.add(new Issue(file, i + 1, "Dokunma alanı 48dp'den küçük olabilir."));
                        }
                    }
                }
            }
        } catch (IOException ignored) { }
        return issues;
    }

    private static String valueAround(String[] lines, int index, String key) {
        for (int i = Math.max(0, index - 4); i <= Math.min(lines.length - 1, index + 4); i++) {
            int at = lines[i].indexOf(key);
            if (at >= 0) {
                int q1 = lines[i].indexOf('"', at);
                int q2 = q1 < 0 ? -1 : lines[i].indexOf('"', q1 + 1);
                if (q1 >= 0 && q2 > q1) return lines[i].substring(q1 + 1, q2);
            }
        }
        return "";
    }

    private static boolean small(String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.trim();
        if (v.startsWith("@") || v.contains("match_parent") || v.contains("wrap_content")) return false;
        if (v.endsWith("dp")) {
            try { return Double.parseDouble(v.substring(0, v.length() - 2)) < 48; }
            catch (NumberFormatException ignored) { }
        }
        return false;
    }
}
