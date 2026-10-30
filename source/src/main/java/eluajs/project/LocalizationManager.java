package eluajs.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Finds string resources missing from locale folders. */
public final class LocalizationManager {
    private static final Pattern STRING = Pattern.compile("<string[^>]*name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private LocalizationManager() {}

    public record LocaleReport(String locale, int total, List<String> missing) { }

    public static List<LocaleReport> scan(Path root) throws IOException {
        Path res = ProjectLayout.moduleDir(root).resolve("src/main/res");
        Path base = res.resolve("values/strings.xml");
        if (!Files.isRegularFile(base)) return List.of();
        Set<String> baseKeys = keys(base);
        List<LocaleReport> out = new ArrayList<>();
        try (var stream = Files.list(res)) {
            for (Path dir : stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("values-"))
                    .sorted().toList()) {
                Path file = dir.resolve("strings.xml");
                if (!Files.isRegularFile(file)) continue;
                Set<String> keys = keys(file);
                List<String> missing = baseKeys.stream().filter(k -> !keys.contains(k)).sorted().toList();
                out.add(new LocaleReport(dir.getFileName().toString(), keys.size(), missing));
            }
        }
        return out;
    }

    private static Set<String> keys(Path file) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = STRING.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) keys.add(m.group(1));
        return keys;
    }
}
