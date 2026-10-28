package eluajs.ui;

import eluajs.android.BuildIssueParser;
import eluajs.android.ToolchainManager;
import eluajs.android.ReleaseSigningConfig;
import eluajs.project.ProjectReleaseManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.*;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class BuildDialog extends JDialog {
    private final Path projectRoot;
    private final BiConsumer<Path,Integer> navigator;
    private final JLabel javaStatus = statusLabel();
    private final JLabel gradleStatus = statusLabel();
    private final JLabel sdkManagerStatus = statusLabel();
    private final JLabel platformStatus = statusLabel();
    private final JLabel buildToolsStatus = statusLabel();
    private final JTextArea log = new JTextArea();
    private final JCheckBox license = new JCheckBox(t("Android SDK lisans koşullarını kabul ediyorum"));
    private final JButton prepare = Ui.button(t("Araçları Hazırla"), false);
    private final JButton build = Ui.button("▶  " + t("Debug APK"), false);
    private final JButton buildAab = Ui.button("◆  " + t("Play Store AAB"), true);
    private final JButton openApk = Ui.button(t("Çıktı Klasörünü Aç"), false);
    private final JButton goIssue = Ui.button("↗  " + t("Hata Satırına Git"), false);
    private final JProgressBar progress = new JProgressBar();
    private Path lastApk;
    private Path lastOutput;
    private BuildIssueParser.Issue lastIssue;
    private final ConcurrentLinkedQueue<String> pendingLog = new ConcurrentLinkedQueue<>();
    private final Timer logFlushTimer = new Timer(60, e -> flushLog());
    private static final int MAX_LOG_CHARS = 2_000_000;

    public BuildDialog(Frame owner, Path projectRoot, BiConsumer<Path,Integer> navigator) {
        super(owner, t("eLuajs — Derle / APK / AAB"), true);
        this.projectRoot = projectRoot;
        this.navigator = navigator;
        setIconImage(Ui.appIcon());
        setSize(920, 760);
        setMinimumSize(new Dimension(740, 620));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(content(), BorderLayout.CENTER);
        logFlushTimer.start();
        refreshStatuses();
        detectExistingApk();
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(24, 28, 22, 28));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.add(Ui.label(t("Android çıktısı al"), 24, Font.BOLD, Theme.TEXT));
        titleLine.add(Ui.badge("APK + SIGNED AAB", Theme.ACCENT));
        top.add(titleLine);
        top.add(Box.createVerticalStrut(5));
        top.add(Ui.label(t("Debug APK test içindir. Play Store AAB ise upload keystore ile imzalı release çıktısı üretir."), 12, Font.PLAIN, Theme.MUTED));
        top.add(Box.createVerticalStrut(18));

        JPanel statuses = Ui.card(new BorderLayout());
        JPanel statusList = new JPanel();
        statusList.setOpaque(false);
        statusList.setLayout(new BoxLayout(statusList, BoxLayout.Y_AXIS));
        statusList.setBorder(new EmptyBorder(10, 14, 10, 14));
        statusList.add(statusRow("JDK (java + javac + jlink)", javaStatus));
        statusList.add(statusRow("Gradle " + ToolchainManager.GRADLE_VERSION, gradleStatus));
        statusList.add(statusRow("Android SDK Manager", sdkManagerStatus));
        statusList.add(statusRow("Android platform " + ToolchainManager.ANDROID_PLATFORM, platformStatus));
        statusList.add(statusRow("Build Tools " + ToolchainManager.BUILD_TOOLS, buildToolsStatus));
        statuses.add(statusList, BorderLayout.CENTER);
        top.add(statuses);
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
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_SOFT));
        root.add(logScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        progress.setPreferredSize(new Dimension(1, 5));
        progress.setBorderPainted(false);
        bottom.add(progress);
        bottom.add(Box.createVerticalStrut(10));

        license.setOpaque(false);
        license.setForeground(Theme.MUTED);
        license.setFont(Theme.font(12, Font.PLAIN));
        license.setFocusPainted(false);
        bottom.add(license);
        bottom.add(Box.createVerticalStrut(12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        prepare.addActionListener(e -> prepareSdk());
        build.addActionListener(e -> buildApk());
        buildAab.addActionListener(e -> showAabSigning());
        openApk.addActionListener(e -> openApkFolder());
        goIssue.setEnabled(false);
        goIssue.setForeground(Theme.ACCENT);
        goIssue.addActionListener(e -> { if (lastIssue != null) navigateTo(lastIssue); });
        actions.add(goIssue);
        actions.add(prepare);
        actions.add(openApk);
        actions.add(build);
        actions.add(buildAab);
        bottom.add(actions);
        root.add(bottom, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(buildAab);
        return root;
    }

    private JPanel statusRow(String name, JLabel status) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(6, 0, 6, 0));
        p.add(Ui.label(name, 13, Font.PLAIN, Theme.TEXT), BorderLayout.WEST);
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
        Path apk = projectRoot.resolve("app/build/outputs/apk/debug/app-debug.apk");
        if (Files.isRegularFile(aab)) {
            lastOutput = aab;
            openApk.setEnabled(true);
        } else if (Files.isRegularFile(apk)) {
            lastApk = apk;
            lastOutput = apk;
            openApk.setEnabled(true);
        } else {
            lastOutput = null;
            openApk.setEnabled(false);
        }
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

    private <T> void runTask(String title, Task<T> task) {
        prepare.setEnabled(false);
        build.setEnabled(false);
        buildAab.setEnabled(false);
        openApk.setEnabled(false);
        progress.setVisible(true);
        appendLog("\n--- " + title + " ---");
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() throws Exception { return task.run(); }
            @Override protected void done() {
                prepare.setEnabled(true);
                build.setEnabled(true);
                buildAab.setEnabled(true);
                progress.setVisible(false);
                try {
                    T result = get();
                    refreshStatuses();
                    if (result instanceof Path p) {
                        lastOutput = p;
                        if (p.toString().endsWith(".apk")) lastApk = p;
                        openApk.setEnabled(true);
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
                    String suffix = lastIssue == null ? "" : t("\n\nKaynak konumu bulundu. 'Hata Satırına Git' düğmesini kullanabilirsiniz.");
                    JOptionPane.showMessageDialog(BuildDialog.this,
                            friendlyError(cause) + suffix, t("İşlem başarısız"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
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
            batch.append(line).append('\n');
            BuildIssueParser.parse(line).ifPresent(issue -> {
                if (Files.isRegularFile(issue.file())) {
                    lastIssue = issue;
                    goIssue.setEnabled(navigator != null);
                    goIssue.setToolTipText(issue.file() + ":" + issue.line());
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

    private void navigateTo(BuildIssueParser.Issue issue) {
        if (navigator == null || issue == null) return;
        setVisible(false);
        navigator.accept(issue.file(), issue.line());
    }

    private void openApkFolder() {
        Path folder = lastOutput != null ? lastOutput.getParent() : (lastApk != null ? lastApk.getParent() : projectRoot.resolve("release"));
        try {
            Files.createDirectories(folder);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) Desktop.getDesktop().open(folder.toFile());
            else new ProcessBuilder("xdg-open", folder.toString()).start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, t("Klasör açılamadı: %s", e.getMessage()), t("Hata"), JOptionPane.ERROR_MESSAGE);
        }
    }

    @FunctionalInterface private interface Task<T> { T run() throws Exception; }
}
