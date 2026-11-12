package eluajs.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Writes uncaught exceptions to ~/.eluajs/crash.log instead of losing them. */
public final class CrashReporter {
    private CrashReporter() {}

    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> record(thread, error));
    }

    public static Path logFile() {
        return AppPaths.config().resolve("crash.log");
    }

    private static final long MAX_LOG_BYTES = 1_000_000L;

    public static synchronized void record(Thread thread, Throwable error) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            writer.println("===== " + Instant.now() + " =====");
            writer.println("thread: " + (thread == null ? "?" : thread.getName()));
            writer.println("version: " + AppInfo.version());
            if (error != null) error.printStackTrace(writer);
            writer.println();
        }
        try {
            Path file = logFile();
            Files.createDirectories(file.getParent());
            FileUtil.secureDir(file.getParent());
            try {
                if (Files.isRegularFile(file) && Files.size(file) > MAX_LOG_BYTES) {
                    Files.move(file, file.resolveSibling("crash.log.1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) { }
            Files.writeString(file, buffer.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            FileUtil.secureFile(file);
        } catch (Exception ignored) { }
    }

    public static String tail(int maxChars) {
        try {
            Path file = logFile();
            if (!Files.isRegularFile(file)) return "";
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return text.length() <= maxChars ? text : text.substring(text.length() - maxChars);
        } catch (Exception ex) {
            return "";
        }
    }
}
