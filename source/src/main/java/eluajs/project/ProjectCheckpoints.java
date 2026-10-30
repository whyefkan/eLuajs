package eluajs.project;

import eluajs.util.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Local whole-project snapshots without Git. */
public final class ProjectCheckpoints {
    private ProjectCheckpoints() {}

    public static Path dir(Path root) {
        return root.resolve(".eluajs/checkpoints");
    }

    public static Path create(Path root, String label) throws IOException {
        Path dir = dir(root);
        Files.createDirectories(dir);
        FileUtil.secureDir(dir);
        String safe = (label == null || label.isBlank() ? "checkpoint" : label.trim())
                .replaceAll("[^A-Za-z0-9._-]+", "-");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path zip = dir.resolve(stamp + "-" + safe + ".zip");
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            Path base = root.toAbsolutePath().normalize();
            try (var walk = Files.walk(base)) {
                for (Path path : walk.filter(Files::isRegularFile).toList()) {
                    Path rel = base.relativize(path);
                    String first = rel.getName(0).toString();
                    if (first.equals(".eluajs") || first.equals(".git") || first.equals(".gradle")
                            || first.equals("build") || first.equals("release") || first.equals("out")) continue;
                    try {
                        long size = Files.size(path);
                        if (size > 8L * 1024 * 1024) continue;
                        ZipEntry entry = new ZipEntry(rel.toString().replace('\\', '/'));
                        zos.putNextEntry(entry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (Exception ignored) { }
                }
            }
        }
        FileUtil.secureFile(zip);
        return zip;
    }

    public static List<Path> list(Path root) {
        Path dir = dir(root);
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparingLong(ProjectCheckpoints::modified).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public static void restore(Path root, Path zip) throws IOException {
        if (zip == null || !Files.isRegularFile(zip)) throw new IOException("Checkpoint bulunamadı.");
        Path base = root.toAbsolutePath().normalize();
        try (InputStream in = Files.newInputStream(zip); ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = base.resolve(entry.getName()).normalize();
                if (!out.startsWith(base)) throw new IOException("Geçersiz checkpoint girdisi: " + entry.getName());
                if (entry.isDirectory()) Files.createDirectories(out);
                else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    public static void delete(Path root, Path zip) throws IOException {
        Path dir = dir(root).toAbsolutePath().normalize();
        Path target = zip.toAbsolutePath().normalize();
        if (!target.startsWith(dir)) throw new IOException("Geçersiz checkpoint yolu.");
        Files.deleteIfExists(target);
    }

    private static long modified(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
    }
}
