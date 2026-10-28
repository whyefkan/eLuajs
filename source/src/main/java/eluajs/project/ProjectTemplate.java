package eluajs.project;

import eluajs.model.ProjectMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public final class ProjectTemplate {
    public static final String AGP_VERSION = "8.13.2";
    public static final int COMPILE_SDK = 36;
    public static final int TARGET_SDK = 36;
    public static final int MIN_SDK = 23;

    private ProjectTemplate() {}

    public enum Template { EMPTY, LOGIN, WEBVIEW, NOTES, MATERIAL }

    public static void create(ProjectMeta meta) throws IOException {
        create(meta, Template.EMPTY);
    }

    public static void create(ProjectMeta meta, Template template) throws IOException {
        if (template == null) template = Template.EMPTY;
        validateMeta(meta);
        Path root = meta.rootDir();
        if (Files.exists(root) && Files.list(root).findAny().isPresent()) throw new IOException("Proje klasörü boş değil: " + root);
        Files.createDirectories(root);

        write(root.resolve("settings.gradle"), """
                pluginManagement {
                    repositories { google(); mavenCentral(); gradlePluginPortal() }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories { google(); mavenCentral() }
                }
                rootProject.name = '%s'
                include ':app'
                """.formatted(escapeGroovy(meta.appName())));

        write(root.resolve("build.gradle"), """
                plugins {
                    id 'com.android.application' version '%s' apply false
                }
                """.formatted(AGP_VERSION));

        write(root.resolve("gradle.properties"), """
                org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
                android.useAndroidX=true
                android.nonTransitiveRClass=true
                """);
        write(root.resolve(".gitignore"), """
                .gradle/
                local.properties
                **/build/
                .idea/
                *.iml
                release/
                .eluajs/release-signing.properties
                """);

        Path app = root.resolve("app");
        write(app.resolve("build.gradle"), """
                plugins {
                    id 'com.android.application'
                }

                android {
                    namespace '%s'
                    compileSdk %d

                    defaultConfig {
                        applicationId '%s'
                        minSdk %d
                        targetSdk %d
                        versionCode %d
                        versionName '%s'
                    }

                    buildTypes {
                        release {
                            minifyEnabled false
                            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                        }
                    }

                    compileOptions {
                        sourceCompatibility JavaVersion.VERSION_17
                        targetCompatibility JavaVersion.VERSION_17
                    }
                }

                dependencies {
                    // Kütüphaneleri eLuajs > Kütüphane yöneticisinden veya buraya standart Gradle dependency olarak ekleyin.
                }
                """.formatted(meta.packageName(), COMPILE_SDK, meta.packageName(), MIN_SDK, TARGET_SDK,
                meta.versionCode(), escapeGroovy(meta.versionName())));
        write(app.resolve("proguard-rules.pro"), "# eLuajs - özel ProGuard kurallarınızı buraya ekleyin.\n");

        String iconRef = "@drawable/app_icon";
        if (meta.logoPath() != null && Files.isRegularFile(meta.logoPath())) {
            BufferedImage image = ImageIO.read(meta.logoPath().toFile());
            if (image != null) {
                Path logoOut = app.resolve("src/main/res/drawable/app_logo.png");
                Files.createDirectories(logoOut.getParent());
                ImageIO.write(image, "png", logoOut.toFile());
                iconRef = "@drawable/app_logo";
            }
        }

        String permission = template == Template.WEBVIEW
                ? "    <uses-permission android:name=\"android.permission.INTERNET\" />\n"
                : "";
        write(app.resolve("src/main/AndroidManifest.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                %s    <application
                        android:allowBackup="true"
                        android:icon="%s"
                        android:label="%s"
                        android:theme="@style/AppTheme">
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.formatted(permission, iconRef, xml(meta.appName())));

        Path java = app.resolve("src/main/java").resolve(meta.packageName().replace('.', '/')).resolve("MainActivity.java");
        write(java, mainActivitySource(meta, template));

        if (template == Template.WEBVIEW) {
            write(app.resolve("src/main/assets/index.html"), webViewPage(meta));
        }

        write(app.resolve("src/main/res/values/styles.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
                        <item name="android:fontFamily">sans</item>
                        <item name="android:windowLightStatusBar">false</item>
                        <item name="android:statusBarColor">#12161C</item>
                        <item name="android:navigationBarColor">#12161C</item>
                    </style>
                </resources>
                """);
        write(app.resolve("src/main/res/drawable/app_icon.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                    <corners android:radius="18dp" />
                    <solid android:color="#34D399" />
                    <padding android:left="12dp" android:top="12dp" android:right="12dp" android:bottom="12dp" />
                </shape>
                """);

        Properties p = new Properties();
        p.setProperty("appName", meta.appName());
        p.setProperty("versionName", meta.versionName());
        p.setProperty("versionCode", String.valueOf(meta.versionCode()));
        p.setProperty("packageName", meta.packageName());
        p.setProperty("templateVersion", "5");
        p.setProperty("template", template.name());
        p.setProperty("module", "app");
        Path props = root.resolve(".eluajs/project.properties");
        Files.createDirectories(props.getParent());
        try (OutputStream out = Files.newOutputStream(props)) { p.store(out, "eLuajs project metadata"); }
    }

    private static String mainActivitySource(ProjectMeta meta, Template template) {
        return switch (template) {
            case LOGIN -> loginActivity(meta.packageName(), javaString(meta.appName()));
            case WEBVIEW -> webViewActivity(meta.packageName());
            case NOTES -> notesActivity(meta.packageName(), javaString(meta.appName()));
            case MATERIAL -> materialActivity(meta.packageName(), javaString(meta.appName()));
            default -> emptyActivity(meta.packageName(), javaString(meta.appName()));
        };
    }

    private static String emptyActivity(String pkg, String appName) {
        return """
                package %s;

                import android.app.Activity;
                import android.os.Bundle;
                import android.graphics.Color;
                import android.view.Gravity;
                import android.widget.LinearLayout;
                import android.widget.TextView;

                public class MainActivity extends Activity {
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
                        subtitle.setText("Created with eLuajs");
                        subtitle.setTextColor(Color.rgb(144, 238, 200));
                        subtitle.setTextSize(16);
                        subtitle.setPadding(0, 18, 0, 0);
                        subtitle.setGravity(Gravity.CENTER);

                        root.addView(title);
                        root.addView(subtitle);
                        setContentView(root);
                    }
                }
                """.formatted(pkg, appName);
    }

    private static String loginActivity(String pkg, String appName) {
        return """
                package %s;

                import android.app.Activity;
                import android.graphics.Color;
                import android.os.Bundle;
                import android.text.InputType;
                import android.view.Gravity;
                import android.view.ViewGroup;
                import android.widget.Button;
                import android.widget.EditText;
                import android.widget.LinearLayout;
                import android.widget.TextView;
                import android.widget.Toast;

                public class MainActivity extends Activity {
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
                        subtitle.setText("Sign in to continue");
                        subtitle.setTextColor(Color.rgb(144, 238, 200));
                        subtitle.setTextSize(14);
                        subtitle.setGravity(Gravity.CENTER);
                        subtitle.setPadding(0, 8, 0, 24);

                        EditText email = new EditText(this);
                        email.setHint("Email");
                        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                        email.setSingleLine(true);
                        email.setTextColor(Color.WHITE);
                        email.setHintTextColor(Color.rgb(120, 136, 154));
                        email.setBackgroundColor(Color.rgb(30, 38, 48));

                        EditText password = new EditText(this);
                        password.setHint("Password");
                        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        password.setSingleLine(true);
                        password.setTextColor(Color.WHITE);
                        password.setHintTextColor(Color.rgb(120, 136, 154));
                        password.setBackgroundColor(Color.rgb(30, 38, 48));

                        Button login = new Button(this);
                        login.setText("Sign in");
                        login.setOnClickListener(v -> Toast.makeText(this,
                                "Welcome, " + email.getText(), Toast.LENGTH_SHORT).show());

                        LinearLayout.LayoutParams match = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        root.addView(title);
                        root.addView(subtitle);
                        root.addView(email, match);
                        root.addView(password, match);
                        root.addView(login, match);
                        setContentView(root);
                    }
                }
                """.formatted(pkg, appName);
    }

    private static String webViewActivity(String pkg) {
        return """
                package %s;

                import android.app.Activity;
                import android.os.Bundle;
                import android.webkit.WebView;
                import android.webkit.WebViewClient;

                public class MainActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        WebView web = new WebView(this);
                        web.getSettings().setJavaScriptEnabled(true);
                        web.setWebViewClient(new WebViewClient());
                        web.loadUrl("file:///android_asset/index.html");
                        setContentView(web);
                    }
                }
                """.formatted(pkg);
    }

    private static String notesActivity(String pkg, String appName) {
        return """
                package %s;

                import android.app.Activity;
                import android.content.SharedPreferences;
                import android.graphics.Color;
                import android.os.Bundle;
                import android.view.ViewGroup;
                import android.widget.Button;
                import android.widget.EditText;
                import android.widget.LinearLayout;
                import android.widget.ScrollView;
                import android.widget.TextView;

                public class MainActivity extends Activity {
                    private EditText input;
                    private TextView list;
                    private SharedPreferences prefs;

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        prefs = getSharedPreferences("notes", MODE_PRIVATE);

                        LinearLayout root = new LinearLayout(this);
                        root.setOrientation(LinearLayout.VERTICAL);
                        root.setPadding(40, 40, 40, 40);
                        root.setBackgroundColor(Color.rgb(18, 22, 28));

                        TextView title = new TextView(this);
                        title.setText("%s");
                        title.setTextColor(Color.WHITE);
                        title.setTextSize(26);

                        input = new EditText(this);
                        input.setHint("Write a note...");
                        input.setSingleLine(false);
                        input.setMinLines(2);
                        input.setTextColor(Color.WHITE);
                        input.setHintTextColor(Color.rgb(120, 136, 154));
                        input.setBackgroundColor(Color.rgb(30, 38, 48));

                        Button add = new Button(this);
                        add.setText("Add note");
                        add.setOnClickListener(v -> addNote());

                        list = new TextView(this);
                        list.setTextColor(Color.rgb(199, 211, 224));
                        list.setTextSize(15);
                        list.setPadding(0, 24, 0, 0);

                        root.addView(title);
                        root.addView(input, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        root.addView(add);
                        root.addView(list);
                        ScrollView scroll = new ScrollView(this);
                        scroll.addView(root);
                        setContentView(scroll);
                        render();
                    }

                    private void addNote() {
                        String note = input.getText().toString().trim();
                        if (note.isEmpty()) return;
                        String old = prefs.getString("items", "");
                        prefs.edit().putString("items", old.isEmpty() ? "- " + note : old + "\\n- " + note).apply();
                        input.setText("");
                        render();
                    }

                    private void render() {
                        String items = prefs.getString("items", "");
                        list.setText(items.isEmpty() ? "No notes yet." : items);
                    }
                }
                """.formatted(pkg, appName);
    }

    private static String materialActivity(String pkg, String appName) {
        return """
                package %s;

                import android.app.Activity;
                import android.graphics.Color;
                import android.graphics.drawable.GradientDrawable;
                import android.os.Bundle;
                import android.view.ViewGroup;
                import android.widget.LinearLayout;
                import android.widget.ScrollView;
                import android.widget.Switch;
                import android.widget.TextView;

                public class MainActivity extends Activity {
                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);

                        LinearLayout root = new LinearLayout(this);
                        root.setOrientation(LinearLayout.VERTICAL);
                        root.setPadding(36, 36, 36, 36);
                        root.setBackgroundColor(Color.rgb(18, 22, 28));

                        TextView title = new TextView(this);
                        title.setText("%s");
                        title.setTextColor(Color.WHITE);
                        title.setTextSize(26);

                        TextView subtitle = new TextView(this);
                        subtitle.setText("Dashboard");
                        subtitle.setTextColor(Color.rgb(144, 238, 200));
                        subtitle.setTextSize(14);

                        root.addView(title);
                        root.addView(subtitle);
                        root.addView(card("Today", "3 tasks completed", Color.rgb(30, 38, 48)));
                        root.addView(card("Progress", "Keep the streak alive", Color.rgb(24, 58, 48)));
                        root.addView(card("Settings", "Notifications enabled", Color.rgb(28, 38, 54)));

                        Switch toggle = new Switch(this);
                        toggle.setText("Dark mode");
                        toggle.setTextColor(Color.WHITE);
                        toggle.setPadding(0, 28, 0, 0);
                        root.addView(toggle);

                        ScrollView scroll = new ScrollView(this);
                        scroll.addView(root);
                        setContentView(scroll);
                    }

                    private LinearLayout card(String title, String subtitle, int color) {
                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(28, 24, 28, 24);
                        GradientDrawable bg = new GradientDrawable();
                        bg.setColor(color);
                        bg.setCornerRadius(28);
                        card.setBackground(bg);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0, 22, 0, 0);
                        card.setLayoutParams(params);

                        TextView t = new TextView(this);
                        t.setText(title);
                        t.setTextColor(Color.WHITE);
                        t.setTextSize(18);
                        card.addView(t);

                        TextView s = new TextView(this);
                        s.setText(subtitle);
                        s.setTextColor(Color.rgb(199, 211, 224));
                        s.setTextSize(13);
                        card.addView(s);
                        return card;
                    }
                }
                """.formatted(pkg, appName);
    }

    private static String webViewPage(ProjectMeta meta) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root { color-scheme: dark; }
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center;
                           background: #12161c; color: #f1f6fb; font-family: sans-serif; }
                    main { width: min(86vw, 520px); padding: 32px; border: 1px solid #2a3746;
                           border-radius: 20px; background: #151d26; }
                    .badge { color: #37d6a4; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
                    h1 { margin: 10px 0 6px; font-size: 30px; }
                    p { color: #c7d3e0; line-height: 1.5; }
                    button { border: 0; border-radius: 10px; padding: 12px 16px; font-weight: 700;
                             color: #05241c; background: #37d6a4; }
                    #msg { color: #98c379; min-height: 24px; }
                  </style>
                </head>
                <body>
                  <main>
                    <div class="badge">eLuajs WEBVIEW</div>
                    <h1>%s</h1>
                    <p>Your app is running inside a WebView. Edit this page in <code>app/src/main/assets/index.html</code>.</p>
                    <button onclick="document.getElementById('msg').textContent = 'Hello from JavaScript!'">Say hello</button>
                    <p id="msg"></p>
                  </main>
                </body>
                </html>
                """.formatted(xml(meta.appName()), xml(meta.appName()));
    }

    public static void validateMeta(ProjectMeta m) {
        if (m.appName() == null || m.appName().isBlank()) throw new IllegalArgumentException("Uygulama adı boş olamaz");
        if (m.packageName() == null || !m.packageName().matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))
            throw new IllegalArgumentException("Paket adı geçersiz");
        if (m.versionCode() < 1) throw new IllegalArgumentException("Sürüm kodu en az 1 olmalı");
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content.stripLeading(), StandardCharsets.UTF_8);
    }

    private static String escapeGroovy(String s) { return s.replace("\\", "\\\\").replace("'", "\\'"); }
    private static String javaString(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String xml(String s) { return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"); }
}
