package eluajs.util;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUtil {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private FileUtil() {}

    public static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static void download(String url, Path target, Consumer<String> log) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(partial);
        if (log != null) log.accept("İndiriliyor: " + url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(20)).header("User-Agent", "eLuajs/1.3").build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(partial);
            throw new IOException("İndirme başarısız. HTTP " + response.statusCode());
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        if (log != null) log.accept("İndirme tamamlandı: " + target.getFileName());
    }

    public static void unzip(Path zip, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest.normalize())) throw new IOException("Geçersiz ZIP girdisi: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    if (entry.getName().endsWith("/bin/sdkmanager") || entry.getName().endsWith("/bin/avdmanager")) out.toFile().setExecutable(true);
                }
                zis.closeEntry();
            }
        }
    }

    /** Writes text through a sibling temporary file and replaces the target only after the write succeeds. */
    public static void writeStringAtomic(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = target.resolveSibling(target.getFileName() + ".eluajs.tmp");
        Files.writeString(tmp, content, java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Creates a lightweight recovery copy inside .eluajs/recovery before overwriting an existing file. */
    public static Path recoveryCopy(Path projectRoot, Path source) throws IOException {
        if (projectRoot == null || source == null || !Files.isRegularFile(source)) return null;
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (!normalizedSource.startsWith(normalizedRoot)) return null;
        Path rel = normalizedRoot.relativize(normalizedSource);
        Path recovery = normalizedRoot.resolve(".eluajs/recovery").resolve(rel.toString() + ".bak");
        Files.createDirectories(recovery.getParent());
        Files.copy(normalizedSource, recovery, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return recovery;
    }

    public static boolean isTextFile(Path path) {
        String n = path.getFileName().toString().toLowerCase();
        return n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml") || n.endsWith(".gradle") ||
                n.endsWith(".kts") || n.endsWith(".properties") || n.endsWith(".txt") || n.endsWith(".md") ||
                n.endsWith(".json") || n.endsWith(".pro") || n.endsWith(".gitignore") || n.endsWith(".html") ||
                n.endsWith(".htm") || n.endsWith(".css") || n.endsWith(".js") || n.endsWith(".ts") ||
                n.endsWith(".jsx") || n.endsWith(".tsx") || n.endsWith(".svg") || n.endsWith(".yaml") ||
                n.endsWith(".yml") || n.endsWith(".toml") || n.endsWith(".ini") || n.endsWith(".sh") ||
                n.endsWith(".bat") || n.endsWith(".ps1");
    }
}
