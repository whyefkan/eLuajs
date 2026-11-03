package eluajs.update;

import eluajs.util.AppPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class UpdateConfig {
    private static final Path FILE = AppPaths.config().resolve("update.properties");
    private final Properties props = new Properties();

    public static UpdateConfig load() {
        UpdateConfig cfg = new UpdateConfig();
        if (!Files.isRegularFile(FILE)) return cfg;
        try (InputStream in = Files.newInputStream(FILE)) { cfg.props.load(in); }
        catch (Exception ignored) { }
        return cfg;
    }

    public boolean autoCheck() {
        return !"false".equalsIgnoreCase(props.getProperty("auto", "true"));
    }

    public void setAutoCheck(boolean auto) {
        props.setProperty("auto", auto ? "true" : "false");
    }

    public long lastChecked() {
        try { return Long.parseLong(props.getProperty("lastChecked", "0").trim()); }
        catch (Exception ex) { return 0L; }
    }

    public String lastError() {
        return props.getProperty("lastError", "");
    }

    public boolean onboardingSeen() {
        return Boolean.parseBoolean(props.getProperty("onboardingSeen", "false"));
    }

    public void setOnboardingSeen(boolean seen) {
        props.setProperty("onboardingSeen", seen ? "true" : "false");
    }

    public void setLastCheck(long time, String error) {
        props.setProperty("lastChecked", String.valueOf(Math.max(0, time)));
        if (error == null || error.isBlank()) props.remove("lastError");
        else props.setProperty("lastError", error);
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "eLuajs update settings");
            }
        } catch (Exception ignored) { }
    }
}
