package eluajs.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectValidator {
    private ProjectValidator() {}

    public record Report(boolean ok, List<String> messages) {}

    public static Report validate(Path root) {
        List<String> messages = new ArrayList<>();
        boolean ok = true;
        for (String required : List.of("settings.gradle", "build.gradle", "app/build.gradle", "app/src/main/AndroidManifest.xml")) {
            if (!Files.isRegularFile(root.resolve(required))) {
                ok = false;
                messages.add("Eksik dosya: " + required);
            }
        }
        Path meta = root.resolve(".eluajs/project.properties");
        if (!Files.isRegularFile(meta)) {
            ok = false;
            messages.add("eLuajs proje bilgisi bulunamadı.");
        }

        try {
            Path appGradle = root.resolve("app/build.gradle");
            if (Files.isRegularFile(appGradle)) {
                String g = Files.readString(appGradle);
                if (!g.contains("applicationId")) { ok = false; messages.add("app/build.gradle içinde applicationId yok."); }
                if (!g.contains("compileSdk")) { ok = false; messages.add("app/build.gradle içinde compileSdk yok."); }
                if (!g.contains("dependencies")) { ok = false; messages.add("app/build.gradle içinde dependencies bloğu yok."); }
            }

            Path manifest = root.resolve("app/src/main/AndroidManifest.xml");
            if (Files.isRegularFile(manifest)) {
                String xml = Files.readString(manifest);
                if (!xml.contains("<application")) { ok = false; messages.add("AndroidManifest.xml içinde application etiketi yok."); }
                if (!xml.contains(".MainActivity")) { ok = false; messages.add("AndroidManifest.xml içinde MainActivity kaydı bulunamadı."); }
            }

            Path javaRoot = root.resolve("app/src/main/java");
            if (!Files.isDirectory(javaRoot)) {
                ok = false;
                messages.add("Java kaynak klasörü bulunamadı: app/src/main/java");
            } else {
                try (var walk = Files.walk(javaRoot)) {
                    for (Path p : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                        String text = Files.readString(p);
                        if (!balancedJavaBraces(text)) { ok = false; messages.add("Süslü parantez dengesi hatalı: " + root.relativize(p)); }
                        Matcher m = Pattern.compile("\\bpackage\\s+([A-Za-z_][A-Za-z0-9_.]*);?").matcher(text);
                        if (!m.find()) { ok = false; messages.add("package bildirimi bulunamadı: " + root.relativize(p)); }
                    }
                }
            }
        } catch (IOException e) {
            ok = false;
            messages.add("Doğrulama sırasında okuma hatası: " + e.getMessage());
        }
        if (ok) messages.add("Proje yapısı temel kontrollerden geçti.");
        messages.add("Not: Tam Java/Android derleme doğrulaması APK derleme aşamasında Gradle tarafından yapılır.");
        return new Report(ok, List.copyOf(messages));
    }

    /** Ignores braces inside strings, chars and Java comments to avoid false validation failures. */
    static boolean balancedJavaBraces(String s) {
        int depth = 0;
        boolean string = false, chr = false, lineComment = false, blockComment = false, escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char n = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (c == '*' && n == '/') { blockComment = false; i++; }
                continue;
            }
            if (string) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') string = false;
                continue;
            }
            if (chr) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '\'') chr = false;
                continue;
            }
            if (c == '/' && n == '/') { lineComment = true; i++; continue; }
            if (c == '/' && n == '*') { blockComment = true; i++; continue; }
            if (c == '"') { string = true; continue; }
            if (c == '\'') { chr = true; continue; }
            if (c == '{') depth++;
            else if (c == '}' && --depth < 0) return false;
        }
        return depth == 0 && !string && !chr && !blockComment;
    }
}
