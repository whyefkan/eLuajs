package eluajs.project;

import eluajs.model.ProjectMeta;
import eluajs.util.FileUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Creates a complete, linked Android page: Activity, layout, string and Manifest entry. */
public final class ProjectPageScaffold {
    public record Result(String className, List<Path> files) {}

    private ProjectPageScaffold() {}

    public static Result create(Path root, String requestedName, String pageTitle) throws IOException {
        if (root == null || !Files.isDirectory(root)) throw new IOException("Proje bulunamadı.");
        ProjectGradleManager.Versions versions = ProjectGradleManager.read(root);
        String packageName = versions.applicationId().isBlank() ? versions.namespace() : versions.applicationId();
        if (packageName.isBlank()) throw new IOException("Paket adı bulunamadı.");
        ProjectMeta meta = new ProjectMeta(
                root.getFileName() == null ? "eLuajs" : root.getFileName().toString(),
                versions.versionName(), Math.max(1, versions.versionCode()), packageName, null, root);

        ProjectActivityManager.CreatedActivity created = ProjectActivityManager.create(meta, requestedName, pageTitle);
        String className = created.className();
        String base = className.endsWith("Activity")
                ? className.substring(0, className.length() - "Activity".length()) : className;
        String snake = camelToSnake(base);
        if (snake.isBlank()) snake = "page";
        String layoutName = "activity_" + snake;
        String titleName = snake + "_title";

        Path module = ProjectLayout.moduleDir(root);
        Path layout = module.resolve("src/main/res/layout/" + layoutName + ".xml");
        Path strings = module.resolve("src/main/res/values/strings.xml");
        if (!FileUtil.isSafeProjectPath(root, layout) || !FileUtil.isSafeProjectPath(root, strings)) {
            rollback(meta, className);
            throw new IOException("Sayfa yolu güvenli değil.");
        }
        if (Files.exists(layout)) {
            rollback(meta, className);
            throw new IOException(layoutName + ".xml zaten var.");
        }

        String originalStrings = Files.isRegularFile(strings) ? Files.readString(strings, StandardCharsets.UTF_8) : null;
        List<Path> files = new ArrayList<>();
        try {
            Files.createDirectories(layout.getParent());
            FileUtil.writeStringAtomic(layout, layoutSource(layoutName, titleName));
            files.add(created.javaFile());
            files.add(layout);

            String title = (pageTitle == null || pageTitle.isBlank())
                    ? base.replaceAll("([a-z0-9])([A-Z])", "$1 $2") : pageTitle.trim();
            String stringXml = stringsSource(originalStrings, titleName, title);
            if (stringXml != null) {
                FileUtil.writeStringAtomic(strings, stringXml);
                files.add(strings);
            }
            FileUtil.writeStringAtomic(created.javaFile(), javaSource(packageName, className, layoutName));
            return new Result(className, List.copyOf(files));
        } catch (Exception ex) {
            if (originalStrings == null) Files.deleteIfExists(strings);
            else try { FileUtil.writeStringAtomic(strings, originalStrings); } catch (Exception ignored) { }
            Files.deleteIfExists(layout);
            rollback(meta, className);
            throw ex instanceof IOException io ? io : new IOException(ex.getMessage(), ex);
        }
    }

    private static void rollback(ProjectMeta meta, String className) {
        try { ProjectActivityManager.delete(meta, className); } catch (Exception ignored) { }
    }

    private static String stringsSource(String original, String name, String title) {
        if (original != null && original.contains("name=\"" + name + "\"")) return null;
        String entry = "    <string name=\"" + xml(name) + "\">" + xml(title) + "</string>\n";
        if (original == null) return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" + entry + "</resources>\n";
        int idx = original.lastIndexOf("</resources>");
        if (idx < 0) throw new IllegalArgumentException("strings.xml geçersiz.");
        String inserted = original.substring(0, idx) + entry + original.substring(idx);
        return inserted;
    }

    private static String layoutSource(String layoutName, String titleName) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical"
                    android:gravity="center"
                    android:padding="32dp"
                    android:background="#12161C">

                    <TextView
                        android:id="@+id/title"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/%s"
                        android:textColor="#F1F6FB"
                        android:textSize="26sp"
                        android:textStyle="bold" />

                    <Button
                        android:id="@+id/backButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="24dp"
                        android:text="@android:string/ok"
                        android:onClick="finish" />

                </LinearLayout>
                """.formatted(titleName);
    }

    private static String javaSource(String packageName, String className, String layoutName) {
        return """
                package %s;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.Button;

                public class %s extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.%s);
                        Button back = findViewById(R.id.backButton);
                        back.setOnClickListener(v -> finish());
                    }
                }
                """.formatted(packageName, className, layoutName);
    }

    private static String camelToSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
