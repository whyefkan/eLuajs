package eluajs.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectPermissionManager {
    private ProjectPermissionManager() {}

    public record Permission(String name, String title, String detail) {
        @Override public String toString() { return title + "   •   " + name; }
    }

    public static final List<Permission> COMMON = List.of(
            new Permission("android.permission.INTERNET", "İnternet", "API, Firebase, web ve ağ bağlantıları"),
            new Permission("android.permission.CAMERA", "Kamera", "Fotoğraf/video çekimi"),
            new Permission("android.permission.RECORD_AUDIO", "Mikrofon", "Ses kaydı"),
            new Permission("android.permission.ACCESS_FINE_LOCATION", "Hassas konum", "GPS / hassas konum"),
            new Permission("android.permission.ACCESS_COARSE_LOCATION", "Yaklaşık konum", "Yaklaşık konum"),
            new Permission("android.permission.POST_NOTIFICATIONS", "Bildirim", "Yeni Android sürümlerinde bildirim izni"),
            new Permission("android.permission.READ_MEDIA_IMAGES", "Fotoğrafları okuma", "Cihazdaki resimlere erişim"),
            new Permission("android.permission.READ_MEDIA_VIDEO", "Videoları okuma", "Cihazdaki videolara erişim"),
            new Permission("android.permission.READ_MEDIA_AUDIO", "Sesleri okuma", "Cihazdaki ses dosyalarına erişim"),
            new Permission("android.permission.ACTIVITY_RECOGNITION", "Aktivite / adım", "Adım ve fiziksel aktivite algılama"),
            new Permission("android.permission.BLUETOOTH_SCAN", "Bluetooth tarama", "Yakındaki Bluetooth cihazlarını bulma"),
            new Permission("android.permission.BLUETOOTH_CONNECT", "Bluetooth bağlantı", "Bluetooth cihazlarına bağlanma"),
            new Permission("android.permission.VIBRATE", "Titreşim", "Cihaz titreşimi"),
            new Permission("android.permission.BODY_SENSORS", "Vücut sensörleri", "Nabız gibi vücut sensörleri")
    );

    private static final Pattern USES_PERMISSION = Pattern.compile(
            "<uses-permission\\b[^>]*android:name\\s*=\\s*['\"]([^'\"]+)['\"][^>]*/\\s*>");

    public static Set<String> list(Path root) throws IOException {
        String xml = read(root);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher m = USES_PERMISSION.matcher(xml);
        while (m.find()) result.add(m.group(1));
        return result;
    }

    public static void set(Path root, String permission, boolean enabled) throws IOException {
        String name = normalize(permission);
        Path manifest = manifest(root);
        String xml = Files.readString(manifest, StandardCharsets.UTF_8);
        boolean exists = list(root).contains(name);
        if (enabled && !exists) {
            int app = xml.indexOf("<application");
            if (app < 0) throw new IOException("AndroidManifest.xml içinde <application> bulunamadı.");
            int insert = lineStart(xml, app);
            String entry = "    <uses-permission android:name=\"" + name + "\" />\n";
            xml = xml.substring(0, insert) + entry + xml.substring(insert);
            eluajs.util.FileUtil.recoveryCopy(root, manifest);
            Files.writeString(manifest, xml, StandardCharsets.UTF_8);
        } else if (!enabled && exists) {
            String q = Pattern.quote(name);
            String pattern = "(?m)^\\s*<uses-permission\\b(?=[^>]*android:name\\s*=\\s*['\"]" + q + "['\"])[^>]*/\\s*>\\s*\\R?";
            String updated = xml.replaceFirst(pattern, "");
            eluajs.util.FileUtil.recoveryCopy(root, manifest);
            Files.writeString(manifest, updated, StandardCharsets.UTF_8);
        }
    }

    public static void applyCommon(Path root, Set<String> selected) throws IOException {
        Set<String> existing = list(root);
        Set<String> commonNames = new LinkedHashSet<>();
        for (Permission p : COMMON) commonNames.add(p.name());
        for (String name : commonNames) {
            boolean wanted = selected.contains(name);
            if (wanted != existing.contains(name)) set(root, name, wanted);
        }
    }

    public static void addCustom(Path root, String permission) throws IOException { set(root, permission, true); }

    private static String normalize(String permission) throws IOException {
        if (permission == null) throw new IOException("İzin adı boş olamaz.");
        String s = permission.trim();
        if (s.isBlank()) throw new IOException("İzin adı boş olamaz.");
        if (!s.matches("[A-Za-z0-9_.]+")) throw new IOException("Geçersiz izin adı: " + s);
        return s;
    }

    private static String read(Path root) throws IOException {
        return Files.readString(manifest(root), StandardCharsets.UTF_8);
    }

    private static Path manifest(Path root) throws IOException {
        Path p = ProjectLayout.moduleDir(root).resolve("src/main/AndroidManifest.xml");
        if (!Files.isRegularFile(p) || !eluajs.util.FileUtil.isSafeProjectPath(root, p)) {
            throw new IOException("AndroidManifest.xml bulunamadı veya geçersiz.");
        }
        return p;
    }

    private static int lineStart(String text, int index) {
        int nl = text.lastIndexOf('\n', Math.max(0, index - 1));
        return nl < 0 ? 0 : nl + 1;
    }
}
