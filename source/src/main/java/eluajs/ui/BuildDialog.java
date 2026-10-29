package eluajs.ui;

import eluajs.android.BuildIssueParser;
import eluajs.android.ToolchainManager;
import eluajs.android.ReleaseSigningConfig;
import eluajs.project.ProjectReleaseManager;
import eluajs.project.Problem;
import eluajs.project.ProjectLayout;
import eluajs.project.ProjectGradleManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class BuildDialog extends JDialog {
    private final Path projectRoot;
    private final BiConsumer<Path,Integer> navigator;
    private final Consumer<Problem> problemSink;
    private final JLabel javaStatus = statusLabel();
    private final JLabel gradleStatus = statusLabel();
    private final JLabel sdkManagerStatus = statusLabel();
    private final JLabel platformStatus = statusLabel();
    private final JLabel buildToolsStatus = statusLabel();
    private final JTextArea log = new JTextArea();
    private final JCheckBox license = new JCheckBox(t("Android SDK lisans koşullarını kabul ediyorum"));
    private final JButton prepare = Ui.button(t("Araçları Hazırla"), false);
    private final JButton output = Ui.button("⤓  " + t("Çıktı Al"), true);
    private final JButton openApk = Ui.button(t("Çıktı Klasörünü Aç"), false);
    private final JButton adbInstall = Ui.button(t("ADB ile Yükle"), false);
    private final JButton bumpVersion = Ui.button(t("Sürüm +1"), false);
    private final JButton artifacts = Ui.button(t("Çıktılar"), false);
    private final JButton aiFix = Ui.button(t("AI ile Düzelt"), false);
    private final JButton goIssue = Ui.button("↗  " + t("Hata Satırına Git"), false);
    private Consumer<String> buildFix;
    private String lastFailureLog = "";
    private final JProgressBar progress = new JProgressBar();
    private Path lastApk;
    private Path lastOutput;
    private BuildIssueParser.Issue lastIssue;
    private final ConcurrentLinkedQueue<String> pendingLog = new ConcurrentLinkedQueue<>();
    private final Timer logFlushTimer = new Timer(60, e -> flushLog());
    private static final int MAX_LOG_CHARS = 2_000_000;

    public BuildDialog(Frame owner, Path projectRoot, BiConsumer<Path,Integer> navigator) {
        this(owner, projectRoot, navigator, problem -> { });
    }

    public BuildDialog(Frame owner, Path projectRoot, BiConsumer<Path,Integer> navigator, Consumer<Problem> problemSink) {
        super(owner, t("eLuajs — Çıktı Al"), true);
        WindowChrome.install(this);
        this.projectRoot = projectRoot;
        this.navigator = navigator;
        this.problemSink = problemSink == null ? problem -> { } : problemSink;
        setIconImage(Ui.appIcon());
        setSize(920, 760);
        setMinimumSize(new Dimension(760, 640));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        logFlushTimer.start();
        refreshStatuses();
        detectExistingApk();
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 54));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 8)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Derle / APK + AAB"), 14, Font.BOLD, Theme.TEXT));
        brand.add(Ui.badge("v" + HubFrame.VERSION, Theme.ACCENT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(18, 24, 20, 24));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel kickerLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        kickerLine.setOpaque(false);
        kickerLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        kickerLine.add(Ui.label(t("ANDROID BUILD"), 10, Font.BOLD, Theme.ACCENT));
        kickerLine.add(Ui.badge("APK + SIGNED AAB", Theme.ACCENT));
        top.add(kickerLine);
        top.add(Box.createVerticalStrut(4));

        JLabel title = Ui.label(t("Android çıktısı al"), 24, Font.BOLD, Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(5));

        JLabel subtitle = Ui.label(t("Debug APK test içindir. Play Store AAB ise upload keystore ile imzalı release çıktısı üretir."), 12, Font.PLAIN, Theme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(subtitle);
        top.add(Box.createVerticalStrut(15));

        JPanel statusCard = Ui.card(new BorderLayout(0, 8));
        statusCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusCard.setBorder(new EmptyBorder(12, 15, 12, 15));
        statusCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
        statusCard.add(Ui.sectionLabel(t("TOOLCHAIN")), BorderLayout.NORTH);
        JPanel statusGrid = new JPanel(new GridLayout(2, 3, 10, 8));
        statusGrid.setOpaque(false);
        statusGrid.add(statusChip("JDK", javaStatus));
        statusGrid.add(statusChip("Gradle " + ToolchainManager.GRADLE_VERSION, gradleStatus));
        statusGrid.add(statusChip("SDK Manager", sdkManagerStatus));
        statusGrid.add(statusChip("Platform " + ToolchainManager.ANDROID_PLATFORM, platformStatus));
        statusGrid.add(statusChip("Build Tools " + ToolchainManager.BUILD_TOOLS, buildToolsStatus));
        statusGrid.add(new JPanel());
        statusCard.add(statusGrid, BorderLayout.CENTER);
        top.add(statusCard);

        root.add(top, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(Theme.mono(12));
        log.setBackground(Theme.CONSOLE);
        log.setForeground(Theme.TEXT_SOFT);
        log.setCaretColor(Theme.ACCENT);
        log.setSelectionColor(Theme.SELECTION);
        log.setLineWrap(false);
        log.setBorder(new EmptyBorder(12, 14, 12, 14));
        log.setText(t("Derleme günlüğü burada görünecek.\nHata konumu olan bir satıra çift tıklayarak editörde açabilirsiniz.\n"));
        log.setToolTipText(t("Kaynak dosya:Satır içeren hata satırına çift tıklayın"));
        log.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                try {
                    int offset = log.viewToModel2D(e.getPoint());
                    int rowStart = javax.swing.text.Utilities.getRowStart(log, offset);
                    int rowEnd = javax.swing.text.Utilities.getRowEnd(log, offset);
                    if (rowStart >= 0 && rowEnd >= rowStart) {
                        String line = log.getText(rowStart, rowEnd - rowStart);
                        BuildIssueParser.parse(line).ifPresent(issue -> navigateTo(issue));
                    }
                } catch (BadLocationException ignored) { }
            }
        });

        JPanel logCard = Ui.card(new BorderLayout(0, 8));
        logCard.setBorder(new EmptyBorder(12, 15, 12, 15));
        JPanel logHead = new JPanel(new BorderLayout(8, 0));
        logHead.setOpaque(false);
        logHead.add(Ui.sectionLabel(t("BUILD OUTPUT")), BorderLayout.WEST);
        JButton clear = Ui.button(t("Temizle"), false);
        clear.setFont(Theme.font(10, Font.BOLD));
        clear.setBorder(new EmptyBorder(4, 8, 4, 8));
        clear.addActionListener(e -> log.setText(""));
        JPanel logTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        logTools.setOpaque(false);
        artifacts.setFont(Theme.font(10, Font.BOLD));
        artifacts.setBorder(new EmptyBorder(4, 8, 4, 8));
        artifacts.addActionListener(e -> new ArtifactsDialog(this, projectRoot).setVisible(true));
        bumpVersion.setFont(Theme.font(10, Font.BOLD));
        bumpVersion.setBorder(new EmptyBorder(4, 8, 4, 8));
        bumpVersion.addActionListener(e -> incrementVersion());
        logTools.add(artifacts);
        logTools.add(bumpVersion);
        aiFix.setFont(Theme.font(10, Font.BOLD));
        aiFix.setBorder(new EmptyBorder(4, 8, 4, 8));
        logTools.add(aiFix);
        logTools.add(clear);
        logHead.add(logTools, BorderLayout.EAST);
        logCard.add(logHead, BorderLayout.NORTH);
        JScrollPane logScroll = Ui.scroll(log);
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        logCard.add(logScroll, BorderLayout.CENTER);
        root.add(logCard, BorderLayout.CENTER);

        JPanel footer = Ui.card(new BorderLayout(0, 9));
        footer.setBorder(new EmptyBorder(12, 15, 12, 15));
        license.setOpaque(false);
        license.setForeground(Theme.MUTED);
        license.setFont(Theme.font(11, Font.PLAIN));
        license.setFocusPainted(false);
        footer.add(license, BorderLayout.NORTH);

        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        progress.setPreferredSize(new Dimension(1, 4));
        progress.setBorderPainted(false);
        footer.add(progress, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        JPanel secondaryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        secondaryActions.setOpaque(false);
        JPanel primaryActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        primaryActions.setOpaque(false);
        prepare.addActionListener(e -> prepareSdk());
        output.addActionListener(e -> chooseOutput());
        openApk.addActionListener(e -> openApkFolder());
        adbInstall.addActionListener(e -> installApk());
        adbInstall.setEnabled(false);
        goIssue.setEnabled(false);
        goIssue.setForeground(Theme.ACCENT);
        goIssue.addActionListener(e -> { if (lastIssue != null) navigateTo(lastIssue); });
        aiFix.setEnabled(false);
        aiFix.setForeground(Theme.BLUE);
        aiFix.addActionListener(e -> {
            if (buildFix != null && !lastFailureLog.isBlank()) {
                dispose();
                buildFix.accept(lastFailureLog);
            }
        });
        secondaryActions.add(goIssue);
        secondaryActions.add(prepare);
        secondaryActions.add(openApk);
        secondaryActions.add(adbInstall);
        primaryActions.add(output);
        actions.add(secondaryActions, BorderLayout.WEST);
        actions.add(primaryActions, BorderLayout.EAST);
        footer.add(actions, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(output);
        return root;
    }

    private JPanel statusChip(String name, JLabel status) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        p.add(Ui.label(name, 11, Font.PLAIN, Theme.TEXT_SOFT), BorderLayout.WEST);
        p.add(status, BorderLayout.EAST);
        return p;
    }

    private static JLabel statusLabel() { return Ui.label(t("Kontrol..."), 12, Font.BOLD, Theme.MUTED); }

    private void refreshStatuses() {
        setStatus(javaStatus, ToolchainManager.javaReady());
        setStatus(gradleStatus, ToolchainManager.gradleReady());
        setStatus(sdkManagerStatus, ToolchainManager.sdkManagerReady());
        setStatus(platformStatus, ToolchainManager.platformReady());
        setStatus(buildToolsStatus, ToolchainManager.buildToolsReady());
    }

    private void setStatus(JLabel l, boolean ok) {
        l.setText(ok ? t("● Hazır") : t("○ Eksik"));
        l.setForeground(ok ? Theme.ACCENT : Theme.DANGER);
    }

    private void detectExistingApk() {
        String base = projectRoot.getFileName().toString().replaceAll("[^A-Za-z0-9._-]+", "-");
        Path aab = projectRoot.resolve("release").resolve(base + "-release.aab");
        Path apk = ProjectLayout.debugApk(projectRoot);
        if (apk != null && Files.isRegularFile(apk)) lastApk = apk;
        if (Files.isRegularFile(aab)) {
            lastOutput = aab;
            openApk.setEnabled(true);
        } else if (apk != null && Files.isRegularFile(apk)) {
            lastOutput = apk;
            openApk.setEnabled(true);
        } else {
            lastOutput = null;
            openApk.setEnabled(false);
        }
        adbInstall.setEnabled(lastApk != null && Files.isRegularFile(lastApk));
    }

    private void incrementVersion() {
        try {
            int next = ProjectGradleManager.incrementVersionCode(projectRoot);
            appendLog("versionCode → " + next);
            JOptionPane.showMessageDialog(this, t("Yeni versionCode: %d", next),
                    t("Sürüm kodu artırıldı"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), t("Sürüm kodu artırılamadı"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void installApk() {
        if (lastApk == null || !Files.isRegularFile(lastApk)) {
            detectExistingApk();
            if (lastApk == null) {
                JOptionPane.showMessageDialog(this, t("Önce bir debug APK derleyin."),
                        t("APK bulunamadı"), JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        adbInstall.setEnabled(false);
        appendLog("\n--- " + t("ADB ile kurulum") + " ---");
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ToolchainManager.installApk(lastApk, BuildDialog.this::appendLog);
                return null;
            }
            @Override protected void done() {
                adbInstall.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(BuildDialog.this, t("APK cihaza yüklendi."),
                            t("ADB"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    appendLog(t("ADB HATASI: ") + cause.getMessage());
                    JOptionPane.showMessageDialog(BuildDialog.this, cause.getMessage(), t("ADB hatası"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void prepareSdk() {
        if (!ToolchainManager.sdkReady() && !license.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    t("Android SDK eksik. SDK bileşenlerini indirebilmek için lisans kutusunu işaretlemeniz gerekir."),
                    t("Lisans onayı gerekli"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        runTask(t("Araçlar hazırlanıyor"), () -> {
            ToolchainManager.ensureGradle(this::appendLog);
            if (!ToolchainManager.sdkReady()) ToolchainManager.prepareAndroidSdk(true, this::appendLog);
            return null;
        });
    }

    private void buildApk() {
        if (!ToolchainManager.javaReady()) {
            JOptionPane.showMessageDialog(this,
                    t("eLuajs JDK araçları eksik. java, javac ve jlink birlikte bulunmalıdır.\nBu paket bozuk veya eksik çıkarılmış olabilir."),
                    t("JDK eksik"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!ToolchainManager.sdkReady() && !license.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    t("İlk APK derlemesi için Android SDK kurulacak. Önce lisans kutusunu işaretleyin; ardından APK Derle'ye tekrar basın."),
                    t("İlk kurulum"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        lastIssue = null;
        goIssue.setEnabled(false);
        runTask(t("APK derleniyor"), () -> {
            ToolchainManager.ensureGradle(this::appendLog);
            if (!ToolchainManager.sdkReady()) ToolchainManager.prepareAndroidSdk(true, this::appendLog);
            lastApk = ToolchainManager.buildDebugApk(projectRoot, this::appendLog);
            return lastApk;
        });
    }

    private void chooseOutput() {
        new BuildOutputDialog(this, this::buildApk, this::showAabSigning).setVisible(true);
    }

    private void showAabSigning() {
        if (!ToolchainManager.javaReady()) {
            JOptionPane.showMessageDialog(this,
                    t("eLuajs JDK araçları eksik. Release AAB için java, javac, jlink ve keytool gereklidir."),
                    t("JDK eksik"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!ToolchainManager.sdkReady() && !license.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    t("İlk AAB derlemesi için Android SDK kurulacak. Önce lisans kutusunu işaretleyin."),
                    t("İlk kurulum"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        new ReleaseSigningDialog(this, projectRoot, this::buildAab, this::openSigningSettings).setVisible(true);
    }

    private void buildAab(ReleaseSigningConfig signing) {
        lastIssue = null;
        goIssue.setEnabled(false);
        runTask(t("Play Store release AAB derleniyor"), () -> {
            ToolchainManager.ensureGradle(this::appendLog);
            if (!ToolchainManager.sdkReady()) ToolchainManager.prepareAndroidSdk(true, this::appendLog);
            Path aab = ToolchainManager.buildReleaseBundle(projectRoot, signing, this::appendLog);
            lastOutput = aab;
            return aab;
        });
    }

    private void openSigningSettings() {
        try {
            Path file = ProjectReleaseManager.ensureSettingsFile(projectRoot);
            setVisible(false);
            if (navigator != null) navigator.accept(file, 1);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, t("signing.properties açılamadı: %s", ex.getMessage()), t("Hata"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void setBuildFix(Consumer<String> buildFix) {
        this.buildFix = buildFix;
        aiFix.setEnabled(buildFix != null && !lastFailureLog.isBlank());
    }

    private <T> void runTask(String title, Task<T> task) {
        lastFailureLog = "";
        aiFix.setEnabled(false);
        prepare.setEnabled(false);
        output.setEnabled(false);
        openApk.setEnabled(false);
        progress.setVisible(true);
        appendLog("\n--- " + title + " ---");
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() throws Exception { return task.run(); }
            @Override protected void done() {
                prepare.setEnabled(true);
                output.setEnabled(true);
                progress.setVisible(false);
                try {
                    T result = get();
                    refreshStatuses();
                    if (result instanceof Path p) {
                        lastOutput = p;
                        if (p.toString().endsWith(".apk")) lastApk = p;
                        openApk.setEnabled(true);
                        adbInstall.setEnabled(lastApk != null);
                        boolean aab = p.toString().toLowerCase().endsWith(".aab");
                        String message = aab
                                ? t("İmzalı release AAB başarıyla oluşturuldu:\n%s\n\nBu dosya Google Play Console'a yüklenmek için hazırlanmıştır.", p)
                                : t("Debug APK başarıyla oluşturuldu:\n%s", p);
                        JOptionPane.showMessageDialog(BuildDialog.this, message,
                                aab ? t("Play Store AAB hazır") : t("Derleme başarılı"), JOptionPane.INFORMATION_MESSAGE);
                    } else detectExistingApk();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    appendLog(t("HATA: ") + cause.getMessage());
                    refreshStatuses();
                    detectExistingApk();
                    flushLog();
                    lastFailureLog = logTail(14_000);
                    aiFix.setEnabled(buildFix != null && !lastFailureLog.isBlank());
                    String suffix = lastIssue == null ? "" : t("\n\nKaynak konumu bulundu. 'Hata Satırına Git' düğmesini kullanabilirsiniz.");
                    JOptionPane.showMessageDialog(BuildDialog.this,
                            friendlyError(cause) + suffix, t("İşlem başarısız"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private String logTail(int max) {
        String text = log.getText();
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private String friendlyError(Throwable cause) {
        String msg = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        if (msg.contains("jlink")) {
            return t("JDK içindeki jlink aracı kullanılamadı. eLuajs paketini yeniden çıkarın veya güncel paketi kullanın.\n\n%s", msg);
        }
        return msg;
    }

    private void appendLog(String s) {
        if (s != null) pendingLog.offer(s);
    }

    private void flushLog() {
        if (pendingLog.isEmpty()) return;
        StringBuilder batch = new StringBuilder();
        String line;
        while ((line = pendingLog.poll()) != null) {
            final String logLine = line;
            batch.append(logLine).append('\n');
            BuildIssueParser.parse(logLine).ifPresent(issue -> {
                if (Files.isRegularFile(issue.file())) {
                    lastIssue = issue;
                    goIssue.setEnabled(navigator != null);
                    goIssue.setToolTipText(issue.file() + ":" + issue.line());
                    Problem.Severity severity = isErrorLine(logLine) ? Problem.Severity.ERROR : Problem.Severity.WARNING;
                    problemSink.accept(new Problem(issue.file(), issue.line(), trimBuildLine(logLine), severity));
                }
            });
        }
        log.append(batch.toString());
        int over = log.getDocument().getLength() - MAX_LOG_CHARS;
        if (over > 0) {
            try { log.getDocument().remove(0, Math.min(over + 100_000, log.getDocument().getLength())); }
            catch (BadLocationException ignored) { }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    @Override public void dispose() {
        flushLog();
        logFlushTimer.stop();
        super.dispose();
    }

    private static boolean isErrorLine(String line) {
        String lower = line == null ? "" : line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("error:") || lower.contains("failed") || lower.startsWith("e:")
                || lower.contains("exception") || lower.contains("hata:");
    }

    private static String trimBuildLine(String line) {
        String text = line == null ? "" : line.strip();
        return text.length() > 300 ? text.substring(0, 297) + "..." : text;
    }

    private void navigateTo(BuildIssueParser.Issue issue) {
        if (navigator == null || issue == null) return;
        setVisible(false);
        navigator.accept(issue.file(), issue.line());
    }

    private void openApkFolder() {
        Path folder = lastOutput != null ? lastOutput.getParent() : (lastApk != null ? lastApk.getParent() : projectRoot.resolve("release"));
        try {
            Files.createDirectories(folder);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder.toFile());
            } else if (eluajs.util.Platform.isWindows()) {
                new ProcessBuilder("explorer", folder.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", folder.toString()).start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, t("Klasör açılamadı: %s", e.getMessage()), t("Hata"), JOptionPane.ERROR_MESSAGE);
        }
    }

    @FunctionalInterface private interface Task<T> { T run() throws Exception; }
}
