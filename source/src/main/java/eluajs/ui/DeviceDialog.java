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
    private final JComboBox<String> levelFilter = new JComboBox<>(new String[]{"All", "V", "D", "I", "W", "E"});
    private final JTextField logFilter = Ui.field();
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
        JButton explain = Ui.button(t("Açıkla"), false);
        explain.addActionListener(e -> explainSelected());
        actions.add(explain);
        JButton saveLog = Ui.button(t("Kaydet"), false);
        saveLog.addActionListener(e -> saveLog());
        actions.add(saveLog);
        actions.add(start);
        actions.add(stop);
        top.add(actions, BorderLayout.EAST);
        JPanel filters = new JPanel(new BorderLayout(8, 0));
        filters.setOpaque(false);
        filters.setBorder(new EmptyBorder(8, 0, 0, 0));
        Ui.styleCombo(levelFilter);
        levelFilter.setPreferredSize(new Dimension(92, 30));
        logFilter.putClientProperty("JTextField.placeholderText", t("Loglarda ara"));
        filters.add(levelFilter, BorderLayout.WEST);
        filters.add(logFilter, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(top, BorderLayout.NORTH);
        north.add(filters, BorderLayout.SOUTH);
        root.add(north, BorderLayout.NORTH);

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

    private boolean matchesLog(String line) {
        if (line == null) return false;
        String needle = logFilter.getText() == null ? "" : logFilter.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (!needle.isBlank() && !line.toLowerCase(java.util.Locale.ROOT).contains(needle)) return false;
        int level = levelFilter.getSelectedIndex();
        if (level <= 0) return true;
        String letter = new String[]{"", "V", "D", "I", "W", "E"}[level];
        return line.matches(".*\\s" + letter + "/.*");
    }

    private void saveLog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(t("Log dosyasını kaydet"));
        chooser.setSelectedFile(new java.io.File("logcat.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { Files.writeString(chooser.getSelectedFile().toPath(), log.getText()); status.setText(t("Log kaydedildi.")); }
        catch (Exception ex) { status.setText(t("Kaydedilemedi: %s", ex.getMessage())); }
    }

    private void explainSelected() {
        String selected = log.getSelectedText();
        if (selected == null || selected.isBlank()) {
            int pos = log.getCaretPosition();
            try {
                int start = javax.swing.text.Utilities.getRowStart(log, pos);
                int end = javax.swing.text.Utilities.getRowEnd(log, pos);
                selected = log.getText(start, end - start);
            } catch (Exception ignored) { }
        }
        if (selected == null || selected.isBlank()) return;
        String lower = selected.toLowerCase(java.util.Locale.ROOT);
        String explanation;
        if (lower.contains("nullpointerexception")) explanation = t("Bir nesne null iken kullanılmış. İlgili değişkeni ve yaşam döngüsünü kontrol edin.");
        else if (lower.contains("classnotfound") || lower.contains("noclassdeffound")) explanation = t("Bir sınıf bulunamadı. Gradle bağımlılığını ve paket adını kontrol edin.");
        else if (lower.contains("permission")) explanation = t("İzin hatası olabilir. Manifest iznini ve çalışma zamanı iznini kontrol edin.");
        else if (lower.contains("outofmemory")) explanation = t("Bellek yetersiz. Büyük görselleri küçültün ve kaynakları serbest bırakın.");
        else if (lower.contains("fatal exception")) explanation = t("Uygulama çökmüş. Bu satırın üstündeki ilk uygulama stack trace satırına bakın.");
        else explanation = t("Seçili log satırı için yerel bir açıklama bulunamadı. AI asistanına bu satırı gönderebilirsiniz.");
        JTextArea area = new JTextArea(selected + "\n\n" + explanation);
        area.setEditable(false);
        area.setFont(Theme.mono(11));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), t("Log Açıklaması"), JOptionPane.INFORMATION_MESSAGE);
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
                        if (matchesLog(value)) SwingUtilities.invokeLater(() -> {
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
