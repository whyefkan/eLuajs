package eluajs.ai;

import eluajs.util.AppPaths;
import eluajs.util.CrashReporter;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/** Persistent AI provider settings. Keys stay in the user's home directory. */
public final class AiConfig {
    private static final Path FILE = AppPaths.config().resolve("ai.properties");
    private final Properties props = new Properties();

    public static AiConfig load() {
        return load(FILE);
    }

    public static AiConfig load(Path file) {
        AiConfig cfg = new AiConfig();
        if (file == null || !Files.isRegularFile(file)) return cfg;
        try (InputStream in = Files.newInputStream(file)) { cfg.props.load(in); }
        catch (Exception ex) { CrashReporter.record(Thread.currentThread(), ex); }
        return cfg;
    }

    public AiConfig copy() {
        AiConfig clone = new AiConfig();
        clone.props.putAll(this.props);
        return clone;
    }

    public AiProvider provider() { return AiProvider.fromId(props.getProperty("provider", AiProvider.OPENAI.name())); }
    public void setProvider(AiProvider provider) {
        if (provider != null) props.setProperty("provider", provider.name());
    }

    public String savedModel(AiProvider provider) {
        if (provider == null) return "";
        String value = props.getProperty("model." + provider.name());
        return value == null ? "" : value.trim();
    }

    public String model(AiProvider provider) {
        if (provider == null) return "";
        String value = savedModel(provider);
        if (!value.isEmpty()) return value;
        List<String> cached = cachedModels(provider);
        if (!cached.isEmpty()) return cached.get(0);
        return provider.defaultModel();
    }

    public void setModel(AiProvider provider, String model) {
        if (provider == null) return;
        if (model == null || model.isBlank()) props.remove("model." + provider.name());
        else props.setProperty("model." + provider.name(), model.trim());
    }

    public List<String> cachedModels(AiProvider provider) {
        if (provider == null) return List.of();
        String raw = props.getProperty("models." + provider.name());
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) items.add(trimmed);
        }
        return List.copyOf(items);
    }

    public boolean hasCachedModels(AiProvider provider) {
        return !cachedModels(provider).isEmpty();
    }

    public void setCachedModels(AiProvider provider, List<String> models) {
        if (provider == null) return;
        String key = "models." + provider.name();
        if (models == null || models.isEmpty()) {
            props.remove(key);
            return;
        }
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String m : models) {
            if (m != null && !m.isBlank()) clean.add(m.trim());
        }
        if (clean.isEmpty()) props.remove(key);
        else props.setProperty(key, String.join("\n", clean));
    }

    public List<String> availableModels(AiProvider provider) {
        if (provider == null) return List.of();
        List<String> cached = cachedModels(provider);
        List<String> base = cached.isEmpty() ? provider.models() : cached;
        LinkedHashSet<String> out = new LinkedHashSet<>(base);
        String saved = savedModel(provider);
        if (!saved.isEmpty()) out.add(saved);
        return new ArrayList<>(out);
    }

    public String baseUrl(AiProvider provider) {
        if (provider == null) return "";
        String value = props.getProperty("base." + provider.name());
        return value == null || value.isBlank() ? provider.defaultBaseUrl() : value.trim();
    }

    public void setBaseUrl(AiProvider provider, String url) {
        if (provider == null) return;
        if (url == null || url.isBlank()) props.remove("base." + provider.name());
        else props.setProperty("base." + provider.name(), url.trim());
    }

    public String key(AiProvider provider) {
        if (provider == null) return "";
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

    public boolean fullAccess() {
        return Boolean.parseBoolean(props.getProperty("fullAccess", "false"));
    }

    public void setFullAccess(boolean enabled) {
        props.setProperty("fullAccess", enabled ? "true" : "false");
    }

    public void setKey(AiProvider provider, String key) {
        if (provider == null) return;
        if (key == null || key.isBlank()) props.remove("key." + provider.name());
        else props.setProperty("key." + provider.name(), key.trim());
    }

    public String activeKey() { return key(provider()); }
    public String activeModel() { return model(provider()); }
    public String activeBaseUrl() { return baseUrl(provider()); }

    public void save() {
        save(FILE);
    }

    public void save(Path file) {
        if (file == null) return;
        Path parent = file.getParent();
        Path temp = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                eluajs.util.FileUtil.secureDir(parent);
            }
            temp = Files.createTempFile(parent != null ? parent : Path.of("."), ".ai-", ".tmp");
            eluajs.util.FileUtil.secureFile(temp);
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "eLuajs AI settings - keep this file private");
            }
            try {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            eluajs.util.FileUtil.secureFile(file);
        } catch (Exception ex) {
            CrashReporter.record(Thread.currentThread(), ex);
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
        }
    }
}
