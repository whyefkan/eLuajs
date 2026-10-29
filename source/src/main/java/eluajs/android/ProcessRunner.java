package eluajs.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ProcessRunner {
    private static final long DEFAULT_TIMEOUT_MINUTES = 60;
    private static final java.util.concurrent.atomic.AtomicReference<Process> ACTIVE =
            new java.util.concurrent.atomic.AtomicReference<>();
    private ProcessRunner() {}

    public static void cancelCurrent() {
        Process process = ACTIVE.getAndSet(null);
        if (process != null && process.isAlive()) terminateTree(process);
    }

    public static int run(List<String> command, Path workingDir, Map<String,String> env, String stdin, Consumer<String> log) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        if (env != null) pb.environment().putAll(env);
        Process p = pb.start();
        ACTIVE.set(p);

        if (stdin != null) {
            try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(stdin);
                w.flush();
            }
        } else {
            p.getOutputStream().close();
        }

        ExecutorService readerPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "eluajs-process-output");
            t.setDaemon(true);
            return t;
        });
        Future<?> reader = readerPool.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) if (log != null) log.accept(line);
            } catch (IOException e) {
                if (p.isAlive() && log != null) log.accept("Süreç çıktısı okunamadı: " + e.getMessage());
            }
        });

        try {
            boolean finished = p.waitFor(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                if (log != null) log.accept("HATA: İşlem " + DEFAULT_TIMEOUT_MINUTES + " dakika içinde tamamlanmadı; güvenli şekilde sonlandırılıyor.");
                terminateTree(p);
                throw new IOException("Harici işlem zaman aşımına uğradı: " + String.join(" ", command));
            }
            try { reader.get(10, TimeUnit.SECONDS); }
            catch (ExecutionException | TimeoutException ignored) { }
            return p.exitValue();
        } finally {
            ACTIVE.compareAndSet(p, null);
            if (p.isAlive()) terminateTree(p);
            reader.cancel(true);
            readerPool.shutdownNow();
        }
    }

    private static void terminateTree(Process p) {
        ProcessHandle handle = p.toHandle();
        // Gradle and SDK tools may spawn children. Kill descendants first so a
        // cancelled/timeout build does not leave background Java processes.
        handle.descendants().forEach(child -> {
            try { child.destroy(); } catch (Exception ignored) { }
        });
        try { handle.destroy(); } catch (Exception ignored) { }
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                handle.descendants().forEach(child -> {
                    try { child.destroyForcibly(); } catch (Exception ignored) { }
                });
                try { handle.destroyForcibly(); } catch (Exception ignored) { }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            try { handle.destroyForcibly(); } catch (Exception ignored) { }
        }
    }
}
