package eluajs.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private AppPaths() {}

    public static Path home() { return Paths.get(System.getProperty("user.home")); }
    public static Path config() { return home().resolve(".eluajs"); }
    public static Path defaultProjects() { return home().resolve("eLuajsProjects"); }
    public static Path tools() { return config().resolve("tools"); }
    public static Path downloads() { return config().resolve("downloads"); }
    public static Path managedSdk() { return config().resolve("android-sdk"); }

    public static void ensureBaseDirs() {
        try {
            Files.createDirectories(config());
            Files.createDirectories(defaultProjects());
            Files.createDirectories(tools());
            Files.createDirectories(downloads());
            FileUtil.secureDir(config());
            FileUtil.secureDir(tools());
            FileUtil.secureDir(downloads());
        } catch (IOException e) {
            throw new IllegalStateException("eLuajs klasörleri oluşturulamadı", e);
        }
    }
}
