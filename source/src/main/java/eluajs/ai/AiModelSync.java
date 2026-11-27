package eluajs.ai;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Silently refreshes AI provider model lists in the background on app startup. */
public final class AiModelSync {
    private static final Object SAVE_LOCK = new Object();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final CopyOnWriteArrayList<WeakReference<Consumer<AiProvider>>> LISTENERS = new CopyOnWriteArrayList<>();

    private AiModelSync() {}

    public static void addListener(Consumer<AiProvider> listener) {
        if (listener == null) return;
        pruneListeners();
        LISTENERS.add(new WeakReference<>(listener));
    }

    private static void pruneListeners() {
        LISTENERS.removeIf(ref -> ref.get() == null);
    }

    private static void notifyListeners(AiProvider provider) {
        for (WeakReference<Consumer<AiProvider>> ref : LISTENERS) {
            Consumer<AiProvider> listener = ref.get();
            if (listener == null) {
                LISTENERS.remove(ref);
                continue;
            }
            try {
                listener.accept(provider);
            } catch (Exception ignored) { }
        }
    }

    /** Starts a one-time silent background refresh of all configured providers. */
    public static void refreshOnStartup() {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> refreshAll(null), "eluajs-ai-model-sync");
        worker.setDaemon(true);
        worker.start();
    }

    /** Refreshes every configured provider (active provider first) and returns how many succeeded. */
    public static int refreshAll(Path configFile) {
        AiConfig initial = configFile == null ? AiConfig.load() : AiConfig.load(configFile);
        List<AiProvider> targets = orderedConfiguredProviders(initial);
        int updated = 0;
        for (AiProvider provider : targets) {
            try {
                AiConfig snapshot = initial.copy();
                snapshot.setProvider(provider);
                List<String> models = AiClient.listModelsFast(snapshot);
                if (models == null || models.isEmpty()) continue;
                synchronized (SAVE_LOCK) {
                    AiConfig fresh = configFile == null ? AiConfig.load() : AiConfig.load(configFile);
                    fresh.setCachedModels(provider, models);
                    if (fresh.savedModel(provider).isBlank()) {
                        fresh.setModel(provider, models.get(0));
                    }
                    if (configFile == null) fresh.save();
                    else fresh.save(configFile);
                }
                updated++;
                notifyListeners(provider);
            } catch (Exception ignored) {
                // Offline, invalid key, or local Ollama not running — fail silently.
            }
        }
        return updated;
    }

    static List<AiProvider> orderedConfiguredProviders(AiConfig cfg) {
        List<AiProvider> out = new ArrayList<>();
        AiProvider active = cfg.provider();
        if (isRefreshable(cfg, active)) out.add(active);
        for (AiProvider provider : AiProvider.values()) {
            if (provider != active && isRefreshable(cfg, provider)) {
                out.add(provider);
            }
        }
        return out;
    }

    private static boolean isRefreshable(AiConfig cfg, AiProvider provider) {
        if (provider == null) return false;
        String base = cfg.baseUrl(provider);
        if (base == null || base.isBlank()) return false;
        return !provider.requiresKey() || cfg.hasKey(provider);
    }
}
