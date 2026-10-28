package eluajs.project;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import eluajs.util.FileUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ProjectMigrator {
    private ProjectMigrator() {}

    public record Result(boolean changed, String message) {}

    public static Result migrate(Path root) {
        try {
            boolean changed = false;
            Path gradleProps = root.resolve("gradle.properties");
            if (Files.isRegularFile(gradleProps)) {
                String text = Files.readString(gradleProps, StandardCharsets.UTF_8);
                if (text.contains("android.useAndroidX=false")) {
                    text = text.replace("android.useAndroidX=false", "android.useAndroidX=true");
                    FileUtil.writeStringAtomic(gradleProps, text);
                    changed = true;
                } else if (!text.contains("android.useAndroidX=")) {
                    if (!text.endsWith("\n")) text += "\n";
                    text += "android.useAndroidX=true\n";
                    FileUtil.writeStringAtomic(gradleProps, text);
                    changed = true;
                }
            }

            Path meta = root.resolve(".eluajs/project.properties");
            if (Files.isRegularFile(meta)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(meta)) { p.load(in); }
                int templateVersion;
                try { templateVersion = Integer.parseInt(p.getProperty("templateVersion", "1")); }
                catch (NumberFormatException ex) { templateVersion = 1; }
                if (templateVersion < 5) {
                    p.setProperty("templateVersion", "5");
                    try (OutputStream out = Files.newOutputStream(meta)) { p.store(out, "eLuajs project metadata"); }
                    changed = true;
                }
            }
            try { ProjectReleaseManager.ensureGitIgnore(root); } catch (IOException ignored) { }
            return new Result(changed, changed ? "Proje v1.3 uyumluluğuna yükseltildi (AndroidX + Play Store AAB signing altyapısı hazır)." : "Proje güncel.");
        } catch (IOException ex) {
            return new Result(false, "Proje uyumluluk kontrolü tamamlanamadı: " + ex.getMessage());
        }
    }
}
