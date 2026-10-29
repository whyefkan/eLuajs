package eluajs.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Lightweight recursive project watcher using the JDK WatchService. */
public final class ProjectWatcher implements AutoCloseable {
    private static final Set<String> SKIP = Set.of(
            ".git", ".gradle", ".idea", ".eluajs", "build", "release", "out", "dist", "node_modules");
    private final Path root;
    private final Runnable callback;
    private final WatchService service;
    private final Thread thread;

    private ProjectWatcher(Path root, Runnable callback, WatchService service, Thread thread) {
        this.root = root;
        this.callback = callback;
        this.service = service;
        this.thread = thread;
    }

    public static ProjectWatcher start(Path root, Runnable callback) {
        try {
            WatchService service = FileSystems.getDefault().newWatchService();
            ProjectWatcher[] holder = new ProjectWatcher[1];
            Thread thread = new Thread(() -> holder[0].loop(), "eluajs-project-watch");
            thread.setDaemon(true);
            ProjectWatcher watcher = new ProjectWatcher(root.toAbsolutePath().normalize(), callback, service, thread);
            holder[0] = watcher;
            registerTree(watcher.root, service);
            thread.start();
            return watcher;
        } catch (IOException ex) {
            return null;
        }
    }

    private void loop() {
        boolean pending = false;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = pending ? service.poll(250, TimeUnit.MILLISECONDS) : service.take();
                if (key == null) {
                    if (pending) {
                        pending = false;
                        SwingCallback.fire(callback);
                    }
                    continue;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    Path dir = (Path) key.watchable();
                    Path changed = dir.resolve((Path) event.context());
                    if (Files.isDirectory(changed, LinkOption.NOFOLLOW_LINKS)) {
                        if (!skipped(changed)) registerTree(changed, service);
                    }
                    pending = true;
                }
                key.reset();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private static void registerTree(Path root, WatchService service) {
        try (var walk = Files.walk(root, 12)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> !skipped(p))
                    .forEach(p -> {
                        try { p.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE); }
                        catch (IOException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    private static boolean skipped(Path path) {
        for (Path part : path) if (SKIP.contains(part.toString())) return true;
        return false;
    }

    @Override public void close() {
        thread.interrupt();
        try { service.close(); } catch (IOException ignored) { }
    }

    private static final class SwingCallback {
        static void fire(Runnable callback) {
            if (callback == null) return;
            javax.swing.SwingUtilities.invokeLater(() -> {
                try { callback.run(); } catch (Exception ignored) { }
            });
        }
    }
}
