package eluajs;

import eluajs.project.ProjectStore;
import eluajs.project.ProjectTemplate;
import eluajs.project.ProjectValidator;
import eluajs.project.ProjectActivityManager;
import eluajs.project.ProjectAssetManager;
import eluajs.project.ProjectLibraryManager;
import eluajs.project.ProjectPermissionManager;
import eluajs.project.ProjectSearch;
import eluajs.project.ProjectImporter;
import eluajs.project.ProjectFileManager;
import eluajs.project.ProjectGradleManager;
import eluajs.project.ProjectLayout;
import eluajs.project.BuildArtifacts;
import eluajs.git.GitManager;
import eluajs.project.ProjectJavaClassManager;
import eluajs.project.ProjectReleaseManager;
import eluajs.model.ProjectMeta;
import eluajs.ui.HubFrame;
import eluajs.ui.Theme;
import eluajs.util.AppPaths;
import eluajs.util.I18n;
import eluajs.android.ToolchainManager;
import eluajs.android.ReleaseSigningConfig;
import eluajs.android.BuildIssueParser;
import eluajs.util.FileUtil;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        eluajs.util.CrashReporter.install();
        if (args.length > 0 && "--self-test".equals(args[0])) {
            selfTest();
            return;
        }
        if (args.length > 0 && "--diagnostics".equals(args[0])) {
            diagnostics();
            return;
        }
        AppPaths.ensureBaseDirs();
        I18n.load();
        Theme.load();
        Theme.install();
        SwingUtilities.invokeLater(() -> new HubFrame().setVisible(true));
    }

    private static void diagnostics() {
        AppPaths.ensureBaseDirs();
        System.out.println("eLuajs 1.3 diagnostics");
        System.out.println("java.home=" + ToolchainManager.javaHome());
        System.out.println("java.ready=" + ToolchainManager.javaReady());
        System.out.println("tls.jdk.crypto.ec=" + ToolchainManager.tlsCryptoReady());
        System.out.println("keytool=" + ToolchainManager.keytoolExecutable());
        System.out.println("gradle.home=" + ToolchainManager.gradleHome());
        System.out.println("gradle.ready=" + ToolchainManager.gradleReady());
        System.out.println("android.sdk=" + ToolchainManager.sdkRoot());
        System.out.println("sdkmanager.ready=" + ToolchainManager.sdkManagerReady());
        System.out.println("platform.ready=" + ToolchainManager.platformReady());
        System.out.println("buildTools.ready=" + ToolchainManager.buildToolsReady());
        System.out.println("projects=" + AppPaths.defaultProjects());
    }

    private static void selfTest() {
        try {
            if (!ToolchainManager.javaReady()) {
                throw new IllegalStateException("JDK toolchain incomplete; java/javac/jlink required at " + ToolchainManager.javaHome());
            }
            System.out.println("JDK toolchain OK: " + ToolchainManager.javaHome());
            System.out.println("jlink: " + ToolchainManager.jlinkExecutable());
            if (!ToolchainManager.tlsCryptoReady()) throw new IllegalStateException("TLS crypto module jdk.crypto.ec missing");
            if (!Files.isExecutable(ToolchainManager.keytoolExecutable())) throw new IllegalStateException("keytool missing");
            System.out.println("TLS crypto: jdk.crypto.ec OK");
            System.out.println("keytool: " + ToolchainManager.keytoolExecutable());
            Path temp = Files.createTempDirectory("eluajs-selftest-");
            ProjectMeta meta = new ProjectMeta("Self Test", "1.0", 1, "com.example.selftest", null, temp.resolve("SelfTest"));
            ProjectTemplate.create(meta);
            var report = ProjectValidator.validate(meta.rootDir());
            if (!report.ok()) {
                throw new IllegalStateException("Validation failed: " + report.messages());
            }
            String gradleProps = Files.readString(meta.rootDir().resolve("gradle.properties"));
            if (!gradleProps.contains("android.useAndroidX=true")) {
                throw new IllegalStateException("AndroidX was not enabled in generated project");
            }
            Path selfKey = temp.resolve("selftest-upload-key.jks");
            ToolchainManager.generateUploadKeystore(selfKey, "upload", "changeit1", "changeit2", "eLuajs Self Test", x -> {});
            ReleaseSigningConfig selfSigning = new ReleaseSigningConfig(selfKey, "changeit1", "upload", "changeit2");
            ToolchainManager.verifyKeystore(selfSigning, x -> {});
            ProjectReleaseManager.save(meta.rootDir(), selfSigning, false);
            var savedSigning = ProjectReleaseManager.load(meta.rootDir());
            if (savedSigning.storeFile() == null || !savedSigning.storeFile().equals(selfKey.toAbsolutePath().normalize()) || !savedSigning.storePassword().isEmpty()) {
                throw new IllegalStateException("Release signing settings save/load failed");
            }
            Path initScript = ProjectReleaseManager.ensureInitScript(meta.rootDir());
            String initText = Files.readString(initScript);
            if (!initText.contains("ELUAJS_STORE_FILE") || initText.contains("changeit1") || initText.contains("changeit2")) {
                throw new IllegalStateException("Release signing init script is invalid or leaked secrets");
            }
            String ignore = Files.readString(meta.rootDir().resolve(".gitignore"));
            if (!ignore.contains(ProjectReleaseManager.SETTINGS_RELATIVE)) {
                throw new IllegalStateException("Release signing secrets are not gitignored");
            }
            var dep = ProjectLibraryManager.add(meta.rootDir(), "implementation", "com.example:testlib:1.2.3", false);
            if (ProjectLibraryManager.list(meta.rootDir()).stream().noneMatch(x -> x.notation().equals("com.example:testlib:1.2.3"))) {
                throw new IllegalStateException("Library manager add/list failed");
            }
            ProjectLibraryManager.remove(meta.rootDir(), dep);
            if (ProjectLibraryManager.list(meta.rootDir()).stream().anyMatch(x -> x.notation().equals("com.example:testlib:1.2.3"))) {
                throw new IllegalStateException("Library manager remove failed");
            }
            ProjectPermissionManager.set(meta.rootDir(), "android.permission.INTERNET", true);
            if (!ProjectPermissionManager.list(meta.rootDir()).contains("android.permission.INTERNET")) {
                throw new IllegalStateException("Permission manager add failed");
            }
            ProjectPermissionManager.set(meta.rootDir(), "android.permission.INTERNET", false);
            if (ProjectPermissionManager.list(meta.rootDir()).contains("android.permission.INTERNET")) {
                throw new IllegalStateException("Permission manager remove failed");
            }
            Path helperClass = ProjectJavaClassManager.create(meta.rootDir(), meta.packageName() + ".data", "SelfTestRepository");
            if (!Files.isRegularFile(helperClass) || !Files.readString(helperClass).contains("class SelfTestRepository")) {
                throw new IllegalStateException("Java class manager failed");
            }
            var searchMatches = ProjectSearch.search(meta.rootDir(), "SelfTestRepository", false, 20);
            if (searchMatches.stream().noneMatch(m -> m.file().equals(helperClass))) {
                throw new IllegalStateException("Project search failed");
            }
            Path replaceFile = meta.rootDir().resolve("replace-test.txt");
            Files.writeString(replaceFile, "alpha alpha\n");
            var replaced = ProjectSearch.replaceAll(meta.rootDir(), "alpha", "beta", true);
            if (replaced.replacements() != 2 || !Files.readString(replaceFile).contains("beta beta")) {
                throw new IllegalStateException("Project search/replace failed");
            }
            Files.deleteIfExists(replaceFile);
            var detected = ProjectImporter.detect(meta.rootDir());
            if (!detected.packageName().equals(meta.packageName()) || detected.versionCode() != 1) {
                throw new IllegalStateException("Android project import detection failed");
            }
            String safeJava = "package " + meta.packageName() + ".data;\n" +
                    "public class SelfTestRepository {\n" +
                    "  String brace = \"{ not code }\"; // } comment\n" +
                    "  /* { comment } */\n" +
                    "}\n";
            FileUtil.recoveryCopy(meta.rootDir(), helperClass);
            FileUtil.writeStringAtomic(helperClass, safeJava);
            if (!ProjectValidator.validate(meta.rootDir()).ok()) {
                throw new IllegalStateException("Validator incorrectly counted braces inside strings/comments");
            }
            Path recovery = meta.rootDir().resolve(".eluajs/recovery").resolve(meta.rootDir().relativize(helperClass).toString() + ".bak");
            if (!Files.isRegularFile(recovery)) throw new IllegalStateException("Recovery copy was not created");
            String compilerLine = helperClass.toAbsolutePath() + ":7: error: self-test";
            var issue = BuildIssueParser.parse(compilerLine).orElseThrow(() -> new IllegalStateException("Build issue parser failed"));
            if (!issue.file().equals(helperClass.toAbsolutePath().normalize()) || issue.line() != 7) {
                throw new IllegalStateException("Build issue parser returned wrong location");
            }

            var page = ProjectActivityManager.create(meta, "SecondPage", "Second Page");
            if (!Files.isRegularFile(page.javaFile()) || !Files.readString(meta.rootDir().resolve("app/src/main/AndroidManifest.xml")).contains(".SecondPageActivity")) {
                throw new IllegalStateException("Activity creation/manifest registration failed");
            }
            if (ProjectActivityManager.list(meta).stream().noneMatch(x -> x.className().equals("SecondPageActivity"))) {
                throw new IllegalStateException("Activity listing failed");
            }
            ProjectActivityManager.delete(meta, "SecondPageActivity");
            if (Files.exists(page.javaFile()) || Files.readString(meta.rootDir().resolve("app/src/main/AndroidManifest.xml")).contains(".SecondPageActivity")) {
                throw new IllegalStateException("Activity deletion/manifest cleanup failed");
            }
            Path asset = temp.resolve("Logo Test.PNG");
            Files.writeString(asset, "asset");
            var imported = ProjectAssetManager.importFiles(meta.rootDir(), java.util.List.of(asset), ProjectAssetManager.Destination.DRAWABLE);
            if (imported.size() != 1 || !Files.isRegularFile(imported.get(0).target()) || !imported.get(0).target().getFileName().toString().equals("logo_test.png")) {
                throw new IllegalStateException("Asset import failed");
            }
            Path broken = temp.resolve("BrokenProject");
            Files.createDirectories(broken.resolve(".eluajs"));
            Files.writeString(broken.resolve(".eluajs/project.properties"), "appName=Broken\nversionCode=not-a-number\npackageName=\n");
            ProjectStore store = new ProjectStore(temp);
            if (store.list().stream().noneMatch(x -> x.rootDir().equals(meta.rootDir()))) {
                throw new IllegalStateException("A damaged project hid valid projects in ProjectStore");
            }
            for (ProjectTemplate.Template template : ProjectTemplate.Template.values()) {
                Path templateRoot = temp.resolve("Template-" + template.name());
                ProjectMeta templateMeta = new ProjectMeta("Template " + template.name(), "1.0", 1,
                        "com.example.template" + template.ordinal(), null, templateRoot);
                ProjectTemplate.create(templateMeta, template);
                if (!ProjectValidator.validate(templateRoot).ok()) {
                    throw new IllegalStateException("Template validation failed: " + template);
                }
                if (template == ProjectTemplate.Template.WEBVIEW
                        && !Files.isRegularFile(templateRoot.resolve("app/src/main/assets/index.html"))) {
                    throw new IllegalStateException("WebView template asset missing");
                }
            }
            Path filesRoot = temp.resolve("Files-Test");
            Files.createDirectories(filesRoot);
            Path docs = ProjectFileManager.createDirectory(filesRoot, filesRoot, "docs");
            Path note = ProjectFileManager.createFile(filesRoot, docs, "note.txt");
            Files.writeString(note, "hello");
            Path duplicate = ProjectFileManager.duplicate(filesRoot, note);
            Path renamed = ProjectFileManager.rename(filesRoot, duplicate, "renamed.txt");
            if (!Files.exists(renamed)) throw new IllegalStateException("File manager rename failed");
            ProjectFileManager.delete(filesRoot, docs);
            if (Files.exists(docs)) throw new IllegalStateException("File manager delete failed");

            Path gradleRoot = temp.resolve("Gradle-Test");
            ProjectTemplate.create(new ProjectMeta("Gradle Test", "1.0", 1, "com.example.gradletest", null, gradleRoot));
            int nextCode = ProjectGradleManager.incrementVersionCode(gradleRoot);
            if (nextCode != 2 || !ProjectGradleManager.read(gradleRoot).versionName().equals("1.0")) {
                throw new IllegalStateException("Gradle version manager failed");
            }
            ProjectGradleManager.writePerformance(gradleRoot,
                    new ProjectGradleManager.Performance("-Xmx2048m", true, true, false, true));
            String props = Files.readString(gradleRoot.resolve("gradle.properties"));
            if (!props.contains("org.gradle.configuration-cache=true") || !props.contains("org.gradle.daemon=false")) {
                throw new IllegalStateException("Gradle performance settings failed");
            }
            if (!ProjectLayout.module(gradleRoot).equals("app")) throw new IllegalStateException("Project layout module failed");

            Path artifact = gradleRoot.resolve("app/build/outputs/apk/debug/app-debug.apk");
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact, "fake apk");
            if (BuildArtifacts.list(gradleRoot).stream().noneMatch(a -> a.file().equals(artifact.toAbsolutePath().normalize()))) {
                throw new IllegalStateException("Build artifact discovery failed");
            }
            Files.deleteIfExists(artifact);

            if (GitManager.available()) {
                Path gitRoot = temp.resolve("Git-Test");
                Files.createDirectories(gitRoot);
                Files.writeString(gitRoot.resolve("readme.txt"), "self-test");
                if (!GitManager.init(gitRoot).ok() || !GitManager.stageAll(gitRoot).ok()) {
                    throw new IllegalStateException("Git init/stage failed");
                }
                var commit = GitManager.commit(gitRoot, "self-test");
                if (!commit.ok() || !GitManager.isRepository(gitRoot) || GitManager.changes(gitRoot).size() != 0) {
                    throw new IllegalStateException("Git commit failed: " + commit.output());
                }
            }

            System.out.println("eLuajs self-test OK");
            System.out.println("Generated: " + meta.rootDir());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
