package eluajs.ai;

import eluajs.util.AppPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/** Persistent AI provider settings. Keys stay in the user's home directory. */
public final class AiConfig {
    private static final Path FILE = AppPaths.config().resolve("ai.properties");
    private final Properties props = new Properties();

    public static AiConfig load() {
        AiConfig cfg = new AiConfig();
        if (!Files.isRegularFile(FILE)) return cfg;
        try (InputStream in = Files.newInputStream(FILE)) { cfg.props.load(in); }
        catch (Exception ignored) { }
        return cfg;
    }

    public AiProvider provider() { return AiProvider.fromId(props.getProperty("provider", AiProvider.OPENAI.name())); }
    public void setProvider(AiProvider provider) { props.setProperty("provider", provider.name()); }

    public String model(AiProvider provider) {
        String value = props.getProperty("model." + provider.name());
        return value == null || value.isBlank() ? provider.defaultModel() : value.trim();
    }
    public void setModel(AiProvider provider, String model) {
        if (model == null || model.isBlank()) props.remove("model." + provider.name());
        else props.setProperty("model." + provider.name(), model.trim());
    }

    public String baseUrl(AiProvider provider) {
        String value = props.getProperty("base." + provider.name());
        return value == null || value.isBlank() ? provider.defaultBaseUrl() : value.trim();
    }
    public void setBaseUrl(AiProvider provider, String url) {
        if (url == null || url.isBlank()) props.remove("base." + provider.name());
        else props.setProperty("base." + provider.name(), url.trim());
    }

    public String key(AiProvider provider) {
        String value = props.getProperty("key." + provider.name());
        return value == null ? "" : value.trim();
    }
    public boolean hasKey(AiProvider provider) { return !key(provider).isEmpty(); }

    public int tokenBudget() {
        try {
            int value = Integer.parseInt(props.getProperty("tokenBudget", "24000").trim());
            return Math.max(4000, Math.min(200000, value));
        } catch (Exception ex) {
            return 24000;
        }
    }

    public void setTokenBudget(int budget) {
        props.setProperty("tokenBudget", String.valueOf(Math.max(4000, Math.min(200000, budget))));
    }

    public boolean tokenBudgetEnabled() {
        return Boolean.parseBoolean(props.getProperty("tokenBudgetEnabled", "false"));
    }

    public void setTokenBudgetEnabled(boolean enabled) {
        props.setProperty("tokenBudgetEnabled", enabled ? "true" : "false");
    }
    public void setKey(AiProvider provider, String key) {
        if (key == null || key.isBlank()) props.remove("key." + provider.name());
        else props.setProperty("key." + provider.name(), key.trim());
    }

    public String activeKey() { return key(provider()); }
    public String activeModel() { return model(provider()); }
    public String activeBaseUrl() { return baseUrl(provider()); }

    public void save() {
        Path parent = FILE.getParent();
        try {
            Files.createDirectories(parent);
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "eLuajs AI settings - keep this file private");
            }
            try { Files.setPosixFilePermissions(FILE, PosixFilePermissions.fromString("rw-------")); }
            catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }
}
