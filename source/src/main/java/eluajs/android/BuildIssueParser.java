package eluajs.android;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts clickable source-file locations from common Gradle/Javac/AAPT output lines. */
public final class BuildIssueParser {
    private static final Pattern LOCATION = Pattern.compile("((?:file://)?/.*?\\.(?:java|kt|xml|gradle|kts|properties)):(\\d+)(?::\\d+)?", Pattern.CASE_INSENSITIVE);
    private BuildIssueParser() {}

    public record Issue(Path file, int line) { }

    public static Optional<Issue> parse(String line) {
        if (line == null || line.isBlank()) return Optional.empty();
        Matcher m = LOCATION.matcher(line);
        if (!m.find()) return Optional.empty();
        try {
            String raw = m.group(1);
            Path path = raw.startsWith("file://") ? Path.of(URI.create(raw)) : Path.of(raw);
            int number = Integer.parseInt(m.group(2));
            return Optional.of(new Issue(path.toAbsolutePath().normalize(), Math.max(1, number)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
