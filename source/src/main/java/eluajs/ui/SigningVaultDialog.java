package eluajs.ui;

import eluajs.android.ToolchainManager;
import eluajs.util.AppPaths;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static eluajs.util.I18n.t;

/** Local keystore vault with fingerprint inspection. */
final class SigningVaultDialog extends JDialog {
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final JPasswordField password = Ui.passwordField();
    private final JTextField alias = Ui.field();
    private final JTextArea output = new JTextArea();
    private final JLabel status = Ui.label("", 10, Font.PLAIN, Theme.MUTED);

    SigningVaultDialog(Window owner) {
        super(owner, t("Signing Vault"), ModalityType.APPLICATION_MODAL);
        WindowChrome.install(this);
        setIconImage(Ui.appIcon());
        setSize(860, 620);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());
        add(titleBar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        refresh();
    }

    private JComponent titleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.TITLE_BAR);
        bar.setPreferredSize(new Dimension(1, 54));
        bar.setBorder(new EmptyBorder(0, 16, 0, 8));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        brand.setOpaque(false);
        brand.add(new JLabel(new ImageIcon(Ui.appIcon().getScaledInstance(24, 24, Image.SCALE_SMOOTH))));
        brand.add(Ui.label(t("Signing Vault"), 15, Font.BOLD, Theme.TEXT));
        bar.add(Ui.centerVertically(brand), BorderLayout.WEST);
        bar.add(Ui.centerVertically(WindowChrome.controls(this)), BorderLayout.EAST);
        WindowChrome.enableDrag(this, bar);
        return bar;
    }

    private JComponent content() {
        JPanel page = new JPanel(new BorderLayout(0, 10));
        page.setBackground(Theme.BG);
        page.setBorder(new EmptyBorder(16, 20, 16, 20));
        list.setBackground(Theme.PANEL);
        list.setForeground(Theme.TEXT);
        list.setSelectionBackground(Theme.SELECTION);
        list.setSelectionForeground(Theme.TEXT);
        list.setFont(Theme.mono(11));
        list.setFixedCellHeight(30);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, false);
                Path p = (Path) value;
                String date;
                try { date = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis())); }
                catch (Exception ex) { date = "-"; }
                label.setText(p.getFileName() + "   •   " + date);
                label.setToolTipText(p.toString());
                label.setBackground(selected ? Theme.SELECTION : Theme.PANEL);
                label.setForeground(Theme.TEXT);
                label.setBorder(new EmptyBorder(3, 7, 3, 7));
                return label;
            }
        });
        JScrollPane scroll = Ui.scroll(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        page.add(scroll, BorderLayout.CENTER);

        JPanel form = Ui.card(new BorderLayout(0, 8));
        form.setBorder(new EmptyBorder(10, 12, 10, 12));
        JPanel fields = new JPanel(new GridLayout(1, 2, 8, 0));
        fields.setOpaque(false);
        alias.putClientProperty("JTextField.placeholderText", t("Alias (isteğe bağlı)"));
        password.putClientProperty("JTextField.placeholderText", t("Keystore şifresi"));
        fields.add(alias);
        fields.add(password);
        form.add(fields, BorderLayout.NORTH);
        output.setEditable(false);
        output.setFont(Theme.mono(11));
        output.setBackground(Theme.CONSOLE);
        output.setForeground(Theme.TEXT_SOFT);
        output.setBorder(new EmptyBorder(8, 9, 8, 9));
        output.setPreferredSize(new Dimension(1, 120));
        form.add(Ui.scroll(output), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton inspect = Ui.button(t("Parmak izini göster"), true);
        inspect.addActionListener(e -> inspect());
        JButton folder = Ui.button(t("Klasörü Aç"), false);
        folder.addActionListener(e -> openFolder());
        JButton copy = Ui.button(t("Yolu Kopyala"), false);
        copy.addActionListener(e -> copyPath());
        JButton refresh = Ui.button(t("Yenile"), false);
        refresh.addActionListener(e -> refresh());
        JButton close = Ui.button(t("Kapat"), false);
        close.addActionListener(e -> dispose());
        actions.add(status); actions.add(copy); actions.add(folder); actions.add(refresh); actions.add(inspect); actions.add(close);
        form.add(actions, BorderLayout.SOUTH);
        page.add(form, BorderLayout.SOUTH);
        return page;
    }

    private void refresh() {
        model.clear();
        Path dir = AppPaths.config().resolve("keystores");
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".jks") || p.getFileName().toString().endsWith(".keystore"))
                        .sorted()
                        .forEach(model::addElement);
            } catch (Exception ex) {
                status.setText(ex.getMessage());
            }
        }
        status.setText(model.isEmpty() ? t("Vault boş. Release ekranından yeni keystore oluşturun.") : t("%d keystore", model.size()));
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void inspect() {
        Path file = list.getSelectedValue();
        if (file == null) return;
        char[] pass = password.getPassword();
        if (pass.length == 0) {
            status.setText(t("Keystore şifresini girin."));
            return;
        }
        status.setText(t("Kontrol ediliyor..."));
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() throws Exception {
                List<String> command = new ArrayList<>(List.of(ToolchainManager.keytoolExecutable().toString(),
                        "-list", "-v", "-keystore", file.toString(), "-storepass", new String(pass)));
                if (!alias.getText().isBlank()) { command.add("-alias"); command.add(alias.getText().trim()); }
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                StringBuilder text = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line).append('\n');
                }
                process.waitFor();
                return text.toString();
            }
            @Override protected void done() {
                try {
                    String text = get();
                    output.setText(text);
                    output.setCaretPosition(0);
                    status.setText(text.contains("SHA256:") || text.contains("SHA-256") ? t("Keystore doğrulandı.") : t("Parmak izi bulunamadı."));
                } catch (Exception ex) {
                    output.setText(ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
                    status.setText(t("Keystore okunamadı."));
                }
            }
        };
        worker.execute();
    }

    private void openFolder() {
        try {
            Path dir = AppPaths.config().resolve("keystores");
            Files.createDirectories(dir);
            Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) { status.setText(ex.getMessage()); }
    }

    private void copyPath() {
        Path file = list.getSelectedValue();
        if (file != null) Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(file.toString()), null);
    }
}
