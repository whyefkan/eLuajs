package eluajs.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectValidator {
    private ProjectValidator() {}

    public record Report(boolean ok, List<String> messages, List<Problem> problems) {
        public Report(boolean ok, List<String> messages) {
            this(ok, messages, List.of());
        }
    }

    public static Report validate(Path root) {
        List<String> messages = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        Path settings = existing(root.resolve("settings.gradle"), root.resolve("settings.gradle.kts"));
        if (settings == null) {
            problem(problems, messages, root.resolve("settings.gradle"),
                    "Eksik dosya: settings.gradle (veya settings.gradle.kts)");
        }
        Path rootBuild = existing(root.resolve("build.gradle"), root.resolve("build.gradle.kts"));
        if (rootBuild == null) {
            problem(problems, messages, root.resolve("build.gradle"),
                    "Eksik dosya: build.gradle (veya build.gradle.kts)");
        }
        Path meta = root.resolve(".eluajs/project.properties");
        if (!Files.isRegularFile(meta)) {
            problem(problems, messages, meta, "eLuajs proje bilgisi bulunamadı.");
        }

        Path module;
        try {
            module = findModule(root);
        } catch (IOException ex) {
            problem(problems, messages, root, ex.getMessage());
            module = root.resolve("app");
        }
        Path appGradle = existing(module.resolve("build.gradle"), module.resolve("build.gradle.kts"));
        if (appGradle == null) {
            problem(problems, messages, module.resolve("build.gradle"),
                    "Eksik dosya: " + root.relativize(module.resolve("build.gradle")) + " (veya build.gradle.kts)");
        }
        Path manifest = module.resolve("src/main/AndroidManifest.xml");
        if (!Files.isRegularFile(manifest)) {
            problem(problems, messages, manifest, "Eksik dosya: AndroidManifest.xml");
        }

        try {
            if (appGradle != null) {
                String g = Files.readString(appGradle, StandardCharsets.UTF_8);
                if (!g.contains("applicationId") && !g.contains("namespace")) {
                    problem(problems, messages, appGradle, "Uygulama kimliği (applicationId veya namespace) yok.");
                }
                if (!g.contains("compileSdk")) {
                    problem(problems, messages, appGradle, "build.gradle içinde compileSdk yok.");
                }
                if (!g.contains("dependencies")) {
                    problem(problems, messages, appGradle, "build.gradle içinde dependencies bloğu yok.");
                }
            }

            if (Files.isRegularFile(manifest)) {
                String xml = Files.readString(manifest, StandardCharsets.UTF_8);
                if (!xml.contains("<application")) {
                    problem(problems, messages, manifest, "AndroidManifest.xml içinde application etiketi yok.");
                }
                if (!xml.contains("<activity")) {
                    problem(problems, messages, manifest, "AndroidManifest.xml içinde activity kaydı bulunamadı.");
                }
            }

            Path javaRoot = module.resolve("src/main/java");
            Path kotlinRoot = module.resolve("src/main/kotlin");
            if (!Files.isDirectory(javaRoot) && !Files.isDirectory(kotlinRoot)) {
                problem(problems, messages, module.resolve("src/main/java"),
                        "Java veya Kotlin kaynak klasörü bulunamadı.");
            } else {
                if (Files.isDirectory(javaRoot)) checkSources(javaRoot, root, problems, messages, true);
                if (Files.isDirectory(kotlinRoot)) checkSources(kotlinRoot, root, problems, messages, false);
            }
        } catch (IOException e) {
            problem(problems, messages, root, "Doğrulama sırasında okuma hatası: " + e.getMessage());
        }

        boolean ok = problems.stream().noneMatch(p -> p.severity() == Problem.Severity.ERROR);
        if (ok) messages.add("Proje yapısı temel kontrollerden geçti.");
        messages.add("Not: Tam Java/Android derleme doğrulaması APK derleme aşamasında Gradle tarafından yapılır.");
        return new Report(ok, List.copyOf(messages), List.copyOf(problems));
    }

    private static void checkSources(Path sourceRoot, Path projectRoot, List<Problem> problems,
                                     List<String> messages, boolean java) throws IOException {
        Path extensionRoot = sourceRoot;
        try (var walk = Files.walk(extensionRoot)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (java && !name.endsWith(".java")) continue;
                if (!java && !name.endsWith(".kt")) continue;
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (!balancedBraces(text)) {
                    problem(problems, messages, p, "Süslü parantez dengesi hatalı: " + projectRoot.relativize(p));
                }
                if (java) {
                    Matcher m = Pattern.compile("\\bpackage\\s+([A-Za-z_][A-Za-z0-9_.]*);?").matcher(text);
                    if (!m.find()) {
                        problem(problems, messages, p, "package bildirimi bulunamadı: " + projectRoot.relativize(p));
                    }
                }
            }
        }
    }

    private static Path findModule(Path root) throws IOException {
        Path app = root.resolve("app");
        if (isAndroidModule(app)) return app;
        if (isAndroidModule(root)) return root;
        try (var stream = Files.list(root)) {
            for (Path dir : stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                if (isAndroidModule(dir)) return dir;
            }
        }
        throw new IOException("Android uygulama modülü bulunamadı.");
    }

    private static boolean isAndroidModule(Path dir) {
        return ProjectLayout.isAndroidModule(dir);
    }

    private static Path existing(Path first, Path second) {
        if (Files.isRegularFile(first)) return first;
        return Files.isRegularFile(second) ? second : null;
    }

    private static void problem(List<Problem> problems, List<String> messages, Path file, String message) {
        problems.add(Problem.error(file, message));
        messages.add(message);
    }

    /** Ignores braces inside strings, chars and Java comments to avoid false validation failures. */
    static boolean balancedBraces(String s) {
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
