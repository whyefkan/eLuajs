package eluajs.ui;

import eluajs.android.ToolchainManager;
import eluajs.util.FileUtil;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** ADB device picker, install and logcat viewer. */
final class DeviceDialog extends JDialog {
    private final JComboBox<String> device = new JComboBox<>();
    private final JTextArea log = new JTextArea();
    private final JLabel status = Ui.label("", 11, Font.PLAIN, Theme.MUTED);
    private final JButton install = Ui.button(t("APK Yükle"), false);
    private final JButton start = Ui.button(t("Logcat Başlat"), true);
    private final JButton stop = Ui.button(t("Durdur"), false);
    private Path apk;
    private Process logcat;

    DeviceDialog(Window owner, Path projectRoot, Path apk) {
        super(owner, t("Cihazlar ve Logcat"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        this.apk = apk;
        setIconImage(Ui.appIcon());
        setSize(900, 620);
        setMinimumSize(new Dimension(760, 500));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        refreshDevices();
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 52));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                new EmptyBorder(0, 16, 0, 8)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(23, 23, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Cihazlar ve Logcat"), 15, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent content() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(16, 20, 16, 20));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        JLabel label = Ui.label(t("Bağlı cihaz"), 11, Font.BOLD, Theme.MUTED);
        top.add(label, BorderLayout.WEST);
        top.add(device, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton refresh = Ui.button(t("Yenile"), false);
        refresh.addActionListener(e -> refreshDevices());
        install.addActionListener(e -> installApk());
        actions.add(refresh);
        actions.add(install);
        actions.add(start);
        actions.add(stop);
        top.add(actions, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(Theme.mono(11));
        log.setBackground(Theme.CONSOLE);
        log.setForeground(Theme.TEXT_SOFT);
        log.setBorder(new EmptyBorder(8, 10, 8, 10));
        root.add(Ui.scroll(log), BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        start.addActionListener(e -> startLogcat());
        stop.addActionListener(e -> stopLogcat());
        stop.setEnabled(false);
        return root;
    }

    private void refreshDevices() {
        status.setText(t("Cihazlar okunuyor..."));
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override protected List<String> doInBackground() throws Exception {
                return ToolchainManager.adbDevices(line -> { });
            }
            @Override protected void done() {
                device.removeAllItems();
                try {
                    List<String> devices = get();
                    for (String item : devices) device.addItem(item);
                    status.setText(devices.isEmpty() ? t("Cihaz bulunamadı.") : t("%d cihaz", devices.size()));
                    install.setEnabled(!devices.isEmpty());
                } catch (Exception ex) {
                    status.setText(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                }
            }
        };
        worker.execute();
    }

    private void installApk() {
        if (apk == null || !Files.isRegularFile(apk)) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(t("APK seç"));
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("APK", "apk"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            apk = chooser.getSelectedFile().toPath();
        }
        String serial = (String) device.getSelectedItem();
        if (serial == null) return;
        install.setEnabled(false);
        status.setText(t("APK yükleniyor..."));
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                ToolchainManager.installApk(serial, apk, line -> SwingUtilities.invokeLater(() -> append(line)));
                return null;
            }
            @Override protected void done() {
                install.setEnabled(true);
                try { get(); status.setText(t("APK yüklendi.")); status.setForeground(Theme.ACCENT); }
                catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText(t("ADB hatası: %s", cause.getMessage()));
                    status.setForeground(Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void startLogcat() {
        if (logcat != null) return;
        String serial = (String) device.getSelectedItem();
        if (serial == null) return;
        try {
            Path adb = ToolchainManager.adbExecutable();
            logcat = new ProcessBuilder(adb.toString(), "-s", serial, "logcat", "-v", "time")
                    .redirectErrorStream(true).start();
            start.setEnabled(false);
            stop.setEnabled(true);
            log.setText("");
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String value = line + "\n";
                        SwingUtilities.invokeLater(() -> {
                            log.append(value);
                            if (log.getDocument().getLength() > 1_000_000) {
                                log.replaceRange("", 0, 200_000);
                            }
                            log.setCaretPosition(log.getDocument().getLength());
                        });
                    }
                } catch (Exception ignored) { }
                SwingUtilities.invokeLater(() -> {
                    start.setEnabled(true);
                    stop.setEnabled(false);
                    logcat = null;
                });
            }, "eluajs-logcat");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception ex) {
            status.setText(t("Logcat başlatılamadı: %s", ex.getMessage()));
        }
    }

    private void stopLogcat() {
        if (logcat != null) {
            logcat.destroy();
            logcat = null;
        }
        start.setEnabled(true);
        stop.setEnabled(false);
    }

    private void append(String line) {
        if (line == null) return;
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    @Override public void dispose() {
        stopLogcat();
        super.dispose();
    }
}
