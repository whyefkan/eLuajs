package eluajs.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class Platform {
    private Platform() {}

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String exe(String name) {
        return isWindows() ? name + ".exe" : name;
    }

    public static String bat(String name) {
        return isWindows() ? name + ".bat" : name;
    }

    public static boolean runnable(Path path) {
        return path != null && (isWindows() ? Files.isRegularFile(path) : Files.isExecutable(path));
    }
}
