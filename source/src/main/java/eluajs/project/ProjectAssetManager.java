package eluajs.project;

import java.io.IOException;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProjectAssetManager {
    private ProjectAssetManager() {}

    public enum Destination {
        ASSETS("assets — her tür dosya", "app/src/main/assets", false),
        DRAWABLE("drawable — görseller / XML", "app/src/main/res/drawable", true),
        RAW("raw — ses, json, veri", "app/src/main/res/raw", true),
        FONT("font — TTF / OTF", "app/src/main/res/font", true);

        private final String label;
        private final String relativePath;
        private final boolean androidResourceName;

        Destination(String label, String relativePath, boolean androidResourceName) {
            this.label = label;
            this.relativePath = relativePath;
            this.androidResourceName = androidResourceName;
        }
        public String relativePath() { return relativePath; }
        public boolean androidResourceName() { return androidResourceName; }
        @Override public String toString() { return label; }
    }

    public record Imported(Path source, Path target) {}

    public static List<Imported> importFiles(Path projectRoot, List<Path> files, Destination destination) throws IOException {
        if (files == null || files.isEmpty()) return List.of();
        Path targetDir = projectRoot.resolve(destination.relativePath()).normalize();
        if (!targetDir.startsWith(projectRoot.normalize())) throw new IOException("Geçersiz asset hedefi.");
        Files.createDirectories(targetDir);

        List<Imported> imported = new ArrayList<>();
        for (Path source : files) {
            if (!Files.isRegularFile(source)) continue;
            String name = destination.androidResourceName() ? resourceFileName(source.getFileName().toString()) : source.getFileName().toString();
            Path target = uniqueTarget(targetDir, name);
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            imported.add(new Imported(source, target));
        }
        return imported;
    }

    private static Path uniqueTarget(Path dir, String fileName) {
        Path candidate = dir.resolve(fileName);
        if (!Files.exists(candidate)) return candidate;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 2; ; i++) {
            candidate = dir.resolve(base + "_" + i + ext);
            if (!Files.exists(candidate)) return candidate;
        }
    }

    private static String resourceFileName(String original) {
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        String ext = dot > 0 ? original.substring(dot).toLowerCase(Locale.ROOT) : "";
        String ascii = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        ascii = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        ascii = ascii.replaceAll("^_+|_+$", "");
        if (ascii.isBlank()) ascii = "asset";
        if (!Character.isLetter(ascii.charAt(0))) ascii = "asset_" + ascii;
        return ascii + ext;
    }
}
