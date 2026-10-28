package eluajs.project;

import eluajs.model.ProjectMeta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class ProjectActivityManager {
    private ProjectActivityManager() {}

    public record CreatedActivity(String className, Path javaFile) {}
    public record ActivityInfo(String className, Path javaFile, boolean registered) {
        @Override public String toString() {
            if (javaFile == null) return className;
            if (className.equals("MainActivity")) return "★  MainActivity   •   Ana sayfa";
            return className + (registered ? "   •   Manifest kayıtlı" : "   •   Manifest kaydı yok");
        }
    }

    public static CreatedActivity create(ProjectMeta meta, String requestedName, String pageTitle) throws IOException {
        String className = normalizeClassName(requestedName);
        String title = (pageTitle == null || pageTitle.isBlank()) ? className.replace("Activity", "") : pageTitle.trim();

        Path javaDir = packageJavaDir(meta);
        Path javaFile = javaDir.resolve(className + ".java");
        if (Files.exists(javaFile)) throw new IOException(className + ".java zaten var.");

        Files.createDirectories(javaDir);
        Files.writeString(javaFile, activitySource(meta.packageName(), className, title), StandardCharsets.UTF_8);
        try {
            registerInManifest(meta.rootDir(), className);
        } catch (IOException ex) {
            Files.deleteIfExists(javaFile);
            throw ex;
        }
        return new CreatedActivity(className, javaFile);
    }

    /** Lists extra Activity pages. MainActivity is intentionally excluded from deletion. */
    public static List<ActivityInfo> list(ProjectMeta meta) throws IOException {
        Path dir = packageJavaDir(meta);
        List<ActivityInfo> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) return result;
        String manifest = readManifest(meta.rootDir());
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Activity.java"))
                    .filter(p -> !p.getFileName().toString().equals("MainActivity.java"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> {
                        String className = p.getFileName().toString().replaceFirst("\\.java$", "");
                        result.add(new ActivityInfo(className, p, manifestHasActivity(manifest, className)));
                    });
        }
        return result;
    }


    /** Lists MainActivity plus extra Activity pages for the editor side panel. */
    public static List<ActivityInfo> listAll(ProjectMeta meta) throws IOException {
        List<ActivityInfo> result = new ArrayList<>();
        Path main = packageJavaDir(meta).resolve("MainActivity.java");
        if (Files.isRegularFile(main)) result.add(new ActivityInfo("MainActivity", main, true));
        result.addAll(list(meta));
        return result;
    }

    public static void delete(ProjectMeta meta, String className) throws IOException {
        if (className == null || className.isBlank()) throw new IOException("Silinecek sayfa seçilmedi.");
        String safe = className.trim();
        if (safe.endsWith(".java")) safe = safe.substring(0, safe.length() - 5);
        if (safe.equals("MainActivity")) throw new IOException("MainActivity ana sayfadır ve bu ekrandan silinemez.");
        if (!safe.matches("[A-Za-z_$][A-Za-z0-9_$]*")) throw new IOException("Geçersiz Activity adı.");

        Path javaFile = packageJavaDir(meta).resolve(safe + ".java");
        if (!Files.isRegularFile(javaFile)) throw new IOException(safe + ".java bulunamadı.");

        unregisterFromManifest(meta.rootDir(), safe);
        Files.delete(javaFile);
    }

    public static String navigationSnippet(String className) {
        return "Intent intent = new Intent(this, " + className + ".class);\nstartActivity(intent);";
    }

    private static Path packageJavaDir(ProjectMeta meta) {
        return meta.rootDir().resolve("app/src/main/java").resolve(meta.packageName().replace('.', '/'));
    }

    private static String normalizeClassName(String input) {
        if (input == null) throw new IllegalArgumentException("Sayfa adı boş olamaz.");
        String raw = input.trim();
        if (raw.endsWith(".java")) raw = raw.substring(0, raw.length() - 5);
        raw = raw.replaceAll("[^A-Za-z0-9_$]", "");
        if (raw.isBlank()) throw new IllegalArgumentException("Geçerli bir sayfa adı yazın.");
        if (!Character.isJavaIdentifierStart(raw.charAt(0))) raw = "Page" + raw;
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (i == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) safe.append(c);
        }
        if (safe.isEmpty()) throw new IllegalArgumentException("Geçerli bir sayfa adı yazın.");
        safe.setCharAt(0, Character.toUpperCase(safe.charAt(0)));
        String result = safe.toString();
        if (!result.endsWith("Activity")) result += "Activity";
        if (result.equals("MainActivity")) throw new IllegalArgumentException("MainActivity zaten ana sayfadır. Başka bir isim seçin.");
        return result;
    }

    private static void registerInManifest(Path projectRoot, String className) throws IOException {
        Path manifest = projectRoot.resolve("app/src/main/AndroidManifest.xml");
        String xml = readManifest(projectRoot);
        if (manifestHasActivity(xml, className)) return;
        int idx = xml.lastIndexOf("</application>");
        if (idx < 0) throw new IOException("AndroidManifest.xml içinde </application> bulunamadı.");
        String entry = "        <activity\n" +
                "            android:name=\"." + className + "\"\n" +
                "            android:exported=\"false\" />\n";
        String updated = xml.substring(0, idx) + entry + xml.substring(idx);
        Files.writeString(manifest, updated, StandardCharsets.UTF_8);
    }

    private static void unregisterFromManifest(Path projectRoot, String className) throws IOException {
        Path manifest = projectRoot.resolve("app/src/main/AndroidManifest.xml");
        String xml = readManifest(projectRoot);
        String quoted = Pattern.quote(className);
        Pattern activity = Pattern.compile(
                "(?s)\\s*<activity\\b(?=[^>]*android:name\\s*=\\s*['\"](?:\\.|[A-Za-z0-9_$.]+\\.)?" + quoted + "['\"])[^>]*(?:/\\s*>|>.*?</activity\\s*>)\\s*");
        String updated = activity.matcher(xml).replaceFirst("\n");
        if (!updated.equals(xml)) Files.writeString(manifest, updated, StandardCharsets.UTF_8);
    }

    private static String readManifest(Path projectRoot) throws IOException {
        Path manifest = projectRoot.resolve("app/src/main/AndroidManifest.xml");
        if (!Files.isRegularFile(manifest)) throw new IOException("AndroidManifest.xml bulunamadı.");
        return Files.readString(manifest, StandardCharsets.UTF_8);
    }

    private static boolean manifestHasActivity(String xml, String className) {
        Pattern p = Pattern.compile("android:name\\s*=\\s*['\"](?:\\.|[A-Za-z0-9_$.]+\\.)?" + Pattern.quote(className) + "['\"]");
        return p.matcher(xml).find();
    }

    private static String activitySource(String packageName, String className, String title) {
        return """
                package %s;

                import android.app.Activity;
                import android.os.Bundle;
                import android.graphics.Color;
                import android.view.Gravity;
                import android.widget.Button;
                import android.widget.LinearLayout;
                import android.widget.TextView;

                public class %s extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);

                        LinearLayout root = new LinearLayout(this);
                        root.setOrientation(LinearLayout.VERTICAL);
                        root.setGravity(Gravity.CENTER);
                        root.setPadding(48, 48, 48, 48);
                        root.setBackgroundColor(Color.rgb(18, 22, 28));

                        TextView title = new TextView(this);
                        title.setText("%s");
                        title.setTextColor(Color.WHITE);
                        title.setTextSize(28);
                        title.setGravity(Gravity.CENTER);

                        TextView subtitle = new TextView(this);
                        subtitle.setText("eLuajs sayfası");
                        subtitle.setTextColor(Color.rgb(144, 238, 200));
                        subtitle.setTextSize(16);
                        subtitle.setPadding(0, 18, 0, 28);
                        subtitle.setGravity(Gravity.CENTER);

                        Button back = new Button(this);
                        back.setText("Geri");
                        back.setOnClickListener(v -> finish());

                        root.addView(title);
                        root.addView(subtitle);
                        root.addView(back);
                        setContentView(root);
                    }
                }
                """.formatted(packageName, className, javaString(title)).stripLeading();
    }

    private static String javaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
