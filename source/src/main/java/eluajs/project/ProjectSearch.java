package eluajs.project;

import eluajs.util.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free project text search.
 * v0.17 scans files as a stream instead of materialising the full project and
 * reads files line-by-line, so large projects use far less memory and can stop
 * immediately when the result limit is reached.
 */
public final class ProjectSearch {
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private ProjectSearch() {}

    public record Match(Path file, int line, String preview) { }

    public record ReplaceResult(int files, int replacements, List<String> errors, Set<Path> changed) { }

    public static List<Match> search(Path projectRoot, String query, boolean caseSensitive, int maxResults) throws IOException {
        String needle = query == null ? "" : query.trim();
        if (needle.isEmpty()) return List.of();
        int limit = Math.max(1, maxResults);
        String cmpNeedle = caseSensitive ? needle : needle.toLowerCase(Locale.ROOT);
        List<Match> out = new ArrayList<>();

        try (var walk = Files.walk(projectRoot)) {
            var it = walk.filter(Files::isRegularFile).filter(ProjectSearch::searchable).iterator();
            while (it.hasNext() && out.size() < limit) {
                Path file = it.next();
                scanFile(file, cmpNeedle, caseSensitive, limit, out);
            }
        }
        return List.copyOf(out);
    }

    public static ReplaceResult replaceAll(Path projectRoot, String query, String replacement, boolean caseSensitive)
            throws IOException {
        String needle = query == null ? "" : query;
        if (needle.isEmpty()) throw new IllegalArgumentException("Aranacak metin boş olamaz.");
        String replacementText = replacement == null ? "" : replacement;
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern pattern = Pattern.compile(Pattern.quote(needle), flags);
        String quotedReplacement = Matcher.quoteReplacement(replacementText);
        int files = 0;
        int replacements = 0;
        List<String> errors = new ArrayList<>();
        Set<Path> changed = new LinkedHashSet<>();

        try (var walk = Files.walk(projectRoot)) {
            var it = walk.filter(Files::isRegularFile).filter(ProjectSearch::searchable).iterator();
            while (it.hasNext()) {
                Path file = it.next();
                try {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = pattern.matcher(text);
                    int count = 0;
                    while (matcher.find()) count++;
                    if (count == 0) continue;
                    FileUtil.recoveryCopy(projectRoot, file);
                    String next = pattern.matcher(text).replaceAll(quotedReplacement);
                    if (next.equals(text)) continue;
                    FileUtil.writeStringAtomic(file, next);
                    files++;
                    replacements += count;
                    changed.add(file);
                } catch (Exception ex) {
                    errors.add(file + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                }
            }
        }
        return new ReplaceResult(files, replacements, List.copyOf(errors), Set.copyOf(changed));
    }

    private static void scanFile(Path file, String cmpNeedle, boolean caseSensitive, int limit, List<Match> out) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null && out.size() < limit) {
                lineNo++;
                String cmpLine = caseSensitive ? line : line.toLowerCase(Locale.ROOT);
                if (cmpLine.contains(cmpNeedle)) out.add(new Match(file, lineNo, compact(line)));
            }
        } catch (Exception ignored) { }
    }

    private static boolean searchable(Path file) {
        if (!FileUtil.isTextFile(file)) return false;
        for (Path part : file) {
            String p = part.toString();
            if (p.equals(".gradle") || p.equals(".git") || p.equals("build") || p.equals(".idea") || p.equals(".eluajs")) return false;
        }
        try { return Files.size(file) <= MAX_FILE_BYTES; }
        catch (IOException e) { return false; }
    }

    private static String compact(String line) {
        String s = line.strip().replace('\t', ' ');
        s = s.replaceAll(" {2,}", " ");
        if (s.length() > 150) s = s.substring(0, 147) + "...";
        return s;
    }
}
