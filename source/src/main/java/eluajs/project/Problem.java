package eluajs.project;

import java.nio.file.Path;

public record Problem(Path file, int line, String message, Severity severity) {
    public enum Severity { ERROR, WARNING, INFO }

    public static Problem error(Path file, String message) {
        return new Problem(file, 1, message, Severity.ERROR);
    }

    public static Problem warning(Path file, int line, String message) {
        return new Problem(file, Math.max(1, line), message, Severity.WARNING);
    }

    public String location(Path root) {
        if (file == null) return "";
        String path;
        try { path = root == null ? file.toString() : root.relativize(file).toString(); }
        catch (Exception ex) { path = file.toString(); }
        return path + ":" + Math.max(1, line);
    }
}
