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

    public String repo() {
        String value = props.getProperty("repo", "whyefkan/eLuajs");
        return value == null ? "" : value.trim();
    }

    public void setRepo(String repo) {
        props.setProperty("repo", repo == null ? "" : repo.trim());
    }

    public boolean autoCheck() {
        return !"false".equalsIgnoreCase(props.getProperty("auto", "true"));
    }

    public void setAutoCheck(boolean auto) {
        props.setProperty("auto", auto ? "true" : "false");
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
