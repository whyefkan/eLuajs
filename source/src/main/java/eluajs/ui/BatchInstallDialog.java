package eluajs.ui;

import eluajs.android.ToolchainManager;

import static eluajs.util.I18n.t;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.*;
import java.util.List;

final class BatchInstallDialog extends JDialog {
    private final JList<String> devices = new JList<>();
    private final JTextField apk = Ui.field();
    private final JTextArea log = new JTextArea();

    BatchInstallDialog(Window owner) {
        super(owner, t("Çoklu Cihaza Yükle"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(760, 560);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Theme.TITLE_BAR);
        head.setBorder(new EmptyBorder(10, 16, 10, 8));
        head.add(Ui.label(t("Çoklu Cihaza Yükle"), 14, Font.BOLD, Theme.TEXT), BorderLayout.WEST);
        head.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        add(head, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBackground(Theme.BG);
        body.setBorder(new EmptyBorder(14, 18, 16, 18));
        devices.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        devices.setBackground(Theme.PANEL);
        devices.setForeground(Theme.TEXT);
        devices.setFont(Theme.mono(11));
        body.add(Ui.scroll(devices), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.setOpaque(false);
        bottom.add(apk, BorderLayout.NORTH);
        log.setEditable(false);
        log.setFont(Theme.mono(11));
        log.setBackground(Theme.CONSOLE);
        log.setForeground(Theme.TEXT_SOFT);
        log.setPreferredSize(new Dimension(1, 130));
        bottom.add(Ui.scroll(log), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton choose = Ui.button(t("APK Seç"), false);
        choose.addActionListener(e -> chooseApk());
        JButton install = Ui.button(t("Seçilenlere Yükle"), true);
        install.addActionListener(e -> install());
        JButton refresh = Ui.button(t("Yenile"), false);
        refresh.addActionListener(e -> refresh());
        actions.add(refresh); actions.add(choose); actions.add(install);
        bottom.add(actions, BorderLayout.SOUTH);
        body.add(bottom, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        devices.setListData(new String[0]);
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override protected List<String> doInBackground() throws Exception { return ToolchainManager.adbDevices(line -> { }); }
            @Override protected void done() {
                try { devices.setListData(get().toArray(String[]::new)); }
                catch (Exception ex) { log.append(ex.getMessage() + "\n"); }
            }
        };
        worker.execute();
    }

    private void chooseApk() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("APK", "apk"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) apk.setText(chooser.getSelectedFile().getAbsolutePath());
    }

    private void install() {
        Path file = apk.getText().isBlank() ? null : Path.of(apk.getText().trim());
        if (file == null || !Files.isRegularFile(file)) { log.append(t("APK seçin.") + "\n"); return; }
        List<String> selected = devices.getSelectedValuesList();
        if (selected.isEmpty()) { log.append(t("Cihaz seçin.") + "\n"); return; }
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                for (String serial : selected) {
                    try { ToolchainManager.installApk(serial, file, line -> SwingUtilities.invokeLater(() -> log.append(line + "\n"))); }
                    catch (Exception ex) { SwingUtilities.invokeLater(() -> log.append(serial + ": " + ex.getMessage() + "\n")); }
                }
                return null;
            }
        };
        worker.execute();
    }
}
