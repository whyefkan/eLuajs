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

    public static void create(ProjectMeta meta) throws IOException {
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

        write(app.resolve("src/main/AndroidManifest.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application
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
                """.formatted(iconRef, xml(meta.appName())));

        Path java = app.resolve("src/main/java").resolve(meta.packageName().replace('.', '/')).resolve("MainActivity.java");
        write(java, """
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
                        subtitle.setText("eLuajs ile oluşturuldu");
                        subtitle.setTextColor(Color.rgb(144, 238, 200));
                        subtitle.setTextSize(16);
                        subtitle.setPadding(0, 18, 0, 0);
                        subtitle.setGravity(Gravity.CENTER);

                        root.addView(title);
                        root.addView(subtitle);
                        setContentView(root);
                    }
                }
                """.formatted(meta.packageName(), javaString(meta.appName())));

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
        Path props = root.resolve(".eluajs/project.properties");
        Files.createDirectories(props.getParent());
        try (OutputStream out = Files.newOutputStream(props)) { p.store(out, "eLuajs project metadata"); }
    }

    private static void validateMeta(ProjectMeta m) {
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
